package sword.blemesh.sdk.mesh_graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Stack;

import timber.log.Timber;

/**
 * Created by 力 on 2016/6/2.
 */
public class LocalGraph extends PeersGraph {

    private static final int MAX_HOPS = 20;
    private static final int MAX_RSSI = 200;
    private static final int MAX_Metric = 20;
    class Shortest_Path_Info {
        private String prev_node_address;
        private int rssi;
        private int rssiMetric;
        private int hops;

        Shortest_Path_Info(){
            rssi = 0;
            rssiMetric = MAX_Metric;
            hops = MAX_HOPS;
            prev_node_address = null;
        }

        public Shortest_Path_Info setRssiMetric(int rm){rssiMetric = rm; return this;}
        public int getRssiMetric(){return rssiMetric;}

        public Shortest_Path_Info setPrevNode(String prev){
            this.prev_node_address = prev;
            return this;
        }

        public Shortest_Path_Info setHops(int hops){
            this.hops = hops;
            return this;
        }
        private void increaseHops(){
            hops++;
        }
        public int getHops(){
            return this.hops;
        }
        public Shortest_Path_Info setRssi(int r){
            rssi = r;
            return this;
        }
        public Shortest_Path_Info newRssi(int rssi){
            this.rssi = this.rssi > rssi ? this.rssi : rssi;
            return this;
        }
        public String getPrev_node_address(){return this.prev_node_address;}
        public int getRssi() {return this.rssi;}
    }
    public int calMetricbyRSSI(int r){
        if(r<=30)
            return 1;
        if(r<=40)
            return 2;
        if(r<=50)
            return 3;
        if(r<=55)
            return 4;
        if(r<=60)
            return 5;
        if(r<=65)
            return 6;
        if(r<=70)
            return 7;
        if(r<=75)
            return 8;
        if(r<=80)
            return 9;
        if(r<=85)
            return 10;
        if(r<=90)
            return 11;
        return MAX_Metric;
    }

    private Peer localNode;
    private LinkedHashMap<String,Shortest_Path_Info> Shortest_Path_Info_Map;
    private LinkedHashSet<String> unMergedNewNodes = new LinkedHashSet<>();
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

    //<editor-fold desc="Public Method">

    /**
     * Get Shortest path,Only Local Graph has this method
     * @param desc mac address of desc device;
     * @return
     */
    public LinkedHashSet<String> gerttPathToDesc(String desc){

        return null;
    }

    synchronized public void newDirectRemote(Peer remoteNode) {
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
        unMergedNewNodes.add(remoteNode.getMacAddress());
    }

    synchronized public void lostDirectRemote(Peer remoteNode){
        Timber.d("Lost direct remote peer: %s  %s", remoteNode.getAlias(), remoteNode.getMacAddress());
        deleteEdge(localNode.getMacAddress(),remoteNode.getMacAddress());
        if(unMergedNewNodes.contains(remoteNode.getMacAddress()))
            unMergedNewNodes.remove(remoteNode.getMacAddress());
        else
            calCluateShortestPath();
    }

    /**
     * 1. when connect to new node, then exchange other's graph and merge to a new one
     * 2. or when received graph from other node's broadcast, merge it.
     * @param remoteNode remote peer that broadcast this graph message
     * @param otherGraph graph of new connected node
     */
    synchronized public void mergeGarph(Peer remoteNode, PeersGraph otherGraph) {
        for(Peer node : otherGraph.getVertexList().values()){
            if (!hasVertex(node)) {
                insertVertex(node);
                addMatrixRow(node.getMacAddress());
            }
        }
        for(String src : otherGraph.getEdgeMatrix().keySet()){
            mergeRow(src,otherGraph.getEdgeMatrix().get(src));
        }
        unMergedNewNodes.remove(remoteNode.getMacAddress());
        if(unMergedNewNodes.isEmpty())
            calCluateShortestPath();
    }

    /**
     * when other node broadcast that some node has disconnect, replace local graph
     * with the graph that node generate
     * @param remoteNode remote peer that broadcast this graph message
     * @param otherGraph graph after some remote node disconnect, used to replace loace one
     */
    synchronized public void trimGraph(Peer remoteNode, PeersGraph otherGraph){
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
        Shortest_Path_Info_Map.get(localNode.getMacAddress()).setHops(0).setRssiMetric(0).setRssi(0);
        String this_visit, next_visit;
        next_visit = localNode.getMacAddress();

        //core function to get shortest path
        while(!unVisited.isEmpty()){
            this_visit = next_visit;
            HashMap<String,Integer> edgeRow = edgeMatrix.get(this_visit);
            Shortest_Path_Info this_node = Shortest_Path_Info_Map.get(this_visit);
            Shortest_Path_Info adjacent;

            int worst_rssi_this_route = MAX_RSSI;
            int min_hops_these_nodes = MAX_HOPS;
            int min_metric_these_nodes = MAX_Metric;

            for (String desc : edgeRow.keySet()) {
                //松弛
                adjacent = Shortest_Path_Info_Map.get(desc);
                worst_rssi_this_route = Math.max(edgeRow.get(desc),this_node.getRssi());
                int metric = calMetricbyRSSI(worst_rssi_this_route);
                if(adjacent.getHops() != MAX_HOPS && metric == MAX_Metric) continue;
                if(this_node.getHops()+1 < adjacent.getHops()
                 || (metric<MAX_Metric && adjacent.getRssiMetric() == MAX_Metric)
                 || (this_node.getHops()+1 == adjacent.getHops() && metric < adjacent.getRssiMetric())) {
                    adjacent.setPrevNode(this_visit)
                            .setHops(this_node.getHops() + 1)
                            .setRssi(worst_rssi_this_route)
                            .setRssiMetric(metric);
                }
            }

            unVisited.remove(this_visit);

            for (String key : unVisited) {
                adjacent = Shortest_Path_Info_Map.get(key);
                if (min_hops_these_nodes > adjacent.getHops()
                  || (min_hops_these_nodes == adjacent.getHops() && min_metric_these_nodes > adjacent.getRssiMetric())) {
                    next_visit = key;
                    min_hops_these_nodes = adjacent.getHops();
                    min_metric_these_nodes = adjacent.getRssiMetric();
                }
            }
            if(next_visit.equals(this_visit)) break;
        }

        //delete vertex that cannot reach
        Iterator it = vertexList.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry e = (Map.Entry)it.next();
            String node = (String)e.getKey();
            if(Shortest_Path_Info_Map.get(node).getHops() == MAX_HOPS){
                it.remove();
            }
            else{
                vertexList.get(node).setHops(Shortest_Path_Info_Map.get(node).getHops());
                vertexList.get(node).setRssi(Shortest_Path_Info_Map.get(node).getRssi());
                //if changed, modified it's time
                vertexList.get(node).updateTime();
            }
        }

//        for (String node : vertexList.entrySet().iterator()) {
//            if(Shortest_Path_Info_Map.get(node).getRssi_pow() == MAX_RSSI_POW){
//                deleteVertex(node);
//            }
//            else{
//                vertexList.get(node).setHops(Shortest_Path_Info_Map.get(node).getHops());
//                vertexList.get(node).setRssi(Shortest_Path_Info_Map.get(node).getRssi());
//            }
//        }

    }

    public Peer getNextReply(String desc){
        if(LocalPeer.getLocalMacAddress().equals(desc)){
            return null;
        }
        Stack<String> shortestPath = new Stack<>();
        String address = desc;
        Shortest_Path_Info this_info;
        while(address!=null){
            shortestPath.push(address);
            this_info = Shortest_Path_Info_Map.get(address);
            address = this_info.getPrev_node_address();
        }
        shortestPath.pop(); // pop the local node;
        Peer next_reply = vertexList.get(shortestPath.peek());
        Timber.d("getNextReply desc is %s, next replay is %s : %s",
                desc,
                next_reply.getAlias(),
                next_reply.getMacAddress());
        return next_reply;
    }

    public String displayAllShortestPath(){
        String all_shortest_path = "";
        System.out.println("displayAllShortestPath");
        for (String node : vertexList.keySet()) {
            all_shortest_path+=displayShortestPath(node);
        }
        all_shortest_path+="\n";
        System.out.println();
        return all_shortest_path;
    }

    public String displayShortestPath(String desc){
        Stack<String> shortestPath = new Stack<>();
        String address = desc;
        Shortest_Path_Info this_info;
        int rssiMetric = Shortest_Path_Info_Map.get(desc).getRssiMetric();
        String shortest_path = "desc:" + desc + " metric:" + rssiMetric + " wrost rssi:" + Shortest_Path_Info_Map.get(desc).getRssi() + " ";
        while(address!=null){
            shortestPath.push(address);
            this_info = Shortest_Path_Info_Map.get(address);
            address = this_info.getPrev_node_address();
        }
        while(!shortestPath.isEmpty()){
            shortest_path+=shortestPath.peek();
            shortestPath.pop();
            if(shortestPath.size() > 0)  shortest_path+="-->";
        }
        shortest_path+="\n";
        System.out.print(shortest_path);
        return shortest_path;
    }
    //</editor-fold>

}
