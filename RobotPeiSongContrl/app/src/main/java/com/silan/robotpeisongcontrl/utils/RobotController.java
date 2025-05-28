package com.silan.robotpeisongcontrl.utils;

import com.google.gson.Gson;
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

    // 获取机器人状态 (文档: 5.2节)
    public static void getRobotStatus(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/system/v1/power/status";
        OkHttpUtils.get(url, callback);
    }

    // 获取POI信息 (文档: 5.3节)
    public static void getPoiList(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/multi-floor/map/v1/pois";
        OkHttpUtils.get(url, callback);
    }

    // 创建移动任务 (文档: 5.6.2节)
    public static void createMoveAction(String poiName, OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/motion/v1/actions";

        JSONObject json = new JSONObject();
        try {
            json.put("action_name", "slamtec.agent.actions.MultiFloorMoveAction");

            JSONObject options = new JSONObject();
            JSONObject target = new JSONObject();
            target.put("poi_name", poiName);
            options.put("target", target);

            JSONObject moveOptions = new JSONObject();
            moveOptions.put("mode", 0);
            JSONArray flags = new JSONArray();
            flags.put("precise");
            moveOptions.put("flags", flags);
            options.put("move_options", moveOptions);

            json.put("options", options);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        OkHttpUtils.post(url, json.toString(), callback);
    }

    // 创建回桩任务 (文档: 5.8节)
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

    // 解析POI列表
    public static List<Poi> parsePoiList(String jsonResponse) {
        Type listType = new TypeToken<ArrayList<Poi>>(){}.getType();
        return gson.fromJson(jsonResponse, listType);
    }

    // 解析机器人状态
    public static RobotStatus parseRobotStatus(String jsonResponse) {
        return gson.fromJson(jsonResponse, RobotStatus.class);
    }

    // 检查点位是否存在
    public static boolean pointExists(String pointId, List<Poi> poiList) {
        for (Poi poi : poiList) {
            if (poi.getDisplayName().equals(pointId)) {
                return true;
            }
        }
        return false;
    }
}
