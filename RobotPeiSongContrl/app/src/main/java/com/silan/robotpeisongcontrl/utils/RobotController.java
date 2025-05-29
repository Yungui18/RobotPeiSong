package com.silan.robotpeisongcontrl.utils;

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
import java.util.ArrayList;
import java.util.List;

public class RobotController {
    private static final String BASE_URL = "http://192.168.11.1:1448";
    private static final Gson gson = new Gson();

    // 获取机器人状态
    public static void getRobotStatus(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/system/v1/power/status";
        OkHttpUtils.get(url, callback);
    }

    // 获取POI信息
    public static void getPoiList(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/artifact/v1/pois";
        OkHttpUtils.get(url, callback);
    }

    // 创建移动任务 - 根据新的API格式修改
    public static void createMoveAction(Poi poi, OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/motion/v1/actions";

        JSONObject json = new JSONObject();
        try {
            json.put("action_name", "slamtec.agent.actions.MoveToAction");

            JSONObject options = new JSONObject();

            // 目标坐标
            JSONObject target = new JSONObject();
            target.put("x", poi.getX());
            target.put("y", poi.getY());
            target.put("z", 0); // 通常z坐标为0
            options.put("target", target);

            // 移动选项
            JSONObject moveOptions = new JSONObject();
            moveOptions.put("mode", 0); // 自由导航模式
            moveOptions.put("flags", new JSONArray()); // 空标志数组
            moveOptions.put("yaw", 0); // 目标朝向角
            moveOptions.put("acceptable_precision", 0.1); // 可接受的精度
            moveOptions.put("fail_retry_count", 0); // 失败重试次数
            moveOptions.put("speed_ratio", 1.0); // 速度比例（1.0表示100%速度）
            options.put("move_options", moveOptions);

            json.put("options", options);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        OkHttpUtils.post(url, json.toString(), callback);
    }

    // 创建回桩任务
    public static void createReturnHomeAction(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/motion/v1/actions";

        JSONObject json = new JSONObject();
        try {
            json.put("action_name", "slamtec.agent.actions.MultiFloorBackHomeAction");
            json.put("options", new JSONObject());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        OkHttpUtils.post(url, json.toString(), callback);
    }

    // 解析POI列表 - 根据新的API响应格式修改
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
