package com.bigheart.byrtv.util;

/**
 * Created by BigHeart on 15/12/9.
 */
public class SqlUtil {
    /**
     * 根据频道名称得到其唯一的id
     * id max value = 9223372036854775807
     * 6个
     *
     * @param uri
     * @return
     */
    public static long getUniqueIdByChannelUri(String uri) {
        long id = 0;
        String aimStr = uri.substring(uri.lastIndexOf('/') + 1, uri.length() - 5);
        String strId = new String();

        if (aimStr.length() > 5) {
            //倒数后 5 个之前的所有数取相加，后 5 个 append
            for (int i = 0; i < aimStr.length(); i++) {
                if (i >= aimStr.length() - 5) {
                    //append
//                    System.out.println((int) aimStr.charAt(i) + "");
                    strId += (int) aimStr.charAt(i);
                } else {
                    //plus
//                    System.out.println((int) aimStr.charAt(i) + " + ");
                    id += (int) aimStr.charAt(i);
                    strId = String.valueOf(id);
                }
            }

        } else {
            //直接全部 append
            for (int i = 0; i < aimStr.length(); i++) {
//                System.out.println((int) aimStr.charAt(i) + "");
                strId += (int) aimStr.charAt(i);
            }
        }
//        System.out.println(aimStr + " => " + strId);
        id = Long.valueOf(strId);
        return id;
    }
}
