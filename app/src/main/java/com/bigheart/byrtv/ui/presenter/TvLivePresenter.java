package com.bigheart.byrtv.ui.presenter;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.media.AudioManager;
import android.view.WindowManager;

import com.bigheart.byrtv.data.sharedpreferences.DanmuPreferences;
import com.bigheart.byrtv.ui.view.TvLiveActivityView;


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


    /**
     * 调节音量
     *
     * @param dis 可正可负，表示调节差值
     */
    public void adjustVolume(float dis) {
        AudioManager audioMgr = (AudioManager) context.getSystemService(Service.AUDIO_SERVICE);
        if (dis > 0) {
            audioMgr.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        } else if (dis < 0) {
            audioMgr.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        }

        view.setAdjustViewContent("音量：" + (int) (audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC) * 6.67));//转化为 0~100
    }

    /**
     * 调节屏幕亮度
     *
     * @param activity
     * @param dis      可正可负，表示调节屏幕亮度 差值
     */
    public void adjustBrightness(Activity activity, float dis) {
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = lp.screenBrightness + dis / 20.0f;
        if (lp.screenBrightness > 1) {
            lp.screenBrightness = 1;
        } else if (lp.screenBrightness < 0.009) {
            lp.screenBrightness = 0.009f;
        }
        activity.getWindow().setAttributes(lp);
//        LogUtil.d("adjustBrightness", lp.screenBrightness * 100 + "");
        view.setAdjustViewContent("亮度：" + (int) (lp.screenBrightness * 100));
    }

}
