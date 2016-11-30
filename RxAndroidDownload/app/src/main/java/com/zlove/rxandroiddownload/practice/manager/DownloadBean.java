package com.zlove.rxandroiddownload.practice.manager;

import com.zlove.rxandroiddownload.download.DownloadRecord;

import rx.subscriptions.CompositeSubscription;
import zlc.season.practicalrecyclerview.ItemType;

/**
 * Created by ZLOVE on 2016/11/30.
 */
public class DownloadBean implements ItemType {

    DownloadRecord mRecord;
    CompositeSubscription mSubscription = new CompositeSubscription();

    public void unSubscribe() {
        mSubscription.clear();
    }

    @Override
    public int itemType() {
        return 0;
    }
}
