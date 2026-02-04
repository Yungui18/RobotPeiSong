package com.silan.robotpeisongcontrl;

import android.os.Bundle;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.utils.FileCopyUtils;
import com.silan.robotpeisongcontrl.utils.SoundPlayerManager;
import com.silan.robotpeisongcontrl.utils.SoundSettingsSPUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SoundSettingsActivity extends BaseActivity {

    private static final String VOICE_FILE_PATH = "/storage/D8A5-66CD/muisc";

    private SoundPlayerManager mSoundManager;
    private SeekBar mVolumeSeekBar;
    private TextView mVolumeValueTv;
    private CheckBox mAutoVolumeCb;

    // 所有业务节点的Spinner
    private Spinner mDoorOpenSpinner, mAfterStartSpinner, mCompleteTakeSpinner
            , mTimeOutSpinner, mTaskArriveSpinner, mSpinnerPathOccupied, mSpinnerCurrentPoseOccupied
            , mSpinnerOnDock, mSpinnerOffDock, mSpinnerPassNarrowCorridor, mSpinnerPowerOff, mSpinnerMoveDockFailed
            , mSpinnerSearchDockFailed, mSpinnerBrakeReleased, mSpinnerBumperTriggered;
//    private Spinner mClickStartSpinner;

    // 播放按钮
    private ImageButton mBtnPlayDoorOpen, mBtnPlayAfterStart, mBtnPlayCompleteTake
            , mBtnPlayTimeOut, mBtnPlayTaskArrive, mBtnPlayPathOccupied, mBtnPlayCurrentPoseOccupied
            , mBtnPlayOnDock, mBtnPlayOffDock, mBtnPlayPassNarrowCorridor, mBtnPlayPowerOff, mBtnPlayMoveDockFailed
            , mBtnPlaySearchDockFailed, mBtnPlayBrakeReleased, mBtnPlayBumperTriggered;
//    private ImageButton  mBtnPlayClickStart;

    // 素材列表
    private List<String> mSoundFileNames = new ArrayList<>();
    private List<String> mSoundFilePaths = new ArrayList<>();

    // 当前播放中的按钮标记
    private ImageButton mCurrentPlayingBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_settings);

        mSoundManager = SoundPlayerManager.getInstance(this);
        // 新增：设置播放完成回调，用于恢复播放按钮图标
        mSoundManager.setOnPlayCompleteListener(this::resetPlayButtonState);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 初始化控件
        initViews();
        // 扫描素材文件
        scanVoiceFiles();
        // 初始化Spinner适配器
        initSpinners();
        // 初始化音量调节（包含实时生效逻辑）
        initVolumeControl();
        // 初始化自动音量开关
        initAutoVolume();
        // 初始化播放按钮点击事件
        initPlayButtonListeners();
    }

    private void initViews() {
        // 音量相关控件
        mVolumeSeekBar = findViewById(R.id.seekbar_volume);
        mVolumeValueTv = findViewById(R.id.tv_volume_value);
        mAutoVolumeCb = findViewById(R.id.cb_auto_volume);

        // 所有业务节点的Spinner
        mDoorOpenSpinner = findViewById(R.id.spinner_door_open);
//        mClickStartSpinner = findViewById(R.id.spinner_click_start);
        mAfterStartSpinner = findViewById(R.id.spinner_after_start);
        mCompleteTakeSpinner = findViewById(R.id.spinner_complete_take);
        mTimeOutSpinner = findViewById(R.id.spinner_time_out);
        mTaskArriveSpinner = findViewById(R.id.spinner_task_arrive);
        mSpinnerPathOccupied = findViewById(R.id.spinner_path_occupied);
        mSpinnerCurrentPoseOccupied = findViewById(R.id.spinner_current_pose_occupied);
        mSpinnerOnDock = findViewById(R.id.spinner_on_dock);
        mSpinnerOffDock = findViewById(R.id.spinner_off_dock);
        mSpinnerPassNarrowCorridor = findViewById(R.id.spinner_pass_narrow_corridor);
        mSpinnerPowerOff = findViewById(R.id.spinner_power_off);
        mSpinnerMoveDockFailed = findViewById(R.id.spinner_move_dock_failed);
        mSpinnerSearchDockFailed = findViewById(R.id.spinner_search_dock_failed);
        mSpinnerBrakeReleased = findViewById(R.id.spinner_brake_released);
        mSpinnerBumperTriggered = findViewById(R.id.spinner_bumper_triggered);

        // 所有播放按钮
        mBtnPlayDoorOpen = findViewById(R.id.btn_play_door_open);
//        mBtnPlayClickStart = findViewById(R.id.btn_play_click_start);
        mBtnPlayAfterStart = findViewById(R.id.btn_play_after_start);
        mBtnPlayCompleteTake = findViewById(R.id.btn_play_complete_take);
        mBtnPlayTimeOut = findViewById(R.id.btn_play_time_out);
        mBtnPlayTaskArrive = findViewById(R.id.btn_play_task_arrive);
        mBtnPlayPathOccupied = findViewById(R.id.btn_play_path_occupied);
        mBtnPlayCurrentPoseOccupied = findViewById(R.id.btn_play_current_pose_occupied);
        mBtnPlayOnDock = findViewById(R.id.btn_play_on_dock);
        mBtnPlayOffDock = findViewById(R.id.btn_play_off_dock);
        mBtnPlayPassNarrowCorridor = findViewById(R.id.btn_play_pass_narrow_corridor);
        mBtnPlayPowerOff = findViewById(R.id.btn_play_power_off);
        mBtnPlayMoveDockFailed = findViewById(R.id.btn_play_move_dock_failed);
        mBtnPlaySearchDockFailed = findViewById(R.id.btn_play_search_dock_failed);
        mBtnPlayBrakeReleased = findViewById(R.id.btn_play_brake_released);
        mBtnPlayBumperTriggered = findViewById(R.id.btn_play_bumper_triggered);

        // 保存按钮
        Button saveBtn = findViewById(R.id.btn_save);
        saveBtn.setOnClickListener(v -> saveSettings());

        // 禁用所有播放按钮（扫描到素材后再启用）
        setAllPlayButtonsEnabled(false);
    }

    /**
     * 初始化所有播放按钮的点击事件
     */
    private void initPlayButtonListeners() {
        // 仓门开启播放按钮
        mBtnPlayDoorOpen.setOnClickListener(v -> playSelectedSound(mDoorOpenSpinner, mBtnPlayDoorOpen));
        // 点击出发播放按钮
//        mBtnPlayClickStart.setOnClickListener(v -> playSelectedSound(mClickStartSpinner, mBtnPlayClickStart));
        // 出发后播放按钮
        mBtnPlayAfterStart.setOnClickListener(v -> playSelectedSound(mAfterStartSpinner, mBtnPlayAfterStart));
        // 取物完成播放按钮
        mBtnPlayCompleteTake.setOnClickListener(v -> playSelectedSound(mCompleteTakeSpinner, mBtnPlayCompleteTake));
        // 倒计时超时播放按钮
        mBtnPlayTimeOut.setOnClickListener(v -> playSelectedSound(mTimeOutSpinner, mBtnPlayTimeOut));
        // 任务到达播放按钮
        mBtnPlayTaskArrive.setOnClickListener(v -> playSelectedSound(mTaskArriveSpinner, mBtnPlayTaskArrive));
        // 行进路径被阻挡播放按钮
        mBtnPlayPathOccupied.setOnClickListener(v -> playSelectedSound(mSpinnerPathOccupied, mBtnPlayPathOccupied));
        // 当前位姿被占据播放按钮
        mBtnPlayCurrentPoseOccupied.setOnClickListener(v -> playSelectedSound(mSpinnerCurrentPoseOccupied, mBtnPlayCurrentPoseOccupied));
        // 机器人上桩播放按钮
        mBtnPlayOnDock.setOnClickListener(v -> playSelectedSound(mSpinnerOnDock, mBtnPlayOnDock));
        // 机器人下桩播放按钮
        mBtnPlayOffDock.setOnClickListener(v -> playSelectedSound(mSpinnerOffDock, mBtnPlayOffDock));
        // 通过窄走廊播放按钮
        mBtnPlayPassNarrowCorridor.setOnClickListener(v -> playSelectedSound(mSpinnerPassNarrowCorridor, mBtnPlayPassNarrowCorridor));
        // 正在关机播放按钮
        mBtnPlayPowerOff.setOnClickListener(v -> playSelectedSound(mSpinnerPowerOff, mBtnPlayPowerOff));
        // 找桩失败播放按钮
        mBtnPlayMoveDockFailed.setOnClickListener(v -> playSelectedSound(mSpinnerMoveDockFailed, mBtnPlayMoveDockFailed));
        // 刹车释放播放按钮
        mBtnPlaySearchDockFailed.setOnClickListener(v -> playSelectedSound(mSpinnerSearchDockFailed, mBtnPlaySearchDockFailed));
        // 碰撞传感器触发播放按钮
        mBtnPlayBrakeReleased.setOnClickListener(v -> playSelectedSound(mSpinnerBrakeReleased, mBtnPlayBrakeReleased));
        // 原有保存按钮保持不变播放按钮
        mBtnPlayBumperTriggered.setOnClickListener(v -> playSelectedSound(mSpinnerBumperTriggered, mBtnPlayBumperTriggered));
    }

    /**
     * 播放选中的语音素材
     * @param spinner 对应的素材选择Spinner
     * @param playBtn 对应的播放按钮
     */
    private void playSelectedSound(Spinner spinner, ImageButton playBtn) {
        // 素材列表为空，直接返回
        if (mSoundFilePaths.isEmpty()) {
            Toast.makeText(this, "暂无语音素材可播放", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedPos = spinner.getSelectedItemPosition();

        // 索引有效性校验（0是"请选择"，大于列表长度也无效）
        if (selectedPos < 0 || selectedPos >= mSoundFilePaths.size()) {
            Toast.makeText(this, "未选择有效的语音素材", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否选择了"请选择"（第一个选项）
        if (selectedPos == 0) {
            Toast.makeText(this, "请先选择语音素材", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取路径前再次校验列表长度
        String soundPath = "";
        try {
            soundPath = mSoundFilePaths.get(selectedPos);
        } catch (IndexOutOfBoundsException e) {
            Toast.makeText(this, "素材索引异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        // 路径为空校验
        if (soundPath.isEmpty()) {
            Toast.makeText(this, "素材路径为空", Toast.LENGTH_SHORT).show();
            return;
        }

        //  如果当前有正在播放的按钮，先恢复其图标
        if (mCurrentPlayingBtn != null && mCurrentPlayingBtn != playBtn) {
            mCurrentPlayingBtn.setImageResource(android.R.drawable.ic_media_play);
        }

        //  如果点击的是当前播放中的按钮，停止播放并恢复图标
        if (mCurrentPlayingBtn == playBtn && mSoundManager.isPlaying()) {
            mSoundManager.stopSound();
            resetPlayButtonState();
            return;
        }

        //  标记当前播放按钮，切换为暂停图标
        mCurrentPlayingBtn = playBtn;
        playBtn.setImageResource(android.R.drawable.ic_media_pause);

        // 播放选中的素材（添加try-catch防护）
        try {
            mSoundManager.playSoundByPath(soundPath);
        } catch (Exception e) {
            Toast.makeText(this, "播放失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            // 播放失败时恢复图标
            resetPlayButtonState();
        }
    }

    /**
     * 重置播放按钮状态
     */
    private void resetPlayButtonState() {
        if (mCurrentPlayingBtn != null) {
            runOnUiThread(() -> {
                mCurrentPlayingBtn.setImageResource(android.R.drawable.ic_media_play);
                mCurrentPlayingBtn = null;
            });
        }
    }

    /**
     * 初始化音量调节
     */
    private void initVolumeControl() {
        // 设置SeekBar范围（0-100）
        mVolumeSeekBar.setMax(100);
        // 回显当前音量
        int currentVolume = SoundSettingsSPUtils.getManualVolume(this);
        mVolumeSeekBar.setProgress(currentVolume);
        mVolumeValueTv.setText(currentVolume + "%");

        // 音量调节监听
        mVolumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mVolumeValueTv.setText(progress + "%");
                if (fromUser) {
                    mSoundManager.setPreviewVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 拖动结束后，再次确认设置
                mSoundManager.setPreviewVolume(seekBar.getProgress());
            }
        });
    }

    /**
     * 扫描指定路径下的MP3素材
     */
    private void scanVoiceFiles() {
        // 先清空列表，避免重复数据
        mSoundFileNames.clear();
        mSoundFilePaths.clear();

        // 扫描本地已保存的素材
        File[] localFiles = mSoundManager.scanMp3Files(FileCopyUtils.APP_VOICE_DIR);
        // 扫描U盘素材
        File[] usbFiles = mSoundManager.scanMp3Files(VOICE_FILE_PATH);

        // 合并素材
        Map<String, String> soundFileMap = new HashMap<>();

        // 先添加本地素材
        for (File file : localFiles) {
            soundFileMap.put(file.getName(), file.getAbsolutePath());
        }
        // 再添加U盘素材
        for (File file : usbFiles) {
            if (!soundFileMap.containsKey(file.getName())) {
                soundFileMap.put(file.getName(), file.getAbsolutePath());
            }
        }

        // 校验是否有素材
        if (soundFileMap.isEmpty()) {
            Toast.makeText(this, "未扫描到任何语音素材（本地/U盘均无），请检查U盘路径：" + VOICE_FILE_PATH, Toast.LENGTH_SHORT).show();
            // 即使无素材，也添加"请选择"选项，避免列表为空
            mSoundFileNames.add("请选择");
            mSoundFilePaths.add("");
            setAllPlayButtonsEnabled(false);
            return;
        }

        //  填充素材列表
        mSoundFileNames.add("请选择");
        mSoundFilePaths.add("");
        // 按文件名排序
        List<String> sortedFileNames = new ArrayList<>(soundFileMap.keySet());
        Collections.sort(sortedFileNames);
        for (String fileName : sortedFileNames) {
            mSoundFileNames.add(fileName);
            mSoundFilePaths.add(soundFileMap.get(fileName));
        }

        // 扫描到素材后启用播放按钮
        setAllPlayButtonsEnabled(true);
    }

    /**
     * 初始化所有Spinner（素材选择下拉框）
     */
    private void initSpinners() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, mSoundFileNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 通用设置Spinner的方法（减少重复代码）
        setSpinnerAdapterAndSelection(mDoorOpenSpinner, adapter, SoundPlayerManager.KEY_DOOR_OPEN);
//        setSpinnerAdapterAndSelection(mClickStartSpinner, adapter, SoundPlayerManager.KEY_CLICK_START);
        setSpinnerAdapterAndSelection(mAfterStartSpinner, adapter, SoundPlayerManager.KEY_AFTER_START);
        setSpinnerAdapterAndSelection(mCompleteTakeSpinner, adapter, SoundPlayerManager.KEY_COMPLETE_TAKE);
        setSpinnerAdapterAndSelection(mTimeOutSpinner, adapter, SoundPlayerManager.KEY_TIME_OUT);
        setSpinnerAdapterAndSelection(mTaskArriveSpinner, adapter, SoundPlayerManager.KEY_TASK_ARRIVE);
        setSpinnerAdapterAndSelection(mSpinnerPathOccupied, adapter, SoundPlayerManager.KEY_PATH_OCCUPIED);
        setSpinnerAdapterAndSelection(mSpinnerCurrentPoseOccupied, adapter, SoundPlayerManager.KEY_CURRENT_POSE_OCCUPIED);
        setSpinnerAdapterAndSelection(mSpinnerOnDock, adapter, SoundPlayerManager.KEY_ON_DOCK);
        setSpinnerAdapterAndSelection(mSpinnerOffDock, adapter, SoundPlayerManager.KEY_OFF_DOCK);
        setSpinnerAdapterAndSelection(mSpinnerPassNarrowCorridor, adapter, SoundPlayerManager.KEY_PASS_NARROW_CORRIDOR);
        setSpinnerAdapterAndSelection(mSpinnerPowerOff, adapter, SoundPlayerManager.KEY_POWER_OFF);
        setSpinnerAdapterAndSelection(mSpinnerMoveDockFailed, adapter, SoundPlayerManager.KEY_MOVE_DOCK_FAILED);
        setSpinnerAdapterAndSelection(mSpinnerSearchDockFailed, adapter, SoundPlayerManager.KEY_SEARCH_DOCK_FAILED);
        setSpinnerAdapterAndSelection(mSpinnerBrakeReleased, adapter, SoundPlayerManager.KEY_BRAKE_RELEASED);
        setSpinnerAdapterAndSelection(mSpinnerBumperTriggered, adapter, SoundPlayerManager.KEY_BUMPER_TRIGGERED);
    }

    /**
     * 通用方法：设置Spinner适配器和选中项
     */
    private void setSpinnerAdapterAndSelection(Spinner spinner, ArrayAdapter<String> adapter, String key) {
        spinner.setAdapter(adapter);
        String soundPath = SoundSettingsSPUtils.getBindSoundPath(this, key);
        // indexOf返回-1时，默认选中0（请选择）
        int index = mSoundFilePaths.indexOf(soundPath);
        if (index > 0 && index < mSoundFilePaths.size()) {
            spinner.setSelection(index);
        } else {
            spinner.setSelection(0); // 默认选中"请选择"
        }
    }

    /**
     * 初始化自动音量开关
     */
    private void initAutoVolume() {
        boolean isAutoEnable = SoundSettingsSPUtils.isAutoVolumeEnable(this);
        mAutoVolumeCb.setChecked(isAutoEnable);
        mAutoVolumeCb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 实时保存自动音量开关状态
            SoundSettingsSPUtils.saveAutoVolumeEnable(SoundSettingsActivity.this, isChecked);
        });
    }

    /**
     * 保存所有设置
     */
    private void saveSettings() {
        // 无素材时提示
        if (mSoundFilePaths.size() <= 1) { // 只有"请选择"选项
            Toast.makeText(this, "暂无语音素材可保存", Toast.LENGTH_SHORT).show();
            return;
        }

        //  保存手动音量
        int volume = mVolumeSeekBar.getProgress();
        SoundSettingsSPUtils.saveManualVolume(this, volume);

        //  保存所有业务节点的素材绑定关系（先复制到项目目录，再保存新路径）
        saveSoundBinding(mDoorOpenSpinner, SoundPlayerManager.KEY_DOOR_OPEN);
//        saveSoundBinding(mClickStartSpinner, SoundPlayerManager.KEY_CLICK_START);
        saveSoundBinding(mAfterStartSpinner, SoundPlayerManager.KEY_AFTER_START);
        saveSoundBinding(mCompleteTakeSpinner, SoundPlayerManager.KEY_COMPLETE_TAKE);
        saveSoundBinding(mTimeOutSpinner, SoundPlayerManager.KEY_TIME_OUT);
        saveSoundBinding(mTaskArriveSpinner, SoundPlayerManager.KEY_TASK_ARRIVE);
        saveSoundBinding(mSpinnerPathOccupied, SoundPlayerManager.KEY_PATH_OCCUPIED);
        saveSoundBinding(mSpinnerCurrentPoseOccupied, SoundPlayerManager.KEY_CURRENT_POSE_OCCUPIED);
        saveSoundBinding(mSpinnerOnDock, SoundPlayerManager.KEY_ON_DOCK);
        saveSoundBinding(mSpinnerOffDock, SoundPlayerManager.KEY_OFF_DOCK);
        saveSoundBinding(mSpinnerPassNarrowCorridor, SoundPlayerManager.KEY_PASS_NARROW_CORRIDOR);
        saveSoundBinding(mSpinnerPowerOff, SoundPlayerManager.KEY_POWER_OFF);
        saveSoundBinding(mSpinnerMoveDockFailed, SoundPlayerManager.KEY_MOVE_DOCK_FAILED);
        saveSoundBinding(mSpinnerSearchDockFailed, SoundPlayerManager.KEY_SEARCH_DOCK_FAILED);
        saveSoundBinding(mSpinnerBrakeReleased, SoundPlayerManager.KEY_BRAKE_RELEASED);
        saveSoundBinding(mSpinnerBumperTriggered, SoundPlayerManager.KEY_BUMPER_TRIGGERED);

        Toast.makeText(this, "设置保存成功（素材已同步到本地）", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * 通用方法：保存单个业务节点的素材绑定关系
     */
    private void saveSoundBinding(Spinner spinner, String key) {
        int selectedPos = spinner.getSelectedItemPosition();
        // 索引无效时保存空路径
        if (selectedPos <= 0 || selectedPos >= mSoundFilePaths.size()) {
            SoundSettingsSPUtils.saveBindSoundPath(this, key, "");
            return;
        }

        String sourcePath = mSoundFilePaths.get(selectedPos);
        String targetPath = FileCopyUtils.copyFileToAppDir(sourcePath);
        SoundSettingsSPUtils.saveBindSoundPath(this, key, targetPath);
    }

    /**
     * 批量设置播放按钮启用/禁用状态
     */
    private void setAllPlayButtonsEnabled(boolean enabled) {
        mBtnPlayDoorOpen.setEnabled(enabled);
//        mBtnPlayClickStart.setEnabled(enabled);
        mBtnPlayAfterStart.setEnabled(enabled);
        mBtnPlayCompleteTake.setEnabled(enabled);
        mBtnPlayTimeOut.setEnabled(enabled);
        mBtnPlayTaskArrive.setEnabled(enabled);
        mBtnPlayPathOccupied.setEnabled(enabled);
        mBtnPlayCurrentPoseOccupied.setEnabled(enabled);
        mBtnPlayOnDock.setEnabled(enabled);
        mBtnPlayOffDock.setEnabled(enabled);
        mBtnPlayPassNarrowCorridor.setEnabled(enabled);
        mBtnPlayPowerOff.setEnabled(enabled);
        mBtnPlayMoveDockFailed.setEnabled(enabled);
        mBtnPlaySearchDockFailed.setEnabled(enabled);
        mBtnPlayBrakeReleased.setEnabled(enabled);
        mBtnPlayBumperTriggered.setEnabled(enabled);

        // 禁用时改变按钮透明度，提示用户不可点击
        float alpha = enabled ? 1.0f : 0.5f;
        mBtnPlayDoorOpen.setAlpha(alpha);
//        mBtnPlayClickStart.setAlpha(alpha);
        mBtnPlayAfterStart.setAlpha(alpha);
        mBtnPlayCompleteTake.setAlpha(alpha);
        mBtnPlayTimeOut.setAlpha(alpha);
        mBtnPlayTaskArrive.setAlpha(alpha);
        mBtnPlayPathOccupied.setAlpha(alpha);
        mBtnPlayCurrentPoseOccupied.setAlpha(alpha);
        mBtnPlayOnDock.setAlpha(alpha);
        mBtnPlayOffDock.setAlpha(alpha);
        mBtnPlayPassNarrowCorridor.setAlpha(alpha);
        mBtnPlayPowerOff.setAlpha(alpha);
        mBtnPlayMoveDockFailed.setAlpha(alpha);
        mBtnPlaySearchDockFailed.setAlpha(alpha);
        mBtnPlayBrakeReleased.setAlpha(alpha);
        mBtnPlayBumperTriggered.setAlpha(alpha);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止播放，释放资源
        mSoundManager.stopSound();
        // 移除回调，避免内存泄漏
        mSoundManager.setOnPlayCompleteListener(null);
    }
}