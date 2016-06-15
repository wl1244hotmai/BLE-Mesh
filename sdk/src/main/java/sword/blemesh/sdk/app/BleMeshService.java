package sword.blemesh.sdk.app;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import sword.blemesh.sdk.crypto.KeyPair;
import sword.blemesh.sdk.crypto.SodiumShaker;
import sword.blemesh.sdk.mesh_graph.LocalGraph;
import sword.blemesh.sdk.mesh_graph.PeersGraph;
import sword.blemesh.sdk.session.DataTransferMessage;
import sword.blemesh.sdk.mesh_graph.LocalPeer;
import sword.blemesh.sdk.mesh_graph.Peer;
import sword.blemesh.sdk.session.SessionManager;
import sword.blemesh.sdk.session.SessionMessage;
import sword.blemesh.sdk.transport.Transport;
import timber.log.Timber;

/**
 * Created by davidbrodsky on 11/4/14.
 */
public class BleMeshService extends Service implements ActivityRecevingMessagesIndicator,
                                                        SessionManager.SessionManagerCallback {

    public interface Callback {

        void onDataRecevied(@NonNull ServiceBinder binder,
                            @Nullable byte[] data,
                            @NonNull Peer sender,
                            @Nullable Exception exception);

        void onDataSent(@NonNull ServiceBinder binder,
                        @Nullable byte[] data,
                        @NonNull Peer recipient,
                        @Nullable Exception exception);

        void onPeerStatusUpdated(@NonNull ServiceBinder binder,
                                 @NonNull Peer peer,
                                 @NonNull Transport.ConnectionStatus newStatus,
                                 boolean peerIsHost);

        void onPeerTransportUpdated(@NonNull ServiceBinder binder,
                                    @NonNull Peer peer,
                                    int newTransportCode,
                                    @Nullable Exception exception);

    }

    private SessionManager sessionManager;
    private Callback callback;
    private boolean activityRecevingMessages;

    //TODO: （不确定）考虑删除inPeerTransfers、incomingMessageListeners，因为已经收到的信息不需要对信息的这次传输有什么管理；
    //TODO: （不确定）考虑修改outPeerTransfers、messageDeliveryListeners,向外发出的消息可能需要管理，查看其是否发送成功，但是此处outPeerTransfers等变量的维护存在问题；
    private BiMap<Peer, ArrayDeque<OutgoingTransfer>> outPeerTransfers = HashBiMap.create();
    private BiMap<Peer, ArrayDeque<IncomingTransfer>> inPeerTransfers = HashBiMap.create();
    private Set<IncomingMessageListener> incomingMessageListeners = new HashSet<>();
    private Set<MessageDeliveryListener> messageDeliveryListeners = new HashSet<>();

    private ServiceBinder binder;

    private Looper backgroundLooper;
    private BackgroundThreadHandler backgroundHandler;
    private Handler foregroundHandler;

    private LocalPeer localPeer;
    private LocalGraph mPeersGraph;

    /** Handler Messages */
    public static final int ADVERTISE     = 0;
    public static final int SCAN          = 1;
    public static final int SEND_MESSAGE  = 2;
    public static final int SHUTDOWN      = 3;

    @Override
    public void onCreate() {
        Timber.d("onCreate");
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("BleMeshService", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        backgroundLooper = thread.getLooper();
        backgroundHandler = new BackgroundThreadHandler(backgroundLooper);
        foregroundHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onDestroy() {
        Timber.d("Service destroyed");
        //TODO: 是否需要删除mPeersGraph?
        mPeersGraph = null;
        sessionManager.stop();
        backgroundLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (binder == null) binder = new ServiceBinder();
        Timber.d("Bind service");
        return binder;
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    /** ActivityReceivingMessagesIndicator */
    @Override
    public boolean isActivityReceivingMessages() {
        return activityRecevingMessages;
    }

    /** Binder through which Activities can interact with this Service */
    public class ServiceBinder extends Binder {

        public void registerLocalUserWithService(String userAlias, String serviceName) {
            KeyPair keyPair = SodiumShaker.generateKeyPair();
            localPeer = new LocalPeer(getApplicationContext(), keyPair, userAlias);
            //initialize graph using local peer.
            mPeersGraph = new LocalGraph(localPeer);
            if (sessionManager != null) sessionManager.stop();

            sessionManager = new SessionManager(BleMeshService.this, serviceName, localPeer, BleMeshService.this);
        }

        public LocalPeer getLocalPeer() {
            return localPeer;
        }

        public void advertiseLocalUser() {
            sessionManager.advertiseLocalPeer();
        }

        public void scanForOtherUsers() {
            sessionManager.scanForPeers();
        }

        public void stop() {
            sessionManager.stop();
        }

        public void setCallback(Callback callback) {
            BleMeshService.this.callback = callback;
        }

        public void send(byte[] data, Peer recipient) {
            addOutgoingTransfer(new OutgoingTransfer(data, recipient, sessionManager));
        }

        /** Get the current preferred available transport for the given peer
         *  This is generally the available transport with the highest bandwidth
         *
         *  @return either {@link sword.blemesh.sdk.transport.ble.BLETransport#TRANSPORT_CODE},
         *                 or -1 if none available.
         */
        public int getTransportCodeForPeer(Peer remotePeer) {
            return sessionManager.getTransportCodeForPeer(remotePeer);
        }

        /**
         * Set by Activity bound to this Service. If isActive is false, this Service
         * should post incoming messages as Notifications.
         *
         * Note: It seems more appropriate for this to simply be a convenience value for
         * a client application. e.g: The value is set by BleMeshFragment and the client
         * application can query the state via {@link #isActivityReceivingMessages()}
         * to avoid manually keeping track of such state themselves.
         */
        public void setActivityReceivingMessages(boolean receivingMessages) {
            activityRecevingMessages = receivingMessages;
        }

        public boolean isActivityReceivingMessages() {
            return activityRecevingMessages;
        }
    }

    private void addIncomingTransfer(IncomingTransfer transfer) {
        Peer recipient = transfer.getSender();

        incomingMessageListeners.add(transfer);
        messageDeliveryListeners.add(transfer);

        if (!inPeerTransfers.containsKey(recipient))
            inPeerTransfers.put(recipient, new ArrayDeque<IncomingTransfer>());

        inPeerTransfers.get(recipient).add(transfer);
    }

    private void addOutgoingTransfer(OutgoingTransfer transfer) {
        Peer recipient = transfer.getRecipient();

        incomingMessageListeners.add(transfer);
        messageDeliveryListeners.add(transfer);

        if (!outPeerTransfers.containsKey(recipient))
            outPeerTransfers.put(recipient, new ArrayDeque<OutgoingTransfer>());

        outPeerTransfers.get(recipient).add(transfer);
    }

    /** Handler that processes Messages on a background thread */
    private final class BackgroundThreadHandler extends Handler {
        public BackgroundThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
//            switch (msg.what) {
//                case ADVERTISE:
//                    Log.i(TAG, "handling connect");
//                    sessionManager.advertiseLocalPeer();
//                    break;
//                case SEND_MESSAGEE:
//                    mApp.sendPublicMessageFromPrimaryIdentity((String) msg.obj);
//                    break;
//                case SHUTDOWN:
//                    Log.i(TAG, "handling shutdown");
//                    mApp.makeUnavailable();
//
//                    // Stop the service using the startId, so that we don't stop
//                    // the service in the middle of handling another job
//                    stopSelf(msg.arg1);
//                    break;
//            }
        }
    }

    private @Nullable IncomingTransfer getIncomingTransferForFileTransferMessage(SessionMessage transferMessage,
                                                                                 Peer sender) {

        IncomingTransfer incomingTransfer = null;
        for (IncomingTransfer transfer : inPeerTransfers.get(sender)) {
            if (transferMessage instanceof DataTransferMessage) {
                // If we only target API 19+, we can move to the java.util.Objects.equals
                if (Objects.equal(transfer.getTransferId(), transferMessage.getHeaders().get(SessionMessage.HEADER_ID)))
                    incomingTransfer = transfer;
            } else
                throw new IllegalStateException("Only DataTransferMessage is supported!");
        }

        return incomingTransfer;
    }

    private @Nullable OutgoingTransfer getOutgoingTransferForFileTransferMessage(SessionMessage transferMessage,
                                                                                 Peer recipient) {

        OutgoingTransfer outgoingTransfer = null;
        for (OutgoingTransfer transfer : outPeerTransfers.get(recipient)) {
            if (transferMessage instanceof DataTransferMessage) {
                // If we only target API 19+, we can move to the java.util.Objects.equals
                if (Objects.equal(transfer.getTransferId(), transferMessage.getHeaders().get(SessionMessage.HEADER_ID)))
                    outgoingTransfer = transfer;
            } else
                throw new IllegalStateException("Only DataTransferMessage is supported!");
        }

        return outgoingTransfer;
    }

    // <editor-fold desc="SessionManagerCallback">

    @Override
    public void directPeerStatusUpdated(@NonNull final Peer peer, @NonNull final Transport.ConnectionStatus newStatus, final boolean isHost) {
        //更新单跳的新用户
        switch (newStatus) {
            case CONNECTED:
                mPeersGraph.newDirectRemote(peer);
                break;

            case DISCONNECTED:

                break;
        }


        foregroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null)
                    callback.onPeerStatusUpdated(binder, peer, newStatus, isHost);
                else
                    Timber.w("Could not report peer status update, no callback registered");
            }
        });

    }

    @Override
    public void messageReceivingFromPeer(@NonNull SessionMessage message, @NonNull final Peer recipient, final float progress) {
        // currently unused
    }

    @Override
    public void messageReceivedFromPeer(@NonNull SessionMessage message, @NonNull final Peer sender) {
        Timber.d("Got %s message from %s", message.getType(), sender.getAlias());
        Iterator<IncomingMessageListener> iterator = incomingMessageListeners.iterator();
        IncomingMessageListener listener;

        while (iterator.hasNext()) {
            listener = iterator.next();

            if (!listener.onMessageReceived(message, sender))
                iterator.remove();

        }

        final IncomingTransfer incomingTransfer;
        if(message.getType().equals(DataTransferMessage.HEADER_TYPE)) {

            incomingTransfer = new IncomingTransfer((DataTransferMessage) message, sender);
            // No action is required for DataTransferMessage. Report complete
            foregroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null)
                        callback.onDataRecevied(binder, incomingTransfer.getBodyBytes(), sender, null);
                }
            });
        }
    }

    @Override
    public void messageSendingToPeer(@NonNull SessionMessage message, @NonNull Peer recipient, float progress) {
        // currently unused
    }

    @Override
    public void messageSentToPeer(@NonNull SessionMessage message, @NonNull final Peer recipient, Exception exception) {
        Timber.d("Sent %s to %s", message.getType(), recipient.getAlias());
        Iterator<MessageDeliveryListener> iterator = messageDeliveryListeners.iterator();
        MessageDeliveryListener listener;

        while (iterator.hasNext()) {
            listener = iterator.next();

            if (!listener.onMessageDelivered(message, recipient, exception))
                iterator.remove();
        }

        final OutgoingTransfer outgoingTransfer;
        if (message.getType().equals(DataTransferMessage.HEADER_TYPE)) {
            outgoingTransfer = getOutgoingTransferForFileTransferMessage(message, recipient);
            // No action is required for DataTransferMessage. Report complete
            foregroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        assert outgoingTransfer != null;
                        callback.onDataSent(binder, outgoingTransfer.getBodyBytes(), recipient, null);
                    }
                }
            });
        }
    }

    // </editor-fold desc="SessionManagerCallback">
}