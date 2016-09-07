package sword.blemesh.meshmessager;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Serializable;
import java.util.Map;

import sword.blemesh.meshmessager.database.DbManager;
import sword.blemesh.sdk.app.BleMeshService;
import sword.blemesh.sdk.mesh_graph.Peer;
import sword.blemesh.sdk.transport.Transport;

import static sword.blemesh.meshmessager.ChatManager.BleMode.*;

/**
 * Created by 力 on 2016/9/2.
 * Class that process fragments' information, as a coordinator
 */
public class ChatManager implements BleMeshService.Callback, Serializable {

    public enum BleMode {mode_both,mode_scan,mode_advertise};

    public interface ChatManagerCallback {

        /**
         * display the Log text
         * @param logText the log text reported by Ble Mesh Service
         */
        public void onNewLog(@NonNull String logText);

    }

    public static final String BLE_MESH_SERVICE_NAME = "bleMesh";
    private Context   mContext;
    private DbManager dbManager;
    private BleMeshService.ServiceBinder serviceBinder;

    public ChatManager(@NonNull Context context) {
        mContext = context;
        dbManager = new DbManager(context);
    }

    public DbManager getDbManager(){
        return dbManager;
    }

    public Peer getLocalPeer(){
        return dbManager.getLocalPeer();
    }

    public void createLocalPeer(String username,String local_mac_address){
        dbManager.createLocalPeer(username,local_mac_address);
    }

    public void setServiceBinder(BleMeshService.ServiceBinder _serviceBinder){
        this.serviceBinder = _serviceBinder;
        //then register this manager class as callback of BleMeshService
        this.serviceBinder.setCallback(this);
    }

    public void resetData(){
        dbManager.resetData();
    }

    public void startBleMesh(BleMode mode){
        switch(mode){
            case mode_both:
                serviceBinder.startTransport();
                break;
            case mode_scan:
                serviceBinder.scanForOtherUsers();
                break;
            case mode_advertise:
                serviceBinder.advertiseLocalUser();
        }
    }

    public void stopBleMesh(){
        serviceBinder.stop();
    }

    //<editor-fold desc="BleMeshService Callback"
    @Override
    public void onDataRecevied(@NonNull BleMeshService.ServiceBinder binder, @Nullable byte[] data, @NonNull Peer sender, @Nullable Exception exception) {

    }

    @Override
    public void onDataSent(@NonNull BleMeshService.ServiceBinder binder, @Nullable byte[] data, @NonNull Peer recipient, @NonNull Peer desc, @Nullable Exception exception) {

    }

    @Override
    public void onPeerStatusUpdated(@NonNull BleMeshService.ServiceBinder binder, @NonNull Peer peer, @NonNull Transport.ConnectionStatus newStatus, boolean peerIsHost) {

    }

    @Override
    public void onPeersStatusUpdated(@NonNull BleMeshService.ServiceBinder binder, @NonNull Map<String, Peer> vertexes, boolean isJoin) {
        //遍历新的图的所有Peer,更新对应的数据库
        //其中有些Peer不存在，要create，有些Peer已存在但是需要更新，有些Peer不需要变化
        //先判断是Join还是Left来处理
        dbManager.createAndUpdateRemotePeers(vertexes,isJoin);

    }

    @Override
    public void onNewLog(@NonNull String logText) {

    }
    //</editor-fold>

}
