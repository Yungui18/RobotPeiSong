package com.silan.robotpeisongcontrl.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.RobotStatus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class RobotController {
    private static final String TAG = "RobotController";
    public static final String BASE_URL = "http://192.168.11.1:1448";
    private static final Gson gson = new Gson();

    // 获取机器人状态
    public static void getRobotStatus(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/system/v1/power/status";
        OkHttpUtils.get(url, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    RobotStatus status = gson.fromJson(json, RobotStatus.class);
                    callback.onSuccess(responseData);
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String error);
    }

    // 打开指定仓门
    public static void openCargoDoor(int doorId, SimpleCallback callback) {
        Log.d("RobotController", "Simulating opening door: " + doorId);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            callback.onSuccess();
        }, 1000);

//        String url = BASE_URL + "/api/delivery/v1/cargos/" + doorId + "/open";
//        OkHttpUtils.put(url, "", callback);
    }

    // 关闭指定仓门
    public static void closeCargoDoor(int doorId, SimpleCallback callback) {
        Log.d("RobotController", "Simulating closing door: " + doorId);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            callback.onSuccess();
        }, 1000);

//        String url = BASE_URL + "/api/delivery/v1/cargos/" + doorId + "/close";
//        OkHttpUtils.put(url, "", callback);
    }

    // 获取POI信息
    public static void getPoiList(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/artifact/v1/pois";
        OkHttpUtils.get(url, callback);
    }

    // 创建移动任务 - 根据新的API格式修改
    public static void createMoveAction(Poi poi, OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/motion/v1/actions";

        // 构建JSON但不转换为字符串
        JsonObject json = new JsonObject();
        json.addProperty("action_name", "slamtec.agent.actions.MoveToAction");

        JsonObject options = new JsonObject();
        JsonObject target = new JsonObject();
        target.addProperty("x", poi.getX());
        target.addProperty("y", poi.getY());
        target.addProperty("z", 0);
        options.add("target", target);

        JsonObject moveOptions = new JsonObject();
        moveOptions.addProperty("mode", 2);// 自由导航模式
        moveOptions.add("flags", new JsonArray());
        JSONArray flags = new JSONArray();
        flags.put("precise"); // 精确到点模式
        flags.put("with_yaw"); // 启用精确朝向
        moveOptions.addProperty("yaw", poi.getYaw());// 目标朝向角
        moveOptions.addProperty("acceptable_precision", 0.1);// 可接受的精度
        moveOptions.addProperty("fail_retry_count", 3);// 失败重试次数
        moveOptions.addProperty("speed_ratio", 1.0); // 速度比例
        options.add("move_options", moveOptions);

        json.add("options", options);

        OkHttpUtils.post(url, json.toString(), callback);
    }

    public static void pollActionStatus(String actionId, OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/motion/v1/actions/" + actionId;
        OkHttpUtils.get(url, callback);
    }

    public static void getRobotPose(RobotPoseCallback callback) {
        String url = BASE_URL + "/api/core/slam/v1/localization/pose";
        OkHttpUtils.get(url, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String jsonStr = responseData.string(StandardCharsets.UTF_8);
                    JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
                    double x = json.get("x").getAsDouble();
                    double y = json.get("y").getAsDouble();
                    double yaw = json.get("yaw").getAsDouble();
                    callback.onSuccess(new RobotPose(x, y, yaw));
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }
            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }
    public interface RobotPoseCallback {
        void onSuccess(RobotPose pose);
        void onFailure(Exception e);
    }
    public static class RobotPose {
        public double x;
        public double y;
        public double yaw;
        public RobotPose() {}
        public RobotPose(double x, double y, double yaw) {
            this.x = x;
            this.y = y;
            this.yaw = yaw;
        }
    }

    // 创建回桩任务
    public static void createReturnHomeAction(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/motion/v1/actions";

        JSONObject json = new JSONObject();
        try {
            JSONObject gohomeOptions = new JSONObject();
            gohomeOptions.put("mode", 2); // 轨道优先模式
            JSONObject options = new JSONObject();
            options.put("gohome_options", gohomeOptions);
            json.put("action_name", "slamtec.agent.actions.MultiFloorBackHomeAction");
//            json.put("options", new JSONObject());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        OkHttpUtils.post(url, json.toString(), callback);
    }

    // 取消当前任务
    public static void cancelCurrentAction(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/motion/v1/actions/:current";
        OkHttpUtils.delete(url, callback);
    }


    // 解析POI列表
    public static List<Poi> parsePoiList(String jsonResponse) {
        List<Poi> poiList = new ArrayList<>();
        try {
            JsonArray jsonArray = JsonParser.parseString(jsonResponse).getAsJsonArray();

            for (JsonElement element : jsonArray) {
                JsonObject poiObject = element.getAsJsonObject();
                Poi poi = new Poi();

                // 设置ID
                if (poiObject.has("id")) {
                    poi.setId(poiObject.get("id").getAsString());
                }

                // 解析位置信息
                if (poiObject.has("pose")) {
                    JsonObject poseObject = poiObject.getAsJsonObject("pose");
                    if (poseObject.has("x")) poi.setX(poseObject.get("x").getAsDouble());
                    if (poseObject.has("y")) poi.setY(poseObject.get("y").getAsDouble());
                    if (poseObject.has("yaw")) poi.setYaw(poseObject.get("yaw").getAsDouble());
                }

                // 解析元数据 - 获取显示名称
                if (poiObject.has("metadata")) {
                    JsonObject metadata = poiObject.getAsJsonObject("metadata");
                    if (metadata.has("display_name")) {
                        poi.setDisplayName(metadata.get("display_name").getAsString());
                    }
                }

                poiList.add(poi);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return poiList;
    }

    // 解析机器人状态
    public static RobotStatus parseRobotStatus(String jsonResponse) {
        return gson.fromJson(jsonResponse, RobotStatus.class);
    }

    // 根据ID查找POI
    public static Poi findPoiByName(String poiName, List<Poi> poiList) {
        for (Poi poi : poiList) {
            if (poi.getDisplayName().equals(poiName)) {
                return poi;
            }
        }
        return null;
    }
}
