package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.MileageResponse;
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
    public static final Gson gson = new Gson();

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
    }

    // 关闭指定仓门
    public static void closeCargoDoor(int doorId, SimpleCallback callback) {
        Log.d("RobotController", "Simulating closing door: " + doorId);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            callback.onSuccess();
        }, 1000);
    }

    // 获取POI信息
    public static void getPoiList(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/artifact/v1/pois";
        OkHttpUtils.get(url, callback);
    }

    // 创建移动任务 - 根据新的API格式修改
    public static void createMoveAction(Context context, Poi poi, OkHttpUtils.ResponseCallback callback) {
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
        int navMode = MotionConfigSPUtils.getNavMode(context);
        moveOptions.addProperty("mode", navMode);
        JsonArray flags = new JsonArray();
//        flags.add("precise"); // 精确到点模式
        flags.add("with_yaw"); // 启用精确朝向
        moveOptions.add("flags", flags);
        moveOptions.addProperty("yaw", poi.getYaw());// 目标朝向角
        float acceptablePrecision = MotionConfigSPUtils.getAcceptablePrecision(context);
        moveOptions.addProperty("acceptable_precision", acceptablePrecision);
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
                    // 1. 强制使用 UTF-8 并 trim 掉可能的不可见字符
                    String jsonStr = responseData.string(StandardCharsets.UTF_8).trim();
                    JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();

                    JsonObject data;
                    // 2. 自动处理两种常见的 JSON 结构：
                    // A. 直接在根部 {"x":0.1, "y":0.2, "yaw":0}
                    // B. 嵌套在 pose 里 {"pose": {"x":0.1, "y":0.2, "yaw":0}}
                    if (root.has("pose") && root.get("pose").isJsonObject()) {
                        data = root.getAsJsonObject("pose");
                    } else {
                        data = root;
                    }

                    // 3. 安全解析字段，若不存在则抛出异常进入 catch
                    if (!data.has("x") || !data.has("y") || !data.has("yaw")) {
                        throw new Exception("JSON中缺少坐标字段 x/y/yaw: " + jsonStr);
                    }

                    double x = data.get("x").getAsDouble();
                    double y = data.get("y").getAsDouble();
                    double yaw = data.get("yaw").getAsDouble();

                    callback.onSuccess(new RobotPose(x, y, yaw));
                } catch (Exception e) {
                    Log.e(TAG, "解析位姿失败: " + e.getMessage());
                    callback.onFailure(e);
                }
            }
            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    public static void getMileageData(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/core/statistics/v1/odometry";
        OkHttpUtils.get(url, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    // 关键1：先去除首尾空白字符（空格/换行/制表符等）
                    String trimJson = json.trim();
                    Log.d(TAG, "里程接口返回原始数据（trim后）：" + trimJson);

                    MileageResponse response;
                    // 关键2：优化正则，匹配任意浮点数（包括超长小数），且基于trim后的字符串判断
                    if (trimJson.matches("^-?\\d+(\\.\\d+)?$")) {
                        double totalMileage = Double.parseDouble(trimJson);
                        // 手动构建MileageResponse对象
                        response = new MileageResponse();
                        // ===== 务必确认MileageResponse的字段名，示例用setTotalMileage，需替换为你的实际字段 =====
                        response.setTotalMileage(totalMileage);
                        response.setTimestamp(System.currentTimeMillis());
                    }
                    // 兼容JSON对象格式
                    else if (trimJson.startsWith("{") && trimJson.endsWith("}")) {
                        response = gson.fromJson(trimJson, MileageResponse.class);
                        response.setTimestamp(System.currentTimeMillis());
                    }
                    // 非法格式兜底
                    else {
                        Log.e(TAG, "里程接口返回非法格式（trim后）：" + trimJson);
                        callback.onFailure(new Exception("接口返回格式非法，既非数字也非JSON对象"));
                        return;
                    }

                    // 回调成功
                    callback.onSuccess(responseData);

                } catch (NumberFormatException e) {
                    Log.e(TAG, "里程数值解析失败：" + e.getMessage());
                    callback.onFailure(new Exception("里程数值解析失败：" + e.getMessage()));
                } catch (Exception e) {
                    Log.e(TAG, "里程数据处理异常：" + e.getMessage());
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    public static interface MileageDataCallback {
        void onSuccess(MileageResponse response);
        void onFailure(Exception e);
    }

    // ========== 新增：重载方法（返回处理好的MileageResponse，核心修改2） ==========
    public static void getMileageData(MileageDataCallback callback) {
        String url = BASE_URL + "/api/core/statistics/v1/odometry";
        OkHttpUtils.get(url, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    String trimJson = json.trim();
                    Log.d(TAG, "里程接口返回原始数据（trim后）：" + trimJson);

                    MileageResponse response;
                    // 1. 纯数字处理
                    if (trimJson.matches("^-?\\d+(\\.\\d+)?$")) {
                        double totalMileage = Double.parseDouble(trimJson);
                        response = new MileageResponse();
                        // ===== 务必确认MileageResponse的实际字段名，示例用setTotalMileage，需替换 =====
                        response.setTotalMileage(totalMileage);
                        response.setTimestamp(System.currentTimeMillis());
                    }
                    // 2. JSON对象兼容
                    else if (trimJson.startsWith("{") && trimJson.endsWith("}")) {
                        response = gson.fromJson(trimJson, MileageResponse.class);
                        response.setTimestamp(System.currentTimeMillis());
                    }
                    // 3. 非法格式
                    else {
                        callback.onFailure(new Exception("接口返回格式非法：" + trimJson));
                        return;
                    }

                    // 回调：直接返回处理好的MileageResponse
                    callback.onSuccess(response);

                } catch (NumberFormatException e) {
                    callback.onFailure(new Exception("数字解析失败：" + e.getMessage()));
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

    // 持久化保存接口
    public static void persistMapSave(OkHttpUtils.ResponseCallback callback) {
        String url = BASE_URL + "/api/multi-floor/map/v1/stcm/:sync";
        // 调用POST接口（无请求体）
        OkHttpUtils.post(url, "", new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String response = responseData.string(StandardCharsets.UTF_8);
                    Log.d(TAG, "持久化保存成功: " + response);
                    callback.onSuccess(responseData);
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "持久化保存失败", e);
                callback.onFailure(e);
            }
        });
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

    /**
     * 机器人事件实体类（对接/api/platform/v1/events接口响应）
     */
    public static class RobotEvent {
        private String type;
        private String timestamp;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }

    /**
     * 机器人事件回调接口
     */
    public interface RobotEventCallback {
        void onEventReceived(List<RobotEvent> events); // 事件接收成功
        void onEventFailure(Exception e);              // 事件请求失败
    }

    /**
     * 获取机器人事件列表（GET /api/platform/v1/events）
     */
    public static void getRobotEvents(RobotEventCallback callback) {
        String url = BASE_URL + "/api/platform/v1/events";
        OkHttpUtils.get(url, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    // 解析事件列表
                    Type eventListType = new TypeToken<List<RobotEvent>>() {}.getType();
                    List<RobotEvent> events = gson.fromJson(json, eventListType);
                    callback.onEventReceived(events);
                } catch (Exception e) {
                    callback.onEventFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                callback.onEventFailure(e);
            }
        });
    }

    /**
     * 启动机器人事件轮询（持续监听设备状态）
     * @param interval 轮询间隔（毫秒）
     * @param callback 事件回调
     * @return 轮询Runnable（用于停止轮询）
     */
    public static Runnable startRobotEventPolling(long interval, RobotEventCallback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        // 核心修复：用数组包装Runnable变量，数组为final（引用不可变），数组内元素可修改
        final Runnable[] pollingRunnableArr = new Runnable[1];
        // 初始化数组内的Runnable元素，解决编译器初始化检查问题
        pollingRunnableArr[0] = new Runnable() {
            @Override
            public void run() {
                getRobotEvents(new RobotEventCallback() {
                    @Override
                    public void onEventReceived(List<RobotEvent> events) {
                        callback.onEventReceived(events);
                        // 引用数组内的Runnable，满足内部类引用规则
                        handler.postDelayed(pollingRunnableArr[0], interval);
                    }

                    @Override
                    public void onEventFailure(Exception e) {
                        Log.e(TAG, "获取机器人事件失败，将重试", e);
                        callback.onEventFailure(e);
                        // 引用数组内的Runnable，满足内部类引用规则
                        handler.postDelayed(pollingRunnableArr[0], interval);
                    }
                });
            }
        };
        // 立即启动第一次轮询
        handler.post(pollingRunnableArr[0]);
        // 返回实际的Runnable对象，供外部停止轮询使用
        return pollingRunnableArr[0];
    }
}
