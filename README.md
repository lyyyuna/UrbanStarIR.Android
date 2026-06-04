# UrbanStarIR Android

UrbanStarIR 是一个基于 ToupCam SDK 的 Android 相机应用，用于连接 USB ToupCam/兼容相机，进行实时预览、拍照、曝光/增益控制和延时摄影。

## 功能

- 自动扫描 USB 设备并请求 USB 访问权限
- 打开 ToupCam/兼容相机并显示实时预览
- 手动调节曝光时间和增益
- 拍照并保存到系统相册
- 长曝光场景下支持可选去噪
- 延时摄影拍摄并编码为视频
- 监听相机断开和相机错误事件

## 系统要求

- Android 7.0 (API 24) 及以上
- 支持 USB Host/OTG 的 Android 设备
- ToupCam 或兼容 USB 相机
- 当前 APK 仅打包 `arm64-v8a` 原生库

当前 Gradle 配置：

- Android Gradle Plugin: `8.7.3`
- Gradle Wrapper: `8.9`
- Compile SDK: `35`
- Target SDK: `35`
- Min SDK: `24`
- NDK: `25.2.9519653`

## 项目结构

```text
UrbanStarIR.Android/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/lyyyuna/urbanstarir/
│       │   ├── MainActivity.java
│       │   └── ToupCamHelper.java
│       ├── jni/
│       │   ├── Android.mk
│       │   ├── Application.mk
│       │   ├── jnicam.cpp
│       │   └── libusbcam/
│       ├── jniLibs/
│       │   ├── arm64-v8a/
│       │   │   ├── libjnicam.so
│       │   │   └── libtoupcam.so
│       │   ├── armeabi-v7a/
│       │   ├── x86/
│       │   └── x86_64/
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/strings.xml
│           └── xml/device_filter.xml
├── build.gradle
├── gradle/wrapper/gradle-wrapper.properties
└── settings.gradle
```

说明：

- `jniLibs/` 中已包含预编译的 `libtoupcam.so` 和 `libjnicam.so`。
- `app/src/main/jni/` 保留 JNI 源码和 NDK makefile，但当前 Gradle 未配置 `externalNativeBuild`，默认构建不会重新编译 JNI。
- `app/build.gradle` 通过 `abiFilters 'arm64-v8a'` 限制 APK 只打包 arm64 库。如需支持其他 ABI，需要同时调整 Gradle 配置并确认对应 so 可用。

## 构建

使用 Android Studio 打开仓库根目录，等待 Gradle 同步完成后运行 `app`。

也可以在命令行构建 debug APK：

```bash
./gradlew :app:assembleDebug
```

构建产物位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 运行

1. 将应用安装到支持 USB OTG 的 Android 设备。
2. 使用 USB OTG 线连接相机。
3. 启动应用，或在系统弹出的 USB 设备关联入口中打开应用。
4. 授予 USB 访问权限。
5. 相机打开成功后，应用会自动开始预览，并启用拍照和延时摄影按钮。

## 使用说明

### 预览和拍照

- 应用启动后会扫描已连接的 USB 设备。
- 识别到相机后会请求 USB 权限并打开相机。
- 预览区域显示当前图像。
- 点击“拍照”会保存 JPEG 到系统相册的 `DCIM/ToupCam` 目录。

### 曝光和增益

- 曝光时间通过滑杆和加减按钮调整。
- 曝光范围：
  - 0 到 1 秒：每档 10ms
  - 1 到 30 秒：每档 0.5s
- 增益通过滑杆和加减按钮调整，步进为 10。
- 相机打开后应用会关闭自动曝光，以便手动控制曝光和增益。

### 去噪

- 去噪开关只在曝光时间大于等于 1 秒时可用。
- 短曝光时去噪开关会被禁用。

### 延时摄影

- 可设置总帧数。
- 界面会显示按 30fps 计算的视频时长，以及按当前曝光时间估算的实际拍摄耗时。
- 延时摄影期间会禁用部分控制，避免拍摄参数在过程中变化。

## 支持的相机

代码中当前识别以下 USB VID：

- `0x0547` - ToupTek/ToupCam
- `0x1f4d` - 兼容相机 VID
- `0x0403` - FTDI 相关设备

系统 USB attach 过滤器当前配置了 `0x0547`：

```xml
<usb-device vendor-id="1351" />
```

如需支持新的设备，请同步检查：

- `MainActivity.java` 中的 `isCameraDevice()` 方法
- `app/src/main/res/xml/device_filter.xml`

## 权限

应用声明了以下权限/特性：

- `android.hardware.usb.host`
- `android.permission.USB_PERMISSION`
- `android.permission.WRITE_EXTERNAL_STORAGE`，仅 Android 9 及以下
- `android.permission.READ_EXTERNAL_STORAGE`，仅 Android 12 及以下
- `android.permission.READ_MEDIA_IMAGES`

Android 10 及以上保存图片主要通过 `MediaStore`。

## Native 库

Java 层通过 `ToupCamHelper` 加载以下 native 库：

```java
System.loadLibrary("toupcam");
System.loadLibrary("jnicam");
```

当前 debug APK 中预期包含：

```text
lib/arm64-v8a/libtoupcam.so
lib/arm64-v8a/libjnicam.so
```

如果运行时报 `UnsatisfiedLinkError`，优先检查：

1. APK 中是否包含当前设备 ABI 对应的 `libtoupcam.so` 和 `libjnicam.so`。
2. `app/build.gradle` 中的 `abiFilters` 是否与设备 ABI 匹配。
3. `jniLibs` 中对应 ABI 的 so 是否完整。

## 故障排除

### 检测不到相机

- 确认 Android 设备支持 USB OTG/USB Host。
- 检查 OTG 线和相机供电。
- 确认相机 VID 是否在 `isCameraDevice()` 和 `device_filter.xml` 中配置。
- 重新插拔相机后重启应用。

### USB 权限失败

- 断开相机后重新连接并重新授权。
- 在系统设置中清除应用默认 USB 关联后重试。
- 查看 Logcat 中 `ToupCamDemo` 标签的日志。

### 原生库加载失败

- 确认目标设备是 `arm64-v8a`，或调整 ABI 配置重新打包。
- 确认 `libtoupcam.so` 和 `libjnicam.so` 都存在。
- 如果替换了 SDK so，确保 `jnicam` 与 `toupcam` 的 ABI 和版本兼容。

### 构建失败

- 确认本机已安装 Android SDK 35 和 Build Tools 35.0.0。
- 确认 Android Studio/Gradle 能访问本机 Gradle 缓存。
- 重新执行：

```bash
./gradlew clean :app:assembleDebug
```

## 相关文件

- 应用入口：`app/src/main/java/com/lyyyuna/urbanstarir/MainActivity.java`
- JNI 封装：`app/src/main/java/com/lyyyuna/urbanstarir/ToupCamHelper.java`
- USB 过滤器：`app/src/main/res/xml/device_filter.xml`
- 主界面：`app/src/main/res/layout/activity_main.xml`
- Android 14 说明：`ANDROID_14_NOTES.md`
