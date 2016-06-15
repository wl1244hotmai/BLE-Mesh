package sword.blemesh.sdk.mesh_graph;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;

import sword.blemesh.sdk.crypto.KeyPair;

/**
 * Created by davidbrodsky on 2/21/15.
 */
public class LocalPeer extends Peer {

    byte[] privateKey;

    public LocalPeer(Context context,
                     KeyPair keyPair,
                     String alias) {

        super(keyPair.publicKey, alias, getMacAddress(context), null, 0, 0);

        this.privateKey = keyPair.secretKey;
    }

    private static String getMacAddress(Context context) {
        return android.provider.Settings.Secure.getString(context.getContentResolver(), "bluetooth_address");
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
