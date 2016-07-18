package sword.blemesh.sdk.app.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import sword.blemesh.sdk.R;
import sword.blemesh.sdk.mesh_graph.Peer;


/**
 * Created by davidbrodsky on 10/12/14.
 */
public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.ViewHolder> {

    private Context mContext;
    private ArrayList<Peer> peers;

    private View.OnClickListener peerClickListener;

    // Provide a reference to the type of views that you are using
    // (custom viewholder)
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView textView;
        public ViewGroup container;

        public ViewHolder(View v) {
            super(v);
            container = (ViewGroup) v;
            textView = (TextView) v.findViewById(R.id.text);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public PeerAdapter(Context context, ArrayList<Peer> peers) {
        this.peers = peers;
        mContext = context;
    }

    public void setOnPeerViewClickListener(View.OnClickListener listener) {
        this.peerClickListener = listener;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent,
                                         int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.peer_item, parent, false);
        // set the view's size, margins, paddings and layout parameters
        ViewHolder holder = new ViewHolder(v);
        holder.container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (peerClickListener != null && v.getTag() != null) {
                    peerClickListener.onClick(v);
                }
            }
        });
        return holder;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        Peer peer = peers.get(position);
        String text = peer.getAlias() == null ?
                "Unnamed device,mac: =" + peer.getMacAddress() :
                peer.getAlias();
        text += " hops: " + peer.getHops() + " rssi: " + peer.getRssi();
        holder.textView.setText(text);
        holder.container.setTag(peer);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return peers.size();
    }

    public void notifyPeerAdded(Peer peer) {
        peers.add(peer);
        notifyItemInserted(peers.size() - 1);

    }

    @Deprecated
    public void notifyPeerRemoved(Peer peer) {
        int idx = peers.indexOf(peer);
        if (idx != -1) {
            peers.remove(idx);
            notifyItemRemoved(idx);
        }
    }

    public void notifyPeerRemoved(int idx) {
        if (idx != -1) {
            notifyItemRemoved(idx);
        }
    }

    public void notifyPeerChanged(Peer peer) {
        int idx = peers.indexOf(peer);
        if (idx != -1) {
            peers.set(idx, peer);
            notifyItemChanged(idx);
        }
    }

    public void notifyPeersUpdated(LinkedHashMap<String, Peer> vertexes,
                                   boolean isJoinAction) {
        if (isJoinAction) // JOIN MESSAGE
        {
            for (Map.Entry e : vertexes.entrySet()) {
                Peer peer = (Peer) e.getValue();
                if (peers.contains(peer)) {
                    notifyPeerChanged(peer);
                } else {
                    notifyPeerAdded(peer);
                }
            }
        } else {  //LEFT MESSAGE
            Iterator it = peers.iterator();
            while(it.hasNext()){
                Peer p = (Peer)it.next();
                if(vertexes.containsValue(p)){
                    notifyPeerChanged(vertexes.get(p.getMacAddress()));
                }
                else{
                    it.remove();
                    int idx = peers.indexOf(p);
                    notifyPeerRemoved(idx);
                }
            }
        }


    }
}