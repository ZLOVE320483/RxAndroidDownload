package com.zlove.rxandroiddownload.practice.market;

import rx.subscriptions.CompositeSubscription;
import zlc.season.practicalrecyclerview.ItemType;

/**
 * Created by ZLOVE on 2016/11/30.
 */
public class AppInfoBean implements ItemType {

    String name;
    String img;
    String info;
    String downloadUrl;
    String saveName;

    CompositeSubscription mSubscriptions;

    public AppInfoBean(String name, String img, String info, String downloadUrl) {
        this.name = name;
        this.img = img;
        this.info = info;
        this.downloadUrl = downloadUrl;
        this.saveName = getSaveNameByUrl(downloadUrl);
        this.mSubscriptions = new CompositeSubscription();
    }

    /**
     * 截取Url最后一段作为文件保存名称
     *
     * @param url url
     * @return saveName
     */
    private String getSaveNameByUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    @Override
    public int itemType() {
        return 0;
    }
}
