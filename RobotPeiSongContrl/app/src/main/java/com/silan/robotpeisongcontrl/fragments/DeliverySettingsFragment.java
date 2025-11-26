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
    private List<BasicSettingsFragment.DoorInfo> doorInfos;
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
        doorInfos = BasicSettingsFragment.getEnabledDoors(requireContext());
        Log.d(TAG, "获取到仓门数量: " + doorInfos.size());

        // 初始化串口管理器
        serialPortManager = SerialPortManager.getInstance();
        serialPortManager.setOnDataReceivedListener(this);
        if (!serialPortManager.openSerialPort()) {
            Toast.makeText(getContext(), "串口打开失败", Toast.LENGTH_SHORT).show();
        }

        // 初始化仓门控制器
        initDoorControllers();

        // 初始化视图容器
        doorIndicators = new View[doorInfos.size()];
        btnDoorOpens = new Button[doorInfos.size()];
        btnDoorCloses = new Button[doorInfos.size()];
        btnDoorPauses = new Button[doorInfos.size()];

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

        for (int i = 0; i < doorInfos.size(); i++) {
            BasicSettingsFragment.DoorInfo info = doorInfos.get(i);
            // Key=列表索引i，与getDoorIdFromRegister返回的索引一致
            doorControllers.put(i, DoorControllerFactory.createDoorController(
                    requireContext(),
                    info.getType(),
                    info.getHardwareId()
            ));
            Log.d(TAG, "初始化仓门：索引=" + i + "，类型=" + info.getType() + "，硬件ID=" + info.getHardwareId() + "，寄存器地址=" + Integer.toHexString(getStateRegisterForDoor(info.getType(), info.getHardwareId())));
        }

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
        // 添加一个特殊标识(-1)表示急停状态轮询
        doorIds.add(-1);
        doorIds.add(-2); // 电机1旋转方向(0x18)
        doorIds.add(-3); // 电机1正转延时(0x1D)

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

                if (doorId == -1) {
                    // 轮询急停状态寄存器(0x49)
                    currentPollingRegister = 0x49;
                    serialPortManager.sendModbusReadCommand(0x01, 0x49, 1);
                    Log.d(TAG, "轮询急停状态, 寄存器地址: 0x49");
                }
                // 新增：轮询旋转方向和延时寄存器
                else if (doorId == -2) {
                    currentPollingRegister = 0x18;
                    serialPortManager.sendModbusReadCommand(0x01, 0x18, 1);
                    Log.d(TAG, "轮询电机1旋转方向寄存器: 0x18");
                } else if (doorId == -3) {
                    currentPollingRegister = 0x1D;
                    serialPortManager.sendModbusReadCommand(0x01, 0x1D, 1);
                    Log.d(TAG, "轮询电机1正转延时寄存器: 0x1D");
                }else {
                    BasicSettingsFragment.DoorInfo info = doorInfos.get(doorId);
                    // 获取对应的状态寄存器地址
                    int stateReg = getStateRegisterForDoor(info.getType(), info.getHardwareId());
                    if (stateReg != -1) {
                        currentPollingRegister = stateReg;
                        serialPortManager.sendModbusReadCommand(0x01, stateReg, 1);
                        Log.d(TAG, "轮询仓门" + doorId + "状态, 寄存器地址: 0x" + Integer.toHexString(stateReg));
                    }
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
    private int getStateRegisterForDoor(int type, int hardwareId) {
        switch (type) {
            case 0: // 电机仓门（协议：0x50-0x53，hardwareId=1-4）
                if (hardwareId < 1 || hardwareId > 4) {
                    Log.e(TAG, "电机仓门硬件ID无效（1-4）：" + hardwareId);
                    return -1;
                }
                return 0x50 + (hardwareId - 1); // 1号→0x50，2号→0x51...
            case 1: // 电磁锁仓门（协议：0x58-0x5B，hardwareId=1-4）
                if (hardwareId < 1 || hardwareId > 4) {
                    Log.e(TAG, "电磁锁硬件ID无效（1-4）：" + hardwareId);
                    return -1;
                }
                return 0x58 + (hardwareId - 1); // 1号→0x58，2号→0x59...
            case 2: // 推杆电机（协议：0x57，唯一）
                return 0x57;
            default:
                Log.e(TAG, "未知仓门类型：" + type);
                return -1;
        }
    }

    private void initViews(View view) {
        tvStatus = view.findViewById(R.id.tv_status);
        LinearLayout doorsContainer = view.findViewById(R.id.doors_container);

        // 清空容器
        doorsContainer.removeAllViews();

        // 根据仓门控制器中的实际仓门ID动态添加视图
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < doorInfos.size(); i++) {
            BasicSettingsFragment.DoorInfo info = doorInfos.get(i);
            View doorView = inflater.inflate(R.layout.item_door_control, doorsContainer, false);

            // 设置标题
            TextView tvDoorTitle = doorView.findViewById(R.id.tv_door_title);
            String typeStr = info.getType() == 0 ? "电机" : (info.getType() == 1 ? "电磁锁" : "推杆");
            tvDoorTitle.setText(String.format("行%d-%d号（%s）",
                    info.getRow(), info.getPosition(), typeStr));

            // 存储视图引用
            doorIndicators[i] = doorView.findViewById(R.id.door_indicator);
            btnDoorOpens[i] = doorView.findViewById(R.id.btn_door_open);
            btnDoorCloses[i] = doorView.findViewById(R.id.btn_door_close);
            btnDoorPauses[i] = doorView.findViewById(R.id.btn_door_pause);

            // 初始状态
            updateDoorIndicator(i, DoorController.DoorState.CLOSED);
            updateDoorButtonStates(i);

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
        getActivity().runOnUiThread(() -> {
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
        });
    }

    private void updateDoorButtonStates(int doorId) {
        getActivity().runOnUiThread(() -> {
            DoorController controller = doorControllers.get(doorId);
            if (controller == null) return;

            DoorController.DoorState state = controller.getCurrentState();
            // 暂停按钮仅在开门中或关门中时可点击
            boolean isPauseEnabled = state == DoorController.DoorState.OPENING
                    || state == DoorController.DoorState.CLOSING;

            switch (state) {
                case CLOSED:
                    btnDoorOpens[doorId].setEnabled(true);
                    btnDoorOpens[doorId].setAlpha(1.0f);
                    btnDoorCloses[doorId].setEnabled(false);
                    btnDoorCloses[doorId].setAlpha(0.5f);
                    btnDoorPauses[doorId].setEnabled(isPauseEnabled);
                    btnDoorPauses[doorId].setAlpha(isPauseEnabled ? 1.0f : 0.5f);
                    break;
                case PAUSED:
                    // 根据暂停前的状态决定哪个按钮可点击
                    DoorController.DoorState previousState = controller.getPreviousState();
                    if (previousState == DoorController.DoorState.OPENING) {
                        // 之前在开门，允许继续开门
                        btnDoorOpens[doorId].setEnabled(true);
                        btnDoorOpens[doorId].setAlpha(1.0f);
                        btnDoorCloses[doorId].setEnabled(false);
                        btnDoorCloses[doorId].setAlpha(0.5f);
                    } else if (previousState == DoorController.DoorState.CLOSING) {
                        // 之前在关门，允许继续关门
                        btnDoorOpens[doorId].setEnabled(false);
                        btnDoorOpens[doorId].setAlpha(0.5f);
                        btnDoorCloses[doorId].setEnabled(true);
                        btnDoorCloses[doorId].setAlpha(1.0f);
                    }
                    // 暂停状态下暂停按钮禁用
                    btnDoorPauses[doorId].setEnabled(false);
                    btnDoorPauses[doorId].setAlpha(0.5f);
                    break;
                case OPENED:
                    btnDoorOpens[doorId].setEnabled(false);
                    btnDoorOpens[doorId].setAlpha(0.5f);
                    btnDoorCloses[doorId].setEnabled(true);
                    btnDoorCloses[doorId].setAlpha(1.0f);
                    btnDoorPauses[doorId].setEnabled(isPauseEnabled);
                    btnDoorPauses[doorId].setAlpha(isPauseEnabled ? 1.0f : 0.5f);
                    break;
                case OPENING:
                case CLOSING:
                    btnDoorOpens[doorId].setEnabled(false);
                    btnDoorOpens[doorId].setAlpha(0.5f);
                    btnDoorCloses[doorId].setEnabled(false);
                    btnDoorCloses[doorId].setAlpha(0.5f);
                    btnDoorPauses[doorId].setEnabled(isPauseEnabled);
                    btnDoorPauses[doorId].setAlpha(isPauseEnabled ? 1.0f : 0.5f);
                    break;
                case IDLE:
                    // 空闲状态默认允许开门
                    btnDoorOpens[doorId].setEnabled(true);
                    btnDoorOpens[doorId].setAlpha(1.0f);
                    btnDoorCloses[doorId].setEnabled(false);
                    btnDoorCloses[doorId].setAlpha(0.5f);
                    btnDoorPauses[doorId].setEnabled(false);
                    btnDoorPauses[doorId].setAlpha(0.5f);
                    break;
            }

            updateStatusText();
        });
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
        handler.post(() -> {
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

            // 确保tvStatus不为null
            if (tvStatus != null) {
                tvStatus.setText(sb.toString().trim());
            }
        });
    }

    @Override
    public void onDataReceived(byte[] data) {
        Log.d(TAG, "收到串口响应数据，长度: " + data.length + "，原始数据: " + bytesToHexString(data));
        if (data == null || data.length < 3) { // 最小帧长度：设备地址(1)+功能码(1)+CRC(2) 不，最小有效帧至少3字节（含错误码）
            Log.e(TAG, "无效的Modbus响应数据，长度不足");
            return;
        }

        int functionCode = data[1] & 0xFF; // 功能码在第2个字节（索引1）

        // 根据功能码判断合法长度
        boolean isLengthValid = false;
        switch (functionCode) {
            case 0x03: // 读寄存器响应
                // 0x03响应长度 = 1(地址) + 1(功能码) + 1(数据长度) + 2*n(数据) + 2(CRC)
                // 数据长度字段（第3字节）表示数据的字节数，对于寄存器数据，字节数=2*n
                if (data.length >= 3) {
                    int dataLength = data[2] & 0xFF; // 数据长度字段（第3字节）
                    int expectedLength = 3 + dataLength + 2; // 3=地址+功能码+数据长度，2=CRC
                    isLengthValid = (data.length == expectedLength);
                }
                break;
            case 0x06: // 写寄存器响应（与请求长度相同）
                isLengthValid = (data.length == 8);
                break;
            default:
                Log.d(TAG, "未处理的功能码: 0x" + Integer.toHexString(functionCode));
                return;
        }

        if (!isLengthValid) {
            Log.e(TAG, String.format("响应数据长度异常（功能码0x%02X），实际长度: %d", functionCode, data.length));
            return;
        }

        // 长度校验通过，继续处理数据（匹配轮询的寄存器地址）
        processValidResponse(data, functionCode);
    }

    // 处理校验通过的响应数据
    private void processValidResponse(byte[] data, int functionCode) {
        if (functionCode == 0x03) {
            // 解析0x03响应：通过当前轮询的寄存器地址确定对应的仓门
            int receivedReg = currentPollingRegister;

            // 新增：处理旋转方向和延时寄存器的读取结果
            if (receivedReg == 0x18) {
                byte[] dirData = new byte[2];
                System.arraycopy(data, 3, dirData, 0, 2);
                int dirValue = ((dirData[0] & 0xFF) << 8) | (dirData[1] & 0xFF);
                Log.d(TAG, "电机1旋转方向读取结果: " + dirValue + "（0=Normal，1=Reserval）");
            } else if (receivedReg == 0x1D) {
                byte[] delayData = new byte[2];
                System.arraycopy(data, 3, delayData, 0, 2);
                int delayValue = ((delayData[0] & 0xFF) << 8) | (delayData[1] & 0xFF);
                Log.d(TAG, "电机1正转延时读取结果: " + delayValue + "（0.02ms单位，对应" + (delayValue * 0.02) + "ms）");
            }

            // 检查是否是急停状态寄存器的响应
            if (receivedReg == 0x49) {
                // 提取急停状态数据（2字节）
                if (data.length >= 5) { // 确保有足够数据（地址1 + 功能码1 + 数据长度1 + 数据2 + CRC2）
                    byte[] emergencyData = new byte[2];
                    System.arraycopy(data, 3, emergencyData, 0, 2);
                    int emergencyState = ((emergencyData[0] & 0xFF) << 8) | (emergencyData[1] & 0xFF);

                    // 急停状态：0x0001表示触发
                    if (emergencyState == 0x0001) {
                        Log.d(TAG, "收到急停信号，执行急停操作");
                        handleEmergencyStop();
                    }
                } else {
                    Log.e(TAG, "急停状态响应数据长度不足");
                }
                return;
            }

            // 通过寄存器地址获取对应的仓门ID（使用已有的映射方法）
            int doorId = getDoorIdFromRegister(receivedReg);
            if (doorId == -1) {
                Log.w(TAG, "未找到寄存器地址0x" + Integer.toHexString(receivedReg)+"对应的仓门");
                return;
            }

            // 获取对应的仓门控制器
            DoorController controller = doorControllers.get(doorId);
            if (controller == null) {
                Log.w(TAG, "未找到仓门" + doorId + "的控制器");
                return;
            }

            // 提取状态数据（2字节寄存器值）
            if (data.length >= 5) { // 验证数据长度
                byte[] stateData = new byte[2];
                System.arraycopy(data, 3, stateData, 0, 2);

                // 处理状态数据并更新UI
                handler.post(() -> {
                    controller.handleStateData(stateData);
                    DoorController.DoorState newState = controller.getCurrentState();
                    Log.d(TAG, "仓门" + doorId + "状态更新为: " + newState);

                    // 更新指示器和按钮状态
                    updateDoorIndicator(doorId, newState);
                    updateDoorButtonStates(doorId);
                });
            } else {
                Log.e(TAG, "仓门状态响应数据长度不足，寄存器0x" + Integer.toHexString(receivedReg));
            }

        } else if (functionCode == 0x06) {
            // 0x06响应处理（确认写入成功）
            Log.d(TAG, "0x06指令执行成功，响应数据: " + bytesToHexString(data));
        }
    }

    // 急停处理方法
    private void handleEmergencyStop() {
        // 发送急停指令停止所有电机
        serialPortManager.sendEmergencyStop();

        // 更新所有仓门状态为暂停
        for (Map.Entry<Integer, DoorController> entry : doorControllers.entrySet()) {
            int doorId = entry.getKey();
            DoorController controller = entry.getValue();
            controller.emergencyStop();
            updateDoorIndicator(doorId, DoorController.DoorState.PAUSED);
            updateDoorButtonStates(doorId);
        }

        // 显示急停提示
        requireActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), "已执行急停操作，所有电机已停止", Toast.LENGTH_LONG).show()
        );
    }

    // 从0x03响应反推寄存器地址（根据轮询上下文，也可通过请求记录匹配）
    private int getRegisterFromResponse(byte[] data) {
        // 实际应通过当前轮询的寄存器地址currentPollingRegister匹配，此处简化
        return currentPollingRegister;
    }

    // 辅助方法：根据寄存器地址获取对应的仓门Controller
    private DoorController getControllerByRegAddr(int regAddr) {
        // 映射：寄存器地址 → 仓门ID（根据协议“一键指令部分起始寄存器地址码”）
        Map<Integer, Integer> regAddrToDoorId = new HashMap<>();
        regAddrToDoorId.put(0x44, 1); // 1号仓门
        regAddrToDoorId.put(0x45, 2); // 2号仓门
        regAddrToDoorId.put(0x42, 5); // 5号仓门
        regAddrToDoorId.put(0x43, 6); // 6号仓门
        regAddrToDoorId.put(0x40, 7); // 7号仓门
        regAddrToDoorId.put(0x41, 8); // 8号仓门
        regAddrToDoorId.put(0x48, 9); // 9号仓门
        regAddrToDoorId.put(0x46, 3); // 3号仓门
        regAddrToDoorId.put(0x47, 4); // 4号仓门
        Integer doorId = regAddrToDoorId.get(regAddr);
        if (doorId != null) {
            return doorControllers.get(doorId);
        }
        return null;
    }

    // 辅助方法：字节数组转16进制字符串
    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * 从寄存器地址获取对应的仓门ID
     */
    private int getDoorIdFromRegister(int registerAddress) {
        // 遍历所有已启用的仓门，匹配寄存器地址
        for (int i = 0; i < doorInfos.size(); i++) {
            BasicSettingsFragment.DoorInfo info = doorInfos.get(i);
            int type = info.getType();
            int hardwareId = info.getHardwareId();

            // 计算当前仓门对应的状态寄存器地址（与getStateRegisterForDoor逻辑一致）
            int targetReg = -1;
            switch (type) {
                case 0: // 电机仓门（协议：0x50-0x53）
                    targetReg = 0x50 + (hardwareId - 1);
                    break;
                case 1: // 电磁锁仓门（协议：0x58-0x5B）
                    targetReg = 0x58 + (hardwareId - 1);
                    break;
                case 2: // 推杆电机（协议：0x57）
                    targetReg = 0x57;
                    break;
            }

            // 匹配成功，返回列表索引（控制器的Key）
            if (targetReg == registerAddress) {
                return i;
            }
        }

        // 未匹配到（急停寄存器等）
        Log.w(TAG, "未找到寄存器地址0x" + Integer.toHexString(registerAddress) + "对应的仓门");
        return -1;
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