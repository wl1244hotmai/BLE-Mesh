package sword.blemesh.sdk.session;

import android.content.Context;

import com.google.common.base.Objects;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import sword.blemesh.sdk.mesh_graph.Peer;

/**
 * Representation of network identity. Closely related to {@link Peer}
 * Created by davidbrodsky on 2/22/15.
 */
public class IdentityMessage extends SessionMessage {

    public static final String HEADER_TYPE = "identity";

    /** Header keys */
    public static final String HEADER_ALIAS       = "alias";

    public static final String BODY_RSSI = "rssi";

    private Peer peer;

    /**
     * Convenience creator for deserialization
     */
    public static IdentityMessage fromHeaders(Map<String, Object> headers) {

        Peer peer = new Peer((String) headers.get(HEADER_ALIAS),
                             (String) headers.get(HEADER_MAC_ADDRESS),
                             new Date(),
                             -1,
                             0);

        return new IdentityMessage((String) headers.get(SessionMessage.HEADER_ID),
                                   peer);
    }

    public IdentityMessage(String id, Peer peer) {
        super(peer.getMacAddress(),id);
        this.peer = peer;
        init();
        serializeAndCacheHeaders();
    }

    /**
     * Constructor for own identity
     * @param context Context to determine transport capabilities
     * @param peer    peer to provide keypair, alias
     */
    public IdentityMessage(Context context, Peer peer) {
        super();
        this.peer = peer;
        init();
        serializeAndCacheHeaders();
    }

    private void init() {
        type = HEADER_TYPE;
    }

    public Peer getPeer() {
        return peer;
    }

    @Override
    protected HashMap<String, Object> populateHeaders() {
        HashMap<String, Object> headerMap = super.populateHeaders();

        headerMap.put(HEADER_ALIAS, peer.getAlias());

        return headerMap;
    }

    @Override
    public byte[] getBodyAtOffset(int offset, int length) {
        return null;
    }

    @Override
    public int hashCode() {
        // If we only target API 19+, we can move to java.util.Objects.hash
        return Objects.hashCode(headers.get(HEADER_TYPE),
                                headers.get(HEADER_BODY_LENGTH),
                                headers.get(HEADER_ID),
                                headers.get(HEADER_MAC_ADDRESS),
                                headers.get(HEADER_ALIAS));
    }

    @Override
    public boolean equals(Object obj) {

        if(obj == this) return true;
        if(obj == null) return false;

        if (getClass().equals(obj.getClass()))
        {
            final IdentityMessage other = (IdentityMessage) obj;

            // If we only target API 19+, we can move to the java.util.Objects.equals
            return super.equals(obj) &&
                    Objects.equal(getHeaders().get(HEADER_MAC_ADDRESS),
                            other.getHeaders().get(HEADER_MAC_ADDRESS)) &&
                    Objects.equal(getHeaders().get(HEADER_ALIAS),
                            other.getHeaders().get(HEADER_ALIAS));
        }

        return false;
    }

}
