package com.bigheart.byrtv.ui.presenter;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.bigheart.byrtv.ui.module.ChannelModule;
import com.bigheart.byrtv.ui.view.MyCollectionView;
import com.bigheart.byrtv.ui.view.activity.TvLiveActivity;
import com.bigheart.byrtv.util.SortByPinYin;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by BigHeart on 15/12/8.
 */
public class MyCollectionPresenter extends Presenter {
    private MyCollectionView view;
    private Context context;

    private Handler handler;

    public MyCollectionPresenter(Context c, MyCollectionView myCollectionView) {
        context = c;
        view = myCollectionView;
        handler = new Handler();
    }


    /**
     * 处理item点击事件
     *
     * @param channel 被选中的 item
     */
    public void onItemClick(ChannelModule channel) {
        Intent intent = new Intent(context, TvLiveActivity.class);
        intent.putExtra(TvLiveActivity.TV_SERVER_NAME, channel.getServerName());
        context.startActivity(intent);
    }


    public ArrayList<ChannelModule> channelSort(ArrayList<ChannelModule> channels) {
        Collections.sort(channels, new SortByPinYin());
//            }
        return channels;
    }
}
