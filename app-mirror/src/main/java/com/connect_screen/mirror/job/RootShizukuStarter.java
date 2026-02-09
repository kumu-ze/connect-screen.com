package com.connect_screen.mirror.job;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.topjohnwu.superuser.Shell;

import rikka.shizuku.Shizuku;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Root 模式下自动启动 Shizuku 的工具类。
 *
 * 核心逻辑：
 * 1. Root 权限本身比 Shizuku 更强，但 Android 系统服务（IActivityManager, IInputManager 等）
 *    只接受来自 shell (uid 2000) 或 system (uid 1000) 的调用，会拒绝 root (uid 0) 直接调用。
 * 2. 因此我们用 Root 以 shell 身份 (uid 2000) 启动 Shizuku，
 *    这样 Shizuku 就能以正确的身份调用系统服务。
 * 3. 同时提供 Root shell 命令作为后备方案。
 */
public class RootShizukuStarter {

    private static final String TAG = "RootShizukuStarter";
    private static boolean hasAttempted = false;
    private static final int MAX_RETRY = 3;
    private static final long SHIZUKU_WAIT_MS = 3000;

    /**
     * 检查是否应该使用 Root 模式
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
     * 通过 Root 以 shell 身份 (uid 2000) 启动 Shizuku。
     * 关键：必须以 uid 2000 启动，而不是 uid 0，否则系统服务会拒绝调用。
     */
    public static void startShizukuViaRoot() {
        if (hasAttempted) {
            return;
        }
        if (!shouldUseRootMode()) {
            return;
        }
        if (isShizukuReadyAsShell()) {
            State.log("Shizuku 已在运行且身份正确（Root 模式）");
            return;
        }
        hasAttempted = true;

        // 如果 Shizuku 以 root 身份运行，先杀掉再重启
        if (ShizukuUtils.hasShizukuStarted()) {
            try {
                if (Shizuku.getUid() == 0) {
                    State.log("Root 模式：Shizuku 当前以 root 身份运行 (uid=0)，需要重启为 shell 身份...");
                    killShizukuServer();
                    // 等待 Shizuku 完全退出
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                } else {
                    State.log("Shizuku 已在运行且身份正确 (uid=" + Shizuku.getUid() + ")");
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "检查 Shizuku uid 失败", e);
            }
        }

        State.log("Root 模式：以 shell 身份 (uid 2000) 启动 Shizuku...");

        new Thread(() -> {
            boolean success = false;

            // 方法1：使用 Shizuku 官方启动脚本（以 uid 2000 运行）
            if (!success) {
                success = startAsShell("sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh");
            }

            // 方法2：使用 /data/local/tmp 中的 shizuku_starter
            if (!success) {
                success = startAsShell("/data/local/tmp/shizuku_starter");
            }

            // 方法3：使用 Magisk 模块中的 starter
            if (!success) {
                Shell.Result findResult = Shell.cmd("ls /data/adb/modules/*/shizuku_starter 2>/dev/null").exec();
                for (String path : findResult.getOut()) {
                    if (path != null && !path.isEmpty()) {
                        success = startAsShell(path.trim());
                        if (success) break;
                    }
                }
            }

            // 方法4：如果 Shizuku 已安装但找不到 starter，尝试找 APK 直接用 app_process 启动
            if (!success) {
                success = startViaAppProcess();
            }

            // 方法5：从 assets 中提取 Shizuku 安装并启动
            if (!success) {
                success = installAndStartFromAssets();
            }

            final boolean finalSuccess = success;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalSuccess) {
                    State.log("Root 模式：Shizuku 启动命令已执行，等待就绪...");
                    waitForShizuku(0);
                } else {
                    State.log("Root 模式：Shizuku 启动失败，请手动安装 Shizuku 并通过 ADB/Root 启动");
                    Log.e(TAG, "所有启动方法均失败");
                }
            });
        }).start();
    }

    /**
     * 以 uid 2000 (shell) 身份运行命令启动 Shizuku。
     * 使用 "su 2000 -c" 从 root shell 切换到 shell 用户执行。
     */
    private static boolean startAsShell(String command) {
        try {
            // 关键：su 2000 -c "cmd" 让 Shizuku 以 uid 2000 (shell) 启动
            Shell.Result result = Shell.cmd("su 2000 -c '" + command + "' &").exec();
            if (result.isSuccess()) {
                Log.i(TAG, "以 shell 身份启动: " + command);
                return true;
            }
            // 回退：某些 su 实现不支持 "su 2000 -c"，尝试其他格式
            result = Shell.cmd("su -u 2000 -c '" + command + "' &").exec();
            if (result.isSuccess()) {
                Log.i(TAG, "以 shell 身份启动(alt): " + command);
                return true;
            }
            // 再回退：直接设置 uid 后执行
            result = Shell.cmd("(export HOME=/data/local/tmp; cd /data/local/tmp; exec su 2000 " + command + ") &").exec();
            if (result.isSuccess()) {
                Log.i(TAG, "以 shell 身份启动(alt2): " + command);
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "启动失败: " + command + " - " + e.getMessage());
        }
        return false;
    }

    /**
     * 通过 app_process 直接启动 Shizuku server（适用于已安装但 starter 不可用的情况）
     */
    private static boolean startViaAppProcess() {
        try {
            // 查找已安装的 Shizuku APK 路径
            Shell.Result result = Shell.cmd("pm path moe.shizuku.privileged.api 2>/dev/null | head -1 | sed 's/package://'").exec();
            String apkPath = String.join("", result.getOut()).trim();
            if (apkPath.isEmpty()) {
                return false;
            }
            Log.i(TAG, "找到 Shizuku APK: " + apkPath);

            // 以 uid 2000 通过 app_process 启动 Shizuku server
            String cmd = "su 2000 -c 'app_process -Djava.class.path=" + apkPath +
                    " /system/bin --nice-name=shizuku_server rikka.shizuku.server.ShizukuService' &";
            Shell.Result startResult = Shell.cmd(cmd).exec();
            Log.i(TAG, "app_process 启动结果: " + startResult.isSuccess());
            return true; // app_process 在后台运行，即使返回也可能成功
        } catch (Exception e) {
            Log.w(TAG, "app_process 启动失败", e);
            return false;
        }
    }

    /**
     * 从 assets 中提取内置 Shizuku APK，安装并启动
     */
    private static boolean installAndStartFromAssets() {
        try {
            Context context = State.getContext();
            if (context == null) {
                Log.w(TAG, "Context 不可用，无法提取 assets");
                return false;
            }

            // 检查 Shizuku 是否已安装
            Shell.Result checkResult = Shell.cmd("pm path moe.shizuku.privileged.api 2>/dev/null").exec();
            boolean isInstalled = !String.join("", checkResult.getOut()).trim().isEmpty();

            if (!isInstalled) {
                State.log("Root 模式：正在安装内置 Shizuku...");
                // 从 assets 提取 APK 到 /data/local/tmp
                String tmpApk = "/data/local/tmp/shizuku.apk";
                File tmpFile = new File(context.getCacheDir(), "shizuku.apk");
                try (InputStream is = context.getAssets().open("moe.shizuku.privileged.api_1049.apk");
                     FileOutputStream fos = new FileOutputStream(tmpFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }
                // 用 root 复制到 /data/local/tmp 并安装
                Shell.cmd("cp " + tmpFile.getAbsolutePath() + " " + tmpApk).exec();
                Shell.cmd("chmod 644 " + tmpApk).exec();
                Shell.Result installResult = Shell.cmd("pm install -r " + tmpApk).exec();
                if (!installResult.isSuccess()) {
                    Log.e(TAG, "安装 Shizuku 失败: " + String.join("\n", installResult.getErr()));
                    return false;
                }
                State.log("Root 模式：Shizuku 安装成功");
                // 等待安装完成
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }

            // 安装后尝试用 app_process 启动
            return startViaAppProcess();
        } catch (Exception e) {
            Log.e(TAG, "从 assets 安装 Shizuku 失败", e);
            return false;
        }
    }

    /**
     * 检查 Shizuku 是否以非 root 身份运行（正确状态）
     */
    private static boolean isShizukuReadyAsShell() {
        try {
            if (!ShizukuUtils.hasShizukuStarted()) return false;
            return Shizuku.getUid() != 0; // uid != 0 表示非 root 身份，正常
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 杀死 Shizuku server 进程
     */
    private static void killShizukuServer() {
        try {
            Shell.cmd("killall shizuku_server 2>/dev/null; " +
                    "pkill -f shizuku_server 2>/dev/null; " +
                    "pkill -f rikka.shizuku.server 2>/dev/null").exec();
            Log.i(TAG, "已尝试杀死 Shizuku server");
        } catch (Exception e) {
            Log.w(TAG, "杀死 Shizuku 失败", e);
        }
    }

    /**
     * 等待 Shizuku 就绪（带重试）
     */
    private static void waitForShizuku(int attempt) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (ShizukuUtils.hasShizukuStarted()) {
                try {
                    int uid = Shizuku.getUid();
                    if (uid == 0) {
                        State.log("Root 模式：Shizuku 启动了但仍以 root 身份运行，尝试重启...");
                        // 重新杀掉再启动
                        new Thread(() -> {
                            killShizukuServer();
                            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                            startViaAppProcess();
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (attempt < MAX_RETRY) {
                                    waitForShizuku(attempt + 1);
                                } else {
                                    State.log("Root 模式：多次重试后 Shizuku 仍以 root 身份运行，部分功能可能受限");
                                    State.resumeJob();
                                }
                            }, SHIZUKU_WAIT_MS);
                        }).start();
                    } else {
                        State.log("Root 模式：Shizuku 已就绪 (uid=" + uid + ")");
                        State.resumeJob();
                    }
                } catch (Exception e) {
                    State.log("Root 模式：Shizuku 已就绪");
                    State.resumeJob();
                }
            } else {
                if (attempt < MAX_RETRY) {
                    State.log("Root 模式：等待 Shizuku 启动... (" + (attempt + 1) + "/" + MAX_RETRY + ")");
                    waitForShizuku(attempt + 1);
                } else {
                    State.log("Root 模式：Shizuku 启动超时，请检查 Shizuku 是否正确安装");
                }
            }
        }, SHIZUKU_WAIT_MS);
    }

    /**
     * 通过 Root 直接执行 shell 命令（作为后备方案）
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
     * 通过 Root 授予应用权限（不需要 Shizuku）
     */
    public static boolean grantPermissionViaRoot(String packageName, String permission) {
        try {
            Shell.Result result = Shell.cmd("pm grant " + packageName + " " + permission).exec();
            return result.isSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Root 授权失败", e);
            return false;
        }
    }

    /**
     * 通过 Root 设置 appops（不需要 Shizuku）
     */
    public static boolean setAppOpsViaRoot(String packageName, String op, String mode) {
        try {
            Shell.Result result = Shell.cmd("appops set " + packageName + " " + op + " " + mode).exec();
            return result.isSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Root appops 失败", e);
            return false;
        }
    }

    /**
     * 通过 Root 在指定显示器上启动应用
     */
    public static boolean startActivityOnDisplayViaRoot(String packageName, int displayId) {
        try {
            // 获取启动 Activity 组件名
            Shell.Result result = Shell.cmd(
                    "cmd package resolve-activity --brief " + packageName + " | tail -1"
            ).exec();
            String component = String.join("", result.getOut()).trim();
            if (component.isEmpty()) {
                return false;
            }
            Shell.Result startResult = Shell.cmd(
                    "am start --display " + displayId + " -n " + component + " --windowingMode 1"
            ).exec();
            return startResult.isSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Root 启动应用失败", e);
            return false;
        }
    }

    /**
     * 通过 Root 修改显示器分辨率
     */
    public static boolean setDisplaySizeViaRoot(int displayId, int width, int height) {
        try {
            Shell.Result result = Shell.cmd(
                    "wm size " + width + "x" + height + " -d " + displayId
            ).exec();
            return result.isSuccess();
        } catch (Exception e) {
            Log.e(TAG, "Root 设置分辨率失败", e);
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
