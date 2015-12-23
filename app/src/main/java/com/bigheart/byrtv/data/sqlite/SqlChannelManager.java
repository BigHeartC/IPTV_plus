package com.bigheart.byrtv.data.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.bigheart.byrtv.ui.module.ChannelModule;
import com.bigheart.byrtv.util.LogUtil;
import com.bigheart.byrtv.util.SqlUtil;

import java.util.ArrayList;

/**
 * Created by BigHeart on 15/12/8.
 */
public class SqlChannelManager {
    private DbHelper helper;
    private SQLiteDatabase db;

    private static SqlChannelManager ourInstance;

    public static SqlChannelManager getInstance() {
        if (ourInstance == null)
            throw new NullPointerException();
        return ourInstance;
    }

    /**
     * 初始化数据库，只需在Application中调用一次即可
     *
     * @param context
     */
    public static void initChannelManager(Context context) {
        ourInstance = new SqlChannelManager(context);
    }

    private SqlChannelManager(Context c) {
        helper = new DbHelper(c);
        db = helper.getWritableDatabase();
    }

    public void addChannels(ArrayList<ChannelModule> channels) {
        try {
            db.beginTransaction();
            for (ChannelModule channel : channels) {
                ContentValues values = new ContentValues();
                values.put(ChannelColumn.CHANNEL_ID, SqlUtil.getUniqueIdByChannelUri(channel.getUri()));
                values.put(ChannelColumn.CHANNEL_NAME, channel.getChannelName());
                values.put(ChannelColumn.IMG_URI, channel.getUri());
                values.put(ChannelColumn.IS_COLLECTION, channel.isCollected());
                long rowId = db.insert(ChannelColumn.CHANNEL_TABLE_NAME, null, values);
                if (rowId != -1)
                    channel.setSqlId(rowId);
                LogUtil.i("SqlChannelManager add id", rowId + " : " + channel.getChannelName());

            }
            db.setTransactionSuccessful();
            LogUtil.i("SqlChannelManager add", channels.size() + "");
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }

    public void addChannel(ChannelModule channel) {
        try {
            db.beginTransaction();
            ContentValues values = new ContentValues();
            values.put(ChannelColumn.CHANNEL_ID, SqlUtil.getUniqueIdByChannelUri(channel.getUri()));
            values.put(ChannelColumn.CHANNEL_NAME, channel.getChannelName());
            values.put(ChannelColumn.IMG_URI, channel.getUri());
            values.put(ChannelColumn.IS_COLLECTION, channel.isCollected());
            long rowId = db.insert(ChannelColumn.CHANNEL_TABLE_NAME, null, values);
            if (rowId != -1)
                channel.setSqlId(rowId);
            LogUtil.i("SqlChannelManager add id", rowId + " : " + channel.getChannelName());

            db.setTransactionSuccessful();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } finally {
            db.endTransaction();
        }
    }


    public ArrayList<ChannelModule> queryChannel(String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
        ArrayList<ChannelModule> qChannels = new ArrayList<>();
        Cursor c = db.query(ChannelColumn.CHANNEL_TABLE_NAME, columns, selection, selectionArgs, groupBy, having, orderBy);
        while (c.moveToNext()) {
            ChannelModule channel = new ChannelModule(c.getString(c.getColumnIndex(ChannelColumn.CHANNEL_NAME)), c.getInt(c.getColumnIndex(ChannelColumn.IS_COLLECTION)) > 0, c.getString(c.getColumnIndex(ChannelColumn.IMG_URI)), c.getLong(c.getColumnIndex(ChannelColumn.CHANNEL_ID)));
            qChannels.add(channel);
        }
        c.close();

        return qChannels;
    }

    /**
     * @param values
     * @param whereClause 修改条件
     * @param whereArgs   修改添加参数
     */
    public int upDateChannel(ContentValues values, String whereClause, String[] whereArgs) {
//        LogUtil.i("SqlChannelManager update", whereClause);
        return db.update(ChannelColumn.CHANNEL_TABLE_NAME, values, whereClause, whereArgs);
    }


    /**
     * @param whereClause 删除条件
     * @param whereArgs   删除条件参数
     */
    public int delChannel(String whereClause, String[] whereArgs) {
        int count = db.delete(ChannelColumn.CHANNEL_TABLE_NAME, whereClause, whereArgs);
        LogUtil.i("SqlChannelManager del", count + "");
        return count;
    }

    public void closeDataBase() {
        db.close();
    }


}
