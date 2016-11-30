package com.zlove.rxandroiddownload.practice.manager;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.Toolbar;

import com.zlove.rxandroiddownload.R;
import com.zlove.rxandroiddownload.download.DownloadRecord;
import com.zlove.rxandroiddownload.download.RxDownload;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import zlc.season.practicalrecyclerview.PracticalRecyclerView;

/**
 * Created by ZLOVE on 2016/11/30.
 */
public class DownloadManagerActivity extends AppCompatActivity {

    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.recycler)
    PracticalRecyclerView mRecycler;

    private DownloadAdapter mAdapter;
    private Subscription mSubscription;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_manager);

        ButterKnife.bind(this);
        setSupportActionBar(mToolbar);

        mAdapter = new DownloadAdapter();
        mRecycler.setLayoutManager(new LinearLayoutManager(this));
        mRecycler.setAdapterWithLoading(mAdapter);
        loadData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unSubscribe();
        if (mSubscription != null && !mSubscription.isUnsubscribed()) {
            mSubscription.unsubscribe();
        }
    }

    private void loadData() {
        mSubscription = RxDownload.getInstance().context(this).getTotalDownloadRecords()
                .map(new Func1<List<DownloadRecord>, List<DownloadBean>>() {
                    @Override
                    public List<DownloadBean> call(List<DownloadRecord> downloadRecords) {
                        List<DownloadBean> result = new ArrayList<>();
                        for (DownloadRecord each : downloadRecords) {
                            DownloadBean bean = new DownloadBean();
                            bean.mRecord = each;
                            result.add(bean);
                        }
                        return result;
                    }
                })
                .subscribe(new Action1<List<DownloadBean>>() {
                    @Override
                    public void call(List<DownloadBean> downloadBeen) {
                        mAdapter.addAll(downloadBeen);
                    }
                });
    }

    private void unSubscribe() {
        List<DownloadBean> list = mAdapter.getData();
        for (DownloadBean each : list) {
            each.unSubscribe();
        }
    }
}
