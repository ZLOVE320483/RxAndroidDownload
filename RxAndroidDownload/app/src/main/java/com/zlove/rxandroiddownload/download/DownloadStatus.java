package com.zlove.rxandroiddownload.download;

import android.os.Parcel;
import android.os.Parcelable;

import java.text.DecimalFormat;

import static com.zlove.rxandroiddownload.download.Utils.formatSize;

/**
 * Created by ZLOVE on 2016/11/25.
 */
public class DownloadStatus implements Parcelable {

    public static final Creator<DownloadStatus> CREATOR = new Creator<DownloadStatus>() {
        @Override
        public DownloadStatus createFromParcel(Parcel parcel) {
            return new DownloadStatus(parcel);
        }

        @Override
        public DownloadStatus[] newArray(int size) {
            return new DownloadStatus[size];
        }
    };

    public boolean isChunked = false;
    private long totalSize;
    private long downloadSize;

    public DownloadStatus() {
    }

    public DownloadStatus(long totalSize, long downloadSize) {
        this.totalSize = totalSize;
        this.downloadSize = downloadSize;
    }

    public DownloadStatus(boolean isChunked, long totalSize, long downloadSize) {
        this.isChunked = isChunked;
        this.totalSize = totalSize;
        this.downloadSize = downloadSize;
    }

    protected DownloadStatus(Parcel in) {
        this.isChunked = in.readByte() != 0;
        this.totalSize = in.readLong();
        this.downloadSize = in.readLong();
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public long getDownloadSize() {
        return downloadSize;
    }

    public void setDownloadSize(long downloadSize) {
        this.downloadSize = downloadSize;
    }

    /**
     * 获得格式化的总Size
     *
     * @return example: 2KB , 10MB
     */
    public String getFormatTotalSize() {
        return formatSize(totalSize);
    }

    public String getFormatDownloadSize() {
        return formatSize(downloadSize);
    }


    /**
     * 获得格式化的状态字符串
     *
     * @return example: 2MB/36MB
     */
    public String getFormatStatusString() {
        return getFormatDownloadSize() + "/" + getFormatTotalSize();
    }

    /**
     * 获得下载的百分比, 保留两位小数
     *
     * @return example: 5.25%
     */
    public String getPercent() {
        String percent;
        Double result;
        if (totalSize == 0L) {
            result = 0.0;
        } else {
            result = downloadSize * 1.0 / totalSize;
        }
        DecimalFormat df = new DecimalFormat("0.00");
        percent = df.format(result);
        return percent;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.isChunked ? (byte) 1 : (byte) 0);
        dest.writeLong(this.totalSize);
        dest.writeLong(this.downloadSize);
    }
}
