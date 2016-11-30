package com.zlove.rxandroiddownload.practice.service;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.tbruyelle.rxpermissions.RxPermissions;
import com.zlove.rxandroiddownload.R;
import com.zlove.rxandroiddownload.download.DownloadFlag;
import com.zlove.rxandroiddownload.download.DownloadRecord;
import com.zlove.rxandroiddownload.download.DownloadStatus;
import com.zlove.rxandroiddownload.download.RxDownload;
import com.zlove.rxandroiddownload.practice.DownloadController;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * Created by ZLOVE on 2016/11/30.
 */
public class ServiceDownloadActivity extends AppCompatActivity {

    final String saveName = "王者荣耀.apk";
    final String defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
    final String url = "http://120.192.69.163/dlied5.myapp.com/myapp/1104466820/1104466820/sgame/10024163_com.tencent.tmgp.sgame_u131_1.15.2.13.apk";

    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.img)
    ImageView mImg;
    @BindView(R.id.percent)
    TextView mPercent;
    @BindView(R.id.progress)
    ProgressBar mProgress;
    @BindView(R.id.size)
    TextView mSize;
    @BindView(R.id.status)
    TextView mStatusText;
    @BindView(R.id.action)
    Button mAction;

    private RxDownload mRxDownload;
    private CompositeSubscription mSubscriptions;

    private DownloadController mDownloadController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_download);
        ButterKnife.bind(this);
        setSupportActionBar(mToolbar);

        String icon = "http://static.yingyonghui.com/icon/128/4196396.png";
        Picasso.with(this).load(icon).into(mImg);
        mStatusText.setText("开始");

        mRxDownload = RxDownload.getInstance().context(this);
        mSubscriptions = new CompositeSubscription();

        mDownloadController = new DownloadController(mStatusText, mAction);
        mDownloadController.setStateAndDisplay(DownloadFlag.NORMAL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Subscription query = mRxDownload.getDownloadRecord(url)
                .subscribe(new Action1<DownloadRecord>() {
                    @Override
                    public void call(DownloadRecord record) {
                        mProgress.setIndeterminate(record.getStatus().isChunked);
                        mProgress.setMax((int) record.getStatus().getTotalSize());
                        mProgress.setProgress((int) record.getStatus().getDownloadSize());
                        mPercent.setText(record.getStatus().getPercent());
                        mSize.setText(record.getStatus().getFormatStatusString());

                        int flag = record.getDownloadFlag();
                        //设置下载状态
                        mDownloadController.setStateAndDisplay(flag);
                    }
                });

        Subscription temp = mRxDownload.registerReceiver(url)
                .subscribe(new Subscriber<DownloadStatus>() {
                    @Override
                    public void onCompleted() {
                        mDownloadController.setStateAndDisplay(DownloadFlag.COMPLETED);
                    }

                    @Override
                    public void onError(Throwable e) {
                        mDownloadController.setStateAndDisplay(DownloadFlag.FAILED);
                    }

                    @Override
                    public void onNext(DownloadStatus status) {
                        mProgress.setIndeterminate(status.isChunked);
                        mProgress.setMax((int) status.getTotalSize());
                        mProgress.setProgress((int) status.getDownloadSize());
                        mPercent.setText(status.getPercent());
                        mSize.setText(status.getFormatStatusString());
                    }
                });
        mSubscriptions.add(temp);
        mSubscriptions.add(query);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSubscriptions.clear();
    }

    @OnClick({R.id.action, R.id.finish})
    public void onClick(View view) {
        if (view.getId() == R.id.finish) {
            finish();
        } else if (view.getId() == R.id.action) {
            mDownloadController.performClick(new DownloadController.Callback() {
                @Override
                public void startDownload() {
                    start();
                }

                @Override
                public void pauseDownload() {
                    pause();
                }

                @Override
                public void install() {
                    installApk();
                }
            });
        }
    }

    private void installApk() {
        mDownloadController.setStateAndDisplay(DownloadFlag.INSTALL);
        Uri uri = Uri.fromFile(new File(defaultPath + File.separator + saveName));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        startActivity(intent);
    }

    private void start() {
        //开始下载, 先检查权限
        Subscription temp = RxPermissions.getInstance(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .doOnNext(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean granted) {
                        if (!granted) {
                            throw new RuntimeException("no permission");
                        }
                    }
                })
                .observeOn(Schedulers.io())
                .compose(mRxDownload.transformServiceNoReceiver(url, saveName, defaultPath))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        mDownloadController.setStateAndDisplay(DownloadFlag.STARTED);
                    }
                });
        mSubscriptions.add(temp);
    }

    private void pause() {
        Subscription subscription = mRxDownload.pauseServiceDownload(url)
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        mDownloadController.setStateAndDisplay(DownloadFlag.PAUSED);
                    }
                });
        mSubscriptions.add(subscription);
    }

    private void cancel() {
        Subscription subscription = mRxDownload.cancelServiceDownload(url)
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        mDownloadController.setStateAndDisplay(DownloadFlag.CANCELED);
                    }
                });
        mSubscriptions.add(subscription);
    }
}
