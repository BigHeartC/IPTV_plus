package com.bigheart.byrtv.domain.interactor;

import android.content.Context;

import com.bigheart.byrtv.data.net.GetChannelTask;
import com.bigheart.byrtv.data.net.NetWorkRsp;
import com.bigheart.byrtv.data.sqlite.SqlChannelManager;
import com.bigheart.byrtv.ui.module.ChannelModule;

import java.util.ArrayList;

/**
 * Created by BigHeart on 15/12/8.
 */
public class GetChannelList {
    private ChannelsRsp rsp;


    public GetChannelList(ChannelsRsp channelsRsp) {
        rsp = channelsRsp;
    }


    /**
     * @param isFromSql 从数据库中加载
     * @param isFromNet 从网络加载
     */
    public void getChannels(boolean isFromSql, boolean isFromNet) {

        if (isFromSql) {
            rsp.getFromSqLiteSuccess(SqlChannelManager.getInstance().queryChannel(null, null, null, null, null, null));
        }

        if (isFromNet) {
            new GetChannelTask(new NetWorkRsp<ArrayList<ChannelModule>, Exception>() {
                @Override
                public void onSuccess(ArrayList<ChannelModule> channels) {
                    rsp.getFromNetSuccess(channels);
                }

                @Override
                public void onError(Exception e) {
                    rsp.getFromNetError(e);
                }
            }).start();
        }
    }
}
