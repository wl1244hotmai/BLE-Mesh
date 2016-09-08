package sword.blemesh.meshmessager.database;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;

import sword.blemesh.meshmessager.MainActivity;
import sword.blemesh.sdk.DataUtil;
import sword.blemesh.sdk.mesh_graph.LocalPeer;
import sword.blemesh.sdk.mesh_graph.Peer;

/**
 * Created by 力 on 2016/9/1.
 * Manager class for Database
 */
public class DbManager{

    private static final int PEER_AVAILABLE = 1;
    private static final int PEER_UN_AVAILABLE = 0;
    private ContentResolver mContentResolver;

    public DbManager(Context context) {
        mContentResolver = context.getContentResolver();
    }

    /**
     * @param alias      the nick name of user
     * @param macAddress the true ble macaddress of device
     * @return the key value of this row
     */
    public Uri createLocalPeer(@NonNull String alias, @NonNull String macAddress) {
        ContentValues values = new ContentValues();
        values.put(PeerTable.COLUMN_NAME_ALIAS, alias);
        values.put(PeerTable.COLUMN_NAME_MAC_ADDRESS, macAddress);
        values.put(PeerTable.COLUMN_NAME_IS_AVAILABLE, 1);
        return mContentResolver.insert(MeshMessagerContentProvider.PEER_URI,values);
    }

    public Peer getLocalPeer() {
        LocalPeer localPeer = null;
        String[] projection = {
                PeerTable.COLUMN_NAME_ALIAS,
                PeerTable.COLUMN_NAME_MAC_ADDRESS
        };
        String[] selectionArgs = {MainActivity.local_mac_address};
        String sortOrder = PeerTable.COLUMN_NAME_ALIAS;

        Cursor cursor = mContentResolver.query(
                MeshMessagerContentProvider.PEER_URI,
                projection,
                PeerTable.COLUMN_NAME_MAC_ADDRESS + " = ?",
                selectionArgs,
                sortOrder
        );

        if (cursor != null && cursor.moveToFirst()) {
            String alias = cursor.getString
                    (cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_ALIAS));
            String macAddress = cursor.getString
                    (cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_MAC_ADDRESS));
            cursor.close();
            localPeer = new LocalPeer(alias, macAddress);
        }
        return localPeer;
    }

    public Cursor getAvailablePeersCursor() {
        String[] selectionArgs = {MainActivity.local_mac_address};

        //TODO:未来根据具体需求可以修改此处的排列顺序，例如加入兴趣等的匹配程序来排列；
        String sortOrder = PeerTable.COLUMN_NAME_IS_AVAILABLE + " DESC,"
                + PeerTable.COLUMN_NAME_HOPS + " ASC,"
                + PeerTable.COLUMN_NAME_RSSI + " ASC";
        return mContentResolver.query(
                MeshMessagerContentProvider.PEER_URI,
                null,
                PeerTable.COLUMN_NAME_MAC_ADDRESS + "!=?",
                selectionArgs,
                sortOrder
        );
    }

    public Peer getRemotePeer(String macAddress) {
        String[] projection = {
                PeerTable.COLUMN_NAME_ALIAS,
                PeerTable.COLUMN_NAME_MAC_ADDRESS,
                PeerTable.COLUMN_NAME_LAST_SEEN,
                PeerTable.COLUMN_NAME_RSSI,
                PeerTable.COLUMN_NAME_HOPS,
        };
        String[] selectionArgs = {macAddress};
        String sortOrder = PeerTable.COLUMN_NAME_ALIAS;

        Cursor cursor = mContentResolver.query(
                MeshMessagerContentProvider.PEER_URI,
                projection,
                PeerTable.COLUMN_NAME_MAC_ADDRESS + " = ?",
                selectionArgs,
                sortOrder
        );

        if (cursor != null && cursor.moveToFirst()) {
            String alias = cursor.getString
                    (cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_ALIAS));
            String address = cursor.getString
                    (cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_MAC_ADDRESS));
            Date date = new Date(cursor.getInt(cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_LAST_SEEN)));
            int rssi = cursor.getInt(cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_RSSI));
            int hops = cursor.getInt(cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_HOPS));
            cursor.close();
            return new Peer(alias, address, date, rssi, hops);
        }
        return null;
    }

    public void createAndUpdateRemotePeers(@NonNull Map<String, Peer> vertexes,
                                           boolean isJoin) {
        if (isJoin) {
            //Join Action
            for (Peer peer : vertexes.values()) {
                String macAddress = peer.getMacAddress();
                Date date = peer.getLastSeen();
                ContentValues values = new ContentValues();
                values.put(PeerTable.COLUMN_NAME_ALIAS, peer.getAlias());
                values.put(PeerTable.COLUMN_NAME_MAC_ADDRESS, peer.getMacAddress());
                values.put(PeerTable.COLUMN_NAME_RSSI, peer.getRssi());
                values.put(PeerTable.COLUMN_NAME_LAST_SEEN, date.getTime());
                values.put(PeerTable.COLUMN_NAME_IS_AVAILABLE, PEER_AVAILABLE);
                values.put(PeerTable.COLUMN_NAME_HOPS,peer.getHops());

                if (getRemotePeer(macAddress) == null) {
                    //insert new record
                    mContentResolver.insert(MeshMessagerContentProvider.PEER_URI, values);
                } else {
                    //update
                    String[] selectionArgs = {peer.getMacAddress(), Long.toString(peer.getLastSeen().getTime())};
                    mContentResolver.update(MeshMessagerContentProvider.PEER_URI, values,
                            PeerTable.COLUMN_NAME_MAC_ADDRESS + "= ? AND "
                                    + PeerTable.COLUMN_NAME_LAST_SEEN + "<> ?"
                            , selectionArgs);
                }
            }
        } else {
            String[] projection = {
                    PeerTable.COLUMN_NAME_MAC_ADDRESS
            };
            String[] selectionArgs_r = {Integer.toString(PEER_AVAILABLE)};
            String sortOrder = PeerTable.COLUMN_NAME_ALIAS;

            Cursor cursor = mContentResolver.query(
                    MeshMessagerContentProvider.PEER_URI,
                    projection,
                    PeerTable.COLUMN_NAME_IS_AVAILABLE + " = ?",
                    selectionArgs_r,
                    sortOrder
            );
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String address = cursor.getString
                            (cursor.getColumnIndexOrThrow(PeerTable.COLUMN_NAME_MAC_ADDRESS));
                    if (!vertexes.keySet().contains(address)) {
                        ContentValues values = new ContentValues();
                        values.put(PeerTable.COLUMN_NAME_IS_AVAILABLE, PEER_UN_AVAILABLE);
                        String[] selectionArgs = {address};
                        mContentResolver.update(MeshMessagerContentProvider.PEER_URI, values,
                                PeerTable.COLUMN_NAME_MAC_ADDRESS + "= ?",
                                selectionArgs);
                    }
                }
                cursor.close();
            }
        }
        return;
    }

    public Cursor getChatMessages(String macAddress){
        String[] selectionArgs = {macAddress, MainActivity.local_mac_address};
        String sortOrder = MessageTable.COLUMN_NAME_MESSAGE_TIME + " DESC";

        return mContentResolver.query(MeshMessagerContentProvider.MESSAGE_URI,
                null,
                MessageTable.COLUMN_NAME_MAC_ADDRESS + " IN (?,?)",
                selectionArgs,
                sortOrder);
    }

    public void insertNewMessage(@Nullable byte[] data, Date date, Peer sender){
        ContentValues values = new ContentValues();
        ContentValues values_2 = new ContentValues();

        values.put(MessageTable.COLUMN_NAME_ALIAS,sender.getAlias());
        values.put(MessageTable.COLUMN_NAME_MAC_ADDRESS,sender.getMacAddress());
        values.put(MessageTable.COLUMN_NAME_MESSAGE_TIME, DataUtil.storedDateFormatter.format(date));
        if(data ==null){
            values.put(MessageTable.COLUMN_NAME_MESSAGE_BODY,"");
            values_2.put(PeerTable.COLUMN_NAME_LAST_MESSAGE, "");
        }
        else{
            values.put(MessageTable.COLUMN_NAME_MESSAGE_BODY,new String(data));
            values_2.put(PeerTable.COLUMN_NAME_LAST_MESSAGE,new String(data));
        }

        mContentResolver.insert(MeshMessagerContentProvider.MESSAGE_URI,values);

        String selection = PeerTable.COLUMN_NAME_MAC_ADDRESS + " =?";
        String selectionArgs[] = {sender.getMacAddress()};
        mContentResolver.update(MeshMessagerContentProvider.PEER_URI,values_2,selection,selectionArgs);
    }

    /**
     * when restart app and reload database, first reset all entry to status of unavailable
     */
    public boolean resetData() {
        return mContentResolver.delete(MeshMessagerContentProvider.PEER_URI,
                PeerTable.COLUMN_NAME_MAC_ADDRESS + "!= ?",
                new String[] {MainActivity.local_mac_address}) > 0;
    }

}