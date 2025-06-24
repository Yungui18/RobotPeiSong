package com.silan.robotpeisongcontrl.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;

public class OkHttpUtils {
    private static final String TAG = "OkHttpUtils";
    private static final int MAX_RESPONSE_SIZE = 2 * 1024 * 1024; // 2MB最大响应限制

    // 配置自定义的OkHttpClient
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                // 添加请求重试拦截器
                Request request = chain.request();
                Response response = null;
                IOException exception = null;

                // 最多重试3次
                for (int i = 0; i < 3; i++) {
                    try {
                        response = chain.proceed(request);
                        if (response.isSuccessful()) {
                            return response;
                        } else if (response.code() == 502 || response.code() == 503) {
                            // 服务器错误，关闭响应体后重试
                            if (response.body() != null) {
                                response.body().close();
                            }
                            try {
                                Thread.sleep(1000); // 等待1秒后重试
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            // 非重试错误，直接返回
                            return response;
                        }
                    } catch (IOException e) {
                        exception = e;
                        Log.w(TAG, "Retry " + (i + 1) + " for: " + request.url(), e);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }

                // 所有重试都失败
                if (exception != null) throw exception;
                if (response != null) return response;
                throw new IOException("Unknown network error");
            })
            .build();

    public interface ResponseCallback {
        void onSuccess(ByteString responseData);
        void onFailure(Exception e);
    }

    public static void get(String url, ResponseCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new StreamHandlingCallback(callback));
    }

    public static void post(String url, String json, ResponseCallback callback) {
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new StreamHandlingCallback(callback));
    }

    public static void put(String url, String json, ResponseCallback callback) {
        // 模拟实现
        Log.d(TAG, "模拟PUT请求: " + url);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            callback.onSuccess(ByteString.EMPTY);
        }, 1000);

        /*
        // 实际实现（设备到位后启用）
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .put(body)
                .build();

        client.newCall(request).enqueue(new StreamHandlingCallback(callback));
        */
    }
    public static void delete(String url, ResponseCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .delete()  // 使用 DELETE 方法
                .build();

        client.newCall(request).enqueue(new StreamHandlingCallback(callback));
    }

    // 自定义Callback处理流式响应
    private static class StreamHandlingCallback implements Callback {
        private final ResponseCallback callback;
        private int retryCount = 0;

        public StreamHandlingCallback(ResponseCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onFailure(Call call, IOException e) {
            // 网络故障时重试
            if (retryCount < 2 && !call.isCanceled()) {
                retryCount++;
                Log.w(TAG, "Retry " + retryCount + " for: " + call.request().url());
                call.clone().enqueue(this);
            } else {
                callback.onFailure(e);
            }
        }

        @Override
        public void onResponse(Call call, Response response) {
            try (ResponseBody body = response.body()) {
                if (body == null) {
                    callback.onFailure(new IOException("Empty response body"));
                    return;
                }

                // 获取内容长度（可能不存在）
                long contentLength = body.contentLength();
                if (contentLength > MAX_RESPONSE_SIZE) {
                    callback.onFailure(new IOException("Response too large: " + contentLength + " bytes"));
                    return;
                }

                // 流式读取响应
                BufferedSource source = body.source();
                Buffer buffer = new Buffer();
                long totalRead = 0;

                while (!source.exhausted()) {
                    long read = source.read(buffer, 2048); // 每次读取2KB
                    if (read == -1) break;

                    totalRead += read;
                    if (totalRead > MAX_RESPONSE_SIZE) {
                        callback.onFailure(new IOException("Response exceeded size limit"));
                        return;
                    }
                }

                // 获取完整的响应数据
                ByteString byteString = buffer.readByteString();

                // 验证响应完整性
                if (contentLength > 0 && byteString.size() != contentLength) {
                    callback.onFailure(new ProtocolException(
                            "Incomplete response. Expected: " + contentLength +
                                    ", received: " + byteString.size()
                    ));
                    return;
                }

                // 成功回调
                callback.onSuccess(byteString);

            } catch (IOException e) {
                // 处理流读取异常
                if (retryCount < 2) {
                    retryCount++;
                    Log.w(TAG, "Retry " + retryCount + " for: " + call.request().url());
                    call.clone().enqueue(this);
                } else {
                    callback.onFailure(e);
                }
            } finally {
                if (!response.isSuccessful() && response.body() != null) {
                    response.body().close();
                }
            }
        }
    }

    // 自定义协议异常类
    public static class ProtocolException extends IOException {
        public ProtocolException(String message) {
            super(message);
        }
    }
}
