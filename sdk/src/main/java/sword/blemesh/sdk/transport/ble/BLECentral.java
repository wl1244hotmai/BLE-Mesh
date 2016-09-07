package sword.blemesh.sdk.transport.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import timber.log.Timber;

/**
 * A basic BLE Central device that discovers peripherals.
 * <p>
 * Upon connection to a Peripheral this device performs a few initialization steps in order:
 * 1. Requests an MTU
 * 2. (On response to the MTU request) discovers services
 * 3. (On response to service discovery) reports connection
 * <p>
 * Created by davidbrodsky on 10/2/14.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BLECentral {

    public interface CentralCallback {
        void onScan(int callbackType, ScanResult scanResult);
    }

    public static final String TAG = "BLECentral";

    private UUID serviceUUID;

    private BluetoothAdapter btAdapter;
    private ScanCallback scanCallback;
    private CentralCallback mCentralCallback;
    private BluetoothLeScanner scanner;
    private boolean isScanning = false;

    // <editor-fold desc="Public API">

    public BLECentral(@NonNull BluetoothAdapter btAdapter, @NonNull UUID serviceUUID) {
        this.btAdapter = btAdapter;
        this.serviceUUID = serviceUUID;
    }

    public void start() {
        startScanning();
    }

    public void stop() {
        stopScanning();

    }

    public boolean isScanning() {
        return isScanning;
    }

    public void setCentralCallback(CentralCallback centralCallback) {
        this.mCentralCallback = centralCallback;
    }

    // </editor-fold>

    //<editor-fold desc="Private API">

    private void setScanCallback(ScanCallback callback) {
        if (callback != null) {
            scanCallback = callback;
            return;
        }
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult scanResult) {
                mCentralCallback.onScan(callbackType, scanResult);
            }

            @Override
            public void onScanFailed(int i) {
                Timber.e("Scan failed with code " + i);
            }
        };
    }

    private void startScanning() {
        if ((btAdapter != null) && (!isScanning)) {
            if (scanner == null) {
                scanner = btAdapter.getBluetoothLeScanner();
            }
            if (scanCallback == null) setScanCallback(null);

            scanner.startScan(createScanFilters(), createScanSettings(), scanCallback);
            isScanning = true;
            Timber.d("Scanning started successfully"); // TODO : This is a lie but I can't find a way to be notified when scan is successful aside from BluetoothGatt Log
            //Toast.makeText(context, context.getString(R.string.scan_started), Toast.LENGTH_SHORT).show();
        }
    }

    private List<ScanFilter> createScanFilters() {
        ScanFilter.Builder builder = new ScanFilter.Builder();
        builder.setServiceUuid(new ParcelUuid(serviceUUID));
        ArrayList<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(builder.build());
        return scanFilters;
    }

    private ScanSettings createScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        return builder.build();
    }

    private void stopScanning() {
        if (isScanning) {
            Timber.d("stopAdvertising");
            scanner.stopScan(scanCallback);
            scanner = null;
            isScanning = false;
        }
    }

    //</editor-fold>
}
