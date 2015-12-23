package com.bigheart.byrtv.util;

import com.bigheart.byrtv.ui.module.ChannelModule;

import java.util.Comparator;

/**
 * Created by BigHeart on 15/12/19.
 */
public class SortByPinYin implements Comparator {
    @Override
    public int compare(Object o, Object t1) {
        ChannelModule c1 = (ChannelModule) o;
        ChannelModule c2 = (ChannelModule) t1;
        String c1Name = c1.getServerName();
        String c2Name = c2.getServerName();


        return c1Name.compareTo(c2Name);
    }
}
