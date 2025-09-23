package com.silan.robotpeisongcontrl.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.utils.DoorController;
import com.silan.robotpeisongcontrl.utils.DoorControllerFactory;
import com.silan.robotpeisongcontrl.utils.OnDataReceivedListener;
import com.silan.robotpeisongcontrl.utils.SerialPortManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeliverySettingsFragment extends Fragment implements OnDataReceivedListener {

    private static final String TAG = "DeliverySettings";

    // 仓门控制器映射
    private Map<Integer, DoorController> doorControllers = new HashMap<>();
    private TextView tvStatus;

    // 动态存储仓门相关视图
    private View[] doorIndicators;
    private Button[] btnDoorOpens;
    private Button[] btnDoorCloses;
    private Button[] btnDoorPauses;

    private int doorCount;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SerialPortManager serialPortManager;
    // 记录当前轮询的寄存器地址，用于匹配响应
    private int currentPollingRegister = -1;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_delivery_settings, container, false);
        // 获取设置的仓门数量
        doorCount = WarehouseDoorSettingsFragment.getDoorCount(requireContext());
        Log.d(TAG, "获取到仓门数量: " + doorCount);

        // 初始化串口管理器
        serialPortManager = SerialPortManager.getInstance();
        serialPortManager.setOnDataReceivedListener(this);
        if (!serialPortManager.openSerialPort()) {
            Toast.makeText(getContext(), "串口打开失败", Toast.LENGTH_SHORT).show();
        }

        // 初始化仓门控制器
        initDoorControllers();

        // 初始化视图容器 - 支持1-9号仓门
        doorIndicators = new View[10]; // 1-based index (1-9)
        btnDoorOpens = new Button[10];
        btnDoorCloses = new Button[10];
        btnDoorPauses = new Button[10];

        // 初始化视图
        initViews(view);
        // 设置按钮点击监听器
        setupButtonListeners();

        return view;
    }

    /**
     * 初始化仓门控制器
     * 根据仓门数量配置不同的仓门组合
     * 固定包含5号和6号仓门，其余根据数量动态配置
     */
    private void initDoorControllers() {
        doorControllers.clear();

        // 始终添加固定的5号和6号仓门
        doorControllers.put(5, DoorControllerFactory.createDoorController(requireContext(), 5));
        doorControllers.put(6, DoorControllerFactory.createDoorController(requireContext(), 6));

        // 根据总仓门数量添加剩余的仓门（减去固定的2个）
        int dynamicDoorCount = doorCount - 2;

        switch (dynamicDoorCount) {
            case 1:
                // 总3个仓门: 5,6 + 9(大仓门整体)
                doorControllers.put(9, DoorControllerFactory.createDoorController(requireContext(), 9));
                Log.d(TAG, "初始化3个仓门: 5,6,9");
                break;
            case 2:
                // 总4个仓门: 5,6 + 7,8(大仓门分成两个)
                doorControllers.put(7, DoorControllerFactory.createDoorController(requireContext(), 7));
                doorControllers.put(8, DoorControllerFactory.createDoorController(requireContext(), 8));
                Log.d(TAG, "初始化4个仓门: 5,6,7,8");
                break;
            case 4:
                // 总6个仓门: 5,6 + 1-4(大仓门分成四个)
                doorControllers.put(1, DoorControllerFactory.createDoorController(requireContext(), 1));
                doorControllers.put(2, DoorControllerFactory.createDoorController(requireContext(), 2));
                doorControllers.put(3, DoorControllerFactory.createDoorController(requireContext(), 3));
                doorControllers.put(4, DoorControllerFactory.createDoorController(requireContext(), 4));
                Log.d(TAG, "初始化6个仓门: 1,2,3,4,5,6");
                break;
            default:
                Log.e(TAG, "不支持的动态仓门数量: " + dynamicDoorCount + ", 总仓门数: " + doorCount);
                Toast.makeText(getContext(), "不支持的仓门配置", Toast.LENGTH_SHORT).show();
        }

        // 启动状态轮询
        startStatePolling();
    }

    /**
     * 启动状态轮询
     * 依次轮询所有仓门的状态寄存器
     */
    private void startStatePolling() {
        // 清除之前的回调
        handler.removeCallbacksAndMessages(null);

        // 获取所有需要轮询的仓门ID列表
        List<Integer> doorIds = new ArrayList<>(doorControllers.keySet());

        // 轮询任务
        Runnable pollRunnable = new Runnable() {
            private int currentIndex = 0;

            @Override
            public void run() {
                if (doorIds.isEmpty()) {
                    // 没有仓门需要轮询，延迟后重试
                    handler.postDelayed(this, 1000);
                    return;
                }

                // 循环获取下一个仓门ID
                int doorId = doorIds.get(currentIndex % doorIds.size());
                currentIndex++;

                // 获取对应的状态寄存器地址
                int stateReg = getStateRegisterForDoor(doorId);
                if (stateReg != -1) {
                    currentPollingRegister = stateReg;
                    serialPortManager.sendModbusReadCommand(0x01, stateReg, 1);
                    Log.d(TAG, "轮询仓门" + doorId + "状态, 寄存器地址: 0x" + Integer.toHexString(stateReg));
                }

                // 每个仓门轮询间隔500ms，所有仓门轮询一遍后等待一段时间
                handler.postDelayed(this, 500);
            }
        };

        // 立即开始轮询
        handler.post(pollRunnable);
    }

    /**
     * 获取仓门对应的状态寄存器地址
     */
    private int getStateRegisterForDoor(int doorId) {
        switch (doorId) {
            case 1: return 0x44;
            case 2: return 0x45;
            case 3: return 0x46;
            case 4: return 0x47;
            case 5: return 0x42;
            case 6: return 0x43;
            case 7: return 0x40;
            case 8: return 0x41;
            case 9: return 0x48;
            default: return -1;
        }
    }

    private void initViews(View view) {
        tvStatus = view.findViewById(R.id.tv_status);
        LinearLayout doorsContainer = view.findViewById(R.id.doors_container);

        // 清空容器
        doorsContainer.removeAllViews();

        // 根据仓门控制器中的实际仓门ID动态添加视图
        LayoutInflater inflater = LayoutInflater.from(getContext());
        // 按仓门ID排序显示
        List<Integer> sortedDoorIds = new ArrayList<>(doorControllers.keySet());
        sortedDoorIds.sort(Integer::compareTo);

        for (int doorId : sortedDoorIds) {
            // 加载单个仓门的布局
            View doorView = inflater.inflate(R.layout.item_door_control, doorsContainer, false);

            // 设置标题
            TextView tvDoorTitle = doorView.findViewById(R.id.tv_door_title);
            tvDoorTitle.setText("仓门" + doorId);

            // 获取并存储视图引用
            doorIndicators[doorId] = doorView.findViewById(R.id.door_indicator);
            btnDoorOpens[doorId] = doorView.findViewById(R.id.btn_door_open);
            btnDoorCloses[doorId] = doorView.findViewById(R.id.btn_door_close);
            btnDoorPauses[doorId] = doorView.findViewById(R.id.btn_door_pause);

            // 初始状态：关闭状态
            updateDoorIndicator(doorId, DoorController.DoorState.CLOSED);
            updateDoorButtonStates(doorId);

            // 添加到容器
            doorsContainer.addView(doorView);
        }
    }

    private void setupButtonListeners() {
        for (Map.Entry<Integer, DoorController> entry : doorControllers.entrySet()) {
            final int doorId = entry.getKey();

            btnDoorOpens[doorId].setOnClickListener(v -> {
                DoorController controller = doorControllers.get(doorId);
                if (controller != null) {
                    // 无论当前状态如何，重新发送开门信号
                    controller.open();
                    updateDoorButtonStates(doorId);
                    Toast.makeText(getContext(), "发送仓门" + doorId + "开门指令", Toast.LENGTH_SHORT).show();
                }
            });

            btnDoorCloses[doorId].setOnClickListener(v -> {
                DoorController controller = doorControllers.get(doorId);
                if (controller != null) {
                    // 无论当前状态如何，重新发送关门信号
                    controller.close();
                    updateDoorButtonStates(doorId);
                    Toast.makeText(getContext(), "发送仓门" + doorId + "关门指令", Toast.LENGTH_SHORT).show();
                }
            });

            btnDoorPauses[doorId].setOnClickListener(v -> {
                DoorController controller = doorControllers.get(doorId);
                if (controller != null) {
                    controller.pause();
                    Toast.makeText(getContext(), "仓门" + doorId + "已暂停", Toast.LENGTH_SHORT).show();
                    updateDoorButtonStates(doorId);
                }
            });
        }
    }

    private void updateDoorIndicator(int doorId, DoorController.DoorState state) {
        View indicator = doorIndicators[doorId];
        if (indicator != null) {
            switch (state) {
                case OPENING:
                    indicator.setBackgroundResource(R.drawable.door_indicator_opening);
                    playDoorAnimation(doorId, true);
                    break;
                case OPENED:
                    indicator.setBackgroundResource(R.drawable.door_indicator_open);
                    break;
                case CLOSING:
                    indicator.setBackgroundResource(R.drawable.door_indicator_closing);
                    playDoorAnimation(doorId, false);
                    break;
                case CLOSED:
                    indicator.setBackgroundResource(R.drawable.door_indicator_closed);
                    break;
                case PAUSED:
                    indicator.setBackgroundResource(R.drawable.door_indicator_paused);
                    break;
            }
        }
    }

    private void updateDoorButtonStates(int doorId) {
        DoorController controller = doorControllers.get(doorId);
        if (controller == null) return;

        DoorController.DoorState state = controller.getCurrentState();

        switch (state) {
            case CLOSED:
            case PAUSED:
                btnDoorOpens[doorId].setEnabled(true);
                btnDoorOpens[doorId].setAlpha(1.0f);
                btnDoorCloses[doorId].setEnabled(false);
                btnDoorCloses[doorId].setAlpha(0.5f);
                btnDoorPauses[doorId].setEnabled(false);
                btnDoorPauses[doorId].setAlpha(0.5f);
                break;
            case OPENED:
                btnDoorOpens[doorId].setEnabled(false);
                btnDoorOpens[doorId].setAlpha(0.5f);
                btnDoorCloses[doorId].setEnabled(true);
                btnDoorCloses[doorId].setAlpha(1.0f);
                btnDoorPauses[doorId].setEnabled(false);
                btnDoorPauses[doorId].setAlpha(0.5f);
                break;
            case OPENING:
            case CLOSING:
                btnDoorOpens[doorId].setEnabled(false);
                btnDoorOpens[doorId].setAlpha(0.5f);
                btnDoorCloses[doorId].setEnabled(false);
                btnDoorCloses[doorId].setAlpha(0.5f);
                btnDoorPauses[doorId].setEnabled(true);
                btnDoorPauses[doorId].setAlpha(1.0f);
                break;
        }

        updateStatusText();
    }

    private void playDoorAnimation(int doorId, boolean isOpening) {
        View indicator = doorIndicators[doorId];
        if (indicator != null) {
            Animation animation = AnimationUtils.loadAnimation(getContext(),
                    isOpening ? R.anim.door_open : R.anim.door_close);
            indicator.startAnimation(animation);
        }
    }

    private void updateStatusText() {
        StringBuilder sb = new StringBuilder("仓门状态: ");
        boolean hasOpening = false;
        boolean hasClosing = false;
        boolean hasOpened = false;

        for (Map.Entry<Integer, DoorController> entry : doorControllers.entrySet()) {
            int doorId = entry.getKey();
            DoorController.DoorState state = entry.getValue().getCurrentState();

            switch (state) {
                case OPENING:
                    sb.append("仓门").append(doorId).append("开门中 ");
                    hasOpening = true;
                    break;
                case OPENED:
                    sb.append("仓门").append(doorId).append("已打开 ");
                    hasOpened = true;
                    break;
                case CLOSING:
                    sb.append("仓门").append(doorId).append("关门中 ");
                    hasClosing = true;
                    break;
                case PAUSED:
                    sb.append("仓门").append(doorId).append("已暂停 ");
                    break;
            }
        }

        if (!hasOpening && !hasClosing && !hasOpened) {
            sb.append("全部关闭");
        }

        tvStatus.setText(sb.toString().trim());
    }

    @Override
    public void onDataReceived(byte[] data) {
        // 解析接收到的Modbus数据
        if (data == null || data.length < 5) {
            Log.e(TAG, "接收到无效数据，长度不足: " + (data != null ? data.length : 0));
            return;
        }

        // Modbus响应格式: [设备地址][功能码][数据长度][数据...][CRC]
        int deviceId = data[0] & 0xFF;
        int functionCode = data[1] & 0xFF;
        int dataLength = data[2] & 0xFF;

        // 只处理设备01的响应
        if (deviceId != 0x01) {
            Log.d(TAG, "忽略非目标设备数据，设备ID: " + deviceId);
            return;
        }

        // 处理读寄存器响应 (功能码0x03)
        if (functionCode == 0x03 && dataLength > 0) {
            // 提取数据部分
            byte[] doorData = new byte[dataLength];
            System.arraycopy(data, 3, doorData, 0, dataLength);

            // 使用当前轮询的寄存器地址来确定是哪个仓门
            int doorId = getDoorIdFromRegister(currentPollingRegister);

            if (doorId != -1 && doorControllers.containsKey(doorId)) {
                // 更新仓门状态
                DoorController controller = doorControllers.get(doorId);
                controller.handleStateData(doorData);

                // 在UI线程更新界面
                requireActivity().runOnUiThread(() -> {
                    updateDoorIndicator(doorId, controller.getCurrentState());
                    updateDoorButtonStates(doorId);
                });
            } else {
                Log.d(TAG, "未找到对应的仓门，寄存器地址: 0x" +
                        Integer.toHexString(currentPollingRegister) + ", 仓门ID: " + doorId);
            }
        }
    }

    /**
     * 从寄存器地址获取对应的仓门ID
     */
    private int getDoorIdFromRegister(int registerAddress) {
        switch (registerAddress) {
            case 0x44: return 1;
            case 0x45: return 2;
            case 0x46: return 3;
            case 0x47: return 4;
            case 0x42: return 5;
            case 0x43: return 6;
            case 0x40: return 7;
            case 0x41: return 8;
            case 0x48: return 9;
            default: return -1;
        }
    }

    @Override
    public void onError(String error) {
        requireActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), "串口通信错误: " + error, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        if (serialPortManager != null) {
            serialPortManager.closeSerialPort();
        }
        doorControllers.clear();
    }
}