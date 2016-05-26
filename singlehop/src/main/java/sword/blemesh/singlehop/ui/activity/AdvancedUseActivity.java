package sword.blemesh.singlehop.ui.activity;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import sword.blemesh.sdk.app.BleMeshService;
import sword.blemesh.sdk.app.ui.BleMeshFragment;
import sword.blemesh.sdk.session.Peer;
import sword.blemesh.sdk.transport.Transport;
import sword.blemesh.sdk.transport.wifi.WifiTransport;

/**
 * An Activity (currently unused in this app) illustrating advanced / manual use of
 * BleMesh via {@link BleMeshFragment}
 * and {@link BleMeshService.ServiceBinder}
 */
public class AdvancedUseActivity extends AppCompatActivity
        implements BleMeshFragment.Callback {

    private BleMeshFragment bleMeshFragment;
    private BleMeshService.ServiceBinder BleMeshBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (bleMeshFragment == null) {
            bleMeshFragment
                    = BleMeshFragment.newInstance("Alice",  // username
                                                   "MyChat", // service name
                                                   this);    // BleMeshFragment.Callback

            // Control whether BleMeshService's lifecyle
            // is tied to this Activity (false) or should continue
            // to operate in the background (true). Default is false.
            bleMeshFragment.setShouldServiceContinueInBackground(true);

            getSupportFragmentManager().beginTransaction()
                    .add(bleMeshFragment, "BleMesh")
                    .commit();
        }
    }

    /** BleMeshFragment.BleMeshCallback */

    public void onServiceReady(@NonNull BleMeshService.ServiceBinder serviceBinder) {
        BleMeshBinder = serviceBinder;
        // You can now use serviceBinder to perform all sharing operations
        // and register for callbacks reporting network state.
        BleMeshBinder.setCallback(new BleMeshService.Callback() {

            @Override
            public void onDataRecevied(@NonNull BleMeshService.ServiceBinder binder,
                                       @Nullable byte[] data,
                                       @NonNull Peer sender,
                                       @Nullable Exception exception) {
                // Handle data received
            }

            @Override
            public void onDataSent(@NonNull BleMeshService.ServiceBinder binder,
                                   @Nullable byte[] data,
                                   @NonNull Peer recipient,
                                   @Nullable Exception exception) {
                // Handle data sent
            }

            @Override
            public void onPeerStatusUpdated(@NonNull BleMeshService.ServiceBinder binder,
                                            @NonNull Peer peer,
                                            @NonNull Transport.ConnectionStatus newStatus,
                                            boolean peerIsHost) {

                if (newStatus == Transport.ConnectionStatus.CONNECTED) {
                    BleMeshBinder.send("Hello!".getBytes(), peer);

                    if (peer.supportsTransportWithCode(WifiTransport.TRANSPORT_CODE))
                        BleMeshBinder.requestTransportUpgrade(peer);
                }
                // Handle peer disconnected
            }

            @Override
            public void onPeerTransportUpdated(@NonNull BleMeshService.ServiceBinder binder,
                                               @NonNull Peer peer,
                                               int newTransportCode,
                                               @Nullable Exception exception) {
                if (exception == null) {
                    // Successfully upgraded connection with peer to WiFi Transport
                    BleMeshBinder.send("Hello at high speed!".getBytes(), peer);
                }
            }
        });
    }

    public void onFinished(@Nullable Exception exception) {
        // This is currently unused, but will report an error
        // initializing the BleMeshService
    }
}