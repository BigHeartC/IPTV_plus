package com.bigheart.byrtv.ui.view;

import com.bigheart.byrtv.ui.module.ChannelModule;

import java.util.ArrayList;

/**
 * Created by BigHeart on 15/12/7.
 */
public interface MyCollectionView {
    void startRefresh();

    void stopRefresh();

    void updateData(ArrayList<ChannelModule> channels);

    void updateData();
}
