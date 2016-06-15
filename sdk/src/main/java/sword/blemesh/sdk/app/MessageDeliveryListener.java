package sword.blemesh.sdk.app;

import sword.blemesh.sdk.mesh_graph.Peer;
import sword.blemesh.sdk.session.SessionMessage;

/**
 * An item that listens for outgoing {@link sword.blemesh.sdk.session.SessionMessage} delivery events.
 *
 * Created by davidbrodsky on 3/14/15.
 */
public interface MessageDeliveryListener {

    /**
     * Called whenever an outgoing message is delivered.
     *
     * @return true if this listener should continue receiving delivery events. if false
     * the listener will not receive further events.
     *
     */
    public boolean onMessageDelivered(SessionMessage message, Peer recipient, Exception exception);
}
