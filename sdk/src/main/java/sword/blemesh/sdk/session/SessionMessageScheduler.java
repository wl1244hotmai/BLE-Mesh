package sword.blemesh.sdk.session;

import sword.blemesh.sdk.mesh_graph.Peer;

/**
 * An item that schedules {@link sword.blemesh.sdk.session.SessionMessage}s for delivery
 * to a {@link Peer}
 *
 * Created by davidbrodsky on 3/14/15.
 */
public interface SessionMessageScheduler {

    public void sendMessage(SessionMessage message, Peer recipient);

}
