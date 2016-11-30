package com.zlove.rxandroiddownload.practice.manager;

import android.view.ViewGroup;

import zlc.season.practicalrecyclerview.AbstractAdapter;

/**
 * Created by ZLOVE on 2016/11/30.
 */
public class DownloadAdapter extends AbstractAdapter<DownloadBean, DownloadViewHolder> {

    @Override
    protected DownloadViewHolder onNewCreateViewHolder(ViewGroup parent, int viewType) {
        return new DownloadViewHolder(parent, this);
    }

    @Override
    protected void onNewBindViewHolder(DownloadViewHolder holder, int position) {
        holder.setData(get(position));
    }
}
