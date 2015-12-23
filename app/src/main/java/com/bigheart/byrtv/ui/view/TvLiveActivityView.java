package com.bigheart.byrtv.ui.view;

import android.graphics.Color;

/**
 * Created by BigHeart on 15/12/11.
 */
public interface TvLiveActivityView {

    int DANMU_ET_BIG_SIZE_TEXT = 0, DANMU_ET_SMALL_SIZE_TEXT = 1;

    /**
     * 设置弹幕字体大小
     *
     * @param size DANMU_BIG_SIZE_TEXT = 0, DANMU_SMALL_SIZE_TEXT = 1
     */
    void setDanmuEtTextSize(int size);


    int[] danmuColor = {Color.WHITE, Color.RED, Color.GREEN, Color.YELLOW};

    /**
     * 设置弹幕颜色
     *
     * @param colorPos 表第几种颜色
     */
    void setDanmuEtTextColorPos(int colorPos);

    int DANMU_TEXT_TOP = 0, DANMU_TEXT_FLOW = 1, DANMU_TEXT_BOTTOM = 2;

    /**
     * 设置弹幕的位置
     *
     * @param pos
     */
    void setDanmuEtPos(int pos);

    /**
     * 设置 seekBar 的长度
     *
     * @param textScale
     * @param speed
     * @param alpha
     * @param destiny
     */
    void setDanmuSBProgress(int textScale, int speed, int alpha, int destiny);

}
