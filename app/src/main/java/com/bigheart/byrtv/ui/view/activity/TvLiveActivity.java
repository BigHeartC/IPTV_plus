package com.bigheart.byrtv.ui.view.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bigheart.byrtv.R;
import com.bigheart.byrtv.data.sharedpreferences.DanmuPreferences;
import com.bigheart.byrtv.ui.module.ChannelModule;
import com.bigheart.byrtv.ui.presenter.MainActivityPresenter;
import com.bigheart.byrtv.ui.presenter.TvLivePresenter;
import com.bigheart.byrtv.ui.view.TvLiveActivityView;
import com.bigheart.byrtv.ui.view.custom.ijkplayer.IjkVideoView;
import com.bigheart.byrtv.util.ByrTvUtil;
import com.bigheart.byrtv.util.LogUtil;

import java.text.SimpleDateFormat;
import java.util.HashMap;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;


/**
 * Created by BigHeart on 15/12/10.
 */
public class TvLiveActivity extends BaseActivity implements TvLiveActivityView {

    public static final String TV_SERVER_NAME = "tv_server_name";

    private final int OPTION_VOLUME = 0, OPTION_BRIGHTNESS = 1, OPTION_NOTHING = 2;
    private final float THRESHOLD = ByrTvUtil.dip2px(10f);
    private float adjustY = 0;
    private int adjustOption = OPTION_VOLUME;
    private ChannelModule channel;
    private boolean isLockScreen = false;

    //弹幕偏好
    private int danmuEtTextSize = 0, danmuEtColorPos = 0, danmuEtPos = 0;


    private IjkVideoView mVideoView;
    private RelativeLayout rlVvTop, rlVvBottom;
    private Button btLaunchDanmu, btLockScreen, btDanmuSwitch;
    private ImageView ivPlayOrPause, ivUnlockScreenLogo;
    private TextView tvChannelName, tvBufferInfo, tvAdjust;

    private IDanmakuView danmakuView;
    private DanmakuContext danmakuContext;
    private BaseDanmakuParser danmakuParser;

    private DanmuPreferences danmuPreferences;

    //子菜单控件
    //发射弹幕控件
    private LinearLayout llDanmuEdit;
    private EditText etWriteDanmu;
    private ImageButton ibLaunchDanmu;
    private ImageView ivBigText, ivSmallText, ivDanmuInTop, ivDanmuInBottom, ivDanmuFlow, ivColor0, ivColor1, ivColor2, ivColor3;

    //弹幕设置 子菜单
    private LinearLayout llDanmuSetting;
    private ImageView ivFilterTopDanmu, ivFilterBottomDanmu, ivFilterFlowDanmu, ivFilterColorDanmu;
    private SeekBar sbTextScale, sbDestiny, sbSpeed, sbAlpha;
    private boolean isFilterColorDanmu = false, isFilterTopDanmu = false, isFilterFlowDanmu = false, isFilterBottomDanmu = false;

    //弹幕屏蔽
    private LinearLayout llFilterUser;

    private TvLivePresenter presenter;


    /**
     * 监听分钟量级的变化，更新时间
     */
    private BroadcastReceiver minBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //update time every minute
            ((TextView) findViewById(R.id.tv_vv_time)).setText(new SimpleDateFormat("hh:mm").format(System.currentTimeMillis()));
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //隐藏状态栏
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.layout_tv_live);
        initUI();
        initData();


//        final String[] danmuMsg = {"四不四洒！！！", "66666666...", "感觉药丸~", "在座的各位都是辣鸡 ~~~^_^~~~ ", "咕~~(╯﹏╰)b 好饿~", "shen me gui ~"};
//        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
//        exec.scheduleAtFixedRate(new Runnable() {
//            public void run() {
//                double a = Math.random() * 10; //0~9
//                a = Math.ceil(a);
//                addDanmu(danmuMsg[(new Double(a)).intValue() % 6], new DanmuAttrs((new Double(a)).intValue() % 3, (new Double(a)).intValue() % 4, (new Double(a)).intValue() % 2, AVUser.getCurrentUser().getObjectId()), false);
//            }
//        }, 0, (new Double(Math.random() * 3 + 3)).intValue() * 100, TimeUnit.MILLISECONDS);
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (danmakuView != null && danmakuView.isPrepared()) {
            danmakuView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (danmakuView != null && danmakuView.isPrepared() && danmakuView.isPaused()) {
            danmakuView.resume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mVideoView != null) {
            mVideoView.pause();
            ivPlayOrPause.setImageResource(R.drawable.ic_play_circle_fill_white_36dp);
        }
        IjkMediaPlayer.native_profileEnd();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.stopPlayback();
            mVideoView.release(true);
        }
        if (minBroadcast.isOrderedBroadcast()) {
            getApplicationContext().unregisterReceiver(minBroadcast);
        }
        if (danmakuView != null) {
            danmakuView.release();
            danmakuView = null;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (danmakuView != null) {
            danmakuView.release();
            danmakuView = null;
        }
    }

    @Override
    public void setAdjustViewContent(String content) {
        if (!tvAdjust.isShown()) {
            tvAdjust.setVisibility(View.VISIBLE);
        }
        tvAdjust.setText(content);
    }


    @Override
    public void setDanmuEtTextSize(int size) {
        danmuEtTextSize = size;
        danmuPreferences.setDanmuTextEtSize(size);
        ivBigText.setImageDrawable(null);
        ivSmallText.setImageDrawable(null);
        if (size == TvLiveActivity.this.DANMU_ET_BIG_SIZE_TEXT) {
            setBeSelected(ivBigText);
        } else {
            setBeSelected(ivSmallText);
        }
    }

    @Override
    public void setDanmuEtTextColorPos(int colorPos) {
        danmuEtColorPos = colorPos;
        ivColor0.setImageDrawable(null);
        ivColor1.setImageDrawable(null);
        ivColor2.setImageDrawable(null);
        ivColor3.setImageDrawable(null);
        switch (colorPos) {
            case 0:
                setBeSelected(ivColor0);
                break;
            case 1:
                setBeSelected(ivColor1);
                break;
            case 2:
                setBeSelected(ivColor2);
                break;
            case 3:
                setBeSelected(ivColor3);
                break;
        }
        danmuPreferences.setDanmuColorEtPos(danmuEtColorPos);
    }

    @Override
    public void setDanmuEtPos(int pos) {
        danmuEtPos = pos;
        ivDanmuInTop.setImageDrawable(null);
        ivDanmuInBottom.setImageDrawable(null);
        ivDanmuFlow.setImageDrawable(null);
        danmuPreferences.setDanmuEtPos(pos);
        if (pos == TvLiveActivity.this.DANMU_TEXT_TOP) {
            setBeSelected(ivDanmuInTop);
        } else if (pos == TvLiveActivity.this.DANMU_TEXT_BOTTOM) {
            setBeSelected(ivDanmuInBottom);
        } else {
            //flow
            setBeSelected(ivDanmuFlow);
        }
    }

    @Override
    public void setDanmuSBProgress(int textScale, int speed, int alpha, int destiny) {
        sbSpeed.setProgress(speed);
        sbTextScale.setProgress(textScale);
        sbAlpha.setProgress(alpha);
        sbDestiny.setProgress(destiny);

        danmakuContext.setDanmakuTransparency(alpha * 2.0f + 5);
        danmakuContext.setMaximumVisibleSizeInScreen(destiny / 2 + 3);
        danmakuContext.setScrollSpeedFactor(3 - speed / 33f + 0.2f);
        danmakuContext.setScaleTextSize(textScale / 28.6f + 0.5f);
    }

    /**
     * 设置属性被选中
     *
     * @param iv
     */
    private void setBeSelected(ImageView iv) {
        iv.setImageDrawable(getResources().getDrawable(R.drawable.state_selected_outline));
    }

    private void initUI() {
        mVideoView = (IjkVideoView) findViewById(R.id.vv_tv_live);
        danmakuView = (IDanmakuView) findViewById(R.id.dmk_view_live);


        findViewById(R.id.rl_vv_control).setOnClickListener(mainCtlClickListener);

        //主菜单控件
        //top
        rlVvTop = (RelativeLayout) findViewById(R.id.rl_vv_top);
        tvChannelName = (TextView) findViewById(R.id.tv_vv_channel_name);
        findViewById(R.id.iv_vv_back).setOnClickListener(mainCtlClickListener);
        findViewById(R.id.iv_vv_danmu_setting).setOnClickListener(mainCtlClickListener);
        findViewById(R.id.iv_vv_filter_user).setOnClickListener(mainCtlClickListener);
        //center
        tvBufferInfo = (TextView) findViewById(R.id.tv_vv_buffer_info);
        tvAdjust = (TextView) findViewById(R.id.tv_vv_adjust);
        ivUnlockScreenLogo = (ImageView) findViewById(R.id.iv_vv_unlock_screen);
        ivUnlockScreenLogo.setOnClickListener(mainCtlClickListener);
        //bottom
        rlVvBottom = (RelativeLayout) findViewById(R.id.rl_vv_bottom);
        btLockScreen = (Button) findViewById(R.id.bt_vv_lock_screen);
        btLockScreen.setOnClickListener(mainCtlClickListener);
        btDanmuSwitch = (Button) findViewById(R.id.bt_vv_danmu_switch);
        btDanmuSwitch.setOnClickListener(mainCtlClickListener);
        btLaunchDanmu = (Button) findViewById(R.id.bt_vv_launch_danmu);
        btLaunchDanmu.setOnClickListener(mainCtlClickListener);
        ivPlayOrPause = (ImageView) findViewById(R.id.iv_vv_play_pause);
        ivPlayOrPause.setOnClickListener(mainCtlClickListener);

        //子菜单
        // 发射弹幕菜单控件
        llDanmuEdit = (LinearLayout) findViewById(R.id.ll_danmu_edit);
        etWriteDanmu = (EditText) findViewById(R.id.et_write_danmu);
        ibLaunchDanmu = (ImageButton) findViewById(R.id.ib_launch_danmu);
        ivBigText = (ImageView) findViewById(R.id.iv_vv_big_text);
        ivSmallText = (ImageView) findViewById(R.id.iv_vv_small_text);
        ivDanmuInTop = (ImageView) findViewById(R.id.iv_vv_text_still_top);
        ivDanmuInBottom = (ImageView) findViewById(R.id.iv_vv_text_still_bottom);
        ivDanmuFlow = (ImageView) findViewById(R.id.iv_vv_text_flow);
        ivColor0 = (ImageView) findViewById(R.id.iv_vv_color_0);
        ivColor1 = (ImageView) findViewById(R.id.iv_vv_color_1);
        ivColor2 = (ImageView) findViewById(R.id.iv_vv_color_2);
        ivColor3 = (ImageView) findViewById(R.id.iv_vv_color_3);

        ibLaunchDanmu.setOnClickListener(editDanmuClickListen);
        ivBigText.setOnClickListener(editDanmuClickListen);
        ivSmallText.setOnClickListener(editDanmuClickListen);
        ivDanmuInTop.setOnClickListener(editDanmuClickListen);
        ivDanmuInBottom.setOnClickListener(editDanmuClickListen);
        ivDanmuFlow.setOnClickListener(editDanmuClickListen);
        ivColor0.setOnClickListener(editDanmuClickListen);
        ivColor1.setOnClickListener(editDanmuClickListen);
        ivColor2.setOnClickListener(editDanmuClickListen);
        ivColor3.setOnClickListener(editDanmuClickListen);


        //弹幕设置
        llDanmuSetting = (LinearLayout) findViewById(R.id.ll_iv_danmu_setting);
        ivFilterTopDanmu = (ImageView) findViewById(R.id.iv_vv_filter_top);
        ivFilterBottomDanmu = (ImageView) findViewById(R.id.iv_vv_filter_bottom);
        ivFilterFlowDanmu = (ImageView) findViewById(R.id.iv_vv_filter_flow);
        ivFilterColorDanmu = (ImageView) findViewById(R.id.iv_vv_filter_color);
        sbTextScale = (SeekBar) findViewById(R.id.sb_text_scale);
        sbAlpha = (SeekBar) findViewById(R.id.sb_danmu_aphla);
        sbDestiny = (SeekBar) findViewById(R.id.sb_danmu_destiny);
        sbSpeed = (SeekBar) findViewById(R.id.sb_danmu_speed);

        ivFilterTopDanmu.setOnClickListener(danmuSettingClickListener);
        ivFilterBottomDanmu.setOnClickListener(danmuSettingClickListener);
        ivFilterFlowDanmu.setOnClickListener(danmuSettingClickListener);
        ivFilterColorDanmu.setOnClickListener(danmuSettingClickListener);
        sbAlpha.setOnSeekBarChangeListener(userSettingSbChangeListener);
        sbDestiny.setOnSeekBarChangeListener(userSettingSbChangeListener);
        sbSpeed.setOnSeekBarChangeListener(userSettingSbChangeListener);
        sbTextScale.setOnSeekBarChangeListener(userSettingSbChangeListener);
        llDanmuSetting.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });


        //弹幕过滤
        llFilterUser = (LinearLayout) findViewById(R.id.ll_vv_filter_user);
    }

    private void initData() {

        if (getIntent().hasExtra(TV_SERVER_NAME)) {
            channel = MainActivityPresenter.getAllChannelByName(getIntent().getStringExtra(TV_SERVER_NAME));
        } else {
            finish();
        }

        danmuPreferences = new DanmuPreferences(this);

        //先设置一次时间
        ((TextView) findViewById(R.id.tv_vv_time)).setText(new SimpleDateFormat("hh:mm").format(System.currentTimeMillis()));
        //绑定 Receiver
        IntentFilter updateIntent = new IntentFilter();
        updateIntent.addAction("android.intent.action.TIME_TICK");
        getApplicationContext().registerReceiver(minBroadcast, updateIntent);

        tvChannelName.setText(channel.getChannelName());


        //视频控件设置
//        Log.i(TV_LIVE_URI, channelUri + " " + channelUri.length());
        mVideoView.setVideoPath(channel.getUri());
//        this.toast(channelUri);
        mVideoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mediaPlayer) {
                findViewById(R.id.clpb_vv_load_video).setVisibility(View.GONE);
                ivPlayOrPause.setVisibility(View.VISIBLE);
                mVideoView.start();
            }
        });

        mVideoView.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer mp, int what, int extra) {
                LogUtil.d("onError", what + "");
                findViewById(R.id.clpb_vv_load_video).setVisibility(View.GONE);
                new AlertDialog.Builder(TvLiveActivity.this).setTitle("错误").setMessage("视频播放出错！").setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        finish();
                    }
                }).show();
                return false;
            }
        });

        mVideoView.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mp, int what, int extra) {
                switch (what) {
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                        tvBufferInfo.setVisibility(View.VISIBLE);
                        LogUtil.d("MEDIA_INFO_BUFFERING_START", extra + "");
                        break;
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                        tvBufferInfo.setVisibility(View.GONE);
                        LogUtil.d("MEDIA_INFO_BUFFERING_END", extra + "");
                        break;
                    default:
                        break;
                }
                return false;
            }
        });


        //弹幕参数设置
        danmakuParser = new BaseDanmakuParser() {
            @Override
            protected Danmakus parse() {
                return new Danmakus();
            }
        };
        danmakuContext = DanmakuContext.create();
        HashMap<Integer, Integer> maxLineLimit = new HashMap<>();
        maxLineLimit.put(BaseDanmaku.TYPE_SCROLL_RL, 5);
        danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3)
                .setMaximumLines(maxLineLimit);
        danmakuView.setCallback(new DrawHandler.Callback() {
            @Override
            public void prepared() {
                danmakuView.start();
            }

            @Override
            public void updateTimer(DanmakuTimer timer) {

            }

            @Override
            public void drawingFinished() {

            }
        });
        danmakuView.prepare(danmakuParser, danmakuContext);
//        danmakuView.showFPS(true);


        //用户屏蔽

        presenter = new TvLivePresenter(this, this);
        presenter.init();

    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                adjustY = event.getY();
                if (event.getX() < ByrTvUtil.getScreenHeight() / 3.0f) {
                    //左边:亮度
                    adjustOption = OPTION_BRIGHTNESS;
                } else if (event.getX() > 2 * ByrTvUtil.getScreenHeight() / 3.0f) {
                    //右边:声音
                    adjustOption = OPTION_VOLUME;
                } else {
                    adjustOption = OPTION_NOTHING;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float y = event.getY();
                float yy = (adjustY - y) / THRESHOLD;
                if (Math.abs(yy) >= 1) {
                    adjustY = y;
                    switch (adjustOption) {
                        case OPTION_BRIGHTNESS:
                            if (!isLockScreen)
                                presenter.adjustBrightness(TvLiveActivity.this, yy);
                            break;
                        case OPTION_VOLUME:
                            if (!isLockScreen)
                                presenter.adjustVolume(yy);
                            break;
                        default:
                            break;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                break;
            case MotionEvent.ACTION_CANCEL:
                break;
        }

        return super.dispatchTouchEvent(event);
    }


    /**
     * 上下控制菜单、屏幕的点击事件
     */
    private View.OnClickListener mainCtlClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.bt_vv_lock_screen:
                    isLockScreen = true;
                    cleanAllMenu();
                    ivUnlockScreenLogo.setVisibility(View.VISIBLE);
                    break;
                case R.id.bt_vv_danmu_switch:
                    if (danmakuView.isShown()) {
                        //关闭弹幕
                        btDanmuSwitch.setCompoundDrawablesWithIntrinsicBounds(null, getResources().getDrawable(R.drawable.ic_visibility_off_white_24dp), null, null);
                        danmakuView.hide();
                    } else {
                        //打开弹幕
                        btDanmuSwitch.setCompoundDrawablesWithIntrinsicBounds(null, getResources().getDrawable(R.drawable.ic_visibility_white_24dp), null, null);
                        danmakuView.show();
                    }
                    break;
                case R.id.bt_vv_launch_danmu:
                    cleanAllMenu();
                    etWriteDanmu.requestFocus();
                    llDanmuEdit.setVisibility(View.VISIBLE);
                    InputMethodManager inputManager = (InputMethodManager) etWriteDanmu.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.showSoftInput(etWriteDanmu, InputMethodManager.SHOW_FORCED);
                    break;
                case R.id.iv_vv_play_pause:
                    if (mVideoView.isPlaying()) {
//                    LogUtil.d("TvLiveActivity", "pause");
                        mVideoView.pause();
                        ivPlayOrPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_circle_fill_white_36dp));
                    } else {
//                    LogUtil.d("TvLiveActivity", "play");
                        mVideoView.start();
                        ivPlayOrPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_circle_fill_white_36dp));
                    }
                    break;
                case R.id.iv_vv_back:
                    finish();
                    break;
                case R.id.rl_vv_control:
                    onScreenClicked();
                    break;
                case R.id.iv_vv_unlock_screen:
                    isLockScreen = false;
                    ivUnlockScreenLogo.setVisibility(View.INVISIBLE);
                    break;

                case R.id.iv_vv_filter_user:
                    cleanAllMenu();
                    llFilterUser.setVisibility(View.VISIBLE);
                    break;

                case R.id.iv_vv_danmu_setting:
                    cleanAllMenu();
                    llDanmuSetting.setVisibility(View.VISIBLE);
                    break;
                default:
                    LogUtil.d("TvLiveActivity mainCtlClickListener", "未处理监听事件");
                    break;
            }
        }
    };

    /**
     * 弹幕编辑框 点击事件
     */
    private View.OnClickListener editDanmuClickListen = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.ll_danmu_edit:
                    break;
                case R.id.ib_launch_danmu:
                    String content = etWriteDanmu.getText().toString().trim();
                    if (content.length() <= 0) {
                        toast("不能发送空弹幕");
                        break;
                    } else if (content.length() > MAX_TEXT_COUNT) {
                        toast("弹幕不能超过" + MAX_TEXT_COUNT + "字");
                        break;
                    } else {
                        addDanmuToServer(content);
                        etWriteDanmu.setText("");
                        InputMethodManager imm = (InputMethodManager) getSystemService(TvLiveActivity.this.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(etWriteDanmu.getWindowToken(), 0);
                        llDanmuEdit.setVisibility(View.GONE);
                    }
                    break;
                case R.id.iv_vv_big_text:
                    TvLiveActivity.this.setDanmuEtTextSize(TvLiveActivity.this.DANMU_ET_BIG_SIZE_TEXT);
                    break;
                case R.id.iv_vv_small_text:
                    TvLiveActivity.this.setDanmuEtTextSize(TvLiveActivity.this.DANMU_ET_SMALL_SIZE_TEXT);
                    break;
                case R.id.iv_vv_text_still_top:
                    TvLiveActivity.this.setDanmuEtPos(TvLiveActivity.this.DANMU_TEXT_TOP);
                    break;
                case R.id.iv_vv_text_flow:
                    TvLiveActivity.this.setDanmuEtPos(TvLiveActivity.this.DANMU_TEXT_FLOW);
                    break;
                case R.id.iv_vv_text_still_bottom:
                    TvLiveActivity.this.setDanmuEtPos(TvLiveActivity.this.DANMU_TEXT_BOTTOM);
                    break;
                case R.id.iv_vv_color_0:
                    TvLiveActivity.this.setDanmuEtTextColorPos(0);
                    break;
                case R.id.iv_vv_color_1:
                    TvLiveActivity.this.setDanmuEtTextColorPos(1);
                    break;
                case R.id.iv_vv_color_2:
                    TvLiveActivity.this.setDanmuEtTextColorPos(2);
                    break;
                case R.id.iv_vv_color_3:
                    TvLiveActivity.this.setDanmuEtTextColorPos(3);
                    break;
                default:
                    LogUtil.d("TvLiveActivity editDanmuClickListen", "未处理监听事件");
                    break;
            }
        }
    };


    private final int MAX_TEXT_COUNT = 20;


    /**
     * 弹幕过滤 中的点击事件
     */

    private View.OnClickListener danmuSettingClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.iv_vv_filter_top:
                    isFilterTopDanmu = !isFilterTopDanmu;
                    danmakuContext.setFTDanmakuVisibility(!isFilterTopDanmu);
                    if (isFilterTopDanmu) {
                        //show
                        setBeSelected(ivFilterTopDanmu);
                    } else {
                        //hide
                        ivFilterTopDanmu.setImageDrawable(null);
                    }
                    break;
                case R.id.iv_vv_filter_flow:
                    isFilterFlowDanmu = !isFilterFlowDanmu;
                    danmakuContext.setR2LDanmakuVisibility(!isFilterFlowDanmu);
                    if (isFilterFlowDanmu) {
                        //show
                        setBeSelected(ivFilterFlowDanmu);
                    } else {
                        //hide
                        ivFilterFlowDanmu.setImageDrawable(null);
                    }
                    break;
                case R.id.iv_vv_filter_bottom:
                    isFilterBottomDanmu = !isFilterBottomDanmu;
                    danmakuContext.setFBDanmakuVisibility(!isFilterBottomDanmu);
                    if (isFilterBottomDanmu) {
                        //show
                        setBeSelected(ivFilterBottomDanmu);
                    } else {
                        //hide
                        ivFilterBottomDanmu.setImageDrawable(null);

                    }
                    break;
                case R.id.iv_vv_filter_color:
                    isFilterColorDanmu = !isFilterColorDanmu;
                    if (isFilterColorDanmu) {
                        //only show white color
                        setBeSelected(ivFilterColorDanmu);
                        danmakuContext.setColorValueWhiteList(Color.WHITE);
                    } else {
                        //show all color
//                        danmakuContext.setColorValueWhiteList(0);
                        ivFilterColorDanmu.setImageDrawable(null);
                        danmakuContext.setColorValueWhiteList(TvLiveActivity.this.danmuColor[0], TvLiveActivity.this.danmuColor[1], TvLiveActivity.this.danmuColor[2], TvLiveActivity.this.danmuColor[3]);
                    }
                    break;
                default:
                    break;
            }
        }
    };


    private SeekBar.OnSeekBarChangeListener userSettingSbChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            switch (seekBar.getId()) {
                case R.id.sb_danmu_aphla:
                    //5~255
                    danmakuContext.setDanmakuTransparency(progress * 2.5f + 5);
                    danmuPreferences.setDanmuAlpha(sbAlpha.getProgress());
                    LogUtil.d("setDanmakuTransparency ", progress * 2.5f + 5 + "");

                    break;
                case R.id.sb_danmu_destiny:
                    //3~53
                    danmakuContext.setMaximumVisibleSizeInScreen(progress / 2 + 3);
                    danmuPreferences.setDanmuDestiny(sbDestiny.getProgress());
                    LogUtil.d("setMaximumVisibleSizeInScreen ", progress / 2f + 3 + "");
                    break;
                case R.id.sb_danmu_speed:
                    //0.2~3
                    danmakuContext.setScrollSpeedFactor(3 - progress / 33f + 0.2f);
                    danmuPreferences.setDanmuSpeed(sbSpeed.getProgress());
                    LogUtil.d("setScrollSpeedFactor ", 3 - progress / 33f + 0.2f + "");
                    break;
                case R.id.sb_text_scale:
                    //0.5~4
                    danmakuContext.setScaleTextSize(progress / 28.6f + 0.5f);
                    danmuPreferences.setDanmuTextScale(sbTextScale.getProgress());
                    LogUtil.d("setScaleTextSize ", progress / 28.6f + 0.5f + "");
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };


    /**
     * 屏幕被单击
     */
    private void onScreenClicked() {
        if (isLockScreen) {
            if (ivUnlockScreenLogo.isShown()) {
                ivUnlockScreenLogo.setVisibility(View.INVISIBLE);
            } else {
                ivUnlockScreenLogo.setVisibility(View.VISIBLE);
            }
        } else {
            if (rlVvBottom.isShown() || llDanmuEdit.isShown() || llDanmuSetting.isShown() || llFilterUser.isShown()) {
                cleanAllMenu();
            } else {
                showTopBottomMenu();
            }
        }
    }

    /**
     * 清空屏幕上的所有菜单
     */
    private void cleanAllMenu() {
        rlVvBottom.setVisibility(View.GONE);
        rlVvTop.setVisibility(View.GONE);
        llDanmuEdit.setVisibility(View.GONE);
        llDanmuSetting.setVisibility(View.GONE);
        llFilterUser.setVisibility(View.GONE);
    }

    /**
     * 显示上下菜单
     */
    private void showTopBottomMenu() {
        rlVvBottom.setVisibility(View.VISIBLE);
        rlVvTop.setVisibility(View.VISIBLE);
    }


    private final int danmuStayTime = 1200;//弹幕显示时间
    private final float danmuBigTextSize = 25f, danmuSmallTextSize = 15f;

    /**
     * @param content
     * @param attrs
     * @return
     */
    private boolean addDanmu(String content, DanmuAttrs attrs, boolean isFromUser) {
        if (!TextUtils.isEmpty(content) || attrs != null) {
            BaseDanmaku danmaku;
            if (attrs.getShowPos() == TvLiveActivity.this.DANMU_TEXT_TOP) {
                danmaku = danmakuContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_FIX_TOP);
            } else if (attrs.getShowPos() == TvLiveActivity.this.DANMU_TEXT_BOTTOM) {
                danmaku = danmakuContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_FIX_BOTTOM);
            } else {
                danmaku = danmakuContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL);
            }

            if (danmaku != null && danmakuView != null) {
                danmaku.text = content;
                danmaku.padding = 5;
                danmaku.priority = 0;  // 可能会被各种过滤器过滤并隐藏显示
                danmaku.isLive = true;

                danmaku.time = danmakuView.getCurrentTime() + danmuStayTime;
                if (attrs.getTextSize() == TvLiveActivityView.DANMU_ET_BIG_SIZE_TEXT) {
                    danmaku.textSize = danmuBigTextSize * (danmakuParser.getDisplayer().getDensity() - 0.6f);
                } else {
                    danmaku.textSize = danmuSmallTextSize * (danmakuParser.getDisplayer().getDensity() - 0.6f);
                }
                danmaku.textColor = TvLiveActivity.danmuColor[attrs.getColor()];
                danmaku.textShadowColor = Color.WHITE;
                if (isFromUser) {
                    danmaku.borderColor = Color.GREEN;
                }

                danmakuView.addDanmaku(danmaku);
                danmaku.userHash = attrs.userId;
//                LogUtil.d("addDanmu", "addDanmu success " + danmuEtColorPos + " " + danmuEtTextSize);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public class DanmuAttrs {
        private int showPos, color, textSize;
        private String userId;

        public void setShowPos(int showPos) {
            this.showPos = showPos;
        }

        public int getShowPos() {
            return showPos;
        }

        public int getColor() {
            return color;
        }

        public int getTextSize() {
            return textSize;
        }

        public void setColor(int color) {
            this.color = color;
        }

        public void setTextSize(int textSize) {
            this.textSize = textSize;
        }

        DanmuAttrs(int showPos, int color, int textSize, String userId) {
            this.showPos = showPos;
            this.color = color;
            this.textSize = textSize;
            this.userId = userId;
        }
    }

    private boolean addDanmuToServer(String content) {
        addDanmu(content, new DanmuAttrs(danmuEtPos, danmuEtColorPos, danmuEtTextSize, null), true);
        return true;
    }


}
