package com.bigheart.byrtv.ui.view.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.bigheart.byrtv.R;
import com.bigheart.byrtv.ui.module.ChannelModule;
import com.bigheart.byrtv.ui.presenter.MyCollectionPresenter;
import com.bigheart.byrtv.ui.view.FragContactToAct;
import com.bigheart.byrtv.ui.view.MyCollectionView;

import java.util.ArrayList;

/**
 * Created by BigHeart on 15/12/6.
 */

public class MyCollectionFragment extends Fragment implements MyCollectionView {

    private static final String ITEM_SORT_TYPE = "itemSortType";

    private ListView lvCollection;
    private TextView tvNoCollection;
    private SwipeRefreshLayout refreshLayout;

    private ArrayList<ChannelModule> collectionChannels = new ArrayList<>();
    private CollectionAdapter collectionAdapter;
    private MyCollectionPresenter presenter;
    private static FragContactToAct collectionFragContactToAct;

    public static MyCollectionFragment newInstance(FragContactToAct contactToAct) {
        MyCollectionFragment fragment = new MyCollectionFragment();
        collectionFragContactToAct = contactToAct;
        return fragment;
    }

    public MyCollectionFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter = new MyCollectionPresenter(getActivity(), this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layoutView = inflater.inflate(R.layout.fragment_channel, container, false);
        collectionAdapter = new CollectionAdapter(getActivity());
        lvCollection = (ListView) layoutView.findViewById(R.id.lv_all_channel);
        collectionChannels = new ArrayList<>();
        lvCollection.setAdapter(collectionAdapter);
        tvNoCollection = (TextView) layoutView.findViewById(R.id.tv_no_collection);

        refreshLayout = (SwipeRefreshLayout) layoutView.findViewById(R.id.srl_all_channel);
        refreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorPrimary));
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                //不必更新
                stopRefresh();
            }
        });

        lvCollection.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                presenter.onItemClick(collectionChannels.get(position));
            }
        });

//        if (refreshLayout != null) {
        collectionFragContactToAct.fragmentInitOk();
//            LogUtil.d("refreshLayout", "ok");
//        }

        return layoutView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            collectionFragContactToAct = (FragContactToAct) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement FragContactToAct");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        collectionFragContactToAct = null;
    }

    @Override
    public void startRefresh() {
        refreshLayout.setRefreshing(true);
    }

    @Override
    public void stopRefresh() {
        refreshLayout.setRefreshing(false);

    }

    @Override
    public void updateData(ArrayList<ChannelModule> channels) {
        //拼音排序只需排一次
        this.collectionChannels = presenter.channelSort(channels);

        //处理视图
        if (collectionChannels.size() <= 0) {
            refreshLayout.setVisibility(View.GONE);
            tvNoCollection.setVisibility(View.VISIBLE);
        } else {
            if (!refreshLayout.isShown()) {
                refreshLayout.setVisibility(View.VISIBLE);
            }
            if (tvNoCollection.isShown()) {
                tvNoCollection.setVisibility(View.GONE);
            }
            collectionAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void updateData() {
        collectionAdapter.notifyDataSetChanged();
    }

    private class CollectionAdapter extends BaseAdapter {
        private LayoutInflater inflater;

        CollectionAdapter(Context context) {
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return collectionChannels.size();
        }

        @Override
        public Object getItem(int position) {
            return collectionChannels.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_channel, null);
                holder = new ViewHolder((TextView) convertView.findViewById(R.id.tv_channel_name),
                        (TextView) convertView.findViewById(R.id.tv_people_num),
                        (TextView) convertView.findViewById(R.id.tv_chanel_short),
                        (ImageView) convertView.findViewById(R.id.iv_collection));
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            ChannelModule tmpChannel = collectionChannels.get(position);
            holder.tvChannelName.setText(tmpChannel.getChannelName());
            if (tmpChannel.getChannelName().startsWith("CC")) {
                holder.tvChannelShort.setText("央");
            } else {
                holder.tvChannelShort.setText(tmpChannel.getChannelName().charAt(0) + "");
            }
            return convertView;
        }

        class ViewHolder {
            TextView tvPeopleNum, tvChannelName, tvChannelShort;

            ViewHolder(TextView channelName, TextView peopleNum, TextView channelShort, ImageView collection) {
                tvChannelName = channelName;
                tvPeopleNum = peopleNum;
                tvChannelShort = channelShort;
                collection.setVisibility(View.VISIBLE);
            }
        }
    }

}
