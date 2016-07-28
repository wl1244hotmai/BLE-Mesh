package sword.blemesh.sdk.app;

import sword.blemesh.sdk.session.DataTransferMessage;
import sword.blemesh.sdk.mesh_graph.Peer;
import sword.blemesh.sdk.session.SessionMessage;
import sword.blemesh.sdk.session.SessionMessageScheduler;

/**
 * An OutgoingTransfer wraps an outgoing data transfer.
 *
 * 1. Constructed with a byte[]
 * 2. Sends a DataTransferMessage
 *
 * Created by davidbrodsky on 3/13/15.
 */
public class OutgoingTransfer extends Transfer implements IncomingMessageListener, MessageDeliveryListener {

    public static enum State {

        /** Awaiting data transfer delivery" */
        AWAITING_DATA_ACK,

        /** Transfer completed */
        COMPLETE
    }

    private Peer next_reply_node;
    private SessionMessageScheduler messageSender;
    private State state;

    // <editor-fold desc="Outgoing Constructors">

    public OutgoingTransfer(byte[] data,
                            Peer recipient,
                            Peer next_reply_node,
                            SessionMessageScheduler messageSender) {

        init(next_reply_node, messageSender);

        transferMessage = DataTransferMessage.createOutgoing(null, recipient, data);
        messageSender.sendMessage(transferMessage, next_reply_node);

        state = State.AWAITING_DATA_ACK;
    }

    public OutgoingTransfer(Peer next_reply_node,
                            SessionMessage message,
                            SessionMessageScheduler messageSender){
        init(next_reply_node, messageSender);
        this.transferMessage = message;
        messageSender.sendMessage(transferMessage, next_reply_node);

        state = State.AWAITING_DATA_ACK;
    }


    // </editor-fold desc="Outgoing Constructors">

    private void init(Peer recipient, SessionMessageScheduler sender) {
        this.next_reply_node = recipient;
        this.messageSender = sender;
    }

    public String getTransferId() {
        if (transferMessage == null) return null;
        return (String) transferMessage.getHeaders().get(SessionMessage.HEADER_ID);
    }

    public Peer getNext_reply_node() {
        return next_reply_node;
    }

    @Override
    public boolean onMessageReceived(SessionMessage message, Peer recipient) {
        return false;
    }

    // </editor-fold desc="IncomingMessageInterceptor">

    // <editor-fold desc="MessageDeliveryListener">

    @Override
    public boolean onMessageDelivered(SessionMessage message, Peer recipient, Exception exception) {

        if (state == State.AWAITING_DATA_ACK && transferMessage != null && message.equals(transferMessage)) {

            state = State.COMPLETE;
            return false;
        }

        return true;
    }

    // </editor-fold desc="MessageDeliveryListener">

    @Override
    public boolean isComplete() {
        return state == State.COMPLETE;
    }
}
