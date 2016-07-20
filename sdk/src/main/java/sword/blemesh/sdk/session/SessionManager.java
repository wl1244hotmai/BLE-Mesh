package sword.blemesh.sdk.session;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import hugo.weaving.DebugLog;
import sword.blemesh.sdk.mesh_graph.LocalPeer;
import sword.blemesh.sdk.mesh_graph.Peer;
import sword.blemesh.sdk.transport.Transport;
import sword.blemesh.sdk.transport.TransportState;
import sword.blemesh.sdk.transport.ble.BLETransport;
import timber.log.Timber;

/**
 * Created by davidbrodsky on 2/21/15.
 */
public class SessionManager implements Transport.TransportCallback,
                                       SessionMessageDeserializer.SessionMessageDeserializerCallback,
                                       SessionMessageScheduler {

    private static final boolean VERBOSE = true;

    public interface SessionManagerCallback {

        void directPeerStatusUpdated(@NonNull Peer peer,
                                     @NonNull Transport.ConnectionStatus newStatus,
                                     boolean isHost);

        void messageReceivingFromPeer(@NonNull SessionMessage message,
                                      @NonNull Peer recipient,
                                      float progress);

        void messageReceivedFromPeer(@NonNull SessionMessage message,
                                     @NonNull Peer recipient);

        void messageSendingToPeer(@NonNull SessionMessage message,
                                  @NonNull Peer recipient,
                                  float progress);

        void messageSentToPeer(@NonNull SessionMessage message,
                               @NonNull Peer recipient,
                               @Nullable Exception exception);

    }

    private Context                                   context;
    private String                                    serviceName;
    private SortedSet<Transport>                      transports;
    private LocalPeer localPeer;
    private IdentityMessage                           localIdentityMessage;
    private SessionManagerCallback                    callback;
    private HashMap<String, Transport>                identifierTransports       = new HashMap<>();
    private HashMap<Peer, SortedSet<Transport>>       peerTransports             = new HashMap<>();
    private BiMap<String, SessionMessageDeserializer> identifierReceivers        = HashBiMap.create();
    private BiMap<String, SessionMessageSerializer>   identifierSenders          = HashBiMap.create();
    private HashMap<String, Integer>                  identifierRssis             = new HashMap<>();
    private final HashMap<String, Peer>               identifiedPeers            = new HashMap<>();
    private final SetMultimap<Peer, String>           peerIdentifiers            = HashMultimap.create();
    private final SetMultimap<String,String>          senderAddressMessageIDs = HashMultimap.create();
    private Set<String>                               identifyingPeers           = new HashSet<>();
    private Set<String>                               hostIdentifiers            = new HashSet<>();
//    private HashMap<Peer, Transport>                  peerUpgradeRequests        = new HashMap<>();
    private TransportState                            baseTransportState         = new TransportState(false, false, false);

    // <editor-fold desc="Public API">

    public SessionManager(Context context,
                          String serviceName,
                          LocalPeer localPeer,
                          SessionManagerCallback callback) {

        this.context     = context;
        this.serviceName = serviceName;
        this.localPeer   = localPeer;
        this.callback    = callback;

        localIdentityMessage = new IdentityMessage(this.context, this.localPeer);

        initializeTransports(serviceName);
    }

    public String getServiceName() {
        return serviceName;
    }

    public void startTransport(){
        transports.first().start();
        baseTransportState = new TransportState(baseTransportState.isStopped, true, baseTransportState.wasScanning);
    }

    public void advertiseLocalPeer() {
        // Only advertise on the "base" (first) transport
        transports.first().advertise();
        baseTransportState = new TransportState(baseTransportState.isStopped, true, baseTransportState.wasScanning);
    }

    public void scanForPeers() {
        // Only scan on the "base" (first) transport
        transports.first().scanForPeers();
        baseTransportState = new TransportState(baseTransportState.isStopped, baseTransportState.wasAdvertising, true);
    }
    @DebugLog
    public synchronized void stop() {
        // Stop all running transports
        for (Transport transport : transports)
            transport.stop();

        reset();
    }

    @DebugLog
    public void broadcastMessage(SessionMessage message,Peer receiveFrom){
        Set<Peer> adjacents = getAvailablePeers();
        for (Peer adjacent : adjacents) {
            if(!adjacent.equals(receiveFrom)) {
                Timber.d("broadcast message to Peer %s %s", adjacent.getAlias(), adjacent.getMacAddress());
                sendMessage(message, adjacent);
            }
        }
    }

    @DebugLog
    public void broadcastMessage(SessionMessage message){
        Set<Peer> adjacents = getAvailablePeers();
        for (Peer adjacent : adjacents) {
            Timber.d("broadcast message to Peer %s %s", adjacent.getAlias(),adjacent.getMacAddress());
            sendMessage(message,adjacent);
        }
    }

    /**
     * Send a message to the given recipient. If the recipient is not currently available,
     * delivery will occur next time the peer is available
     */
    // TODO : This  method needs to be re-evaluated to be more robust
    // If preferred transport not available, queue on base transport?
    @DebugLog
    public synchronized void sendMessage(SessionMessage message, Peer recipient) {

        Set<String> recipientIdentifiers = peerIdentifiers.get(recipient);
        String targetRecipientIdentifier = null;

        if (recipientIdentifiers == null || recipientIdentifiers.size() == 0) { // TODO: Does HashMultiMap return null or empty collection?
            Timber.e("No Identifiers for peer %s", recipient.getAlias());
            return;
        }

        Transport transport = getBaseTransportForPeer(recipient);

        if (transport == null) {
            Timber.e("No transport for %s", recipient.getAlias());
            return;
        }

        for (String recipientIdentifier : recipientIdentifiers) {
            if (identifierTransports.get(recipientIdentifier).equals(transport))
                targetRecipientIdentifier = recipientIdentifier;
        }

        if (targetRecipientIdentifier == null) {
            Timber.e("Could not find identifier for %s on preferred transport %d", recipient.getAlias(), transport.getTransportCode());
            return;
            // TODO : Fall back to base transport
        }

        if (!identifierSenders.containsKey(targetRecipientIdentifier))
            identifierSenders.put(targetRecipientIdentifier, new SessionMessageSerializer(message));
        else
            identifierSenders.get(targetRecipientIdentifier).queueMessage(message);

        SessionMessageSerializer sender = identifierSenders.get(targetRecipientIdentifier);

        transport.sendData(sender.getNextChunk(transport.getLongWriteBytes()),
                               targetRecipientIdentifier);
//        else
//            Timber.d("Send queued. No transport available for identifier %s", targetRecipientIdentifier);

        // If no transport for the peer is available, data will be sent next time peer is available
    }

    public Set<Peer> getAvailablePeers() {
        return new HashSet<Peer>(identifiedPeers.values());
    }

    /**
     * Get the current preferred available transport for the given peer
     * This is generally the available transport with the highest bandwidth
     *
     * @return either {@link sword.blemesh.sdk.transport.ble.BLETransport#TRANSPORT_CODE},
     *                 or -1 if none available.
     */
    public int getTransportCodeForPeer(Peer peer) {
        Transport preferredTransport = getPreferredTransportForPeer(peer);
        return preferredTransport != null ? preferredTransport.getTransportCode() :
                -1;
    }

    // </editor-fold desc="Public API">

    // <editor-fold desc="Private API">

    private void reset() {

        identifierTransports.clear();
        peerTransports.clear();
        identifierReceivers.clear();
        identifierSenders.clear();
        identifiedPeers.clear();
        identifyingPeers.clear();
        hostIdentifiers.clear();
        peerIdentifiers.clear();
        senderAddressMessageIDs.clear();
        baseTransportState = new TransportState(false, false, false);
    }

    private void initializeTransports(String serviceName) {
        // First transport is considered "base" transport
        // Additional transports are considered supplementary and
        // will only be activated upon request
        transports = new TreeSet<>();
        transports.add(new BLETransport(context, serviceName, this));
        //TODO: if other transport such as WFD can support Mesh in the future, it can be added.
    }

    private boolean shouldIdentifyPeer(String identifier) {
        // TODO : Might have banned peers etc.
        return !identifyingPeers.contains(identifier);
    }

    private @Nullable Transport getBaseTransportForPeer(Peer peer) {

        if (!peerTransports.containsKey(peer) || peerTransports.get(peer).size() == 0)
            return null;

        // Return the Transport with the highest value (largest MTU)
        return peerTransports.get(peer)
                .first();
    }

    /**
     * Get the best preferred transport.
     * usually the preferred transport is more fast.
     * such as WFD is preferred than BLE.
     * After WFD support Mesh, this method may be useful
     */
    private @Nullable Transport getPreferredTransportForPeer(Peer peer) {

        if (!peerTransports.containsKey(peer) || peerTransports.get(peer).size() == 0)
            return null;

        // Return the Transport with the highest value (largest MTU)
        return peerTransports.get(peer)
                .last();
    }

    private void registerTransportForIdentifier(Transport transport, String identifier) {
        if (identifierTransports.containsKey(identifier)) {
            //Timber.w("Transport already registered for identifier %s", identifier);
            return;
        }

        Timber.d("Transported added for identifier %s", identifier);
        identifierTransports.put(identifier, transport);
    }

    private void registerTransportForPeer(Transport transport, Peer peer) {
        if (!peerTransports.containsKey(peer))
            peerTransports.put(peer, new TreeSet<Transport>());

        boolean newTransport = peerTransports.get(peer).add(transport);
        if (newTransport) {
            Timber.d("Transport added for peer %s", peer.getAlias());
        }
    }

    // </editor-fold desc="Private API">

    // <editor-fold desc="TransportCallback">

    @Override
    @DebugLog
    public synchronized void dataReceivedFromIdentifier(Transport transport, byte[] data, String identifier) {

        // An asymmetric transport may not receive connection events
        // so we use this opportunity to associate the identifier with its transport
        registerTransportForIdentifier(transport, identifier);

        if (!identifierReceivers.containsKey(identifier))
            identifierReceivers.put(identifier, new SessionMessageDeserializer(context, this));

        //process received data in SessionMessageDeserializer
        identifierReceivers.get(identifier)
                           .dataReceived(data);
    }

    @Override
    @DebugLog
    public synchronized void dataSentToIdentifier(Transport transport, byte[] data, String identifier, Exception exception) {

        if (exception != null) {
            Timber.w("Data failed to send to %s", identifier);
            return;
        }

        SessionMessageSerializer sender = identifierSenders.get(identifier);

        if (sender == null) {
            Timber.w("No sender for dataSentToIdentifier to %s", identifier);
            return;
        }

        Pair<SessionMessage, Float> messagePair = sender.ackChunkDelivery();

        if (messagePair != null) {

            byte[] toSend = sender.getNextChunk(transport.getLongWriteBytes());
            if (toSend != null)
                transport.sendData(toSend, identifier);

            SessionMessage message = messagePair.first;
            float progress = messagePair.second;

            if (VERBOSE) Timber.d("%d %s bytes (%.0f pct) sent to %s",
                                  data.length,
                                  message.getType(),
                                  progress * 100,
                                  identifier);

            if (progress == 1 && message.equals(localIdentityMessage)) {
                Timber.d("Local identity acknowledged by recipient");
                identifyingPeers.add(identifier);
            }

            Peer recipient = identifiedPeers.get(identifier);
            if (recipient != null) {
                if (progress == 1) {

                    // Process completely sent BleMesh messages, pass non-BleMesh messages
                    // up via messageSentToPeer
                    if (message.equals(localIdentityMessage)) {

                        if (peerIdentifiers.get(recipient).size() == 1) {
                            Timber.d("Reporting peer connected after last id sent");
                            callback.directPeerStatusUpdated(recipient,
                                                       Transport.ConnectionStatus.CONNECTED,
                                                       hostIdentifiers.contains(identifier));
                        }

                    }  else {
                        callback.messageSentToPeer(message,
                                identifiedPeers.get(identifier),
                                null);
                    }

                } else {

                    callback.messageSendingToPeer(message,
                            identifiedPeers.get(identifier),
                            progress);
                }
            } else
                Timber.w("Cannot report %s message send, %s not yet identified",
                    message.getType(), identifier);


        } else
            Timber.w("No current message corresponding to dataSentToIdentifier");
    }

    @Override
    @DebugLog
    public synchronized void identifierUpdated(Transport transport,
                                  String identifier,
                                  Transport.ConnectionStatus status,
                                  boolean peerIsHost,
                                  Map<String, Object> extraInfo) {
        switch(status) {
            case CONNECTED:
                Timber.d("Connected to %s", identifier);
                if (peerIsHost) hostIdentifiers.add(identifier);

                if(extraInfo!=null){
                    int remoteRssi = (Integer)extraInfo.get("rssi");
                    identifierRssis.put(identifier,remoteRssi);
                }

                // Only one peer (client) needs to initiate identification
                if (peerIsHost && shouldIdentifyPeer(identifier)) {
                    Timber.d("Queuing identity to %s", identifier);
                    if (!identifierSenders.containsKey(identifier)) {
                        identifierSenders.put(identifier, new SessionMessageSerializer(localIdentityMessage));
                    } else
                        Timber.w("Outgoing messages already exist for unidentified peer %s", identifier);
                }

                registerTransportForIdentifier(transport, identifier);

                // Send outgoing messages to peer
                SessionMessageSerializer sender = identifierSenders.get(identifier);

                if (sender != null && sender.getCurrentMessage() != null) {

                    boolean sendingIdentity = sender.getCurrentMessage() instanceof IdentityMessage;

                    byte[] toSend = sender.getNextChunk(transport.getLongWriteBytes());
                    if (toSend == null) return;

                    if (transport.sendData(toSend, identifier)) {
                        if (sendingIdentity) {
                            Timber.d("Sent identity to %s", identifier);
                        }
                    } else
                        Timber.w("Failed to send %s message to new peer %s",
                                sender.getCurrentMessage().getType(),
                                identifier);
                }

                break;

            case DISCONNECTED:
                if (peerIsHost) hostIdentifiers.remove(identifier);

                Peer peer = identifiedPeers.get(identifier);

                if (peer != null) {

                    Timber.d("Disconnected from %s (%s). Had %d transports",
                            identifier,
                            peer.getAlias(),
                            peerIdentifiers.get(peer).size());

                    peerTransports.get(peer).remove(transport);
                    Set<String> identifiers = peerIdentifiers.get(peer);
                    identifiers.remove(identifier);

                    // If all transports for this peer are disconnected, send disconnect
                    if (identifiers.size() == 0) {
                        Timber.d("Disconnected from %s", peer.getAlias());

                        callback.directPeerStatusUpdated(identifiedPeers.get(identifier),
                                Transport.ConnectionStatus.DISCONNECTED,
                                peerIsHost);

                    }

                } else
                    Timber.w("Could not report disconnection, peer not identified");

                identifierTransports.remove(identifier);
                identifyingPeers.remove(identifier);
                identifiedPeers.remove(identifier);
                identifierSenders.remove(identifier);
                identifierReceivers.remove(identifier);
                identifierRssis.remove(identifier);
                break;
        }
    }

    // </editor-fold desc="TransportCallback">

    // <editor-fold desc="SessionMessageReceiverCallback">

    @Override
    public void onHeaderReady(SessionMessageDeserializer receiver, SessionMessage message) {

        String senderIdentifier = identifierReceivers.inverse().get(receiver);
        Timber.d("Received header for %s message from %s", message.getType(), senderIdentifier);
    }

    @Override
    public void onBodyProgress(SessionMessageDeserializer receiver, SessionMessage message, float progress) {

        String senderIdentifier = identifierReceivers.inverse().get(receiver);
        if (VERBOSE) Timber.d("Received %s message with progress %f from %s", message.getType(), progress, senderIdentifier);

        callback.messageReceivingFromPeer(message, identifiedPeers.get(senderIdentifier), progress);
    }

    @Override
    @DebugLog
    public void onComplete(SessionMessageDeserializer receiver, SessionMessage message, Exception e) {

        // Process messages belonging to the BleMesh framework and propagate
        // application level messages via our callback

        String senderIdentifier = identifierReceivers.inverse().get(receiver);

        if (e == null) {

            Timber.d("Received complete %s message from %s", message.getType(), senderIdentifier);

            if (message instanceof IdentityMessage) {

                //set the rssi for direct remote device.
                Peer peer = ((IdentityMessage) message).getPeer();
                peer.setRssi( -identifierRssis.get(senderIdentifier)); //change minus rssi to absolute value

                peerIdentifiers.put(peer, senderIdentifier);

                boolean sentIdentityToSender = identifyingPeers.contains(senderIdentifier);
                boolean newIdentity = !identifiedPeers.containsKey(senderIdentifier); // should never be false

                identifyingPeers.remove(senderIdentifier);
                identifiedPeers.put(senderIdentifier, peer);

                Transport identifierTransport = identifierTransports.get(senderIdentifier);
                registerTransportForPeer(identifierTransport, peer);

                if (newIdentity) {
                    Timber.d("Received #%s identifier for %s. %s", String.valueOf(peerIdentifiers.get(peer).size()),
                                                                 peer.getAlias(),
                                                                 sentIdentityToSender ? "" : "Responding with own.");
                    // As far as upper layers are concerned, connection events occur when the remote
                    // peer is identified.
                    if (!sentIdentityToSender)
                        sendMessage(localIdentityMessage, peer); // Report peer connected after identity send ack'd
                    else if (peerIdentifiers.get(peer).size() == 1) // If peer is already connected via another transport, don't re-notify
                        callback.directPeerStatusUpdated(peer, Transport.ConnectionStatus.CONNECTED, hostIdentifiers.contains(senderIdentifier));
                }

            } else if (identifiedPeers.containsKey(senderIdentifier) && !senderAddressMessageIDs.get(message.getMac_address()).contains(message.getID())) {
                // This message is not involved in the Session layer, so we notify the next layer up
                senderAddressMessageIDs.put(message.getMac_address(),message.getID());
                callback.messageReceivedFromPeer(message, identifiedPeers.get(senderIdentifier));

            } else {
                if(!identifiedPeers.containsKey(senderIdentifier))
                    Timber.w("Received complete non-identity message from unidentified peer");
                if(senderAddressMessageIDs.get(message.getMac_address()).contains(message.getID()))
                    Timber.d("Had received this message with id %s before", message.getID());
            }

        } else {
            Timber.d("Incoming message from %s failed with error '%s'", senderIdentifier, e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    // </editor-fold desc="SessionMessageReceiverCallback">

}
