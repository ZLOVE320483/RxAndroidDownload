package com.zlove.rxandroiddownload.practice.market;

import android.view.ViewGroup;

import zlc.season.practicalrecyclerview.AbstractAdapter;

/**
 * Created by ZLOVE on 2016/11/30.
 */
public class AppInfoAdapter extends AbstractAdapter<AppInfoBean, AppInfoViewHolder> {

    @Override
    protected AppInfoViewHolder onNewCreateViewHolder(ViewGroup parent, int viewType) {
        return new AppInfoViewHolder(parent);
    }

    @Override
    protected void onNewBindViewHolder(AppInfoViewHolder holder, int position) {
        holder.setData(get(position));
    }
}
