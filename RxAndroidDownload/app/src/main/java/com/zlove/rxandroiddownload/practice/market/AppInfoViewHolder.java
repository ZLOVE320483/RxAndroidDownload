package com.zlove.rxandroiddownload.practice.market;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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
import zlc.season.practicalrecyclerview.AbstractViewHolder;

/**
 * Created by ZLOVE on 2016/11/30.
 */
public class AppInfoViewHolder extends AbstractViewHolder<AppInfoBean> {
    @BindView(R.id.head)
    ImageView mHead;
    @BindView(R.id.title)
    TextView mTitle;
    @BindView(R.id.content)
    TextView mContent;
    @BindView(R.id.action)
    Button mAction;

    private String defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();

    private AppInfoBean mData;
    private Context mContext;
    private RxDownload mRxDownload;
    private DownloadController mDownloadController;

    public AppInfoViewHolder(ViewGroup parent) {
        super(parent, R.layout.app_info_item);
        ButterKnife.bind(this, itemView);
        mContext = parent.getContext();

        mRxDownload = RxDownload.getInstance().context(mContext);
        mDownloadController = new DownloadController(null, mAction);
    }

    @Override
    public void setData(AppInfoBean data) {
        this.mData = data;
        mDownloadController.setStateAndDisplay(DownloadFlag.NORMAL);

        Picasso.with(mContext).load(data.img).into(mHead);
        mTitle.setText(data.name);
        mContent.setText(data.info);

        // 读取下载状态, 如果存在下载记录,则初始化为上次下载的状态
        Subscription query = mRxDownload.getDownloadRecord(data.downloadUrl)
                .subscribe(new Action1<DownloadRecord>() {
                    @Override
                    public void call(DownloadRecord record) {
                        //如果有下载记录才会执行到这里, 如果没有下载记录不会执行这里
                        int flag = record.getDownloadFlag();
                        //设置下载状态
                        mDownloadController.setStateAndDisplay(flag);
                    }
                });

        //注册广播接收器, 用于接收下载进度
        Subscription temp = mRxDownload.registerReceiver(data.downloadUrl)
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
                    public void onNext(final DownloadStatus status) {
                    }
                });
        //将subscription收集起来,在Activity销毁的时候取消订阅,以免内存泄漏
        mData.mSubscriptions.add(temp);
        mData.mSubscriptions.add(query);
    }

    @OnClick(R.id.action)
    public void onClick() {
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


    private void installApk() {
        mDownloadController.setStateAndDisplay(DownloadFlag.INSTALL);
        Uri uri = Uri.fromFile(new File(defaultPath + File.separator + mData.saveName));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        mContext.startActivity(intent);
    }

    private void start() {
        //开始下载, 先检查权限
        Subscription temp = RxPermissions.getInstance(mContext)
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
                .compose(mRxDownload.transformServiceNoReceiver(mData.downloadUrl, mData.saveName, null,
                        mData.name, mData.img))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        mDownloadController.setStateAndDisplay(DownloadFlag.STARTED);
                    }
                });
        mData.mSubscriptions.add(temp);
    }

    /**
     * 暂停下载
     */
    private void pause() {
        Subscription subscription = mRxDownload.pauseServiceDownload(mData.downloadUrl)
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        mDownloadController.setStateAndDisplay(DownloadFlag.PAUSED);
                    }
                });
        mData.mSubscriptions.add(subscription);
    }
}
