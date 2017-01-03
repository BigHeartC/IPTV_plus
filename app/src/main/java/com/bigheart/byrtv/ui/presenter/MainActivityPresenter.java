package com.bigheart.byrtv.ui.presenter;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.Toast;

import com.bigheart.byrtv.ByrTvApplication;
import com.bigheart.byrtv.R;
import com.bigheart.byrtv.data.sqlite.ChannelColumn;
import com.bigheart.byrtv.data.sqlite.SqlChannelManager;
import com.bigheart.byrtv.domain.interactor.ChannelsRsp;
import com.bigheart.byrtv.domain.interactor.GetChannelList;
import com.bigheart.byrtv.ui.module.ChannelModule;
import com.bigheart.byrtv.ui.view.AllChannelView;
import com.bigheart.byrtv.ui.view.MainActivityView;
import com.bigheart.byrtv.ui.view.MyCollectionView;
import com.bigheart.byrtv.util.ByrTvUtil;
import com.bigheart.byrtv.util.LogUtil;
import com.bigheart.byrtv.util.SortByPinYin;
import com.bigheart.byrtv.util.SqlUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * 控制AllChannelFragment,MyCollectionFragment
 * <p>
 * Created by BigHeart on 15/12/8.
 */
public class MainActivityPresenter extends Presenter {
    private Context context;
    private MainActivityView mainActivityView;
    private MyCollectionView collectionView;
    private AllChannelView channelView;
    private volatile ArrayList<ChannelModule> allChannels;
    private static HashMap<String, ChannelModule> mapChannels;

    private boolean hadSetDataFromNet = false;
    private Handler handler;

    public MainActivityPresenter(Context c, MainActivityView view, MyCollectionView myCollectionView, AllChannelView allChannelView) {
        context = c;
        mainActivityView = view;
        collectionView = myCollectionView;
        channelView = allChannelView;


        handler = new Handler();
        allChannels = new ArrayList<>();
        mapChannels = new HashMap<>();
    }


    public void pullData(boolean isFromSql, boolean isFromNet) {

        handler.post(new Runnable() {
            @Override
            public void run() {
                collectionView.startRefresh();
                channelView.startRefresh();
            }
        });


        //一次打开中若 已成功从网页 获得频道列表则不再请求
        if (ByrTvApplication.isSucPullChannelFromNet()) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    collectionView.stopRefresh();
                    channelView.stopRefresh();
                }
            });
            return;
        }


        new GetChannelList(new ChannelsRsp() {
            @Override
            public void getFromSqLiteSuccess(final ArrayList<ChannelModule> channels) {
                if (channels.size() > 0) {
                    allChannels = channels;
                    shoveChannelsToMap(mapChannels, allChannels);

                    if (!hadSetDataFromNet) {       //如果 网络 已加载到数据，则不用更新 UI
                        final ArrayList<ChannelModule> collectionChannels = filterCollectionChannel(channels);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                LogUtil.i("all Channels sql", channels.size() + " group");
                                LogUtil.i("collection Channels sql", collectionChannels.size() + " group");
                                channelView.updateData(channels);
                                collectionView.updateData(collectionChannels);
                            }
                        });
                    }
                }
            }

            @Override
            public void getFromSqLiteError(Exception e) {

            }

            @Override
            public void getFromNetSuccess(final ArrayList<ChannelModule> channels) {
                hadSetDataFromNet = true;

                //只需更新全部列表
                allChannels = channels;
                updateSqlChannel(channels);
                shoveChannelsToMap(mapChannels, allChannels);
                ByrTvApplication.setIsSucPullChannelFromNet(true);
                LogUtil.i("All Channel net", channels.size() + " group");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        collectionView.stopRefresh();
                        channelView.stopRefresh();
                        channelView.updateData(channels);
                    }
                });
            }

            @Override
            public void getFromNetError(Exception e) {
                e.printStackTrace();

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        channelView.stopRefresh();
                        collectionView.stopRefresh();
                        Toast.makeText(context, R.string.net_wrong, Toast.LENGTH_SHORT).show();
                        LogUtil.i("MainActivityPresenter", "can not get channel from net");
                    }
                });
            }
        }).getChannels(isFromSql, isFromNet);
    }

    public static ChannelModule getAllChannelByName(String channelName) {
        return mapChannels.get(channelName);
    }

    /**
     * 更新MyCollectionFrg中的数据
     */
    public void upDateMyCollectionFrg() {
        final ArrayList<ChannelModule> newCollectionChannels = filterCollectionChannel(allChannels);
        Collections.sort(newCollectionChannels, new SortByPinYin());
        handler.post(new Runnable() {
            @Override
            public void run() {
                collectionView.updateData(newCollectionChannels);
                LogUtil.i("MainActivityPresenter update", newCollectionChannels.size() + " group");
            }
        });
    }


    /**
     * 从全部频道中获取收藏频道
     *
     * @param channels
     * @return
     */
    private ArrayList<ChannelModule> filterCollectionChannel(ArrayList<ChannelModule> channels) {
        ArrayList<ChannelModule> collectionChannels = new ArrayList<>();

        for (ChannelModule c : channels) {
            if (c.isCollected()) {
                collectionChannels.add(c);
            }
        }
        return collectionChannels;
    }


    /**
     * 根据最新获取到的频道，更新本地数据库
     *
     * @param channels
     */
    private void updateSqlChannel(ArrayList<ChannelModule> channels) {
        ArrayList<ChannelModule> tmpChannels;
        for (ChannelModule c : channels) {
            long tmpId = SqlUtil.getUniqueIdByChannelUri(c.getUri());
            tmpChannels = SqlChannelManager.getInstance().queryChannel(null, ChannelColumn.CHANNEL_ID + "=" + tmpId, null, null, null, null);
            if (tmpChannels != null && tmpChannels.size() > 0) {
                c.setIsCollected(tmpChannels.get(0).isCollected());
                //数据库中已存有，则更新除 收藏 外的其他属性
                ContentValues values = new ContentValues();
                values.put(ChannelColumn.CHANNEL_NAME, c.getChannelName());
                values.put(ChannelColumn.IMG_URI, c.getUri());
                values.put(ChannelColumn.IS_COLLECTION, tmpChannels.get(0).isCollected());
                SqlChannelManager.getInstance().upDateChannel(values, ChannelColumn.CHANNEL_ID + " = " + tmpId, null);

                c.setSqlId(tmpId);
            } else {
                //还未存在，新建
                SqlChannelManager.getInstance().addChannel(c);
            }
        }
    }

    /**
     * 将 频道 填充为 Map
     *
     * @param map
     * @param channels
     */
    private void shoveChannelsToMap(HashMap<String, ChannelModule> map, ArrayList<ChannelModule> channels) {
        map.clear();
        for (ChannelModule c : channels) {
            String uri = c.getUri();
            c.setServerName(uri.substring(uri.lastIndexOf('/') + 1, uri.length() - 5));
            map.put(c.getServerName(), c);
        }
    }


}
