package sword.blemesh.sdk.mesh_graph;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;

/**
 * Created by davidbrodsky on 2/21/15.
 */
public class LocalPeer extends Peer {

    private static String local_mac_address;

    public LocalPeer(Context context,
                     String alias) {
        super(alias, getLocalMacAddress(context), null, 0);
    }

    public LocalPeer(String alias,String LocalMacAddress){
        super(alias,LocalMacAddress,null,0);
    }

    private static String getLocalMacAddress(Context context) {
        local_mac_address = android.provider.Settings.Secure.getString(context.getContentResolver(), "bluetooth_address");
        return local_mac_address;
    }

    public static String getLocalMacAddress(){
        return local_mac_address;
    }

    private static boolean doesDeviceSupportWifiDirect(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        FeatureInfo[] features = pm.getSystemAvailableFeatures();
        for (FeatureInfo info : features) {
            if (info != null && info.name != null && info.name.equalsIgnoreCase("android.hardware.wifi.direct")) {
                return true;
            }
        }
        return false;
    }
}
