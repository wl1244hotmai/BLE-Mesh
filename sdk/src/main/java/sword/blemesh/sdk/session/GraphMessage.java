package sword.blemesh.sdk.session;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import sword.blemesh.sdk.mesh_graph.PeersGraph;

/**
 * Created by 力 on 2016/6/15.
 * Message for graph infomation
 */
public class GraphMessage extends SessionMessage{

    public static final int ACTION_JOIN = 0x01;
    public static final int ACTION_LEFT = 0x02;
    public static final int SINGLE_CAST = 0x03;
    public static final int BROADCAST   = 0x04;
    public static final String HEADER_TYPE = "graph_info";
    public static final String HEADER_EXTRA = "extra";
    public static final String HEADER_LOCAL_MACADDRESS = "local_mac_address";
    public static final String HEADER_REMOTE_ACTION = "action";
    public static final String HEADER_CAST_FORM = "cast";
    public static final String BODY_VERTEX = "body_vertex";
    public static final String BODT_EDGES = "body_edges";

    private Map<String, Object> extraHeaders;
    private int action;
    private int cast;
    private String localMacAddress;

    private byte[] dataBytes;

    // <editor-fold desc="Incoming Constructors">

    GraphMessage(@NonNull Map<String, Object> headers,
                        @Nullable byte[] body) {

        super((String)headers.get(SessionMessage.HEADER_MAC_ADDRESS),
                (String) headers.get(SessionMessage.HEADER_ID));
        init();
        this.headers      = headers;
        bodyLengthBytes   = (int) headers.get(HEADER_BODY_LENGTH);
        status            = body == null ? Status.HEADER_ONLY : Status.COMPLETE;
        action            = (int) headers.get(HEADER_REMOTE_ACTION);
        cast              = (int) headers.get(HEADER_CAST_FORM);
        if (body != null){
            setDataBody(body);
        }

        serializeAndCacheHeaders();
    }

    // </editor-fold desc="Incoming Constructors">

    // <editor-fold desc="Outgoing Constructors">

    public static GraphMessage createOutgoing(@Nullable Map<String, Object> extraHeaders,
                                              int action,int cast, @Nullable PeersGraph peersGraph) {

        return new GraphMessage(peersGraph, action, cast, extraHeaders);
    }

    // To avoid confusion between the incoming constructor which takes a
    // Map of the completely deserialized headers and byte payload, we hide
    // this contstructor behind the static creator 'createOutgoing'
    private GraphMessage(@Nullable PeersGraph peersGraph,
                         int action,
                         int cast,
                         @Nullable Map<String, Object> extraHeaders) {
        super();
        this.action = action;
        this.cast = cast;
        this.extraHeaders = extraHeaders;
        init();
        if (peersGraph != null) {
            setPeersGraph(peersGraph);
            bodyLengthBytes = dataBytes.length;
        }
        serializeAndCacheHeaders();
    }

    // </editor-fold desc="Outgoing Constructors">

    private void init() {
        type = HEADER_TYPE;
    }

    public void setDataBody(@NonNull byte[] body) {
        dataBytes = body;
        status = Status.COMPLETE;
    }

    public void setPeersGraph(@Nullable PeersGraph peersGraph) {
        assert peersGraph != null;

        JSONObject graphJSONObject = new JSONObject();
        try {
            graphJSONObject.put(BODT_EDGES,peersGraph.toEdgeJSONOBject());
            graphJSONObject.put(BODY_VERTEX,peersGraph.toVertexJSONObject());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        dataBytes = graphJSONObject.toString().getBytes();
        status = Status.COMPLETE;
    }

    public PeersGraph getPeersGraph(){
        byte[] body = getBodyAtOffset(0,getBodyLengthBytes());
        JSONObject remoteGraphJSONObject = null;
        try {
            assert body != null;
            System.out.println(new String(body));
            remoteGraphJSONObject = new JSONObject(new String(body));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        assert (remoteGraphJSONObject!=null);

        JSONObject vertexJSONObject = remoteGraphJSONObject.optJSONObject(BODY_VERTEX);
        JSONObject edgesJSONObject = remoteGraphJSONObject.optJSONObject(BODT_EDGES);

        return new PeersGraph(vertexJSONObject,edgesJSONObject);
    }

    public int getAction(){return action;}

    @Override
    protected HashMap<String, Object> populateHeaders() {
        HashMap<String, Object> headerMap = super.populateHeaders();
        headerMap.put(HEADER_REMOTE_ACTION,action);
        headerMap.put(HEADER_CAST_FORM,cast);
        if (extraHeaders != null) {
            headerMap.put(HEADER_EXTRA, extraHeaders);
        }
        return headerMap;
    }

    @Nullable
    @Override
    public byte[] getBodyAtOffset(int offset, int length) {
        if (offset > bodyLengthBytes - 1) return null;

        int bytesToRead = Math.min(length, bodyLengthBytes - offset);
        byte[] result = new byte[bytesToRead];

        ByteBuffer dataBuffer = ByteBuffer.wrap(dataBytes);
        dataBuffer.position(offset);
        dataBuffer.get(result, 0, bytesToRead);

        return result;
    }
}
