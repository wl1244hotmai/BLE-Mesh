package sword.blemesh.sdk.mesh_graph;

/**
 * Created by 力 on 2016/6/2.
 */
public class LocalGraph extends PeersGraph {

    private LocalPeer localNode;

    public LocalGraph(LocalPeer LocalNode) {
        super();
        this.localNode = LocalNode;
        insertVertex(LocalNode);
        addMatrixRow(LocalNode.getMacAddress());
//        insertEdge(new PeersEdge(LocalNode.getAddress(),LocalNode.getAddress(),0));
    }

    public void newDirectRemote(Peer remoteNode) {
        if (!hasVertex(remoteNode)) {
            insertVertex(remoteNode);
        }
        if (!hasMatrixRow(remoteNode.getMacAddress())) {
            addMatrixRow(remoteNode.getMacAddress());
        }
        insertEdge(new PeersEdge(remoteNode.getMacAddress(),
                localNode.getMacAddress(),
                remoteNode.getRssi()));
    }

    public void mergeGarph(Peer remoteNode, PeersGraph otherGraph) {
        for(Peer node : otherGraph.getVertexList().values()){
            if (!hasVertex(node)) {
                insertVertex(node);
                addMatrixRow(node.getMacAddress());
            }
        }
        for(String src : otherGraph.getEdgeMatrix().keySet()){
            mergeRow(src,otherGraph.getEdgeMatrix().get(src));
        }
    }
}
