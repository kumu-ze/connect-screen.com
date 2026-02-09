package com.connect_screen.mirror.job;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.connect_screen.mirror.BuildConfig;
import com.connect_screen.mirror.Pref;
import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.connect_screen.mirror.shizuku.UserService;
import com.topjohnwu.superuser.Shell;

import rikka.shizuku.Shizuku;

public class AcquireShizuku implements Job {
    public static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;
    private boolean hasRequestedPermission;
    public boolean acquired = false;

    @Override
    public void start() throws YieldException {
        if (!ShizukuUtils.hasShizukuStarted()) {
            return;
        }
        if (ShizukuUtils.hasPermission()) {
            State.log("已经获得 Shizuku 权限");
            acquired = true;
            if (hasRequestedPermission) {
                fixRootShizuku();
                State.bindUserService();
            }
        } else {
            if (hasRequestedPermission) {
                State.log("获取 Shizuku 权限失败");
                return;
            }
            hasRequestedPermission = true;
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            throw new YieldException("等待 Shizuku 权限");
        }
    }

    public static void fixRootShizuku() {
        if (ShizukuUtils.hasPermission() && Shizuku.getUid() == 0) {
            State.log("检测到 Shizuku 以 root 身份运行 (uid=0)，正在重启为 shell 身份 (uid=2000)...");
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    // 1. 杀死当前 root 身份的 Shizuku
                    Shell.cmd(
                        "killall shizuku_server 2>/dev/null; " +
                        "pkill -f shizuku_server 2>/dev/null; " +
                        "pkill -f rikka.shizuku.server 2>/dev/null"
                    ).exec();
                    Thread.sleep(1000);

                    // 2. 以 uid 2000 (shell 身份) 重启 Shizuku
                    // 修复：旧代码 "su 2000" 和下一条命令是分开执行的，没有效果
                    // 正确做法：用 su 2000 -c "command" 在同一行执行
                    boolean success = false;

                    // 尝试方法1：使用 Shizuku 启动脚本
                    Shell.Result result = Shell.cmd(
                        "su 2000 -c 'sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh' &"
                    ).exec();
                    if (result.isSuccess()) success = true;

                    // 尝试方法2：使用 shizuku_starter
                    if (!success) {
                        result = Shell.cmd("su 2000 -c '/data/local/tmp/shizuku_starter' &").exec();
                        if (result.isSuccess()) success = true;
                    }

                    // 尝试方法3：通过 app_process 直接启动
                    if (!success) {
                        Shell.Result pathResult = Shell.cmd(
                            "pm path moe.shizuku.privileged.api 2>/dev/null | head -1 | sed 's/package://'"
                        ).exec();
                        String apkPath = String.join("", pathResult.getOut()).trim();
                        if (!apkPath.isEmpty()) {
                            Shell.cmd("su 2000 -c 'app_process -Djava.class.path=" + apkPath +
                                " /system/bin --nice-name=shizuku_server rikka.shizuku.server.ShizukuService' &"
                            ).exec();
                            success = true;
                        }
                    }

                    final boolean finalSuccess = success;
                    Log.i("State", "Shizuku 重启结果: " + finalSuccess);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (finalSuccess) {
                            State.log("Shizuku 已重启为 shell 身份，请等待连接...");
                        } else {
                            State.log("Shizuku 重启失败，部分功能可能受限");
                        }
                    });
                } catch (Throwable e) {
                    Log.e("AcquireShizuku", "fixRootShizuku 失败", e);
                }
            }).start();
        }
    }
}
