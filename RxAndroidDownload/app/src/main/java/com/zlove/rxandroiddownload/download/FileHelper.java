package com.zlove.rxandroiddownload.download;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.ParseException;

import okhttp3.ResponseBody;
import retrofit2.Response;
import rx.Subscriber;

/**
 * Created by ZLOVE on 2016/11/25.
 */
public class FileHelper {
    static final String TAG = "RxDownload";

    private static final String TMP_SUFFIX = ".tmp";
    private static final String LMF_SUFFIX = ".lmf";
    private static final String CACHE = ".cache";    //cache directory

    private static final int EACH_RECORD_SIZE = 16;
    private int RECORD_FILE_TOTAL_SIZE;

    //|*********************|
    //|*****Record  File****|
    //|*********************|
    //|  0L      |     7L   | 0
    //|  8L      |     15L  | 1
    //|  16L     |     31L  | 2
    //|  ...     |     ...  | MAX_THREADS-1
    //|*********************|
    private int MAX_THREADS = 3;

    private String mDefaultSavePath;
    private String mDefaultCachePath;

    FileHelper() {
        RECORD_FILE_TOTAL_SIZE = EACH_RECORD_SIZE * MAX_THREADS;
        setDefaultSavePath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());
    }

    void setDefaultSavePath(String defaultSavePath) {
        mDefaultSavePath = defaultSavePath;
        mDefaultCachePath = TextUtils.concat(defaultSavePath, File.separator, CACHE).toString();
    }

    public int getMaxThreads() {
        return MAX_THREADS;
    }

    void setMaxThreads(int MAX_THREADS) {
        this.MAX_THREADS = MAX_THREADS;
        RECORD_FILE_TOTAL_SIZE = EACH_RECORD_SIZE * MAX_THREADS;
    }

    void createDirectories(String savePath) throws IOException {
        createDirectories(getRealDirectoryPaths(savePath));
    }

    String[] getRealDirectoryPaths(String savePath) {
        String fileDirectory;
        String cacheDirectory;
        if (!TextUtils.isEmpty(savePath)) {
            fileDirectory = savePath;
            cacheDirectory = TextUtils.concat(savePath, File.separator, CACHE).toString();
        } else {
            fileDirectory = mDefaultSavePath;
            cacheDirectory = mDefaultCachePath;
        }
        return new String[]{fileDirectory, cacheDirectory};
    }

    String[] getRealFilePaths(String saveName, String savePath) {
        String[] directories = getRealDirectoryPaths(savePath);
        String filePath = TextUtils.concat(directories[0], File.separator, saveName).toString();
        String tempPath = TextUtils.concat(directories[1], File.separator, saveName, TMP_SUFFIX).toString();
        String lmfPath = TextUtils.concat(directories[1], File.separator, saveName, LMF_SUFFIX).toString();
        return new String[]{filePath, tempPath, lmfPath};
    }

    void prepareDownload(File lastModifyFile, File saveFile, long fileLength, String lastModify) throws IOException, ParseException {
        writeLastModify(lastModifyFile, lastModify);
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(saveFile, "rws");
            file.setLength(fileLength);//设置下载文件的长度
        } finally {
            Utils.close(file);
        }
    }

    void saveFile(Subscriber<? super DownloadStatus> sub, File saveFile, Response<ResponseBody> resp) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            try {
                int readLen;
                int downloadSize = 0;
                byte[] buffer = new byte[8192];

                DownloadStatus status = new DownloadStatus();
                inputStream = resp.body().byteStream();
                outputStream = new FileOutputStream(saveFile);

                long contentLength = resp.body().contentLength();
                boolean isChunked = !TextUtils.isEmpty(Utils.transferEncoding(resp));
                if (isChunked || contentLength == -1) {
                    status.isChunked = true;
                }
                status.setTotalSize(contentLength);

                while ((readLen = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, readLen);
                    downloadSize += readLen;
                    status.setDownloadSize(downloadSize);
                    sub.onNext(status);
                }
                outputStream.flush(); // This is important!!!
                sub.onCompleted();
                Log.i(TAG, "Normal download completed!");
            } finally {
                Utils.close(inputStream);
                Utils.close(outputStream);
                Utils.close(resp.body());
            }
        } catch (IOException e) {
            Log.i(TAG, "Normal download failed or cancel!");
            sub.onError(new Throwable(e));
        }
    }

    void prepareDownload(File lastModifyFile, File tempFile, File saveFile,
                         long fileLength, String lastModify) throws IOException, ParseException {
        writeLastModify(lastModifyFile, lastModify);
        RandomAccessFile rFile = null;
        RandomAccessFile rRecord = null;
        FileChannel channel = null;
        try {
            rFile = new RandomAccessFile(saveFile, "rws");
            rFile.setLength(fileLength);//设置下载文件的长度

            rRecord = new RandomAccessFile(tempFile, "rws");
            rRecord.setLength(RECORD_FILE_TOTAL_SIZE); //设置指针记录文件的大小
            channel = rRecord.getChannel();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, RECORD_FILE_TOTAL_SIZE);

            long start;
            long end;
            int eachSize = (int) (fileLength / MAX_THREADS);
            for (int i = 0; i < MAX_THREADS; i++) {
                if (i == MAX_THREADS - 1) {
                    start = i * eachSize;
                    end = fileLength - 1;
                } else {
                    start = i * eachSize;
                    end = (i + 1) * eachSize - 1;
                }
                buffer.putLong(start);
                buffer.putLong(end);
            }
        } finally {
            Utils.close(channel);
            Utils.close(rRecord);
            Utils.close(rFile);
        }
    }

    void saveFile(Subscriber<? super DownloadStatus> subscriber, int i, long start, long end,
                  File tempFile, File saveFile, ResponseBody response) {
        RandomAccessFile record = null;
        FileChannel recordChannel = null;

        RandomAccessFile save = null;
        FileChannel saveChannel = null;

        InputStream inStream = null;
        try {
            try {
                Log.i(TAG, Thread.currentThread().getName() + " start download from " + start + " to " + end + "!");
                int readLen;
                byte[] buffer = new byte[8192];
                DownloadStatus status = new DownloadStatus();

                record = new RandomAccessFile(tempFile, "rws");
                recordChannel = record.getChannel();
                MappedByteBuffer recordBuffer = recordChannel.map(FileChannel.MapMode.READ_WRITE, 0, RECORD_FILE_TOTAL_SIZE);
                long totalSize = recordBuffer.getLong(RECORD_FILE_TOTAL_SIZE - 8) + 1;
                status.setTotalSize(totalSize);

                save = new RandomAccessFile(saveFile, "rws");
                saveChannel = save.getChannel();
                MappedByteBuffer saveBuffer = saveChannel.map(FileChannel.MapMode.READ_WRITE, start, end - start + 1);

                inStream = response.byteStream();
                while ((readLen = inStream.read(buffer)) != -1) {
                    saveBuffer.put(buffer, 0, readLen);
                    recordBuffer.putLong(i * EACH_RECORD_SIZE, recordBuffer.getLong(i * EACH_RECORD_SIZE) + readLen);

                    status.setDownloadSize(totalSize - getResidue(recordBuffer));
                    subscriber.onNext(status);
                }
                Log.i(TAG, Thread.currentThread().getName() + " complete download! Download size is " +
                        response.contentLength() + " bytes");
                subscriber.onCompleted();
            } finally {
                Utils.close(record);
                Utils.close(recordChannel);
                Utils.close(save);
                Utils.close(saveChannel);
                Utils.close(inStream);
                Utils.close(response);
            }
        } catch (IOException e) {
            Log.i(TAG, Thread.currentThread().getName() + " download failed or cancel!");
            subscriber.onError(new Throwable(e));
        }
    }

    boolean downloadNotComplete(File tempFile) throws IOException {
        RandomAccessFile record = null;
        FileChannel channel = null;
        try {
            record = new RandomAccessFile(tempFile, "rws");
            channel = record.getChannel();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, RECORD_FILE_TOTAL_SIZE);
            long startByte;
            long endByte;
            for (int i = 0; i < MAX_THREADS; i++) {
                startByte = buffer.getLong();
                endByte = buffer.getLong();
                if (startByte <= endByte) {
                    return true;
                }
            }
            return false;
        } finally {
            Utils.close(channel);
            Utils.close(record);
        }
    }

    boolean tempFileDamaged(File tempFile, long fileLength) throws IOException {
        RandomAccessFile record = null;
        FileChannel channel = null;
        try {
            record = new RandomAccessFile(tempFile, "rws");
            channel = record.getChannel();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, RECORD_FILE_TOTAL_SIZE);
            long recordTotalSize = buffer.getLong(RECORD_FILE_TOTAL_SIZE - 8) + 1;
            return recordTotalSize != fileLength;
        } finally {
            Utils.close(channel);
            Utils.close(record);
        }
    }

    DownloadRange readDownloadRange(File tempFile) throws IOException {
        RandomAccessFile record = null;
        FileChannel channel = null;
        try {
            record = new RandomAccessFile(tempFile, "rws");
            channel = record.getChannel();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, RECORD_FILE_TOTAL_SIZE);
            long[] startByteArray = new long[MAX_THREADS];
            long[] endByteArray = new long[MAX_THREADS];
            for (int i = 0; i < MAX_THREADS; i++) {
                startByteArray[i] = buffer.getLong();
                endByteArray[i] = buffer.getLong();
            }
            return new DownloadRange(startByteArray, endByteArray);
        } finally {
            Utils.close(channel);
            Utils.close(record);
        }
    }

    String getLastModify(File file) throws IOException {
        RandomAccessFile record = null;
        try {
            record = new RandomAccessFile(file, "rws");
            record.seek(0);
            return Utils.longToGMT(record.readLong());
        } finally {
            Utils.close(record);
        }
    }

    private void createDirectories(String... directoryPaths) throws IOException {
        for (String each : directoryPaths) {
            File file = new File(each);
            if (file.exists() && file.isDirectory()) {
                Log.d(TAG, "Directory exists. Do not need create. Path = " + each);
            } else {
                Log.d(TAG, "Directory is not exists.So we need create. Path = " + each);
                boolean flag = file.mkdir();
                if (flag) {
                    Log.d(TAG, "Directory create succeed! Path = " + each);
                } else {
                    Log.d(TAG, "Directory create failed! Path = " + each);
                    throw new IOException("Directory create failed!");
                }
            }
        }
    }

    private void writeLastModify(File file, String lastModify) throws IOException, ParseException {
        RandomAccessFile record = null;
        try {
            record = new RandomAccessFile(file, "rws");
            record.setLength(8);
            record.seek(0);
            record.writeLong(Utils.GMTToLong(lastModify));
        } finally {
            Utils.close(record);
        }
    }

    /**
     * 还剩多少字节没有下载
     *
     * @param recordBuffer buffer
     * @return 剩余的字节
     */
    private long getResidue(MappedByteBuffer recordBuffer) {
        long residue = 0;
        for (int j = 0; j < MAX_THREADS; j++) {
            long startTemp = recordBuffer.getLong(j * EACH_RECORD_SIZE);
            long endTemp = recordBuffer.getLong(j * EACH_RECORD_SIZE + 8);
            long temp = endTemp - startTemp + 1;
            residue += temp;
        }
        return residue;
    }
}
