package com.bigheart.byrtv.data.net;

import com.bigheart.byrtv.ui.module.ChannelModule;
import com.bigheart.byrtv.util.LogUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by BigHeart on 15/12/7.
 */
public class GetChannelTask extends Thread {

    private NetWorkRsp netWorkRsp;

    public GetChannelTask(NetWorkRsp rsp) {
        netWorkRsp = rsp;
    }

    private String strUrl = "http://tv.byr.cn/mobile/index.html?cdn=bupt";

    @Override
    public void run() {
        super.run();
        BufferedReader in = null;
        try {
            URL url = new URL(strUrl);
            URLConnection connection = url.openConnection();
            HttpURLConnection conn = (HttpURLConnection) connection;
            conn.setRequestProperty("Accept", "application/json");
            //conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2490.80 Safari/537.36");

            //conn.setRequestMethod("POST");// 设置请求方法为post
            conn.setReadTimeout(5000);// 设置读取超时为5秒
            conn.setConnectTimeout(10000);// 设置连接网络超时为10秒
            conn.setDoOutput(true);

            connection.setDoOutput(true);

            connection.connect();
            in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String rst = "";
            String tmpRst;
            while ((tmpRst = in.readLine()) != null) {
                rst += tmpRst;
            }
            netWorkRsp.onSuccess(parseStr2Channels(rst));
            LogUtil.d("GetChannelTask", rst);
        } catch (Exception e) {
            netWorkRsp.onError(e);
        } finally {
            try {
                if (in != null)
                    in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private ArrayList<ChannelModule> parseStr2Channels(String str) {
        ArrayList<ChannelModule> channels = new ArrayList<>();

        String obtainAllChannel = "href=\"http://.*?</a>";
        HashSet<String> hashSet = new HashSet<>();
        Pattern pattern = Pattern.compile(obtainAllChannel);
        Matcher m = pattern.matcher(str);
        while (m.find()) {
            hashSet.add(m.group());
//            System.out.println("Found value: " + m.group());
        }
//        System.out.println(hashSet.size() + "组");


        String getUri = "http://.*m3u8";
        String getName = ">.*</a>";
        Pattern uriPattern = Pattern.compile(getUri);
        Pattern namePattern = Pattern.compile(getName);

        for (String tmpStr : hashSet) {
            ChannelModule channel = new ChannelModule();

            m = uriPattern.matcher(tmpStr);
            if (m.find()) {
                channel.setUri(m.group());
                m = namePattern.matcher(tmpStr);
                if (m.find()) {
                    channel.setChannelName(m.group().toString().substring(1, m.group().length() - 4));
                    channels.add(channel);
//                System.out.println(" " + m.group().toString().substring(1, m.group().toString().lastIndexOf('<')));
                }
            }
        }

        return channels;
    }
}
