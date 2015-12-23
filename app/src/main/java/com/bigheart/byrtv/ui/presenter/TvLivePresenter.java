package com.bigheart.byrtv.ui.presenter;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.media.AudioManager;
import android.view.WindowManager;
import android.widget.Toast;

import com.bigheart.byrtv.ByrTvApplication;
import com.bigheart.byrtv.R;
import com.bigheart.byrtv.data.sharedpreferences.DanmuPreferences;
import com.bigheart.byrtv.ui.module.ChannelModule;
import com.bigheart.byrtv.ui.view.TvLiveActivityView;
import com.bigheart.byrtv.util.LogUtil;

import java.util.List;

import io.vov.vitamio.MediaPlayer;

/**
 * Created by BigHeart on 15/12/10.
 */
public class TvLivePresenter extends Presenter {

    private Context context;
    private TvLiveActivityView view;


    public TvLivePresenter(Context c, TvLiveActivityView tvLiveActivityView) {
        context = c;
        view = tvLiveActivityView;
    }

    @Override
    public void init() {
        super.init();
        DanmuPreferences pref = new DanmuPreferences(context);
        view.setDanmuEtPos(pref.getDanmuEtPos());
        view.setDanmuEtTextColorPos(pref.getDanmuEtColorPos());
        view.setDanmuEtTextSize(pref.getDanmuEtTextSize());
        view.setDanmuSBProgress(pref.getDanmuTextScale(), pref.getDanmuSpeed(), pref.getDanmuAlpha(), pref.getDanmuDestiny());
    }



    public void tvPlayError(MediaPlayer mp, int what, int extra) {

    }

    public void tvPlayBuffering() {

    }

    public void tvPlayInfo() {

    }


    /**
     * 调节音量
     *
     * @param dis 可正可负，表示调节差值
     * @return 调节后的音量
     */
    public int adjustVolume(float dis) {
        AudioManager audioMgr = (AudioManager) context.getSystemService(Service.AUDIO_SERVICE);
        if (dis > 0) {
            audioMgr.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        } else if (dis < 0) {
            audioMgr.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        }
        return audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    /**
     * 调节屏幕亮度
     *
     * @param activity
     * @param dis      可正可负，表示调节屏幕亮度 差值
     * @return 调节后的屏幕亮度
     */
    public float adjustBrightness(Activity activity, float dis) {
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = lp.screenBrightness + dis / 100.0f;
        if (lp.screenBrightness > 1) {
            lp.screenBrightness = 1;
        } else if (lp.screenBrightness < 0.01) {
            lp.screenBrightness = 0.01f;
        }
        activity.getWindow().setAttributes(lp);
//        toast(String.format("亮度：%.0f", lp.screenBrightness * 100));

        return lp.screenBrightness * 100;
    }
}
