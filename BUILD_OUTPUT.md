# APK 编译输出 / APK Build Output

## 输出位置 / Output Location

由于网络限制无法从源代码编译，已提取预构建的 APK 到标准输出目录：

Due to network restrictions preventing source compilation, the pre-built APK has been extracted to the standard output directory:

### 镜像模式 APK / Mirror Mode APK
- **路径 / Path**: `app-mirror/build/outputs/apk/debug/app-mirror-debug.apk`
- **大小 / Size**: 69MB
- **版本 / Version**: v2.0.0
- **架构 / Architectures**: arm64-v8a, armeabi-v7a

## 使用方法 / Usage

### 安装 APK / Install APK
```bash
adb install app-mirror/build/outputs/apk/debug/app-mirror-debug.apk
```

### 或者直接使用根目录的 APK / Or use the APK in root directory
```bash
adb install app-mirror-debug.apk
```

## 从源代码编译 / Compile from Source

如果在有网络访问的环境中，可以使用以下命令编译：

If you have network access, you can compile using:

```bash
# 初始化子模块 / Initialize submodules
git submodule update --init --recursive

# 编译 Debug 版本 / Compile Debug version
./gradlew assembleDebug

# 编译 Release 版本 / Compile Release version
./gradlew assembleRelease
```

## 说明 / Notes

- 预构建的 APK 从 `app-mirror-v2.0.0.zip` 提取
- Pre-built APK extracted from `app-mirror-v2.0.0.zip`
- 这是调试版本，包含调试符号
- This is a debug build with debug symbols
