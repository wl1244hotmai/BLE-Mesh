package sword.blemesh.sdk.transport.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanResult;
import android.content.Context;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.net.UnknownServiceException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import sword.blemesh.sdk.DataUtil;
import sword.blemesh.sdk.transport.Transport;
import timber.log.Timber;

/**
 * Created by 力 on 2016/5/29.
 * class for BleGatt
 */
public class BLEGattClients implements BLECentral.CentralCallback, BLEGattServer.ServerCallback {
    private final Set<UUID> notifyUUIDs = new HashSet<>();

    /**
     * Peripheral MAC Address -> Set of characteristics
     */
    private final HashMap<String, HashSet<BluetoothGattCharacteristic>> discoveredCharacteristics = new HashMap<>();

    /**
     * Peripheral MAC Address -> Peripheral
     */
    private final BiMap<String, BluetoothGatt> connectedDevices = HashBiMap.create();

    /**
     * Intended to prevent multiple simultaneous connection requests
     * and very whether this device is central or peripheral
     */
    private final Set<String> centralScannedDevices = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * Intended to prevent multiple simultaneous connection requests
     * and very whether this device is central or peripheral
     */
    private final Set<String> peripheralReceivedDevices = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private Context context;
    private UUID serviceUUID;
    private BLETransportCallback transportCallback;

    //<editor-fold desc="BLEGattClients initialization"
    public BLEGattClients(Context context, UUID serviceUUID) {
        this.context = context;
        this.serviceUUID = serviceUUID;
    }

    public void setTransportCallback(BLETransportCallback callback) {
        this.transportCallback = callback;
    }

    public void requestNotifyOnCharacteristic(BluetoothGattCharacteristic characteristic) {
        notifyUUIDs.add(characteristic.getUuid());
    }

    //</editor-fold>

    // <editor-fold desc="GattCallback implements"
    private BluetoothGattCallback getGattCallback() {
        return new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

                synchronized (connectedDevices) {

                    // It appears that certain events (like disconnection) won't have a GATT_SUCCESS status
                    // even when they proceed as expected, at least with the Motorola bluetooth stack
                    if (status != BluetoothGatt.GATT_SUCCESS)
                        Timber.w("onConnectionStateChange with newState %d and non-success status %s", newState, gatt.getDevice().getAddress());

                    Set<BluetoothGattCharacteristic> characteristicSet;

                    switch (newState) {
                        case BluetoothProfile.STATE_DISCONNECTING:
                            Timber.d("Disconnecting from " + gatt.getDevice().getAddress());

                            characteristicSet = discoveredCharacteristics.get(gatt.getDevice().getAddress());
                            for (BluetoothGattCharacteristic characteristic : characteristicSet) {
                                if (notifyUUIDs.contains(characteristic.getUuid())) {
                                    Timber.d("Attempting to unsubscribe on disconneting");
                                    setIndictaionSubscription(gatt, characteristic, false);
                                }
                            }
                            discoveredCharacteristics.remove(gatt.getDevice().getAddress());

                            break;

                        case BluetoothProfile.STATE_DISCONNECTED:
                            Timber.d("Disconnected from " + gatt.getDevice().getAddress());

                            BLETransportCallback.DeviceType mDeviceType = null;
                            if (centralScannedDevices.contains(gatt.getDevice().getAddress())) {
                                mDeviceType = BLETransportCallback.DeviceType.GATT;
                            } else if (peripheralReceivedDevices.contains(gatt.getDevice().getAddress())) {
                                //TODO: Should Peripheral's connection report put here or BLEGattServer.class?
                                mDeviceType = BLETransportCallback.DeviceType.GATT_SERVER;
                            }

                            assert (mDeviceType!=null);
                            if (transportCallback != null)
                                transportCallback.identifierUpdated(mDeviceType,
                                        gatt.getDevice().getAddress(),
                                        Transport.ConnectionStatus.DISCONNECTED,
                                        null);

                            connectedDevices.remove(gatt.getDevice().getAddress());
                            centralScannedDevices.remove(gatt.getDevice().getAddress());

                            characteristicSet = discoveredCharacteristics.get(gatt.getDevice().getAddress());
                            if (characteristicSet != null) { // Have we handled unsubscription on DISCONNECTING?
                                for (BluetoothGattCharacteristic characteristic : characteristicSet) {
                                    if (notifyUUIDs.contains(characteristic.getUuid())) {
                                        Timber.d("Attempting to unsubscribe before disconnet");
                                        setIndictaionSubscription(gatt, characteristic, false);
                                    }
                                }
                                // Gatt will be closed on result of descriptor write
                            } else
                                gatt.close();

                            discoveredCharacteristics.remove(gatt.getDevice().getAddress());

                            break;

                        case BluetoothProfile.STATE_CONNECTED:
                            boolean discovering = gatt.discoverServices();
                            Timber.d("Connected to %s. Discovering services %b", gatt.getDevice().getAddress(), discovering);
                            break;
                    }

                    super.onConnectionStateChange(gatt, status, newState);
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                Timber.d("Got MTU (%d bytes) for device %s. Was changed successfully: %b",
                        mtu,
                        gatt.getDevice().getAddress(),
                        status == BluetoothGatt.GATT_SUCCESS);
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS)
                    Timber.d("Discovered services");
                else
                    Timber.d("Discovered services appears unsuccessful with code " + status);
                // TODO: Keep this here to examine characteristics
                // eventually we should get rid of the discoverServices step
                boolean foundService = false;
                try {
                    List<BluetoothGattService> serviceList = gatt.getServices();
                    for (BluetoothGattService service : serviceList) {
                        if (service.getUuid().equals(serviceUUID)) {
                            Timber.d("Discovered Service");
                            foundService = true;
                            HashSet<BluetoothGattCharacteristic> characteristicSet = new HashSet<>();
                            characteristicSet.addAll(service.getCharacteristics());
                            discoveredCharacteristics.put(gatt.getDevice().getAddress(), characteristicSet);

                            for (BluetoothGattCharacteristic characteristic : characteristicSet) {
                                if (notifyUUIDs.contains(characteristic.getUuid())) {
                                    setIndictaionSubscription(gatt, characteristic, true);
                                }
                            }
                        }
                    }
                    if (foundService) {
                        synchronized (connectedDevices) {
                            connectedDevices.put(gatt.getDevice().getAddress(), gatt);
                        }
                    }
                } catch (Exception e) {
                    Timber.d("Exception analyzing discovered services " + e.getLocalizedMessage());
                    e.printStackTrace();
                }
                if (!foundService)
                    Timber.d("Could not discover chat service!");
                super.onServicesDiscovered(gatt, status);
            }

            /**
             * Subscribe or Unsubscribe to/from indication of a peripheral's characteristic.
             *
             * After calling this method you must await the result via
             * {@link #onDescriptorWrite(BluetoothGatt, BluetoothGattDescriptor, int)}
             * before performing any other peripheral actions.
             */
            private void setIndictaionSubscription(BluetoothGatt peripheral,
                                                   BluetoothGattCharacteristic characteristic,
                                                   boolean enable) {

                boolean success = peripheral.setCharacteristicNotification(characteristic, enable);
                Timber.d("Request notification %s %s with sucess %b", enable ? "set" : "unset", characteristic.getUuid().toString(), success);
                BluetoothGattDescriptor desc = characteristic.getDescriptor(BLETransport.CLIENT_CHARACTERISTIC_CONFIG);
                desc.setValue(enable ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                boolean desSuccess = peripheral.writeDescriptor(desc);
                Timber.d("Wrote descriptor with success %b", desSuccess);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                          int status) {

                Timber.d("onDescriptorWrite");
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    if (Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                        Timber.d("enabled indications successfully.");
                        boolean beginReadRssi = gatt.readRemoteRssi();
                        Timber.d("Start read Rssi of remote revice %b", beginReadRssi);

                    } else if (Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        Timber.d("disabled indications successfully. Closing gatt");
                        gatt.close();
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Timber.d("onCharacteristicChanged %s with %d bytes", characteristic.getUuid().toString().substring(0, 5),
                        characteristic.getValue().length);

                if (transportCallback != null)
                    transportCallback.dataReceivedFromIdentifier(BLETransportCallback.DeviceType.GATT,
                            characteristic.getValue(),
                            gatt.getDevice().getAddress());

                super.onCharacteristicChanged(gatt, characteristic);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt,
                                              BluetoothGattCharacteristic characteristic, int status) {

                Timber.d("onCharacteristicWrite with %d bytes", characteristic.getValue().length);
                Exception exception = null;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    String msg = "Write was not successful with code " + status;
                    Timber.w(msg);
                    exception = new UnknownServiceException(msg);
                }

                if (transportCallback != null)
                    transportCallback.dataSentToIdentifier(BLETransportCallback.DeviceType.GATT,
                            characteristic.getValue(),
                            gatt.getDevice().getAddress(),
                            exception);
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                if(status == BluetoothGatt.GATT_SUCCESS  && transportCallback != null){
                    Timber.d("%s rssi: %d", gatt.getDevice().getAddress(), rssi);
                    super.onReadRemoteRssi(gatt, rssi, status);

                    //Get device type of remote
                    BLETransportCallback.DeviceType mDeviceType = null;
                    if (centralScannedDevices.contains(gatt.getDevice().getAddress())) {
                        mDeviceType = BLETransportCallback.DeviceType.GATT;
                    } else if (peripheralReceivedDevices.contains(gatt.getDevice().getAddress())) {
                        //TODO: Peripheral's connection report can put to GattServer to realize it。
                        mDeviceType = BLETransportCallback.DeviceType.GATT_SERVER;
                    }
                    assert (mDeviceType != null);

                    Map<String, Object> extraInfo = new HashMap<>();
                    extraInfo.put("rssi", rssi);
                    transportCallback.identifierUpdated(mDeviceType,
                            gatt.getDevice().getAddress(),
                            Transport.ConnectionStatus.CONNECTED,
                            extraInfo);
                }

            }

        };
    }
    // </editor-fold>

    // <editor-fold desc="Public API">
    public boolean write(byte[] data,
                         UUID characteristicUuid,
                         String deviceAddress) {

        BluetoothGattCharacteristic discoveredCharacteristic = null;

        for (BluetoothGattCharacteristic characteristic : discoveredCharacteristics.get(deviceAddress)) {
            if (characteristic.getUuid().equals(characteristicUuid))
                discoveredCharacteristic = characteristic;
        }

        if (discoveredCharacteristic == null) {
            Timber.w("No characteristic with uuid %s discovered for device %s", characteristicUuid, deviceAddress);
            return false;
        }

        discoveredCharacteristic.setValue(data);

        if ((discoveredCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) !=
                BluetoothGattCharacteristic.PROPERTY_WRITE)
            throw new IllegalArgumentException(String.format("Requested write on Characteristic %s without Notify Property",
                    characteristicUuid.toString()));

        BluetoothGatt recipient = connectedDevices.get(deviceAddress);
        if (recipient != null) {
            boolean success = recipient.writeCharacteristic(discoveredCharacteristic);
            // write type should be 2 (Default)
            Timber.d("Wrote %d bytes with type %d to %s with success %b", data.length, discoveredCharacteristic.getWriteType(), deviceAddress, success);
            return success;
        }
        Timber.w("Unable to write " + deviceAddress);
        return false;
    }

    public boolean readRssi(String deviceAddress){
        BluetoothGatt remote = connectedDevices.get(deviceAddress);
        Timber.d("readRssi from %s", remote.getDevice().getAddress());
        return remote.readRemoteRssi();
    }

    public void disconnect() {
        synchronized (connectedDevices) {
            for (BluetoothGatt peripheral : connectedDevices.values()) {
                peripheral.disconnect();
            }
        }
    }

    public BiMap<String, BluetoothGatt> getConnectedDeviceAddresses() {
        return connectedDevices;
    }

    public boolean isConnectedTo(String deviceAddress) {
        return connectedDevices.containsKey(deviceAddress);
    }
//</editor-fold>

    // <editor-fold desc="Private API">

    private void logCharacteristic(BluetoothGattCharacteristic characteristic) {
        StringBuilder builder = new StringBuilder();
        builder.append(characteristic.getUuid().toString().substring(0, 3));
        builder.append("... instance: ");
        builder.append(characteristic.getInstanceId());
        builder.append(" properties: ");
        builder.append(characteristic.getProperties());
        builder.append(" permissions: ");
        builder.append(characteristic.getPermissions());
        builder.append(" value: ");
        if (characteristic.getValue() != null)
            builder.append(DataUtil.bytesToHex(characteristic.getValue()));
        else
            builder.append("null");

        if (characteristic.getDescriptors().size() > 0) builder.append("descriptors: [\n");
        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
            builder.append("{\n");
            builder.append(descriptor.getUuid().toString());
            builder.append(" permissions: ");
            builder.append(descriptor.getPermissions());
            builder.append("\n value: ");
            if (descriptor.getValue() != null)
                builder.append(DataUtil.bytesToHex(descriptor.getValue()));
            else
                builder.append("null");
            builder.append("\n}");
        }
        if (characteristic.getDescriptors().size() > 0) builder.append("]");
        Timber.d(builder.toString());
    }

    // </editor-fold>

    // <editor-fold desc="BLECentral.CentralCallback implements">

    /**
     * callback from BLECentral
     *
     * @param callbackType
     * @param scanResult
     */
    @Override
    public void onScan(int callbackType, ScanResult scanResult) {
        if (connectedDevices.containsKey(scanResult.getDevice().getAddress())) {
            // If we're already connected, forget it
            //Timber.d("Denied connection. Already connected to  " + scanResult.getDevice().getAddress());
            return;
        }

        if (centralScannedDevices.contains(scanResult.getDevice().getAddress())) {
            // If we're already connected, forget it
            //Timber.d("Denied connection. Already connecting to  " + scanResult.getDevice().getAddress());
            return;
        }

        centralScannedDevices.add(scanResult.getDevice().getAddress());
        Timber.d("GATT Initiating connection to " + scanResult.getDevice().getAddress());
        scanResult.getDevice().connectGatt(context, false, getGattCallback());
    }
    // </editor-fold>

    //<editor-fold desc="BLEGattServer.ServerCallback implements"
    @Override
    public void onConnectionStateChange(BluetoothDevice device,int newState) {

        switch (newState){
            case BluetoothProfile.STATE_CONNECTED:
                /**
                 * If Local device is not scanner ( advertiser instead),
                 * then remote device is scanner,
                 * so we can connect its gatt server
                 */
                if (!centralScannedDevices.contains(device.getAddress())) {
                    peripheralReceivedDevices.add(device.getAddress());
                    device.connectGatt(context,false,getGattCallback());
                }
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                peripheralReceivedDevices.remove(device.getAddress());
                break;
        }
    }
    // </editor-fold>

}
