package com.bigheart.byrtv;

import android.app.Application;

import com.bigheart.byrtv.data.sqlite.SqlChannelManager;
import com.bigheart.byrtv.util.ByrTvUtil;
import com.bigheart.byrtv.util.LogUtil;

public class ByrTvApplication extends Application {


    private static boolean isSucPullChannelFromNet = false;

    @Override
    public void onCreate() {
        super.onCreate();
        SqlChannelManager.initChannelManager(getApplicationContext());
        ByrTvUtil.init(getApplicationContext());
        LogUtil.d("ByrTvApplication", "onCreate");
    }


    public static boolean isSucPullChannelFromNet() {
        return isSucPullChannelFromNet;
    }

    public static void setIsSucPullChannelFromNet(boolean isSucPullChannelFromNet) {
        ByrTvApplication.isSucPullChannelFromNet = isSucPullChannelFromNet;
    }

}
