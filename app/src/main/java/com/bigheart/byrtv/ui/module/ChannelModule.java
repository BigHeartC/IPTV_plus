package com.bigheart.byrtv.ui.module;


/**
 * 频道
 * <p/>
 * Created by BigHeart on 15/12/6.
 */
public class ChannelModule {
    private String channelName;
    private boolean isCollected = false;
    private String Uri;
    private String serverName;
    private int peopleNum = 0;
    private long sqlId;
    private boolean isExistInServer = false;

    public ChannelModule() {

    }

    public ChannelModule(String channelName, boolean isCollected, String uri, long id) {
        setChannelName(channelName);
        setIsCollected(isCollected);
        setUri(uri);
        setSqlId(id);
    }

    public long getSqlId() {
        return sqlId;
    }

    public void setSqlId(long sqlId) {
        this.sqlId = sqlId;
    }

    public int getPeopleNum() {
        return peopleNum;
    }

    public void setPeopleNum(int peopleNum) {
        this.peopleNum = peopleNum;
    }


    public String getUri() {
        return Uri;
    }

    public void setUri(String uri) {
        Uri = uri;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public boolean isCollected() {
        return isCollected;
    }

    public void setIsCollected(boolean isCollected) {
        this.isCollected = isCollected;
    }

    public boolean isExistInServer() {
        return isExistInServer;
    }

    public void setIsExistInServer(boolean isExistInServer) {
        this.isExistInServer = isExistInServer;
    }

}
