package sword.blemesh.meshmessager.database;

import android.provider.BaseColumns;

/**
 * Created by 力 on 2016/9/7.
 * Message Table
 */
public class MessageTable implements BaseColumns {

    public static final String TABLE_NAME = "Messages";
    public static final String COLUMN_NAME_ALIAS = "Alias"; //sender of this message
    public static final String COLUMN_NAME_MAC_ADDRESS = "MacAddress";
    public static final String COLUMN_NAME_MESSAGE_TIME = "Send_Time";
    public static final String COLUMN_NAME_MESSAGE_BODY = "Message_Body";

    public static final String SQL_CREATE_TABLE
            = "CREATE TABLE " + TABLE_NAME + " ("
            + _ID + " INTEGER PRIMARY KEY NOT NULL,"
            + COLUMN_NAME_ALIAS + " TEXT NOT NULL,"
            + COLUMN_NAME_MAC_ADDRESS + " TEXT NOT NULL,"
            + COLUMN_NAME_MESSAGE_TIME + " INTEGER NOT NULL,"
            + COLUMN_NAME_MESSAGE_BODY + " TEXT"  +  " )";
}
