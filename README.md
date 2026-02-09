# 屏易连 (Connect Screen) v2.0

> 让安卓手机/平板通过有线和无线方式连接外部屏幕，提供极致投屏体验。

[![GitHub release](https://img.shields.io/github/v/release/kumu-ze/connect-screen.com)](https://github.com/kumu-ze/connect-screen.com/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## ✨ v2.0 新特性 (by kumu-ze)

- **全新 UI 设计** — 现代卡片式布局，清晰的视觉层次
- **小米/HyperOS 适配** — 修复小窗打开、显示比例、鼠标无法移动到副屏等问题
- **一键切换回镜像模式** — 单应用投屏时可快速切回
- **强制全屏运行** — 避免在 HyperOS/MIUI 等系统上以自由窗口打开
- **权限状态可视化** — 一目了然查看 Shizuku/无障碍/悬浮窗授权状态

## 📱 功能介绍

安卓屏连让安卓手机通过有线和无线方式连接屏幕或电脑，增强投屏时的细节体验。

### 解决的痛点

- **USB2 手机无法满屏投屏** — 通过 DisplayLink 扩展坞弥补 USB2 的带宽限制
- **厂商阉割桌面模式** — 通过 adb/Shizuku 权限还原双屏异显体验
- **跨品牌投屏限制** — 支持投屏到任何屏幕，不限品牌

### 支持的连接方式

| 方式 | 说明 |
|------|------|
| **USB DP Alt Mode** | USB3.0 手机直连显示器 |
| **DisplayLink** | 通过 DisplayLink 扩展坞（USB2.0 手机也能用） |
| **Moonlight** | 无线局域网投屏（基于 Sunshine 串流） |

### 投屏模式

| 模式 | 入口 | 说明 |
|------|------|------|
| **镜像模式** | 屏易连 | 手机画面同步到外部屏幕 |
| **扩展模式** | 屏连 | 外部屏幕作为独立桌面 |
| **单应用投屏** | 设置中开启 | 将指定应用投到外部屏幕 |

## 📥 安装

### 直接下载
从 [Releases](https://github.com/kumu-ze/connect-screen.com/releases) 页面下载最新 APK。

### 从源码编译

#### 环境要求

| 工具 | 版本 |
|------|------|
| Android Studio | Arctic Fox+ |
| JDK | 21 |
| Android SDK | 34 |
| NDK | 28.0.13004108 |
| CMake | 3.22.1+ |

#### 编译步骤

```bash
# 1. 克隆仓库（含子模块）
git clone --recurse-submodules https://github.com/kumu-ze/connect-screen.com.git
cd connect-screen.com

# 2. 如果子模块未初始化
git submodule update --init --recursive

# 3. 编译 Debug 版本
./gradlew assembleDebug

# APK 输出位置：
# 镜像模式: app-mirror/build/outputs/apk/debug/app-mirror-debug.apk
# 扩展模式: app-extend/build/outputs/apk/debug/app-extend-debug.apk
```

## 📁 项目结构

```
connect-screen.com/
├── app-mirror/              # 镜像投屏模块（主模块）
│   ├── src/main/cpp/        # C++ 原生代码（Sunshine 串流服务）
│   │   ├── dependencies/    # 第三方库（Boost, OpenSSL, enet, opus...）
│   │   ├── sunshine.cpp     # 串流服务入口
│   │   └── ...
│   ├── src/main/java/       # Java 主代码
│   │   └── com/connect_screen/mirror/
│   │       ├── MirrorMainActivity.java    # 主界面
│   │       ├── MirrorSettingsActivity.java # 设置
│   │       ├── MirrorActivity.java        # 镜像渲染
│   │       └── ...
│   └── src/main/res/        # 资源文件
├── app-extend/              # 扩展投屏模块
├── hidden-api-stub/         # Android 隐藏 API 桩
├── termux-x11/              # Termux X11（子模块）
└── termux-x11-shell-loader/ # X11 Shell 加载器
```

## 🔑 权限说明

| 权限 | 用途 |
|------|------|
| **Shizuku / ADB** | 虚拟显示器创建、输入注入、屏幕控制 |
| **MediaProjection** | 屏幕内容捕获与投屏 |
| **悬浮窗** | 浮动返回键与控制按钮 |
| **无障碍** | 副屏触摸事件模拟 |

## 🤝 致谢

### 代码修改者
- **[kumu-ze](https://github.com/kumu-ze)** — v2.0 UI 重构、HyperOS/小米适配、强制全屏、模式切换

### 原始项目
- **[taowen/connect-screen](https://github.com/taowen/connect-screen)** — 安卓屏连原始作者

### 开源依赖
- [LizardByte/Sunshine](https://github.com/LizardByte/Sunshine) — 串流服务核心
- [topjohnwu/libsu](https://github.com/topjohnwu/libsu) — Root 权限管理
- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) — ADB 权限框架
- [termux/termux-x11](https://github.com/termux/termux-x11) — X11 显示服务

### DisplayLink 声明

本应用使用了 DisplayLink® 的驱动程序用于支持 DisplayLink® 设备连接功能。DisplayLink® 是 Synaptics Incorporated 的注册商标。本应用与 Synaptics Incorporated 没有任何官方关联。

## 📞 联系方式

- 🌐 官网：[connect-screen.com](https://connect-screen.com)
- 📕 小红书：[安卓屏连](https://www.xiaohongshu.com/user/profile/602cc4c0000000000100be64)
- 📺 B站：[屏易连](https://space.bilibili.com/494726825)
- 🎵 抖音：[安卓屏连](https://www.douyin.com/user/MS4wLjABAAAAolJRQWuFI6KZwaBUvPfzDejygnorK2K-CY_6b1OuWQM)
- ▶️ YouTube：[安卓屏连](https://www.youtube.com/@connect-screen)
- 📱 QQ群：577902537

## 📄 License

[MIT License](LICENSE)
