package com.silan.robotpeisongcontrl.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileCopyUtils {
    private static final String TAG = "FileCopyUtils";
    // 项目内部存储语音素材的目录
    public static final String APP_VOICE_DIR = Environment.getDataDirectory() + "/data/com.silan.robotpeisongcontrl/files/voice/";

    /**
     * 复制文件到项目指定目录（不存在则创建目录，存在则覆盖；源/目标为同一文件时直接返回路径）
     * @param sourcePath 源文件路径（U盘/本地应用目录的MP3路径）
     * @return 复制后的目标文件路径，失败返回空字符串
     */
    public static String copyFileToAppDir(String sourcePath) {
        // 空路径直接返回
        if (sourcePath == null || sourcePath.isEmpty()) {
            return "";
        }

        File sourceFile = new File(sourcePath);
        // 源文件不存在/非文件，直接返回
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            Log.e(TAG, "源文件不存在或非文件：" + sourcePath);
            return "";
        }

        // 1. 创建项目语音目录（不存在则创建）
        File appVoiceDir = new File(APP_VOICE_DIR);
        if (!appVoiceDir.exists()) {
            boolean isCreated = appVoiceDir.mkdirs();
            if (!isCreated) {
                Log.e(TAG, "创建项目语音目录失败：" + APP_VOICE_DIR);
                return "";
            }
        }

        // 2. 构建目标文件路径（使用源文件名称）
        String fileName = sourceFile.getName();
        File targetFile = new File(appVoiceDir, fileName);

        // ============= 核心修复：增加源/目标文件同一性判断 =============
        // 若源文件就是应用目录内的文件（源=目标），直接返回目标路径，不执行复制
        if (sourceFile.getAbsolutePath().equals(targetFile.getAbsolutePath())) {
            Log.d(TAG, "源文件与目标文件为同一文件，无需复制：" + sourcePath);
            return targetFile.getAbsolutePath();
        }
        // ==============================================================

        // 3. 执行文件复制（覆盖已存在的不同文件）
        try (InputStream in = new FileInputStream(sourceFile);
             // 优化：明确指定覆盖模式（与原有逻辑一致，仅增加注释）
             OutputStream out = new FileOutputStream(targetFile, false)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            Log.d(TAG, "文件复制成功：" + sourcePath + " -> " + targetFile.getAbsolutePath());
            return targetFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "文件复制失败：" + sourcePath + " -> " + targetFile.getAbsolutePath(), e);
            return "";
        }
    }
}
