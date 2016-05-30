package sword.blemesh.sdk.transport.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;

import java.util.UUID;

import timber.log.Timber;

/**
 * A basic BLE Peripheral device discovered by centrals
 * <p>
 * Created by davidbrodsky on 10/11/14.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BLEPeripheral {


    private UUID serviceUUID;
    private BluetoothAdapter btAdapter;
    private BluetoothLeAdvertiser advertiser;

    private boolean isAdvertising = false;

    private byte[] lastNotified;

    /**
     * Advertise Callback
     */
    private AdvertiseCallback mAdvCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            if (settingsInEffect != null) {
                Timber.d("Advertise success TxPowerLv="
                        + settingsInEffect.getTxPowerLevel()
                        + " mode=" + settingsInEffect.getMode());
            } else {
                Timber.d("Advertise success");
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            Timber.e("Advertising failed with code " + errorCode);
        }
    };

    // <editor-fold desc="Public API">

    public BLEPeripheral(@NonNull BluetoothAdapter adapter, @NonNull UUID serviceUUID) {
        this.btAdapter = adapter;
        this.serviceUUID = serviceUUID;
    }

    /**
     * Start the BLE Peripheral advertisement.
     */
    public void start() {
        startAdvertising();
    }

    public void stop() {
        stopAdvertising();
    }

    public boolean isAdvertising() {
        return isAdvertising;
    }

    // </editor-fold>

    //<editor-fold desc="Private API">

    private void startAdvertising() {
        if ((btAdapter != null) && (!isAdvertising)) {
            if (advertiser == null) {
                advertiser = btAdapter.getBluetoothLeAdvertiser();
            }
            if (advertiser != null) {
                Timber.d("Starting LE Advertising");
                advertiser.startAdvertising(createAdvSettings(), createAdvData(), mAdvCallback);
                isAdvertising = true;
            } else {
                Timber.d("Unable to access Bluetooth LE Advertiser. Device not supported");
            }
        } else {
            if (isAdvertising)
                Timber.d("Start Advertising called while advertising already in progress");
            else
                Timber.d("Start Advertising WTF error");
        }
    }

    private AdvertiseData createAdvData() {
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        builder.addServiceUuid(new ParcelUuid(serviceUUID));
        builder.setIncludeTxPowerLevel(false);
//        builder.setManufacturerData(0x1234578, manufacturerData);
        return builder.build();
    }

    private AdvertiseSettings createAdvSettings() {
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        builder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        builder.setConnectable(true);
        builder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        return builder.build();
    }

    private void stopAdvertising() {
        if (isAdvertising) {
            advertiser.stopAdvertising(mAdvCallback);
            isAdvertising = false;
        }
    }

    // </editor-fold>
}
