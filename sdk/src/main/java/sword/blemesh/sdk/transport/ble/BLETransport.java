package sword.blemesh.sdk.transport.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.widget.Toast;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import sword.blemesh.sdk.R;
import sword.blemesh.sdk.transport.Transport;
import timber.log.Timber;

/**
 * Bluetooth Low Energy Transport. Requires Android 5.0.
 * <p>
 * Note that only the Central device reports device connection events to {@link #callback}
 * in this implementation.
 * See {@link #identifierUpdated(sword.blemesh.sdk.transport.ble.BLETransportCallback.DeviceType, String, sword.blemesh.sdk.transport.Transport.ConnectionStatus, Map)}
 * <p>
 * Created by davidbrodsky on 2/21/15.
 */

/**
 * Every Identifier gets a ByteBuffer that all outgoing data gets copied to, and
 * is read from in DEFAULT_MTU_BYTES increments for the actual sendData call.
 *
 * *** THOUGHTS ***
 *
 * Need to have buffering at SessionManager to throttle data sent from Session
 * to Transport to match data being sent from Transport
 *
 * Error recovery
 *
 * When an error happens on a Transport write we need to inform the SessionManager for resume
 *
 * What happens when the two devices fall out of sync. What happens when partial data is transferred.
 * How to re-establish Session at new offset
 */
public class BLETransport extends Transport implements BLETransportCallback {

    /**
     * BLE in android supports automatically divide long data into several segments.
     * while in practise, the upper limit may be 600 bytes.
     * here we change aitshare's DEFAULT_MTU_BYTES to DEFAULT_LONG_WRITE_BYTES,
     * from 155 t0 512.
     */
    public static final int DEFAULT_LONG_WRITE_BYTES = 512;

    @Deprecated
    public static final int DEFAULT_MTU_BYTES = 155;

    public static final int TRANSPORT_CODE = 1;
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final UUID serviceUUID;
    private final UUID dataUUID = UUID.fromString("72A7700C-859D-4317-9E35-D7F5A93005B1");

    /** Identifier -> Queue of outgoing buffers */
    private HashMap<String, ArrayDeque<byte[]>> outBuffers = new HashMap<>();

    private final BluetoothGattCharacteristic dataCharacteristic
            = new BluetoothGattCharacteristic(dataUUID,
            BluetoothGattCharacteristic.PROPERTY_READ |
                    BluetoothGattCharacteristic.PROPERTY_WRITE |
                    BluetoothGattCharacteristic.PROPERTY_INDICATE,

            BluetoothGattCharacteristic.PERMISSION_READ |
                    BluetoothGattCharacteristic.PERMISSION_WRITE);

    private BLECentral central;
    private BLEPeripheral peripheral;
    private BLEGattClients gattClients;
    private BLEGattServer gattServer;
    private BluetoothAdapter btAdapter;

    public BLETransport(@NonNull Context context,
                        @NonNull String serviceName,
                        @NonNull Transport.TransportCallback callback) {

        super(serviceName, callback);

        serviceUUID = generateUUIDFromString(serviceName);
        dataCharacteristic.addDescriptor(new BluetoothGattDescriptor(CLIENT_CHARACTERISTIC_CONFIG,
                BluetoothGattDescriptor.PERMISSION_WRITE |
                        BluetoothGattDescriptor.PERMISSION_READ));
        initBtAdaper(context);

        //abstract Gatt side from central class
        gattClients = new BLEGattClients(context, serviceUUID);
        gattClients.setTransportCallback(this);
        gattClients.requestNotifyOnCharacteristic(dataCharacteristic);

        gattServer = new BLEGattServer(context,serviceUUID);
        gattServer.setTransportCallback(this);
        gattServer.addCharacteristic(dataCharacteristic);
        gattServer.setServerCallback(gattClients);

        central = new BLECentral(btAdapter, serviceUUID);
        central.setCentralCallback(gattClients);

        if (isLollipop()) {
            peripheral = new BLEPeripheral(btAdapter, serviceUUID);
        }

    }

    private UUID generateUUIDFromString(String input) {
        String hexString = new String(Hex.encodeHex(DigestUtils.sha256(input)));
        StringBuilder uuid = new StringBuilder();
        // UUID has 32 hex 'digits'
        uuid.insert(0, hexString.substring(0, 32));

        uuid.insert(8, '-');
        uuid.insert(13, '-');
        uuid.insert(18, '-');
        uuid.insert(23, '-');
        Timber.d("Using UUID %s for string %s", uuid.toString(), input);
        return UUID.fromString(uuid.toString());
    }

    // <editor-fold desc="Transport">

    /**
     * Send data to the given identifiers. If identifier is unavailable data will be queued.
     * TODO: Callbacks to {@link sword.blemesh.sdk.transport.Transport.TransportCallback#dataSentToIdentifier(sword.blemesh.sdk.transport.Transport, byte[], String, Exception)}
     * should occur per data-sized chunk, not for each MTU-sized transmit.
     */
    @Override
    public boolean sendData(byte[] data, Set<String> identifiers) {
        boolean didSendAll = true;

        for (String identifier : identifiers) {
            boolean didSend = sendData(data, identifier);

            if (!didSend) didSendAll = false;
        }
        return didSendAll;
    }

    @Override
    public boolean sendData(@NonNull byte[] data, String identifier) {

        queueOutgoingData(data, identifier);

        if (gattClients.isConnectedTo(identifier))
            return transmitOutgoingDataForConnectedPeer(identifier);

        return false;
    }

    /**
     * there are three actions: advertise, scan, and open GattServer.
     * GattServer can either be used in Central or Peripheral,
     * So, the definition and action of GattServer should be independent of BLEPeripheral class.
     * therefore, we add new function called start;
     * which also begin advertise and scan, and start and setup GattServer.
     */
    @Override
    public void start() {
        openGattServer();
        advertise();
        scanForPeers();
    }

    @Override
    public void advertise() {
        openGattServer();
        if (isLollipop() && !peripheral.isAdvertising()) peripheral.start();
    }

    @Override
    public void scanForPeers() {
        openGattServer();
        if (!central.isScanning()) central.start();
    }

    private void openGattServer() {
        gattServer.openServer();
    }

    @Override
    public void stop() {
        if (isLollipop() && peripheral.isAdvertising()) peripheral.stop();
        if (central.isScanning()) central.stop();
        gattClients.disconnect();
        gattServer.closeServer();
    }

    @Override
    public int getTransportCode() {
        return TRANSPORT_CODE;
    }


    public int getLongWriteBytes() {
        return DEFAULT_LONG_WRITE_BYTES;
    }

    // </editor-fold desc="Transport">

    // <editor-fold desc="BLETransportCallback">

    @Override
    public void dataReceivedFromIdentifier(DeviceType deviceType, byte[] data, String identifier) {
        if (callback.get() != null)
            callback.get().dataReceivedFromIdentifier(this, data, identifier);
    }

    @Override
    public void dataSentToIdentifier(DeviceType deviceType, byte[] data, String identifier, Exception exception) {
        Timber.d("Got receipt for %d sent bytes", data.length);

        if (callback.get() != null)
            callback.get().dataSentToIdentifier(this, data, identifier, exception);
    }

    @Override
    public void identifierUpdated(DeviceType deviceType,
                                  String identifier,
                                  ConnectionStatus status,
                                  Map<String, Object> extraInfo) {

        Timber.d("identifierUpdated: %s status: %s", identifier, status.toString());
        if (callback.get() != null) {

            callback.get().identifierUpdated(this,
                    identifier,
                    status,
                    deviceType == DeviceType.GATT,  // If the central reported connection, the remote peer is the host
                    extraInfo);
        }

        //TODO: does it needed?
        if (status == ConnectionStatus.CONNECTED)
            transmitOutgoingDataForConnectedPeer(identifier);
    }

    // </editor-fold desc="BLETransportCallback">

    /**
     * Queue data for transmission to identifier
     */
    private void queueOutgoingData(byte[] data, String identifier) {
        if (!outBuffers.containsKey(identifier)) {
            outBuffers.put(identifier, new ArrayDeque<byte[]>());
        }

        int longWriteBytes = getLongWriteBytes();

        int readIdx = 0;
        while (readIdx < data.length) {

            if (data.length - readIdx > longWriteBytes) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(longWriteBytes);
                bos.write(data, readIdx, longWriteBytes);
                Timber.d("Adding %d byte chunk to queue", bos.size());
                outBuffers.get(identifier).add(bos.toByteArray());
                readIdx += longWriteBytes;
            } else {
                Timber.d("Adding %d byte chunk to queue", data.length);
                outBuffers.get(identifier).add(data);
                break;
            }
        }
    }

    // TODO: Don't think the boolean return type is meaningful here as partial success can't be handled
    private boolean transmitOutgoingDataForConnectedPeer(String identifier) {
        if (!outBuffers.containsKey(identifier)) return false;

        byte[] toSend;
        boolean didSendAll = true;
        while ((toSend = outBuffers.get(identifier).peek()) != null) {
            boolean didSend = false;
            if (gattClients.isConnectedTo(identifier)) {
                didSend = gattClients.write(toSend, dataCharacteristic.getUuid(), identifier);
            }

            if (didSend) {
                Timber.d("Sent %d byte chunk to %s. %d more chunks in queue", toSend.length, identifier, outBuffers.get(identifier).size() - 1);

                outBuffers.get(identifier).poll();
            } else {
                Timber.w("Failed to send %d bytes to %s", toSend.length, identifier);
                didSendAll = false;
                break;
            }
            break; // For now, only attempt one data chunk at a time. Wait delivery before proceeding
        }
        return didSendAll;
    }

    private static boolean isLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private void initBtAdaper(Context context) {
        // BLE check
        if (!BLEUtil.isBLESupported(context)) {
            Toast.makeText(context, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }

        // BT check
        BluetoothManager manager = BLEUtil.getManager(context);
        if (manager != null) {
            btAdapter = manager.getAdapter();
        }
        if (btAdapter == null) {
            Toast.makeText(context, R.string.bt_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}
