package sword.blemesh.meshmessager.ui.adapter;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import im.delight.android.identicons.SymmetricIdenticon;
import sword.blemesh.meshmessager.R;
import sword.blemesh.meshmessager.database.DbManager;
import sword.blemesh.meshmessager.database.PeerTable;

/**
 * Created by 力 on 2016/9/2.
 * adapter for user list
 */
public class UserListAdapter extends BaseAbstractRecycleCursorAdapter<UserListAdapter.ViewHolder>{

    /**
     * callback when one peer item has been pushed.
     */
    public interface MessageSelectedListener {
        void onMessageSelected(View identiconView, View usernameView, String peerAddress);
    }

    private DbManager dbManager;
    private RecyclerView mHost;
    private MessageSelectedListener messageSelectedListener;

    /**
     * ViewHolder of UserListAdapter
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View container;
        public TextView alias_view;
        public TextView info_view;
        public SymmetricIdenticon identicon;
        String peerAddress;

        public ViewHolder(View itemView) {
            super(itemView);
            container = itemView;
            alias_view = (TextView) itemView.findViewById(R.id.peer_alias);
            info_view = (TextView) itemView.findViewById(R.id.peer_info);
            identicon =  (SymmetricIdenticon) itemView.findViewById(R.id.identicon);
        }
    }

    /**
     * Recommended constructor.
     *
     * @param context       The context
     * @param dbManager     The data backend
     *
     **/
    public UserListAdapter(@NonNull Context context,
                           @NonNull DbManager dbManager,
                           @Nullable MessageSelectedListener listener)
    {
        super(context, dbManager.getAvailablePeersCursor());
        this.dbManager = dbManager;
        this.messageSelectedListener = listener;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        mHost = recyclerView;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, Cursor cursor) {
        if (holder.peerAddress == null)
            holder.peerAddress = cursor.getString(cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_MAC_ADDRESS));
        holder.container.setTag(R.id.view_tag_peer_id, holder.peerAddress);
        String alias,user_info,isOnline;
        int isAvailable = cursor.getInt(cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_IS_AVAILABLE));
        int color;
        if(isAvailable!=0){
            isOnline = mContext.getString(R.string.online);
            color = mContext.getResources().getColor(R.color.remote_online);
        }else{
            isOnline = mContext.getString(R.string.offline);
            color = mContext.getResources().getColor(R.color.remote_offline);
        }

        alias = cursor.getString(cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_ALIAS));

        holder.alias_view.setText(mContext.getString(R.string.user_alias,alias,isOnline));
        holder.alias_view.setTextColor(color);
        user_info = mContext.getString(R.string.user_info,
                cursor.getInt(cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_HOPS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_RSSI)));
        holder.info_view.setText(user_info);
        holder.identicon.show(alias);
    }

    @Override
    protected void onContentChanged() {
        changeCursor(dbManager.getAvailablePeersCursor());
        mHost.smoothScrollToPosition(0);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.user_item, parent, false);

        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (messageSelectedListener != null)
                    messageSelectedListener.onMessageSelected(v.findViewById(R.id.identicon),
                            v.findViewById(R.id.peer_alias),
                            (String) v.getTag(R.id.view_tag_peer_id)
                            );
            }
        });
        // set the view's size, margins, paddings and layout parameters
        return new ViewHolder(v);
    }

}
