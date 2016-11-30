package com.zlove.rxandroiddownload.download;

import android.text.TextUtils;

import java.io.Closeable;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.internal.http.HttpHeaders;
import retrofit2.Response;
import rx.Subscription;

/**
 * Author: Season(ssseasonnn@gmail.com)
 * Date: 2016/11/2
 * Time: 09:07
 * 工具类
 */
class Utils {
    static String longToGMT(long lastModify) {
        Date d = new Date(lastModify);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(d);
    }

    static long GMTToLong(String GMT) throws ParseException {
        if (GMT == null || "".equals(GMT)) {
            return new Date().getTime();
        }
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date date = sdf.parse(GMT);
        return date.getTime();
    }

    static void close(Closeable closeable) throws IOException {
        if (closeable != null) {
            closeable.close();
        }
    }

    static void unSubscribe(Subscription subscription) {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
    }

    static String lastModify(Response<?> response) {
        return response.headers().get("Last-Modified");
    }

    static long contentLength(Response<?> response) {
        return HttpHeaders.contentLength(response.headers());
    }

    static String transferEncoding(Response<?> response) {
        return response.headers().get("Transfer-Encoding");
    }

    static boolean notSupportRange(Response<?> response) {
        return TextUtils.isEmpty(contentRange(response)) || contentLength(response) == -1;
    }

    static boolean serverFileChanged(Response<Void> resp) {
        return resp.code() == 200;
    }

    static boolean serverFileNotChange(Response<Void> resp) {
        return resp.code() == 206;
    }

    static String formatSize(long size) {
        String hrSize;

        double b = size;
        double k = size / 1024.0;
        double m = ((size / 1024.0) / 1024.0);
        double g = (((size / 1024.0) / 1024.0) / 1024.0);
        double t = ((((size / 1024.0) / 1024.0) / 1024.0) / 1024.0);

        DecimalFormat dec = new DecimalFormat("0.00");

        if (t > 1) {
            hrSize = dec.format(t).concat(" TB");
        } else if (g > 1) {
            hrSize = dec.format(g).concat(" GB");
        } else if (m > 1) {
            hrSize = dec.format(m).concat(" MB");
        } else if (k > 1) {
            hrSize = dec.format(k).concat(" KB");
        } else {
            hrSize = dec.format(b).concat(" B");
        }
        return hrSize;
    }

    private static String contentRange(Response<?> response) {
        return response.headers().get("Content-Range");
    }
}
