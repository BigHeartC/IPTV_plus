package com.bigheart.byrtv.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

/**
 * Created by BigHeart on 15/12/10.
 */
public class ByrTvUtil {
    private static int screenHeight = 1270, screenWidth = 800;
    private static float scale;


    /**
     * 将在 ByrTvApplication 中调用，仅调用一次
     */
    public static void init(Context context) {

        //屏幕宽高
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            // >= API 13
            Point p = new Point();
            display.getSize(p);
            screenWidth = p.x;
            screenHeight = p.y;
        } else {
            // < API 13
            screenWidth = display.getWidth();
            screenHeight = display.getHeight();
        }

        scale = context.getResources().getDisplayMetrics().density;

    }

    public static int getScreenHeight() {
        return screenHeight;
    }

    public static int getScreenWidth() {
        return screenWidth;
    }

    /**
     * dp 转 px
     *
     * @param dpValue
     * @return
     */
    public static int dip2px(float dpValue) {
        return (int) (dpValue * scale + 0.5f);
    }

    public static int px2dip(float pxValue) {
        return (int) (pxValue / scale + 0.5f);
    }


    public static String getVersionName(Context context) {
        PackageManager manager = context.getPackageManager();
        PackageInfo pInfo = new PackageInfo();
        try {
            pInfo = manager.getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return pInfo.versionName == null ? null : pInfo.versionName;
    }

    public static int getVersionCode(Context context) {
        PackageManager manager = context.getPackageManager();
        PackageInfo pInfo = new PackageInfo();
        try {
            pInfo = manager.getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
