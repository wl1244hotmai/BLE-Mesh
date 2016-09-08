package sword.blemesh.meshmessager.ui.adapter;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.ParseException;

import im.delight.android.identicons.SymmetricIdenticon;
import sword.blemesh.meshmessager.R;
import sword.blemesh.meshmessager.database.DbManager;
import sword.blemesh.meshmessager.database.MessageTable;
import sword.blemesh.sdk.DataUtil;

/**
 * Created by 力 on 2016/9/7.
 */
public class MessageAdapter extends BaseAbstractRecycleCursorAdapter<MessageAdapter.ViewHolder>{

    private DbManager dbManager;
    private RecyclerView mHost;
    private String macAddress;

    public static class ViewHolder extends RecyclerView.ViewHolder{
        public View container;
        public TextView senderView;
        public TextView timeView;
        public TextView messageView;
        public SymmetricIdenticon identicon;

        public ViewHolder(View itemView) {
            super(itemView);
            container = itemView;
            senderView = (TextView) itemView.findViewById(R.id.sender);
            timeView = (TextView) itemView.findViewById(R.id.authoredDate);
            messageView = (TextView) itemView.findViewById(R.id.messageBody);
            identicon = (SymmetricIdenticon) itemView.findViewById(R.id.identicon_message);
        }
    }

    /**
     * Recommended constructor.
     *
     * @param context       The context
     * @param dbManager     The data backend
     *
     **/
    public MessageAdapter(@NonNull Context context,
                          @NonNull DbManager dbManager,
                          @NonNull String remoteAddress){
        super(context, dbManager.getChatMessages(remoteAddress));
        this.dbManager = dbManager;
        this.macAddress = remoteAddress;
    }

    @Override
    public MessageAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.message_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(MessageAdapter.ViewHolder holder, Cursor cursor) {
        holder.container.setTag(R.id.view_tag_peer_id, cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable._ID)));
        String alias = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.COLUMN_NAME_ALIAS));
        holder.senderView.setText(alias);
        holder.identicon.show(alias);
        holder.messageView.setText(cursor.getString(cursor.getColumnIndex(MessageTable.COLUMN_NAME_MESSAGE_BODY)));
        try {
            holder.timeView.setText(DateUtils.getRelativeTimeSpanString(
                    DataUtil.storedDateFormatter.parse(cursor.getString(cursor.getColumnIndex(MessageTable.COLUMN_NAME_MESSAGE_TIME))).getTime()));
        } catch (ParseException e) {
            holder.timeView.setText("");
            e.printStackTrace();
        }
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        mHost = recyclerView;
    }

    @Override
    protected void onContentChanged() {
        changeCursor(dbManager.getChatMessages(macAddress));
        mHost.smoothScrollToPosition(0);
    }

}
