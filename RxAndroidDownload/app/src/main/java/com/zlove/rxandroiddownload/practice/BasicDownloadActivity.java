package com.zlove.rxandroiddownload.practice;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.tbruyelle.rxpermissions.RxPermissions;
import com.zlove.rxandroiddownload.R;
import com.zlove.rxandroiddownload.download.DownloadFlag;
import com.zlove.rxandroiddownload.download.DownloadStatus;
import com.zlove.rxandroiddownload.download.RxDownload;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by ZLOVE on 2016/11/28.
 */
public class BasicDownloadActivity extends AppCompatActivity {

    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.img)
    ImageView mImg;
    @BindView(R.id.status)
    TextView mStatus;
    @BindView(R.id.percent)
    TextView mPercent;
    @BindView(R.id.progress)
    ProgressBar mProgress;
    @BindView(R.id.size)
    TextView mSize;
    @BindView(R.id.action)
    Button mAction;
    @BindView(R.id.finish)
    Button mFinish;

    private String saveName = "weixin.apk";
    private String defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
    private String url = "http://dldir1.qq.com/weixin/android/weixin6330android920.apk";
    private Subscription subscription;
    private DownloadController mDownloadController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic_download);
        ButterKnife.bind(this);
        setSupportActionBar(mToolbar);

        String icon = "http://static.yingyonghui.com/icon/128/4200197.png";
        Picasso.with(this).load(icon).into(mImg);
        mStatus.setText("开始");

        mDownloadController = new DownloadController(mStatus, mAction);
        mDownloadController.setStateAndDisplay(DownloadFlag.NORMAL);
    }

    @OnClick({R.id.action, R.id.finish})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.action:
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
                break;

            case R.id.finish:
                BasicDownloadActivity.this.finish();
                break;
        }
    }

    private void start() {
        subscription = RxPermissions.getInstance(this)
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
                .compose(RxDownload.getInstance().transform(url, saveName, null))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<DownloadStatus>() {

                    @Override
                    public void onStart() {
                        super.onStart();
                        mDownloadController.setStateAndDisplay(DownloadFlag.STARTED);
                    }

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
    }

    private void pause() {
        BasicDownloadActivity.this.unSubscribe(subscription);
        mDownloadController.setStateAndDisplay(DownloadFlag.PAUSED);
    }

    private void installApk() {
        Uri uri = Uri.fromFile(new File(defaultPath + File.separator + saveName));
        Log.d("ZLOVE", defaultPath + File.separator + saveName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        startActivity(intent);
    }

    private void unSubscribe(Subscription subscription) {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
    }
}
