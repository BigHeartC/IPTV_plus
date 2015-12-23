package com.bigheart.byrtv.domain.interactor;

import com.bigheart.byrtv.ui.module.ChannelModule;

import java.util.ArrayList;

/**
 * Created by BigHeart on 15/12/8.
 */
public interface ChannelsRsp {
    void getFromSqLiteSuccess(ArrayList<ChannelModule> channels);

    void getFromSqLiteError(Exception e);

    void getFromNetSuccess(ArrayList<ChannelModule> channels);

    void getFromNetError(Exception e);
}
