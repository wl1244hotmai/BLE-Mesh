package sword.blemesh.sdk.session;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import sword.blemesh.sdk.mesh_graph.Peer;

/**
 * Created by davidbrodsky on 2/22/15.
 * Modified by Wei Li to support multi-hop route and forward on 2016.7.27
 */
public class DataTransferMessage extends SessionMessage {

    public static final int TTL_INITIAL_VALUE = 6;
    public static final String HEADER_TYPE = "datatransfer";
    public static final String HEADER_DESC = "message_destination";
    public static final String HEADER_TTL = "TTL";
    public static final String HEADER_EXTRA = "extra";

    private ByteBuffer dataBuffer;
    private String desc_mac_address;
    private Map<String, Object> extraHeaders;
    private int TTL; //Like TTL in internet, initial value is 10 and when TTL == 0, drop this message.

    // <editor-fold desc="Incoming Constructors">

    DataTransferMessage(@NonNull Map<String, Object> headers,
                        @Nullable byte[] body) {

        super((String)headers.get(SessionMessage.HEADER_MAC_ADDRESS),
                (String) headers.get(SessionMessage.HEADER_ID));
        init();
        this.headers      = headers;
        this.bodyLengthBytes   = (int) headers.get(HEADER_BODY_LENGTH);
        this.desc_mac_address = (String) headers.get(HEADER_DESC);
        this.TTL = (int)headers.get(HEADER_TTL) - 1;
        status            = body == null ? Status.HEADER_ONLY : Status.COMPLETE;

        if (body != null)
            setBody(body);

        serializeAndCacheHeaders();

    }

    // </editor-fold desc="Incoming Constructors">

    // <editor-fold desc="Outgoing Constructors">

    public static DataTransferMessage createOutgoing(@Nullable Map<String, Object> extraHeaders,
                                                     @NonNull Peer recipient,
                                                     @Nullable byte[] data) {

        return new DataTransferMessage(data, recipient,extraHeaders);
    }

    // To avoid confusion between the incoming constructor which takes a
    // Map of the completely deserialized headers and byte payload, we hide
    // this contstructor behind the static creator 'createOutgoing'
    private DataTransferMessage(@Nullable byte[] data,
                                @NonNull Peer recipient,
                                @Nullable Map<String, Object> extraHeaders) {
        super();
        this.desc_mac_address = recipient.getMacAddress();
        this.extraHeaders = extraHeaders;
        this.TTL = TTL_INITIAL_VALUE;
        init();
        if (data != null) {
            setBody(data);
            bodyLengthBytes = data.length;
        }
        serializeAndCacheHeaders();

    }

    // </editor-fold desc="Outgoing Constructors">

    private void init() {
        type = HEADER_TYPE;
    }

    @Override
    protected HashMap<String, Object> populateHeaders() {
        HashMap<String, Object> headerMap = super.populateHeaders();
        headerMap.put(HEADER_DESC,desc_mac_address);
        headerMap.put(HEADER_TTL,TTL);
        if (extraHeaders != null)
            headerMap.put(HEADER_EXTRA, extraHeaders);

        // The following three lines should be deleted
//        headerMap.put(HEADER_TYPE,        type);
//        headerMap.put(HEADER_BODY_LENGTH, bodyLengthBytes);
//        headerMap.put(HEADER_ID,          id);

        return headerMap;
    }

    public void setBody(@NonNull byte[] body) {
        if (dataBuffer != null)
            throw new IllegalStateException("Attempted to set existing message body");

        dataBuffer = ByteBuffer.wrap(body);
        status = Status.COMPLETE;
    }

    @Override
    public byte[] getBodyAtOffset(int offset, int length) {

        if (offset > bodyLengthBytes - 1) return null;

        int bytesToRead = Math.min(length, bodyLengthBytes - offset);
        byte[] result = new byte[bytesToRead];

        dataBuffer.position(offset);
        dataBuffer.get(result, 0, bytesToRead);

        return result;
    }

    public int getTTL(){
        return this.TTL;
    }

    public String getDesc_mac_address(){
        return this.desc_mac_address;
    }

}
