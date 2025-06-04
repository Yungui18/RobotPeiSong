package com.silan.robotpeisongcontrl.utils;

import android.util.Log;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpUtils {
    private static final OkHttpClient client = new OkHttpClient();

    public interface ResponseCallback {
        void onSuccess(String response);
        void onFailure(Exception e);
    }

    public static void get(String url, ResponseCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("TAG", "onFailure: 失败1"+e);
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().string());
                    Log.d("TAG", "onResponse: 成功");
                } else {
                    callback.onFailure(new IOException("Unexpected code " + response));
                    Log.d("TAG", "onResponse: 失败2");
                }
            }
        });
    }

    public static void post(String url, String json, ResponseCallback callback) {
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
                Log.d("TAG", "onFailure: "+e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body().string());
                    Log.d("TAG", "onResponse: 成功");
                } else {
                    callback.onFailure(new IOException("Unexpected code " + response));
                    Log.d("TAG", "onResponse: "+response);
                }
            }
        });
    }
}
