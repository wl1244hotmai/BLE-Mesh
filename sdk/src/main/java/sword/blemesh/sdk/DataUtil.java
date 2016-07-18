package sword.blemesh.sdk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Utilities for converting between Java and Database friendly types
 *
 * Created by davidbrodsky on 10/13/14.
 */
public class DataUtil {

    public static SimpleDateFormat storedDateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * When we query rows by a BLOB column we must
     * convert the BLOB to its String hex form
     * see:
     * http://www.sqlite.org/lang_expr.html#litvalue
     */
    public static String  bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        String rawHex = new String(hexChars);
        return rawHex;
//        String blobLiteral = "X'" + rawHex + "'";
//        return blobLiteral;
    }

    public static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * 对象转数组
     * @param obj
     * @return
     */
    public static byte[] toByteArray(Object obj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(obj);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bos.toByteArray();

    }

    /**
     * 数组转对象
     * @param bytes
     * @return
     */
    public static Object toObject(byte[] bytes) {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInput in = null;
        try {
            in = new ObjectInputStream(bis);
            return in.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

}
