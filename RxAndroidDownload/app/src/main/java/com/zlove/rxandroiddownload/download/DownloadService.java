package com.zlove.rxandroiddownload.download;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import rx.Subscriber;
import rx.Subscription;
import rx.schedulers.Schedulers;

/**
 * Created by ZLOVE on 2016/11/28.
 */
public class DownloadService extends Service {

    private static final String TAG = DownloadService.class.getSimpleName();
    private DownloadBinder mBinder;
    private DatabaseHelper mDatabaseHelper;
    private Map<String, Subscription> mRecordMap;

    @Override
    public void onCreate() {
        super.onCreate();
        mBinder = new DownloadBinder();
        mRecordMap = new HashMap<>();
        mDatabaseHelper = new DatabaseHelper(new DbOpenHelper(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Subscription each : mRecordMap.values()) {
            Utils.unSubscribe(each);
        }
        mDatabaseHelper.closeDataBase();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public void startDownload(RxDownload rxDownload, final String url, String saveName, String savePath, String name, String image) {
        if (mRecordMap.get(url) != null) {
            Log.w(TAG, "This url download task already exists! So do nothing.");
            return;
        }
        Subscription temp = rxDownload.download(url, saveName, savePath)
                .subscribeOn(Schedulers.io())
                .sample(500, TimeUnit.MILLISECONDS)
                .subscribe(new Subscriber<DownloadStatus>() {
                    @Override
                    public void onCompleted() {
                        Intent intent = new Intent(DownloadReceiver.RX_BROADCAST_DOWNLOAD_COMPLETE);
                        intent.putExtra(DownloadReceiver.RX_BROADCAST_KEY_URL, url);
                        sendBroadcast(intent);
                        Utils.unSubscribe(mRecordMap.get(url));
                        mRecordMap.remove(url);
                        mDatabaseHelper.updateRecord(url, DownloadFlag.COMPLETED);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.w("error", e);
                        Intent intent = new Intent(DownloadReceiver.RX_BROADCAST_DOWNLOAD_ERROR);
                        intent.putExtra(DownloadReceiver.RX_BROADCAST_KEY_URL, url);
                        intent.putExtra(DownloadReceiver.RX_BROADCAST_KEY_EXCEPTION, e);
                        sendBroadcast(intent);
                        Utils.unSubscribe(mRecordMap.get(url));
                        mRecordMap.remove(url);
                        mDatabaseHelper.updateRecord(url, DownloadFlag.FAILED);
                    }

                    @Override
                    public void onNext(DownloadStatus status) {
                        Intent intent = new Intent(DownloadReceiver.RX_BROADCAST_DOWNLOAD_NEXT);
                        intent.putExtra(DownloadReceiver.RX_BROADCAST_KEY_URL, url);
                        intent.putExtra(DownloadReceiver.RX_BROADCAST_KEY_STATUS, status);
                        sendBroadcast(intent);
                        mDatabaseHelper.updateRecord(url, status);
                    }
                });
        mRecordMap.put(url, temp);
        if (mDatabaseHelper.recordNotExists(url)) {
            mDatabaseHelper.insertRecord(url, saveName, rxDownload.getFileSavePaths(savePath)[0], name, image);
        }
    }

    public void pauseDownload(String url) {
        Utils.unSubscribe(mRecordMap.get(url));
        mRecordMap.remove(url);
        mDatabaseHelper.updateRecord(url, DownloadFlag.PAUSED);
    }

    public void cancelDownload(String url) {
        Utils.unSubscribe(mRecordMap.get(url));
        mRecordMap.remove(url);
        mDatabaseHelper.updateRecord(url, DownloadFlag.CANCELED);
    }

    public void deleteDownload(String url) {
        Utils.unSubscribe(mRecordMap.get(url));
        mRecordMap.remove(url);
        mDatabaseHelper.deleteRecord(url);
    }

    public class DownloadBinder extends Binder {
        DownloadService getService() {
            return DownloadService.this;
        }
    }
}
