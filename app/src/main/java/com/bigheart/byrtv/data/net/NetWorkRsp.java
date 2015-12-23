package com.bigheart.byrtv.data.net;

import com.bigheart.byrtv.ui.module.ChannelModule;

import java.util.ArrayList;

/**
 * Created by BigHeart on 15/12/7.
 */
public interface NetWorkRsp<T, E> {
    void onSuccess(T channels);

    void onError(E e);
}
