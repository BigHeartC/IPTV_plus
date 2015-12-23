package com.bigheart.byrtv.data.sharedpreferences;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by BigHeart on 15/12/13.
 */
public class DanmuPreferences {
    private final String PREFERENCE_NAME = "danmu_setting";
    private final String DANMU_COLOR_ET_POS = "danmu_color_et_pos", DANMU_TEXT_ET_SIZE = "danmu_text_et_size", DANMU_ET_POSITION = "danmu_et_position";
    private final String DANMU_TEXT_SCALE = "danmu_text_size", DANMU_SPEED = "danmu_speed", DANMU_ALPHA = "danmu_alpha", DANMU_DESTINY = "danmu_destiny";
    private final String DANMU_FILTER_USER_ID = "filter_id";
    private SharedPreferences pref;

    public DanmuPreferences(Context c) {
        pref = c.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    //编辑框的属性

    public int getDanmuEtColorPos() {
        return pref.getInt(DANMU_COLOR_ET_POS, 0);
    }

    public void setDanmuColorEtPos(int danmuColorPos) {
        pref.edit().putInt(DANMU_COLOR_ET_POS, danmuColorPos).commit();
    }

    public int getDanmuEtTextSize() {
        return pref.getInt(DANMU_TEXT_ET_SIZE, 0);
    }

    public void setDanmuTextEtSize(int danmuTextSize) {
        pref.edit().putInt(DANMU_TEXT_ET_SIZE, danmuTextSize).commit();
    }

    public int getDanmuEtPos() {
        return pref.getInt(DANMU_ET_POSITION, 1);
    }

    public void setDanmuEtPos(int danmuPos) {
        pref.edit().putInt(DANMU_ET_POSITION, danmuPos).commit();
    }

    //屏蔽属性


    //用户设置弹幕的属性
    public int getDanmuTextScale() {
        return pref.getInt(DANMU_TEXT_SCALE, 32);
    }

    public void setDanmuTextScale(int danmuTextSize) {
        pref.edit().putInt(DANMU_TEXT_SCALE, danmuTextSize).commit();
    }

    public int getDanmuSpeed() {
        return pref.getInt(DANMU_SPEED, 65);
    }

    public void setDanmuSpeed(int danmuSpeed) {
        pref.edit().putInt(DANMU_SPEED, danmuSpeed).commit();
    }

    public int getDanmuAlpha() {
        return pref.getInt(DANMU_ALPHA, 0);
    }

    public void setDanmuAlpha(int danmuAlpha) {
        pref.edit().putInt(DANMU_ALPHA, danmuAlpha).commit();
    }

    public int getDanmuDestiny() {
        return pref.getInt(DANMU_DESTINY, 90);
    }

    public void setDanmuDestiny(int danmuDestiny) {
        pref.edit().putInt(DANMU_DESTINY, danmuDestiny).commit();
    }

    public String getFilterUserIds() {
        return pref.getString(DANMU_FILTER_USER_ID, null);
    }

    public void resetFilterUserIds(String ids) {
        pref.edit().putString(DANMU_FILTER_USER_ID, ids).commit();
    }

    public void setFilterUserIds(String ids) {
        if (getFilterUserIds() != null)
            pref.edit().putString(DANMU_FILTER_USER_ID, getFilterUserIds() + ids).commit();
        else
            pref.edit().putString(DANMU_FILTER_USER_ID, ids).commit();
    }
}
