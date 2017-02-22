package sword.blemesh.sdk;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;

import sword.blemesh.sdk.crypto.KeyPair;
import sword.blemesh.sdk.crypto.SodiumShaker;
import sword.blemesh.sdk.mesh_graph.LocalGraph;
import sword.blemesh.sdk.mesh_graph.Peer;
import sword.blemesh.sdk.mesh_graph.PeersEdge;
import sword.blemesh.sdk.mesh_graph.PeersGraph;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class GraphTest {
    //@Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    //@Test
    public void graph_merge_test() throws Exception {
        //图初始化和开始的几个邻接点  node2<->local<->node3
        Peer mLocalNode = new Peer("local","192:1:1:100", null, 0, 0);
        LocalGraph mGraph = new LocalGraph(mLocalNode);
        Peer node2 = new Peer("node2","192:1:1:4", new Date(), 55, 1);
        Peer node3 = new Peer("node3","192:1:1:5", null, 76, 1);
        mGraph.newDirectRemote(node2);
        mGraph.insertEdge(new PeersEdge(mLocalNode.getMacAddress(),node2.getMacAddress(),15));
        mGraph.newDirectRemote(node3);
        mGraph.insertEdge(new PeersEdge(mLocalNode.getMacAddress(),node3.getMacAddress(),34));
//        mGraph.newDirectRemote(new DeviceNode("node1","192:1:1:2",-30));
//        mGraph.insertEdge(new DeviceEdge(mLocalNode.getAddress(),new DeviceNode("node1","192:1:1:2",-30).getAddress(),-13));
        mGraph.displayGraph();

        Peer remoteNode = new Peer("node1","192:1:1:2", null, 0, 0);
        LocalGraph remoteGraph = new LocalGraph(remoteNode);
        Peer node4 = new Peer("node4","192:1:1:8",null,100,1);
        remoteGraph.newDirectRemote(node4);
        remoteGraph.insertEdge(new PeersEdge(remoteNode.getMacAddress(),node4.getMacAddress(),65));
        remoteGraph.displayGraph();

        mGraph.newDirectRemote(new Peer("node1","192:1:1:2",null,45,1));
        remoteGraph.newDirectRemote(new Peer("local","192:1:1:100",null,55,1));

        mGraph.mergeGarph(remoteNode,remoteGraph);
        mGraph.displayGraph();

        JSONObject vertexJSONObject = mGraph.toVertexJSONObject();
        JSONObject edgesJSONObject =mGraph.toEdgeJSONOBject();

        PeersGraph copyGraph = new PeersGraph(vertexJSONObject,edgesJSONObject);

        copyGraph.displayGraph();
    }

    @Test
    public void shortest_path_test(){
        Peer mLocalNode = new Peer("local","192.1.1.100", null, 0, 0);
        LocalGraph mGraph = new LocalGraph(mLocalNode);
        mGraph.insertVertex(new Peer("node1","192.1.1.1",new Date(),20,1));
        mGraph.insertVertex(new Peer("node2","192.1.1.2",new Date(),30,1));
        mGraph.insertVertex(new Peer("node3","192.1.1.3",new Date(),35,1));
        mGraph.insertVertex(new Peer("node4","192.1.1.4",new Date(),25,1));

        mGraph.addMatrixRow("192.1.1.1");
        mGraph.addMatrixRow("192.1.1.2");
        mGraph.addMatrixRow("192.1.1.3");
        mGraph.addMatrixRow("192.1.1.4");

        mGraph.insertEdge(new PeersEdge("192.1.1.100","192.1.1.1",20));
        mGraph.insertEdge(new PeersEdge("192.1.1.1","192.1.1.100",20));

        mGraph.insertEdge(new PeersEdge("192.1.1.100","192.1.1.2",45));
        mGraph.insertEdge(new PeersEdge("192.1.1.2","192.1.1.100",45));

        mGraph.insertEdge(new PeersEdge("192.1.1.1","192.1.1.2",30));
        mGraph.insertEdge(new PeersEdge("192.1.1.2","192.1.1.1",30));

        mGraph.insertEdge(new PeersEdge("192.1.1.1","192.1.1.3",100));
        mGraph.insertEdge(new PeersEdge("192.1.1.3","192.1.1.1",100));

        mGraph.insertEdge(new PeersEdge("192.1.1.1","192.1.1.4",100));
        mGraph.insertEdge(new PeersEdge("192.1.1.4","192.1.1.1",100));

        mGraph.insertEdge(new PeersEdge("192.1.1.2","192.1.1.4",100));
        mGraph.insertEdge(new PeersEdge("192.1.1.4","192.1.1.2",100));

        mGraph.insertEdge(new PeersEdge("192.1.1.2","192.1.1.3",45));
        mGraph.insertEdge(new PeersEdge("192.1.1.3","192.1.1.2",45));

        mGraph.insertEdge(new PeersEdge("192.1.1.3","192.1.1.4",30));
        mGraph.insertEdge(new PeersEdge("192.1.1.4","192.1.1.3",30));

        mGraph.displayGraph();
        mGraph.calCluateShortestPath();
        mGraph.displayAllShortestPath();

/*
        Peer remoteNode = new Peer("remote","192.1.1.10",new Date(),30,1);
        LocalGraph remoteGraph = new LocalGraph(remoteNode);
        remoteGraph.newDirectRemote(new Peer("node5","192.1.1.11",new Date(),50,1));
        remoteGraph.newDirectRemote(new Peer("node6","192.1.1.12",new Date(),40,1));
        remoteGraph.insertEdge(new PeersEdge("192.1.1.10","192.1.1.11",55));
        remoteGraph.insertEdge(new PeersEdge("192.1.1.10","192.1.1.12",45));
        mLocalNode.setRssi(35);
        remoteGraph.newDirectRemote(mLocalNode);

        mGraph.newDirectRemote(remoteNode);
        mGraph.mergeGarph(remoteNode,remoteGraph);
        mGraph.displayGraph();
        mGraph.calCluateShortestPath();
        mGraph.displayAllShortestPath();


        mGraph.lostDirectRemote(remoteNode);
        mGraph.displayGraph();
        mGraph.calCluateShortestPath();
        mGraph.displayAllShortestPath();
*/

    }
}