package sword.blemesh.meshmessager.database;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Created by 力 on 2016/9/7.
 * the content provider for meshmessager
 */
public class MeshMessagerContentProvider extends ContentProvider {

    private static final int PEER_MATCHER = 0;
    private static final int MESSAGE_MATCHER = 1;

    private static final String PEER_STR = "peer";
    private static final String MESSAGE_STR = "MESSAGE";

    private static final String AUTHORITY = "sword.blemesh.meshmessager";
    private static final String SCHEME = "content://";
    public static final String PEER_CONTENT_TYPE = "vnd.android.cursor.dir/vnd.sword.peers";
    public static final String MESSAGE_CONTENT_TYPE = "vnd.android.cursor.dir/vnd.sword.messages";

    private static final Uri BASE_CONTENT_URI = Uri.parse(SCHEME+ AUTHORITY);
    public static final Uri PEER_URI = buildUri(PEER_STR);
    public static final Uri MESSAGE_URI = buildUri(MESSAGE_STR);

    MeshMessageDbHelper dbHelper;

    private static Uri buildUri(String... paths) {
        Uri.Builder builder = BASE_CONTENT_URI.buildUpon();
        for (String path : paths) {
            builder.appendPath(path);
        }
        return builder.build();
    }

    private static final UriMatcher sUriMATCHER = new UriMatcher(UriMatcher.NO_MATCH) {{
        addURI(AUTHORITY, PEER_STR, PEER_MATCHER);//Demo列表
        addURI(AUTHORITY, MESSAGE_STR, MESSAGE_MATCHER);//Demo列表
    }};

    private String getTable(Uri uri){
        switch (sUriMATCHER.match(uri)) {
            case PEER_MATCHER://Demo列表
                return PeerTable.TABLE_NAME;
            case MESSAGE_MATCHER:
                return MessageTable.TABLE_NAME;
            default:
                throw new IllegalArgumentException("Unknown Uri" + uri);
        }
    }

    @Override
    public boolean onCreate() {
        dbHelper = new MeshMessageDbHelper(getContext());
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(getTable(uri),
                projection,
                selection,
                selectionArgs,
                null,
                null,
                sortOrder);
        assert (getContext() != null);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;

    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues contentValues) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long rowId = 0;
        db.beginTransaction();
        try {
            rowId = db.insert(getTable(uri), null, contentValues);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
        if (rowId > 0) {
            Uri returnUri = ContentUris.withAppendedId(uri, rowId);
            assert (getContext()!=null);
            getContext().getContentResolver().notifyChange(uri, null);
            return returnUri;
        }
        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try {
            count = db.delete(getTable(uri), selection, selectionArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        assert (getContext()!=null);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues contentValues, String selection, String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count;
        db.beginTransaction();
        try {
            count = db.update(getTable(uri), contentValues, selection, selectionArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        assert (getContext()!=null);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMATCHER.match(uri)) {
            case PEER_MATCHER://Demo列表
                return PEER_CONTENT_TYPE;
            case MESSAGE_MATCHER:
                return MESSAGE_CONTENT_TYPE;
            default:
                throw new IllegalArgumentException("Unknown Uri" + uri);
        }
    }
}
