package com.connect_screen.mirror.job;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.topjohnwu.superuser.Shell;

/**
 * Root 模式下自动启动 Shizuku 的工具类。
 * 当用户开启了 Root 模式且设备已 Root 时，
 * 在 Shizuku 未启动的情况下自动通过 su 启动 Shizuku。
 */
public class RootShizukuStarter {

    private static final String TAG = "RootShizukuStarter";
    private static boolean hasAttempted = false;

    /**
     * 检查是否应该使用 Root 模式启动 Shizuku
     */
    public static boolean shouldUseRootMode() {
        return Pref.getUseRootMode();
    }

    /**
     * 检查设备是否有 Root 权限
     */
    public static boolean isDeviceRooted() {
        try {
            return Shell.getShell().isRoot();
        } catch (Exception e) {
            Log.w(TAG, "检查 Root 失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 通过 Root 启动 Shizuku
     */
    public static void startShizukuViaRoot() {
        if (hasAttempted) {
            return;
        }
        if (!shouldUseRootMode()) {
            return;
        }
        if (ShizukuUtils.hasShizukuStarted()) {
            State.log("Shizuku 已在运行（Root 模式）");
            return;
        }
        hasAttempted = true;
        State.log("Root 模式：尝试通过 Root 启动 Shizuku...");

        new Thread(() -> {
            try {
                // 先尝试通过 rish 或 shizuku_starter 启动
                Shell.Result result = Shell.cmd(
                        "sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh || " +
                        "sh /data/local/tmp/shizuku_starter || " +
                        "/data/adb/modules/*/shizuku_starter 2>/dev/null || " +
                        "echo 'no_starter_found'"
                ).exec();

                String output = String.join("\n", result.getOut());
                boolean success = result.isSuccess() && !output.contains("no_starter_found");

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (success) {
                        State.log("Root 模式：Shizuku 启动成功，等待连接...");
                        // 延迟一段时间等 Shizuku 完全启动后再尝试连接
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (ShizukuUtils.hasShizukuStarted()) {
                                State.log("Root 模式：Shizuku 已就绪");
                                State.resumeJob();
                            } else {
                                State.log("Root 模式：Shizuku 启动可能需要更多时间");
                            }
                        }, 3000);
                    } else {
                        State.log("Root 模式：Shizuku 未能通过 Root 启动，请手动安装并启动 Shizuku");
                        Log.w(TAG, "Root start output: " + output);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Root 启动 Shizuku 失败", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    State.log("Root 模式：启动失败 - " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 通过 Root 直接执行 shell 命令（作为 Shizuku 的后备方案）
     */
    public static String executeCommandViaRoot(String command) {
        try {
            Shell.Result result = Shell.cmd(command).exec();
            return String.join("\n", result.getOut());
        } catch (Exception e) {
            Log.e(TAG, "Root 执行命令失败: " + command, e);
            return "";
        }
    }

    /**
     * 通过 Root 控制屏幕电源
     */
    public static boolean setScreenPowerViaRoot(boolean on) {
        try {
            String cmd = on
                    ? "input keyevent KEYCODE_WAKEUP"
                    : "input keyevent KEYCODE_SLEEP";
            Shell.Result result = Shell.cmd(cmd).exec();
            return result.isSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Root 控制屏幕失败", e);
            return false;
        }
    }

    /**
     * 重置尝试状态（应用重启时调用）
     */
    public static void reset() {
        hasAttempted = false;
    }
}
