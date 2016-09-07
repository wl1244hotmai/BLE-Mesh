package sword.blemesh.sdk.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import sword.blemesh.sdk.app.BleMeshService;
import sword.blemesh.sdk.mesh_graph.LocalPeer;
import sword.blemesh.sdk.transport.ble.BLEUtil;
import timber.log.Timber;

/**
 * Convenience fragment for interacting with BleMesh. Handles connecting to BleMesh Service,
 * prompting user to enable Bluetooth etc.
 *
 * Implementation classes
 * must implement {@link BleMeshFragment.Callback}
 */
public class BleMeshFragment extends Fragment implements ServiceConnection {

    public interface Callback {

        /**
         * Indicates BleMesh is ready
         */
        void onServiceReady(@NonNull BleMeshService.ServiceBinder serviceBinder);

        /**
         * Indicates the BleMesh service is finished.
         * This would occur if the user declined to enable required resources like Bluetooth
         */
        void onFinished(@Nullable Exception exception);

    }

    protected static final String ARG_USERNAME = "uname";
    protected static final String ARG_SERVICENAME = "sname";

    private Callback callback;
    private String username;
    private String servicename;
    private BleMeshService.ServiceBinder serviceBinder;
    private boolean didIssueServiceUnbind = false;
    private boolean serviceBound = false;  // Are we bound to the ChatService?
    private boolean bluetoothReceiverRegistered = false; // Are we registered for Bluetooth status broadcasts?
    private boolean operateInBackground = false;

    private AlertDialog mBluetoothEnableDialog;

    private LocalPeer localPeer;

    public static BleMeshFragment newInstance(String username, String serviceName, Callback callback) {

        Bundle bundle = new Bundle();
        bundle.putString(ARG_USERNAME, username);
        bundle.putString(ARG_SERVICENAME, serviceName);

        BleMeshFragment fragment = new BleMeshFragment();
        fragment.setArguments(bundle);
        fragment.setBleMeshCallback(callback);
        return fragment;
    }

    public BleMeshFragment() {
        super();
    }

    public void setBleMeshCallback(Callback callback) {
        this.callback = callback;
    }
    public BleMeshService.ServiceBinder getServiceBinder(){return serviceBinder;}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retain our instance across Activity re-creations unless added to back stack
        setRetainInstance(true);
        serviceBound = false; // onServiceDisconnected may not be called before fragment destroyed

        username = getArguments().getString(ARG_USERNAME);
        servicename = getArguments().getString(ARG_SERVICENAME);

        if (username == null || servicename == null)
            throw new IllegalStateException("username and servicename cannot be null");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!serviceBound) {
            didIssueServiceUnbind = false;
            startAndBindToService();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (serviceBinder != null) {
            serviceBinder.setActivityReceivingMessages(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (serviceBinder != null) {
            serviceBinder.setActivityReceivingMessages(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound && !didIssueServiceUnbind) {
            Timber.d("Unbinding service. %s", shouldServiceContinueInBackground() ? "service will continue in bg" : "service will be closed");
            didIssueServiceUnbind = true;
            unBindService();
            unregisterBroadcastReceiver();
            serviceBound = false;

            if (!shouldServiceContinueInBackground()){
                stopService();
            }
        }
    }

    /**
     * @return whether the BleMeshService should remain active after {@link #onStop()}
     * if false, the service will be re-started on {@link #onStart()}
     */
    public boolean shouldServiceContinueInBackground() {
        return operateInBackground;
    }

    public void setShouldServiceContinueInBackground(boolean shouldContinueInBackground) {
        operateInBackground = shouldContinueInBackground;
    }

    public void stopService() {
        Timber.d("Stopping service");
        Activity host = getActivity();
        Intent intent = new Intent(host, BleMeshService.class);
        host.stopService(intent);
    }

    private void startAndBindToService() {
        Timber.d("Starting service");
        Activity host = getActivity();
        Intent intent = new Intent(host, BleMeshService.class);
        host.startService(intent);
        host.bindService(intent, this, 0);
    }

    private void unBindService() {
        getActivity().unbindService(this);
    }

    private final BroadcastReceiver mBluetoothBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        if (mBluetoothEnableDialog != null && mBluetoothEnableDialog.isShowing()) {
                            mBluetoothEnableDialog.dismiss();
                        }
                        Timber.d("Bluetooth enabled");
                        checkDevicePreconditions();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        serviceBinder = (BleMeshService.ServiceBinder) iBinder;
        serviceBound = true;
        Timber.d("Bound to service");
        checkDevicePreconditions();

        serviceBinder.setActivityReceivingMessages(true);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Timber.d("Something Error");
        Timber.d("Unbound from service");
        //serviceBinder = null;
        serviceBound = false;
    }

    private void checkDevicePreconditions() {
        if (!BLEUtil.isBluetoothEnabled(getActivity())) {
            // Bluetooth is not Enabled.
            // await result in OnActivityResult
            registerBroadcastReceiver();
            showEnableBluetoothDialog();
        } else {
            // Bluetooth Enabled, Register primary identity
            serviceBinder.registerLocalUserWithService(username, servicename);

            if (callback != null) callback.onServiceReady(serviceBinder);
        }
    }

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        getActivity().registerReceiver(mBluetoothBroadcastReceiver, filter);
        bluetoothReceiverRegistered = true;
    }

    private void unregisterBroadcastReceiver() {
        if (bluetoothReceiverRegistered) {
            getActivity().unregisterReceiver(mBluetoothBroadcastReceiver);
            bluetoothReceiverRegistered = false;
        }
    }

    private void showEnableBluetoothDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Enable Bluetooth")
                .setMessage("This app requires Bluetooth on to function. May we enable Bluetooth?")
                .setPositiveButton("Enable Bluetooth", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mBluetoothEnableDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        mBluetoothEnableDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                        ((TextView) mBluetoothEnableDialog.findViewById(android.R.id.message)).setText("Enabling...");
                        BLEUtil.getManager(BleMeshFragment.this.getActivity()).getAdapter().enable();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (callback != null)
                            callback.onFinished(new UnsupportedOperationException("User declined to enable Bluetooth"));
                    }
                });
        builder.setCancelable(false);
        mBluetoothEnableDialog = builder.create();

        mBluetoothEnableDialog.show();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}
