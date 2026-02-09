package com.connect_screen.mirror;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.connect_screen.mirror.job.AcquireShizuku;
import com.connect_screen.mirror.job.AutoRotateAndScaleForDisplaylink;
import com.connect_screen.mirror.job.CreateVirtualDisplay;
import com.connect_screen.mirror.job.ExitAll;
import com.connect_screen.mirror.job.InputRouting;
import com.connect_screen.mirror.shizuku.ShizukuUtils;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.ref.WeakReference;

import rikka.shizuku.Shizuku;
import com.topjohnwu.superuser.Shell;

public class MirrorMainActivity extends AppCompatActivity implements IMainActivity {

    static {
        // Set settings before the main shell can be created
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10));
    }

    public static final int REQUEST_CODE_MEDIA_PROJECTION = 1001; // 定义一个请求码
    public static final int REQUEST_RECORD_AUDIO_PERMISSION = 1002;
    public static final String TAG = "MirrorMainActivity";

    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;

    // 添加时间戳和延迟常量
    private static final long MONITOR_INIT_DELAY = 3000; // 5秒延迟
    private long lastCheckTime = 0;

    Button settingsBtn;
    Button screenOffBtn;
    Button touchScreenBtn;
    Button exitBtn;
    TextView mirrorStatus;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            State.resumeJob();
        } else {
            State.log("未知权限请求代码: " + requestCode);
        }
    }

    private void onRequestShizukuPermissionsResult(int requestCode, int grantResult) {
        if (requestCode == AcquireShizuku.SHIZUKU_PERMISSION_REQUEST_CODE) {
            State.log("Shizuku 权限请求结果: " + (grantResult == PackageManager.PERMISSION_GRANTED ? "已授权" : "被拒绝"));
            State.resumeJob();
        } else {
            State.log("未知 Shizuku 请求代码: " + requestCode);
        }
    }

    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER =
        this::onRequestShizukuPermissionsResult;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
                android.util.Log.i("MainActivity", "成功添加隐藏API豁免");
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "添加隐藏API豁免失败: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置 State.currentActivity 为当前的 MainActivity 实例
        State.setCurrentActivity(this);

        // 强制全屏模式，防止小米澎湃系统等以小窗打开
        enforceFullScreen();

        // 添加保持屏幕常亮的标志
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 获取 DoNotAutoStartMoonlight 参数
        boolean doNotAutoStartMoonlight = getIntent().getBooleanExtra("DoNotAutoStartMoonlight", false);
        if (doNotAutoStartMoonlight) {
            Pref.doNotAutoStartMoonlight = doNotAutoStartMoonlight;
        }

        AcquireShizuku.fixRootShizuku();
        if (!Pref.getDisableAccessibility()) {
            ensureAccessiblityServiceStarted();
        }

        // 检查 SunshineService 是否已经在运行，如果没有运行才启动
        if (SunshineService.instance == null) {
            startMediaProjectionService();
        } else {
            State.log("SunshineService 服务已在运行");
        }
        
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        setContentView(R.layout.activity_main);

        // 初始化日志列表
        logRecyclerView = findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(State.logs);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);


        // 观察 UI 状态变化
        State.uiState.observe(this, this::updateUI);

        // 初始化主界面控件
        initHomeControls();
    }

    private void ensureAccessiblityServiceStarted() {
        if (TouchpadAccessibilityService.isAccessibilityServiceEnabled(this)) {
            Intent serviceIntent = new Intent(this, TouchpadAccessibilityService.class);
            this.startService(serviceIntent);
        }
    }

    /**
     * 强制全屏运行，防止在小米澎湃系统/MIUI等上以小窗或自由窗口模式打开
     */
    private void enforceFullScreen() {
        try {
            // 退出多窗口/小窗/自由窗口模式
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                if (isInMultiWindowMode()) {
                    // 通过设置FLAG来尝试退出多窗口
                    android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
                    params.flags |= android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;
                    getWindow().setAttributes(params);
                }
            }
            // 确保 Activity 以全屏 Task 模式启动
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    // 尝试通过反射调用 moveTaskToFront 确保全屏
                    android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        am.moveTaskToFront(getTaskId(), 0);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "enforceFullScreen failed: " + e.getMessage());
        }
    }

    // 添加初始化主界面控件的方法
    private void initHomeControls() {
        settingsBtn = findViewById(R.id.settingsBtn);
        screenOffBtn = findViewById(R.id.screenOffBtn);
        touchScreenBtn = findViewById(R.id.touchScreenBtn);
        exitBtn = findViewById(R.id.exitBtn);
        mirrorStatus = findViewById(R.id.mirrorStatus);
        
        // 获取设置
        
        refresh();

        // 设置按钮点击事件
        settingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, MirrorSettingsActivity.class);
            startActivity(intent);
        });

        screenOffBtn.setOnClickListener(v -> {
            CreateVirtualDisplay.doPowerOffScreen(this);
        });

        // 切换镜像模式按钮
        Button switchModeBtn = findViewById(R.id.switchModeBtn);
        if (switchModeBtn != null) {
            switchModeBtn.setOnClickListener(v -> {
                switchToMirrorMode();
            });
        }

        touchScreenBtn.setOnClickListener(v -> {
            boolean useTouchscreen = Pref.getUseTouchscreen();
            if (ShizukuUtils.hasPermission() && useTouchscreen) {
                VirtualDisplay virtualDisplay = State.displaylinkState.getVirtualDisplay();
                if (virtualDisplay == null) {
                    virtualDisplay = State.mirrorVirtualDisplay;
                }
                if (virtualDisplay == null) {
                    return;
                }
                int displayId = virtualDisplay.getDisplay().getDisplayId();
                Intent intent = new Intent(this, TouchscreenActivity.class);
                intent.putExtra("surface", virtualDisplay.getSurface());
                intent.putExtra("display", displayId);
                startActivity(intent);
            } else {
                TouchpadActivity.startTouchpad(this, State.lastSingleAppDisplay, false);
            }
        });

        exitBtn.setOnClickListener(v -> {
            if (AutoRotateAndScaleForDisplaylink.instance != null) {
                AutoRotateAndScaleForDisplaylink.instance.release();
            }
            ExitAll.execute(this, false);
        });
    }

    /**
     * 切换回镜像模式：释放当前单应用虚拟显示，恢复镜像投屏
     */
    private void switchToMirrorMode() {
        try {
            // 释放单应用虚拟显示
            if (State.displaylinkState.getVirtualDisplay() != null) {
                State.displaylinkState.destroy();
            }
            State.lastSingleAppDisplay = 0;

            // 恢复宽高比
            CreateVirtualDisplay.restoreAspectRatio();
            
            // 恢复输入法
            InputRouting.moveImeToDefault();

            // 保存为镜像模式
            SharedPreferences preferences = getSharedPreferences(MirrorSettingsActivity.PREF_NAME, Context.MODE_PRIVATE);
            preferences.edit().putBoolean(Pref.KEY_SINGLE_APP_MODE, false).apply();

            State.log("已切换回镜像模式，重启中...");

            // 重启应用以应用镜像模式
            ExitAll.execute(this, true);
        } catch (Exception e) {
            State.log("切换镜像模式失败: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        State.setCurrentActivity(this);
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        State.setCurrentActivity(null);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
       
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                State.log("用户授予了投屏权限");
                lastCheckTime = System.currentTimeMillis(); // 记录时间戳
                if (SunshineService.instance == null) {
                    Intent sunshineServiceIntent = new Intent(this, SunshineService.class);
                    sunshineServiceIntent.putExtra("data", data);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(sunshineServiceIntent);
                    } else {
                        startService(sunshineServiceIntent);
                    }
                    State.log("启动 SunshineService 服务");
                } else {
                    MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    State.setMediaProjection(mediaProjectionManager.getMediaProjection(RESULT_OK, data));
                    State.getMediaProjection().registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            State.log("MediaProjection onStop 回调");
                        }
                    }, null);
                    State.resumeJob();
                }
            } else {
                State.log("用户拒绝了投屏权限");
                refresh();
                State.resumeJob();
            }
        }
    }

    // 添加一个方法来检查服务是否在运行
    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    // 更新日志列表的方法
    public void updateLogs() {
        try {
            if (logAdapter != null) {
                logAdapter.notifyDataSetChanged();
                logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public void startMediaProjectionService() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) this.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager != null) {
            Intent captureIntent = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
            } else {
                captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            }
            MirrorMainActivity mirrorMainActivity = State.getCurrentActivity();
            if (mirrorMainActivity != null) {
                mirrorMainActivity.startActivityForResult(captureIntent, MirrorMainActivity.REQUEST_CODE_MEDIA_PROJECTION);
                TouchpadAccessibilityService.grantPermissionByClick(mirrorMainActivity);
            }
        } else {
            throw new RuntimeException("无法获取 MediaProjectionManager 服务");
        }
    }

    // 修改 updateUI 方法
    private void updateUI(MirrorUiState state) {
        Button switchModeBtn = findViewById(R.id.switchModeBtn);
        if (state.errorStatusText != null) {
            mirrorStatus.setText(state.errorStatusText);
            settingsBtn.setVisibility(View.VISIBLE);
            screenOffBtn.setVisibility(View.GONE);
            touchScreenBtn.setVisibility(View.GONE);
            if (switchModeBtn != null) switchModeBtn.setVisibility(View.GONE);
            return;
        }
        // 根据状态设置 UI
        mirrorStatus.setText(state.mirrorStatusText);
        mirrorStatus.setVisibility(View.VISIBLE);
        
        settingsBtn.setVisibility(state.settingsBtnVisibility ? View.VISIBLE : View.GONE);
        screenOffBtn.setText("熄屏");
        screenOffBtn.setVisibility(state.screenOffBtnVisibility ? View.VISIBLE : View.GONE);
        touchScreenBtn.setVisibility(state.touchScreenBtnVisibility ? View.VISIBLE : View.GONE);
        
        if (state.touchScreenBtnVisibility) {
            touchScreenBtn.setText(state.touchScreenBtnText);
        }

        // 显示切换镜像模式按钮
        if (switchModeBtn != null) {
            switchModeBtn.setVisibility(state.switchModeBtnVisibility ? View.VISIBLE : View.GONE);
        }
    }

    // 修改 refresh 方法
    public void refresh() {
        if (State.uiState.getValue().errorStatusText != null) {
            return;
        }
        boolean singleAppMode = Pref.getSingleAppMode();
        boolean useTouchscreen = Pref.getUseTouchscreen();
        
        // 更新 ViewModel 中的状态
        boolean isScreenMirroring = State.mirrorVirtualDisplay != null || 
                                   State.displaylinkState.getVirtualDisplay() != null || 
                                   State.lastSingleAppDisplay != 0;

        MirrorUiState newUiState = new MirrorUiState();

        if (SunshineService.instance == null) {
            newUiState.mirrorStatusText = "未获得投屏权限，请手工点击退出按钮";
            newUiState.settingsBtnVisibility = true;
            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        } else if (isScreenMirroring) {
            newUiState.mirrorStatusText = "投屏中，请在系统设置中为屏易连关闭省电，并在任务列表中锁定任务防止被杀。如果没有 shizuku 权限可能无法触摸";
            newUiState.settingsBtnVisibility = false;
            newUiState.screenOffBtnVisibility = ShizukuUtils.hasPermission();
            newUiState.touchScreenBtnVisibility = singleAppMode;
            newUiState.switchModeBtnVisibility = singleAppMode; // 单应用模式时显示切换按钮

            if (singleAppMode) {
                newUiState.touchScreenBtnText = useTouchscreen ? "触摸屏" : "触控板";
            }
        } else {
            newUiState.mirrorStatusText = "请连接屏幕，如果接口是USB2.0的手机需要Displaylink扩展坞或者Moonlight无线投屏";
            try {
                for (String ip : SunshineService.getAllWifiIpAddresses(this)) {
                    newUiState.mirrorStatusText += "\n";
                    newUiState.mirrorStatusText += ip;
                }
            } catch(Throwable e) {
                // ignore
            }
            newUiState.settingsBtnVisibility = true;
            newUiState.screenOffBtnVisibility = false;
            newUiState.touchScreenBtnVisibility = false;
        }

        State.uiState.setValue(newUiState);
    }
}