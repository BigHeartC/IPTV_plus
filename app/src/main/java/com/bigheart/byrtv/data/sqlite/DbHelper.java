package com.bigheart.byrtv.data.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by BigHeart on 15/12/8.
 */
public class DbHelper extends SQLiteOpenHelper {
    public static final int DbVersion = 1; //当前版本号
    public static String DbName = "iptv_plus.db";

    private static final String SQL_CREATE_CHANNEL_TABLE =
            "CREATE TABLE " + ChannelColumn.CHANNEL_TABLE_NAME + " (" +
                    ChannelColumn.CHANNEL_ID + " INTEGER PRIMARY KEY," +
                    ChannelColumn.CHANNEL_NAME + " TEXT," +
                    ChannelColumn.IS_COLLECTION + " BLOB," +
                    ChannelColumn.IMG_URI + " TEXT" + " )";


    public DbHelper(Context context) {
        super(context, DbName, null, DbVersion);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CHANNEL_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
