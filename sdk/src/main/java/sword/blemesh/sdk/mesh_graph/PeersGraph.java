package sword.blemesh.sdk.mesh_graph;

import java.util.LinkedHashMap;

/**
 * Created by 力 on 2016/6/2.
 */
public class PeersGraph {

    private LinkedHashMap<String, Peer> vertexList;
    private LinkedHashMap<String, LinkedHashMap<String, Integer>> edgeMatrix;

    public PeersGraph() {
        vertexList = new LinkedHashMap<>();
        edgeMatrix = new LinkedHashMap<>();
    }

    public void insertVertex(Peer node) {
        vertexList.put(node.getMacAddress(), node);
    }

    public void deleteVertex(Peer node) {
        vertexList.remove(node.getMacAddress());
        edgeMatrix.remove(node.getMacAddress());
        for (LinkedHashMap edge : edgeMatrix.values()) {
            edge.remove(node.getMacAddress());
        }
    }

    public boolean hasVertex(Peer node) {
        return vertexList.containsKey(node.getMacAddress());
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
        System.out.println("Print Garph edges");
        for(String src : getEdgeMatrix().keySet()){
            System.out.print(vertexList.get(src).getAlias()+ "(" + src + ") -  ");
            for(String desc : getEdgeMatrix().get(src).keySet()){
                System.out.print(vertexList.get(desc).getAlias() +
                        "(" + desc + ") " + getEdgeMatrix().get(src).get(desc) + ";");
            }
            System.out.println();
        }
        System.out.println();
    }

    public void deleteEdge(PeersEdge edge) {
        edgeMatrix.get(edge.getSrc()).remove(edge.getDesc());
    }
}
