package sword.blemesh.sdk.transport.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import timber.log.Timber;

/**
 * Created by 力 on 2016/5/29.
 * class for GATT Server
 */
public class BLEGattServer {
    
    public interface ServerCallback {
        void onConnectionStateChange(BluetoothDevice device,int newState);
    }

    private Set<BluetoothGattCharacteristic> characterisitics = new HashSet<>();
    /**
     * Map of connected device addresses to devices
     */
    private BiMap<String, BluetoothDevice> connectedDevices = HashBiMap.create();
    private BiMap<String, byte[]> receivedDatas = HashBiMap.create();

    private Context context;
    private UUID serviceUUID;

    private BluetoothGattServer gattServer;
    private BluetoothGattServerCallback gattServerCallback;
    private BLETransportCallback transportCallback;
    private ServerCallback mServerCallback;

    public BLEGattServer(Context context, UUID serviceUUID) {
        this.context = context;
        this.serviceUUID = serviceUUID;
    }

    public void setTransportCallback(BLETransportCallback callback) {
        transportCallback = callback;
    }

    public void setServerCallback(ServerCallback serverCallback) {
        mServerCallback = serverCallback;
    }

    public void addCharacteristic(BluetoothGattCharacteristic characteristic) {
        characterisitics.add(characteristic);
    }

    public void openServer() {
        startGattServer();
    }

    public void closeServer() {
        if (gattServer != null)
            gattServer.close();
    }

    public BluetoothGattServer getGattServer() {
        return gattServer;
    }

    private void startGattServer() {
        BluetoothManager manager = BLEUtil.getManager(context);
        if (gattServerCallback == null)
            gattServerCallback = new BluetoothGattServerCallback() {

                @Override
                public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        if (connectedDevices.containsKey(device.getAddress())) {
                            // We're already connected (should never happen). Cancel connection
                            Timber.d("Denied connection. Already connected from " + device.getAddress());
                            gattServer.cancelConnection(device);
                            return;
                        } else {
                            // Allow connection to proceed. Mark device connected
                            Timber.d("Accepted connection to " + device.getAddress());
                            connectedDevices.put(device.getAddress(), device);
                            mServerCallback.onConnectionStateChange(device, newState);
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        // We've disconnected
                        Timber.d("Disconnected from " + device.getAddress());
                        connectedDevices.remove(device.getAddress());
                        receivedDatas.remove(device.getAddress());
                        //TODO:
                        mServerCallback.onConnectionStateChange(device, newState);
                    }

                    super.onConnectionStateChange(device, status, newState);
                }

                @Override
                public void onServiceAdded(int status, BluetoothGattService service) {
                    Timber.i("onServiceAdded", service.toString());
                    super.onServiceAdded(status, service);
                }

                @Override
                public void onCharacteristicWriteRequest(BluetoothDevice remoteCentral, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                    Timber.d("onCharacteristicWriteRequest for request %d char %s offset %d length %d responseNeeded %b", requestId, characteristic.getUuid().toString().substring(0, 3), offset, value == null ? 0 : value.length, responseNeeded);

                    BluetoothGattCharacteristic localCharacteristic = gattServer.getService(serviceUUID).getCharacteristic(characteristic.getUuid());
                    if (localCharacteristic != null) {

                        // Must send response before notifying callback (which might trigger data send before remote central received ack)
                        if (responseNeeded) {
                            boolean success = gattServer.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                            Timber.d("Ack'd write with success " + success);
                        }

                        if (!preparedWrite && transportCallback != null)
                            transportCallback.dataReceivedFromIdentifier(BLETransportCallback.DeviceType.GATT_SERVER,
                                    value,
                                    remoteCentral.getAddress());
                        else if (preparedWrite) {
                            byte[] receivedData = receivedDatas.get(remoteCentral.getAddress());
                            int dataOffset = receivedData == null ? 0 : receivedData.length;
                            byte[] temp = receivedData;
                            receivedData = new byte[dataOffset + value.length];
                            if (temp != null)
                                System.arraycopy(temp, 0, receivedData, 0, temp.length);
                            System.arraycopy(value, 0, receivedData, dataOffset, value.length);
                            receivedDatas.put(remoteCentral.getAddress(), receivedData);
                        }

                    } else {
                        Timber.d("CharacteristicWriteRequest. Unrecognized characteristic " + characteristic.getUuid().toString());
                        // Request for unrecognized characteristic. Send GATT_FAILURE
                        try {
                            boolean success = gattServer.sendResponse(remoteCentral, requestId, BluetoothGatt.GATT_FAILURE, 0, null);

                            Timber.w("SendResponse", "write request gatt failure success " + success);
                        } catch (NullPointerException e) {
                            // On Nexus 5 possibly an issue in the Broadcom IBluetoothGatt implementation
                            Timber.w("SendResponse", "NPE on write request gatt failure");
                        }
                    }
                    super.onCharacteristicWriteRequest(remoteCentral, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                }

                @Override
                public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                    Timber.d("onDescriptorReadRequest %s", descriptor.toString());
                    super.onDescriptorReadRequest(device, requestId, offset, descriptor);
                }

                @Override
                public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                    Timber.d("onDescriptorWriteRequest %s", descriptor.toString());
                    if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) && responseNeeded) {
                        boolean success = gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        Timber.d("Sent Indication sub response with success %b", success);
                    }
                    super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
                }

                @Override
                public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
                    Timber.d("onExecuteWrite " + device.toString() + " requestId " + requestId);
                    super.onExecuteWrite(device, requestId, execute);

                    byte[] receivedData = receivedDatas.get(device.getAddress());
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, receivedData.length, receivedData);

                    if (transportCallback != null)
                        transportCallback.dataReceivedFromIdentifier(BLETransportCallback.DeviceType.GATT_SERVER,
                                receivedData,
                                device.getAddress());
                    receivedDatas.remove(device.getAddress());
                }

                @Override
                public void onNotificationSent(BluetoothDevice device, int status) {
                    Timber.d("onNotificationSent");
                }
            };

        Timber.d("Start opening GATT Server");
        gattServer = manager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            Timber.e("Unable to retrieve BluetoothGattServer");
            return;
        }
        setupGattServer();
    }

    private void setupGattServer() {
        assert (gattServer != null);

        BluetoothGattService service = new BluetoothGattService(serviceUUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        for (BluetoothGattCharacteristic characteristic : characterisitics) {
            service.addCharacteristic(characteristic);
        }

        gattServer.addService(service);
    }

}
