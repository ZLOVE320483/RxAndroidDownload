package com.zlove.rxandroiddownload.download;

/**
 * Created by ZLOVE on 2016/11/25.
 */
public class DownloadFactory {
    private String mUrl;
    private long mFileLength;
    private String mLastModify;
    private DownloadHelper mDownloadHelper;

    public DownloadFactory(DownloadHelper mDownloadHelper) {
        this.mDownloadHelper = mDownloadHelper;
    }

    public DownloadFactory url(String mUrl) {
        this.mUrl = mUrl;
        return this;
    }

    DownloadFactory fileLength(long fileLength) {
        this.mFileLength = fileLength;
        return this;
    }

    DownloadFactory lastModify(String lastModify) {
        this.mLastModify = lastModify;
        return this;
    }

    DownloadType buildNormalDownload() {
        DownloadType type = new DownloadType.NormalDownload();
        type.mUrl = this.mUrl;
        type.mFileLength = this.mFileLength;
        type.mLastModify = this.mLastModify;
        type.mDownloadHelper = this.mDownloadHelper;
        return type;
    }

    DownloadType buildContinueDownload() {
        DownloadType type = new DownloadType.ContinueDownload();
        type.mUrl = this.mUrl;
        type.mFileLength = this.mFileLength;
        type.mLastModify = this.mLastModify;
        type.mDownloadHelper = this.mDownloadHelper;
        return type;
    }

    DownloadType buildMultiDownload() {
        DownloadType type = new DownloadType.MultiThreadDownload();
        type.mUrl = this.mUrl;
        type.mFileLength = this.mFileLength;
        type.mLastModify = this.mLastModify;
        type.mDownloadHelper = this.mDownloadHelper;
        return type;
    }

    DownloadType buildAlreadyDownload() {
        DownloadType type = new DownloadType.AlreadyDownloaded();
        type.mUrl = this.mUrl;
        type.mFileLength = this.mFileLength;
        type.mLastModify = this.mLastModify;
        type.mDownloadHelper = this.mDownloadHelper;
        return type;
    }
}
