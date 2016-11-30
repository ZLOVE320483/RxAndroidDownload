package com.zlove.rxandroiddownload.practice.manager;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
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
import zlc.season.practicalrecyclerview.AbstractAdapter;
import zlc.season.practicalrecyclerview.AbstractViewHolder;

/**
 * Created by ZLOVE on 2016/11/30.
 */
public class DownloadViewHolder extends AbstractViewHolder<DownloadBean> {
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
    Button mActionButton;
    @BindView(R.id.name)
    TextView mName;
    @BindView(R.id.delete)
    Button mDelete;
    @BindView(R.id.cancel)
    Button mCancel;

    private AbstractAdapter mAdapter;
    private Context mContext;
    private DownloadBean mData;
    private RxDownload mRxDownload;

    private DownloadController mDownloadController;

    public DownloadViewHolder(ViewGroup parent, AbstractAdapter adapter) {
        super(parent, R.layout.download_manager_item);
        ButterKnife.bind(this, itemView);
        this.mAdapter = adapter;

        mContext = parent.getContext();
        mRxDownload = RxDownload.getInstance().context(mContext);
        mDownloadController = new DownloadController(mStatusText, mActionButton);
    }

    @Override
    public void setData(DownloadBean data) {
        this.mData = data;
        initFirstState(data);

        //注册广播接收器, 用于接收下载进度
        Subscription temp = mRxDownload.registerReceiver(mData.mRecord.getUrl())
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
                        updateProgressStatus(status);
                    }
                });

        //将subscription收集起来,在Activity销毁的时候取消订阅,以免内存泄漏
        mData.mSubscription.add(temp);
    }

    @OnClick({R.id.action, R.id.cancel, R.id.delete})
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

            case R.id.cancel:
                cancel();
                break;

            case R.id.delete:
                delete();
                break;
        }
    }


    //设置初始状态
    private void initFirstState(DownloadBean param) {
        //如果创建下载时没有传入image参数, 则获取到的image为null
        if (TextUtils.isEmpty(param.mRecord.getImage())) {
            Picasso.with(mContext).load(R.mipmap.ic_file_download).into(mImg);
        } else {
            Picasso.with(mContext).load(param.mRecord.getImage()).into(mImg);
        }
        //如果创建下载时没有传入display name参数,则获取到的名称为null
        if (TextUtils.isEmpty(param.mRecord.getName())) {
            mName.setText(param.mRecord.getSaveName());
        } else {
            mName.setText(param.mRecord.getName());
        }

        int flag = param.mRecord.getDownloadFlag();
        mDownloadController.setStateAndDisplay(flag);

        //如果读取出来是已取消或已完成状态, 特殊处理一下,显示删除按钮
        if (flag == DownloadFlag.CANCELED || flag == DownloadFlag.COMPLETED) {
            mCancel.setVisibility(View.GONE);
            mDelete.setVisibility(View.VISIBLE);
        }

        updateProgressStatus(param.mRecord.getStatus());
    }

    //更新下载进度
    private void updateProgressStatus(DownloadStatus status) {
        mProgress.setIndeterminate(status.isChunked);
        mProgress.setMax((int) status.getTotalSize());
        mProgress.setProgress((int) status.getDownloadSize());
        mPercent.setText(status.getPercent());
        mSize.setText(status.getFormatStatusString());
    }

    //下载完成自动打开安装程序
    private void installApk() {
        mDownloadController.setStateAndDisplay(DownloadFlag.INSTALL);
        Uri uri = Uri.fromFile(new File(mData.mRecord.getSavePath() + File.separator + mData.mRecord.getSaveName()));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        mContext.startActivity(intent);
    }

    //开始下载, 先检查权限
    private void start() {
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
                .compose(mRxDownload.transformServiceNoReceiver(mData.mRecord.getUrl(), mData.mRecord.getSaveName(),
                        mData.mRecord.getSavePath()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        mDownloadController.setStateAndDisplay(DownloadFlag.STARTED);
                        mDelete.setVisibility(View.GONE);
                        mCancel.setVisibility(View.VISIBLE);
                    }
                });
        mData.mSubscription.add(temp);
    }

    private void pause() {
        Subscription subscription = mRxDownload.pauseServiceDownload(mData.mRecord.getUrl())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        mDownloadController.setStateAndDisplay(DownloadFlag.PAUSED);
                    }
                });

        mData.mSubscription.add(subscription);
    }

    private void cancel() {
        Subscription subscription = mRxDownload.cancelServiceDownload(mData.mRecord.getUrl())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        mDownloadController.setStateAndDisplay(DownloadFlag.CANCELED);
                        mCancel.setVisibility(View.GONE);
                        mDelete.setVisibility(View.VISIBLE);
                    }
                });
        mData.mSubscription.add(subscription);
    }

    private void delete() {
        Subscription subscription = mRxDownload.deleteServiceDownload(mData.mRecord.getUrl())
                .doOnNext(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        //Important!! 删除item前必须先取消订阅!!
                        mData.mSubscription.clear();
                    }
                })
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        //删除item并刷新adapter
                        mAdapter.remove(getAdapterPosition());
                    }
                });
        mData.mSubscription.add(subscription);
    }
}
