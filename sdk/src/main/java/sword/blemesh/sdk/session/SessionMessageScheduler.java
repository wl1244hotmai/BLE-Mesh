package sword.blemesh.sdk.session;

/**
 * An item that schedules {@link sword.blemesh.sdk.session.SessionMessage}s for delivery
 * to a {@link sword.blemesh.sdk.session.Peer}
 *
 * Created by davidbrodsky on 3/14/15.
 */
public interface SessionMessageScheduler {

    public void sendMessage(SessionMessage message, Peer recipient);

}
