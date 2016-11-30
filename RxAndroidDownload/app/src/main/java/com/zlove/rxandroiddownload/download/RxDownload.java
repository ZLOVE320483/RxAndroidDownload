package com.zlove.rxandroiddownload.download;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import retrofit2.Response;
import retrofit2.Retrofit;
import rx.Observable;
import rx.exceptions.CompositeException;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.PublishSubject;

/**
 * Created by ZLOVE on 2016/11/25.
 */
public class RxDownload {
    private static final String TAG = RxDownload.class.getSimpleName();

    private static DownloadService mDownloadService;
    private static boolean bound = false;

    private DownloadHelper mDownloadHelper;
    private DownloadFactory mFactory;

    private Context mContext;

    private RxDownload() {
        mDownloadHelper = new DownloadHelper();
        mFactory = new DownloadFactory(mDownloadHelper);
    }

    public static RxDownload getInstance() {
        return new RxDownload();
    }

    /**
     * 普通下载时不需要context, 使用Service下载时需要context;
     *
     * @param context context
     * @return RxDownload
     */
    public RxDownload context(Context context) {
        this.mContext = context;
        return this;
    }

    public RxDownload defaultSavePath(String savePath) {
        mDownloadHelper.setDefaultSavePath(savePath);
        return this;
    }

    public RxDownload retrofit(Retrofit retrofit) {
        mDownloadHelper.setRetrofit(retrofit);
        return this;
    }

    public RxDownload maxThread(int max) {
        mDownloadHelper.setMaxThreads(max);
        return this;
    }

    public RxDownload maxRetryCount(int max) {
        mDownloadHelper.setMaxRetryCount(max);
        return this;
    }

    /**
     * 为Service中下载地址为url的下载任务注册广播接收器,用于接收该任务的下载进度.
     * 注意只接收下载地址为url的下载进度.
     * 取消订阅即可取消注册.
     *
     * @param url download url
     * @return Observable<DownloadStatus>
     */
    public Observable<DownloadStatus> registerReceiver(final String url) {
        if (mContext == null) {
            return Observable.error(new Throwable("Context is NULL! You should call " +
                    "#RxDownload.context(Context context)# first!"));
        }
        Observable<DownloadStatus> observable;
        PublishSubject<DownloadStatus> subject = PublishSubject.create();
        final DownloadReceiver receiver = new DownloadReceiver(url, subject);
        observable = subject.doOnSubscribe(new Action0() {
            @Override
            public void call() {
                mContext.registerReceiver(receiver, receiver.getFilter());
            }
        }).doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                mContext.unregisterReceiver(receiver);
            }
        });
        return observable;
    }

    /**
     * 从数据库中读取所有的下载任务
     *
     * @return Observable<List<DownloadRecord>>
     */
    public Observable<List<DownloadRecord>> getTotalDownloadRecords() {
        if (mContext == null) {
            return Observable.error(new Throwable("Context is NULL! You should call " +
                    "#RxDownload.context(Context context)# first!"));
        }
        DatabaseHelper databaseHelper = new DatabaseHelper(new DbOpenHelper(mContext));
        return databaseHelper.readAllRecords();
    }

    /**
     * 从数据库中读取下载地址为url的下载记录
     *
     * @param url download url
     * @return Observable<DownloadStatus>
     */
    public Observable<DownloadRecord> getDownloadRecord(String url) {
        if (mContext == null) {
            return Observable.error(new Throwable("Context is NULL! You should call " +
                    "#RxDownload.context(Context context)# first!"));
        }
        DatabaseHelper dataBaseHelper = new DatabaseHelper(new DbOpenHelper(mContext));
        return dataBaseHelper.readRecord(url);
    }

    /**
     * 暂停Service中下载地址为url的下载任务.
     * 同时标记数据库中的下载记录为暂停状态.
     *
     * @param url download url
     */
    public Observable<?> pauseServiceDownload(final String url) {
        if (mContext == null) {
            return Observable.error(new Throwable("Context is NULL! You should call " +
                    "#RxDownload.context(Context context)# first!"));
        }
        return Observable.just(null).doOnSubscribe(new Action0() {
            @Override
            public void call() {
                if (!bound) {
                    startBindServiceAndDo(new ServiceConnectedCallback() {
                        @Override
                        public void call() {
                            mDownloadService.pauseDownload(url);
                        }
                    });
                } else {
                    mDownloadService.pauseDownload(url);
                }
            }
        });
    }


    /**
     * 取消Service中下载地址为url的下载任务.
     * 同时标记数据库中的下载记录为取消状态.
     * 不会删除已经下载的文件.
     *
     * @param url download url
     */
    public Observable<?> cancelServiceDownload(final String url) {
        if (mContext == null) {
            return Observable.error(new Throwable("Context is NULL! You should call " +
                    "#RxDownload.context(Context context)# first!"));
        }
        return Observable.just(null).doOnSubscribe(new Action0() {
            @Override
            public void call() {
                if (!bound) {
                    startBindServiceAndDo(new ServiceConnectedCallback() {
                        @Override
                        public void call() {
                            mDownloadService.cancelDownload(url);
                        }
                    });
                } else {
                    mDownloadService.cancelDownload(url);
                }
            }
        });
    }

    /**
     * 删除Service中下载地址为url的下载任务.
     * 同时从数据库中删除该下载记录.
     * 不会删除已经下载的文件.
     *
     * @param url download url
     */
    public Observable<?> deleteServiceDownload(final String url) {
        if (mContext == null) {
            return Observable.error(new Throwable("Context is NULL! You should call " +
                    "#RxDownload.context(Context context)# first!"));
        }
        return Observable.just(null).doOnSubscribe(new Action0() {
            @Override
            public void call() {
                if (!bound) {
                    startBindServiceAndDo(new ServiceConnectedCallback() {
                        @Override
                        public void call() {
                            mDownloadService.deleteDownload(url);
                        }
                    });
                } else {
                    mDownloadService.deleteDownload(url);
                }
            }
        });
    }

    /**
     * 使用Service下载.
     * 同时注册了广播接收器接收下载进度.
     * 取消订阅即可取消注册, 但不会暂停下载.
     * 同时在数据库中保存下载记录.
     *
     * @param url      下载文件的Url
     * @param saveName 下载文件的保存名称
     * @param savePath 下载文件的保存路径, null使用默认的路径,默认保存在/storage/emulated/0/Download/目录下
     * @return Observable<DownloadStatus>
     */
    public Observable<DownloadStatus> serviceDownload(@NonNull final String url,
                                                      @NonNull final String saveName,
                                                      @Nullable final String savePath) {
        return serviceDownload(url, saveName, savePath, null, null);
    }

    /**
     * 使用Service下载.
     * 同时注册广播接收器接收下载进度.
     * 取消订阅即可取消注册, 不会暂停下载.
     * 同时在数据库中保存下载记录.
     *
     * @param url          下载文件的Url
     * @param saveName     下载文件的保存名称
     * @param savePath     下载文件的保存路径, null使用默认的路径,默认保存在/storage/emulated/0/Download/目录下
     * @param displayName  可选参数, 显示在下载记录中的名称. 如需要在下载记录中显示名称,传入此参数保存到下载记录中
     * @param displayImage 可选参数, 显示在下载记录中的图片. 如需要在下载记录中显示图片或图标,传入此参数保存到下载记录中
     * @return Observable<DownloadStatus>
     */
    public Observable<DownloadStatus> serviceDownload(@NonNull final String url,
                                                      @NonNull final String saveName,
                                                      @Nullable final String savePath,
                                                      @Nullable final String displayName,
                                                      @Nullable final String displayImage) {
        if (mContext == null) {
            return Observable.error(new Throwable("Context is NULL! You should call " +
                    "#RxDownload.context(Context context)# first!"));
        }

        PublishSubject<DownloadStatus> subject = PublishSubject.create();
        final DownloadReceiver receiver = new DownloadReceiver(url, subject);

        Observable<DownloadStatus> observable;
        observable = subject.doOnSubscribe(new Action0() {
            @Override
            public void call() {
                mContext.registerReceiver(receiver, receiver.getFilter());
                if (!bound) {
                    startBindServiceAndDo(new ServiceConnectedCallback() {
                        @Override
                        public void call() {
                            mDownloadService.startDownload(RxDownload.this,
                                    url, saveName, savePath, displayName, displayImage);
                        }
                    });
                } else {
                    mDownloadService.startDownload(RxDownload.this,
                            url, saveName, savePath, displayName, displayImage);
                }
            }
        }).doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                mContext.unregisterReceiver(receiver);
            }
        });
        return observable;
    }

    /**
     * 使用Service下载.
     * 不会注册广播接收器,接收下载进度请手动注册广播接收器.
     * 取消订阅不会暂停下载.
     * 同时在数据库中保存下载记录.
     *
     * @param url      下载文件的Url
     * @param saveName 下载文件的保存名称
     * @param savePath 下载文件的保存路径, null使用默认的路径,默认保存在/storage/emulated/0/Download/目录下
     * @return Observable emit a NULL object
     */
    public Observable<Object> serviceDownloadNoReceiver(@NonNull final String url,
                                                        @NonNull final String saveName,
                                                        @Nullable final String savePath) {
        return serviceDownloadNoReceiver(url, saveName, savePath, null, null);
    }

    /**
     * 使用Service下载.
     * 不会注册广播接收器,接收下载进度请手动注册广播接收器.
     * 取消订阅不会暂停下载.
     * 同时在数据库中保存下载记录.
     *
     * @param url          下载文件的Url
     * @param saveName     下载文件的保存名称
     * @param savePath     下载文件的保存路径, null使用默认的路径,默认保存在/storage/emulated/0/Download/目录下
     * @param displayName  可选参数, 显示在下载记录中的名称. 如需要在下载记录中显示名称,传入此参数保存到下载记录中
     * @param displayImage 可选参数, 显示在下载记录中的图片. 如需要在下载记录中显示图片或图标,传入此参数保存到下载记录中
     * @return Observable emit a NULL object
     */
    public Observable<Object> serviceDownloadNoReceiver(@NonNull final String url,
                                                        @NonNull final String saveName,
                                                        @Nullable final String savePath,
                                                        @Nullable final String displayName,
                                                        @Nullable final String displayImage) {
        return Observable.just(null).doOnSubscribe(new Action0() {
            @Override
            public void call() {
                if (!bound) {
                    startBindServiceAndDo(new ServiceConnectedCallback() {
                        @Override
                        public void call() {
                            mDownloadService.startDownload(RxDownload.this,
                                    url, saveName, savePath, displayName, displayImage);
                        }
                    });
                } else {
                    mDownloadService.startDownload(RxDownload.this,
                            url, saveName, savePath, displayName, displayImage);
                }
            }
        });
    }

    /**
     * 普通下载, 不使用Service.
     * 取消订阅则暂停下载.
     * 不会在数据库中保存下载记录.
     *
     * @param url      下载文件的Url
     * @param saveName 下载文件的保存名称
     * @param savePath 下载文件的保存路径, null使用默认的路径,默认保存在/storage/emulated/0/Download/目录下
     * @return Observable<DownloadStatus>
     */
    public Observable<DownloadStatus> download(@NonNull final String url,
                                               @NonNull final String saveName,
                                               @Nullable final String savePath) {
        return downloadDispatcher(url, saveName, savePath);
    }

    /**
     * 普通下载的Transformer版本.
     * 提供给RxJava Compose操作符使用.
     *
     * @param url      下载文件的Url
     * @param saveName 下载文件的保存名称
     * @param savePath 下载文件的保存路径, null使用默认的路径,默认保存在/storage/emulated/0/Download/目录下
     * @param <T>      T
     * @return Transformer
     */
    public <T> Observable.Transformer<T, DownloadStatus> transform(@NonNull final String url,
                                                                   @NonNull final String saveName,
                                                                   @Nullable final String savePath) {
        return new Observable.Transformer<T, DownloadStatus>() {
            @Override
            public Observable<DownloadStatus> call(Observable<T> observable) {
                return observable.flatMap(new Func1<T, Observable<DownloadStatus>>() {
                    @Override
                    public Observable<DownloadStatus> call(T t) {
                        return download(url, saveName, savePath);
                    }
                });
            }
        };
    }

    /**
     * ServiceDownload的Transformer版本.
     * 提供给RxJava Compose操作符使用.
     *
     * @param url      下载文件的Url
     * @param saveName 下载文件的保存名称
     * @param savePath 下载文件的保存路径, null使用默认的路径,默认保存在/storage/emulated/0/Download/目录下
     * @param <T>      T
     * @return Transformer
     */
    public <T> Observable.Transformer<T, DownloadStatus> transformService(@NonNull final String url,
                                                                          @NonNull final String saveName,
                                                                          @Nullable final String savePath) {
        return new Observable.Transformer<T, DownloadStatus>() {
            @Override
            public Observable<DownloadStatus> call(Observable<T> observable) {
                return observable.flatMap(new Func1<T, Observable<DownloadStatus>>() {
                    @Override
                    public Observable<DownloadStatus> call(T t) {
                        return serviceDownload(url, saveName, savePath);
                    }
                });
            }
        };
    }

    /**
     * 同上
     *
     * @param url          url
     * @param saveName     saveName
     * @param savePath     savePath
     * @param displayName  display name in download record
     * @param displayImage display image in download record
     * @param <T>          T
     * @return Transformer
     */
    public <T> Observable.Transformer<T, DownloadStatus> transformService(@NonNull final String url,
                                                                          @NonNull final String saveName,
                                                                          @Nullable final String savePath,
                                                                          @Nullable final String displayName,
                                                                          @Nullable final String displayImage) {
        return new Observable.Transformer<T, DownloadStatus>() {
            @Override
            public Observable<DownloadStatus> call(Observable<T> observable) {
                return observable.flatMap(new Func1<T, Observable<DownloadStatus>>() {
                    @Override
                    public Observable<DownloadStatus> call(T t) {
                        return serviceDownload(url, saveName, savePath, displayName, displayImage);
                    }
                });
            }
        };
    }

    /**
     * ServiceDownloadNoReceiver的Transformer版本
     * 提供给RxJava Compose操作符使用.
     *
     * @param url      下载文件的Url
     * @param saveName 下载文件的保存名称
     * @param savePath 下载文件的保存路径, null使用默认的路径,默认保存在/storage/emulated/0/Download/目录下
     * @param <T>      T
     * @return Transformer
     */
    public <T> Observable.Transformer<T, Object> transformServiceNoReceiver(@NonNull final String url,
                                                                            @NonNull final String saveName,
                                                                            @Nullable final String savePath) {
        return new Observable.Transformer<T, Object>() {
            @Override
            public Observable<Object> call(Observable<T> observable) {
                return observable.flatMap(new Func1<T, Observable<Object>>() {
                    @Override
                    public Observable<Object> call(T t) {
                        return serviceDownloadNoReceiver(url, saveName, savePath);
                    }
                });
            }
        };
    }

    /**
     * 同上
     *
     * @param url          url
     * @param saveName     saveName
     * @param savePath     savePath
     * @param displayName  display name in download record
     * @param displayImage display image in download record
     * @param <T>          T
     * @return Transformer
     */
    public <T> Observable.Transformer<T, Object> transformServiceNoReceiver(@NonNull final String url,
                                                                            @NonNull final String saveName,
                                                                            @Nullable final String savePath,
                                                                            @Nullable final String displayName,
                                                                            @Nullable final String displayImage) {
        return new Observable.Transformer<T, Object>() {
            @Override
            public Observable<Object> call(Observable<T> observable) {
                return observable.flatMap(new Func1<T, Observable<Object>>() {
                    @Override
                    public Observable<Object> call(T t) {
                        return serviceDownloadNoReceiver(url, saveName, savePath, displayName, displayImage);
                    }
                });
            }
        };
    }

    String[] getFileSavePaths(String savePath) {
        return mDownloadHelper.getFileSavePaths(savePath);
    }

    private Observable<DownloadStatus> downloadDispatcher(final String url,
                                                          final String saveName,
                                                          final String savePath) {
        try {
            mDownloadHelper.addDownloadRecord(url, saveName, savePath);
        } catch (IOException e) {
            return Observable.error(e);
        }
        return getDownloadType(url)
                .flatMap(new Func1<DownloadType, Observable<DownloadStatus>>() {
                    @Override
                    public Observable<DownloadStatus> call(DownloadType downloadType) {
                        try {
                            downloadType.prepareDownload();
                        } catch (IOException | ParseException e) {
                            return Observable.error(e);
                        }
                        try {
                            return downloadType.startDownload();
                        } catch (IOException e) {
                            return Observable.error(e);
                        }
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        mDownloadHelper.deleteDownloadRecord(url);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if (throwable instanceof CompositeException) {
                            //避免打印CompositeException内的所有异常信息
                            Log.w(TAG, throwable.getMessage());
                        } else {
                            Log.w(TAG, throwable);
                        }
                        mDownloadHelper.deleteDownloadRecord(url);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        mDownloadHelper.deleteDownloadRecord(url);
                    }
                });
    }

    private Observable<DownloadType> getDownloadType(String url) {
        if (mDownloadHelper.downloadFileExists(url)) {
            try {
                return getWhenFileExists(url);
            } catch (IOException e) {
                return getWhenFileNotExists(url);
            }
        } else {
            return getWhenFileNotExists(url);
        }
    }

    private DownloadType getWhenServerFileChanged(Response<Void> resp, String url) {
        if (Utils.notSupportRange(resp)) {
            return mFactory.url(url)
                    .fileLength(Utils.contentLength(resp))
                    .lastModify(Utils.lastModify(resp))
                    .buildNormalDownload();
        } else {
            return mFactory.url(url)
                    .fileLength(Utils.contentLength(resp))
                    .lastModify(Utils.lastModify(resp))
                    .buildMultiDownload();
        }
    }

    private Observable<DownloadType> getWhenFileNotExists(final String url) {
        return mDownloadHelper.getDownloadApi()
                .getHttpHeader(DownloadHelper.TEST_RANGE_SUPPORT, url)
                .map(new Func1<Response<Void>, DownloadType>() {
                    @Override
                    public DownloadType call(Response<Void> response) {
                        if (Utils.notSupportRange(response)) {
                            return mFactory.url(url)
                                    .fileLength(Utils.contentLength(response))
                                    .lastModify(Utils.lastModify(response))
                                    .buildNormalDownload();
                        } else {
                            return mFactory.url(url)
                                    .lastModify(Utils.lastModify(response))
                                    .fileLength(Utils.contentLength(response))
                                    .buildMultiDownload();
                        }
                    }
                }).retry(new Func2<Integer, Throwable, Boolean>() {
                    @Override
                    public Boolean call(Integer integer, Throwable throwable) {
                        return mDownloadHelper.retry(integer, throwable);
                    }
                });
    }

    private Observable<DownloadType> getWhenFileExists(final String url) throws IOException {
        return mDownloadHelper.getDownloadApi()
                .getHttpHeaderWithIfRange(DownloadHelper.TEST_RANGE_SUPPORT, mDownloadHelper.getLastModify(url), url)
                .map(new Func1<Response<Void>, DownloadType>() {
                    @Override
                    public DownloadType call(Response<Void> resp) {
                        if (Utils.serverFileNotChange(resp)) {
                            return getWhenServerFileNotChange(resp, url);
                        } else if (Utils.serverFileChanged(resp)) {
                            return getWhenServerFileChanged(resp, url);
                        } else {
                            throw new RuntimeException("unknown error");
                        }
                    }
                }).retry(new Func2<Integer, Throwable, Boolean>() {
                    @Override
                    public Boolean call(Integer integer, Throwable throwable) {
                        return mDownloadHelper.retry(integer, throwable);
                    }
                });
    }

    private DownloadType getWhenServerFileNotChange(Response<Void> resp, String url) {
        if (Utils.notSupportRange(resp)) {
            return getWhenNotSupportRange(resp, url);
        } else {
            return getWhenSupportRange(resp, url);
        }
    }

    private DownloadType getWhenSupportRange(Response<Void> resp, String url) {
        long contentLength = Utils.contentLength(resp);
        try {
            if (mDownloadHelper.needReDownload(url, contentLength)) {
                return mFactory.url(url)
                        .fileLength(contentLength)
                        .lastModify(Utils.lastModify(resp))
                        .buildMultiDownload();
            }
            if (mDownloadHelper.downloadNotComplete(url)) {
                return mFactory.url(url)
                        .fileLength(contentLength)
                        .lastModify(Utils.lastModify(resp))
                        .buildContinueDownload();
            }
        } catch (IOException e) {
            Log.w(TAG, "download record file may be damaged,so we will re download");
            return mFactory.url(url)
                    .fileLength(contentLength)
                    .lastModify(Utils.lastModify(resp))
                    .buildMultiDownload();
        }
        return mFactory.fileLength(contentLength).buildAlreadyDownload();
    }

    private DownloadType getWhenNotSupportRange(Response<Void> resp, String url) {
        long contentLength = Utils.contentLength(resp);
        if (mDownloadHelper.downloadNotComplete(url, contentLength)) {
            return mFactory.url(url)
                    .fileLength(contentLength)
                    .lastModify(Utils.lastModify(resp))
                    .buildNormalDownload();
        } else {
            return mFactory.fileLength(contentLength).buildAlreadyDownload();
        }
    }

    private void startBindServiceAndDo(final ServiceConnectedCallback callback) {
        Log.w(TAG, "Download Service is not Start or Bind. So start Service and Bind.");
        Intent intent = new Intent(mContext, DownloadService.class);
        mContext.startService(intent);
        mContext.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                DownloadService.DownloadBinder downloadBinder = (DownloadService.DownloadBinder) iBinder;
                mDownloadService = downloadBinder.getService();
                mContext.unbindService(this);
                bound = true;
                callback.call();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                bound = false;
            }
        }, Context.BIND_AUTO_CREATE);
    }

    private interface ServiceConnectedCallback {
        void call();
    }
}
