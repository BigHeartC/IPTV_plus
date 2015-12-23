package com.bigheart.byrtv.ui.view;

import com.bigheart.byrtv.ui.module.ChannelModule;

import java.util.ArrayList;

/**
 * Created by BigHeart on 15/12/6.
 */
public interface MainActivityView {
    void login(Exception e);

    void showUpdateDialog(String newVersionName, String updateInfo, String downloadUrl);
}
