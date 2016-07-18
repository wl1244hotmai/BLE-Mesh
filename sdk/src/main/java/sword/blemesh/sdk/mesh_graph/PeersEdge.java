package sword.blemesh.sdk.mesh_graph;

/**
 * Created by 力 on 2016/6/2.
 */
public class PeersEdge {
    private String src, desc;
    private int weight;

    public PeersEdge(String src, String desc, int rssi){
        this.src = src;
        this.desc = desc;
        this.weight = rssi;
    }

    public String getSrc(){
        return src;
    }

    public String getDesc(){
        return desc;
    }

    public int getWeight(){
        return weight;
    }
}
