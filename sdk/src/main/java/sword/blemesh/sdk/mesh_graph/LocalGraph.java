package sword.blemesh.sdk.mesh_graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.concurrent.PriorityBlockingQueue;

import timber.log.Timber;

/**
 * Created by 力 on 2016/6/2.
 */
public class LocalGraph extends PeersGraph {

    private static final int MAX_RSSI_POW = 1000000000;
    private static final int MAX_RSSI = 200;

    class Shortest_Path_Info {
        private String prev_node_address;
        private int rssi;
        private int rssi_pow;
        private int hop;
        Shortest_Path_Info(){
            rssi = 0;
            rssi_pow = MAX_RSSI_POW;
            hop = 0;
            prev_node_address = null;
        }
        public Shortest_Path_Info setPrevNode(String prev){
            this.prev_node_address = prev;
            return this;
        }
        public Shortest_Path_Info setHop(int hop){
            this.hop = hop;
            return this;
        }
        public Shortest_Path_Info setRssi_pow(){
            this.rssi_pow = (int) Math.pow(rssi,hop);
            return this;
        }
        public Shortest_Path_Info setRssi_pow(int rssi_pow){
            this.rssi_pow = rssi_pow;
            return this;
        }
        public Shortest_Path_Info newRssi(int rssi){
            this.rssi = this.rssi > rssi ? this.rssi : rssi;
            return this;
        }
        public String getPrev_node_address(){return this.prev_node_address;}
        public int getHop(){
            return this.hop;
        }
        public int getRssi_pow(){
            return this.rssi_pow;
        }
        public int getRssi() {return this.rssi;}
    }

    private Peer localNode;
    private LinkedHashMap<String,Shortest_Path_Info> Shortest_Path_Info_Map;

    //存储以用来显示
    private LinkedHashMap<String,Peer> removedPeers;
    private LinkedHashMap<String,Peer> newPeers;

    public LocalGraph(Peer LocalNode) {
        super();
        this.localNode = LocalNode;
        insertVertex(LocalNode);
        addMatrixRow(LocalNode.getMacAddress());
//        insertEdge(new PeersEdge(LocalNode.getAddress(),LocalNode.getAddress(),0));
    }

    //<editor-fold desc="Shortest Path Method">

    /**
     * Get Shortest path,Only Local Graph has this method
     * @param desc mac address of desc device;
     * @return
     */
    public LinkedHashSet<String> gerttPathToDesc(String desc){

        return null;
    }

    public void newDirectRemote(Peer remoteNode) {
        Timber.d("New direct remote peer: %s  %s", remoteNode.getAlias(), remoteNode.getMacAddress());
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

    //TODO: 函数还未实现
    public void lostDirectRemote(Peer remoteNode){
        Timber.d("Lost direct remote peer: %s  %s", remoteNode.getAlias(), remoteNode.getMacAddress());
        deleteEdge(localNode.getMacAddress(),remoteNode.getMacAddress());
        calCluateShortestPath();
    }

    /**
     * 1. when connect to new node, then exchange other's graph and merge to a new one
     * 2. or when received graph from other node's broadcast, merge it.
     * @param remoteNode remote peer that broadcast this graph message
     * @param otherGraph graph of new connected node
     */
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
        calCluateShortestPath();
    }

    /**
     * when other node broadcast that some node has disconnect, replace local graph
     * with the graph that node generate
     * @param remoteNode remote peer that broadcast this graph message
     * @param otherGraph graph after some remote node disconnect, used to replace loace one
     */
    public void trimGraph(Peer remoteNode, PeersGraph otherGraph){
        for (String vertex : otherGraph.getVertexList().keySet()) {
            Peer updatedPeer = otherGraph.getVertexList().get(vertex);
            if(!this.vertexList.containsKey(vertex)){
                deleteVertex(vertex);
            }
            else {
                this.vertexList.put(vertex,updatedPeer);
            }
        }
        this.edgeMatrix = otherGraph.getEdgeMatrix();
        calCluateShortestPath();
    }

    //TODO: design Shortest path method;
    //TODO: 计算最短路径后，同时更新各个节点的连通状态，并对应的修改vertexList
    public void calCluateShortestPath(){

        Shortest_Path_Info_Map = new LinkedHashMap<>();
        HashSet<String> unVisited = new HashSet<>();
        for (String key : vertexList.keySet()) {
            Shortest_Path_Info_Map.put(key,new Shortest_Path_Info());
            unVisited.add(key);
        }

        //initialization
        Shortest_Path_Info_Map.get(localNode.getMacAddress()).setHop(0).setRssi_pow(0);
        String this_visit, next_visit;
        next_visit = localNode.getMacAddress();

        //core function to get shortest path
        while(!unVisited.isEmpty()){
            this_visit = next_visit;
            HashMap<String,Integer> edgeRow = edgeMatrix.get(this_visit);
            Shortest_Path_Info this_node = Shortest_Path_Info_Map.get(this_visit);
            Shortest_Path_Info adjacent;

            int max_rssi_this_route = MAX_RSSI;
            int min_rssi_pow_these_nodes = MAX_RSSI_POW;

            for (String desc : edgeRow.keySet()) {

                //松弛
                adjacent = Shortest_Path_Info_Map.get(desc);
                max_rssi_this_route = Math.max(edgeRow.get(desc),this_node.getRssi());

                if(Math.pow(max_rssi_this_route,this_node.getHop()+1) < adjacent.getRssi_pow()) {
                    adjacent.setPrevNode(this_visit)
                            .setHop(this_node.getHop() + 1)
                            .newRssi(max_rssi_this_route);
                    adjacent.setRssi_pow();
                }
            }
            unVisited.remove(this_visit);
            for (String key : unVisited) {
                adjacent = Shortest_Path_Info_Map.get(key);
                if (min_rssi_pow_these_nodes > adjacent.getRssi_pow()) {
                    next_visit = key;
                    min_rssi_pow_these_nodes = adjacent.getRssi_pow();
                }
            }
            if(next_visit.equals(this_visit)) break;
        }

        //delete vertex that cannot reach
        Iterator it = vertexList.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry e = (Map.Entry)it.next();
            String node = (String)e.getKey();
            if(Shortest_Path_Info_Map.get(node).getRssi_pow() == MAX_RSSI_POW){
                it.remove();
            }
            else{
                vertexList.get(node).setHops(Shortest_Path_Info_Map.get(node).getHop());
                vertexList.get(node).setRssi(Shortest_Path_Info_Map.get(node).getRssi());
            }
        }

//        for (String node : vertexList.entrySet().iterator()) {
//            if(Shortest_Path_Info_Map.get(node).getRssi_pow() == MAX_RSSI_POW){
//                deleteVertex(node);
//            }
//            else{
//                vertexList.get(node).setHops(Shortest_Path_Info_Map.get(node).getHop());
//                vertexList.get(node).setRssi(Shortest_Path_Info_Map.get(node).getRssi());
//            }
//        }

    }

    public void displayAllShortestPath(){
        System.out.println("displayAllShortestPath");
        for (String node : vertexList.keySet()) {
            displayShortestPath(node);
        }
        System.out.println();
    }

    public void displayShortestPath(String desc){
        Stack<String> shortestPath = new Stack<>();
        String address = desc;
        Shortest_Path_Info this_info;
        while(address!=null){
            shortestPath.push(address);
            this_info = Shortest_Path_Info_Map.get(address);
            address = this_info.getPrev_node_address();
        }
        while(!shortestPath.isEmpty()){
            System.out.print(shortestPath.peek());
            shortestPath.pop();
            if(shortestPath.size() > 0)  System.out.print("->");
        }
        System.out.println();
    }
    //</editor-fold>

}
