package sword.blemesh.sdk.mesh_graph;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Created by 力 on 2016/6/2.
 */
public class PeersGraph{

    protected LinkedHashMap<String, Peer> vertexList;
    protected LinkedHashMap<String, LinkedHashMap<String, Integer>> edgeMatrix;

    public PeersGraph() {
        vertexList = new LinkedHashMap<>();
        edgeMatrix = new LinkedHashMap<>();
    }

    /**
     * Static method, Construct PeersGraph from received graph message
     */
    public static PeersGraph newRemoteGraph(JSONObject vertexJSONObject,JSONObject edgesJSONObject){
        return new PeersGraph(vertexJSONObject,edgesJSONObject);
    }

    /**
     * Construct PeersGraph from received graph message
     */
    public PeersGraph(JSONObject vertexJSONObject,JSONObject edgesJSONObject){
        vertexList = new LinkedHashMap<>();
        edgeMatrix = new LinkedHashMap<>();

        Iterator vertexItor = vertexJSONObject.keys();
        Iterator edgesItor = edgesJSONObject.keys();

        while(vertexItor.hasNext()){
            String key = (String) vertexItor.next();
            JSONObject peerJSONObject = (JSONObject) vertexJSONObject.opt(key);
            Peer peer = new Peer(peerJSONObject);
            vertexList.put(key,peer);
        }
        while(edgesItor.hasNext()){
            String src = (String) edgesItor.next();
            JSONObject edgeRowJSONObject = (JSONObject) edgesJSONObject.opt(src);
            LinkedHashMap<String,Integer> edgeRow = new LinkedHashMap<>();
            Iterator edgeRowItor = edgeRowJSONObject.keys();
            while(edgeRowItor.hasNext()){
                String desc = (String) edgeRowItor.next();
                edgeRow.put(desc,(Integer)edgeRowJSONObject.opt(desc));
            }
            edgeMatrix.put(src,edgeRow);
        }

    }

    public void insertVertex(Peer node) {
        //remove if from removedPeers
        vertexList.put(node.getMacAddress(), node);
    }

    public void deleteVertex(Peer node) {
        vertexList.remove(node.getMacAddress());
    }

    public void deleteVertex(String nodeAddress) {
        vertexList.remove(nodeAddress);
    }

    public void deleteEdge(String address_1, String address_2) {
        edgeMatrix.get(address_1).remove(address_2);
        edgeMatrix.get(address_2).remove(address_1);
    }

    public boolean hasVertex(Peer node) {
        return vertexList.containsKey(node.getMacAddress());
    }

    public JSONObject toVertexJSONObject(){
        JSONObject vertexJSONObject = new JSONObject();
        for(String key:vertexList.keySet()){
            try {
                vertexJSONObject.put(key,vertexList.get(key).toJSONObject());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return vertexJSONObject;
    }

    public JSONObject toEdgeJSONOBject(){
        return new JSONObject(edgeMatrix);
    }

    public LinkedHashMap<String, Peer> getVertexList() {
        return vertexList;
    }

    public LinkedHashMap<String, LinkedHashMap<String, Integer>> getEdgeMatrix() {
        return edgeMatrix;
    }

    public void addMatrixRow(String nodeAddress) {
        edgeMatrix.put(nodeAddress, new LinkedHashMap<String, Integer>());
    }

    public void mergeRow(String src, LinkedHashMap<String, Integer> matrixRow) {
        for (String desc : matrixRow.keySet()) {
            edgeMatrix.get(src).put(desc, matrixRow.get(desc));
        }
    }

    public boolean hasMatrixRow(String nodeAddress) {
        return edgeMatrix.containsKey(nodeAddress);
    }

    public void insertEdge(PeersEdge edge) {
        edgeMatrix.get(edge.getSrc()).
                put(edge.getDesc(), edge.getWeight());
    }

    public void displayGraph() {
        System.out.println("Print graph edges");
        for(String src : getVertexList().keySet()){
            System.out.println(vertexList.get(src).getAlias()+ "(" + src + "):  ");
            for(String desc : getEdgeMatrix().get(src).keySet()){
                System.out.println("——" + vertexList.get(desc).getAlias() +
                        "(address:" + desc + " rssi:" + getEdgeMatrix().get(src).get(desc) + "); ");
            }
            System.out.println();
        }
        System.out.println();
    }

}
