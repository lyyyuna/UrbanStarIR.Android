# ToupCam Android Demo

这是一个简化的 ToupCam Android SDK 示例应用，演示如何连接摄像头设备并获取状态信息。

## 功能特性

- ✅ 自动扫描并检测 USB 连接的 ToupCam 相机
- ✅ 请求 USB 权限并打开相机设备
- ✅ 显示相机基本信息（VID、PID、型号、预览尺寸等）
- ✅ 监听相机连接/断开事件
- ✅ 实时显示相机状态

## 系统要求

- **Android 版本**: Android 5.0 (API 21) 及以上
- **测试版本**: ✅ 已在 Android 14 上测试优化
- **硬件要求**: 支持 USB OTG 的 Android 设备
- **相机支持**: ToupCam 系列相机（VID: 0x0547）

## 项目结构

```
android_demo/
├── app/
│   ├── build.gradle                    # 应用级构建配置
│   └── src/main/
│       ├── AndroidManifest.xml         # 应用清单文件
│       ├── java/com/example/toupcamdemo/
│       │   ├── MainActivity.java       # 主活动（UI 和业务逻辑）
│       │   └── ToupCamHelper.java      # 相机辅助类（JNI 封装）
│       ├── res/
│       │   ├── layout/
│       │   │   └── activity_main.xml   # 主界面布局
│       │   ├── values/
│       │   │   └── strings.xml         # 字符串资源
│       │   └── xml/
│       │       └── device_filter.xml   # USB 设备过滤器
│       └── jniLibs/                    # 原生库目录（需手动复制）
│           ├── armeabi-v7a/
│           │   └── libtoupcam.so
│           ├── arm64-v8a/
│           │   └── libtoupcam.so
│           ├── x86/
│           │   └── libtoupcam.so
│           └── x86_64/
│               └── libtoupcam.so
├── build.gradle                        # 项目级构建配置
└── settings.gradle                     # 项目设置
```

## 安装步骤

### 1. 复制原生库文件

将 ToupCam SDK 的原生库文件复制到项目的 `jniLibs` 目录：

```bash
# 创建目标目录
mkdir -p app/src/main/jniLibs/{armeabi-v7a,arm64-v8a,x86,x86_64}

# 复制库文件
cp ../android/arm/libtoupcam.so app/src/main/jniLibs/armeabi-v7a/
cp ../android/arm64/libtoupcam.so app/src/main/jniLibs/arm64-v8a/
cp ../android/x86/libtoupcam.so app/src/main/jniLibs/x86/
cp ../android/x64/libtoupcam.so app/src/main/jniLibs/x86_64/
```

### 2. 配置 JNI 实现

⚠️ **注意**: 本示例需要实现 JNI 原生代码（`libjnicam.so`）。

有两种方式：

**方式 A**: 参考完整示例实现 JNI

可以参考 SDK 完整示例的 JNI 实现：
- 参考文件: `android/samples/demoandroid/app/src/main/jni/jnicam.cpp`
- 将 JNI 代码集成到本项目中

**方式 B**: 使用完整示例

直接使用 SDK 提供的完整示例：
- 位置: `android/samples/demoandroid/`
- 这是一个完整可运行的 Android Studio 项目

### 3. 使用 Android Studio 打开项目

1. 启动 Android Studio
2. 选择 `Open an existing project`
3. 导航到 `android_demo` 目录并打开
4. 等待 Gradle 同步完成

### 4. 构建和运行

1. 连接支持 USB OTG 的 Android 设备
2. 在 Android Studio 中点击 `Run` 按钮
3. 选择目标设备并安装应用
4. 使用 USB OTG 连接线将 ToupCam 相机连接到 Android 设备
5. 应用会自动检测相机并请求权限

## 使用说明

### 连接流程

1. **启动应用**
   - 应用启动后会自动扫描 USB 设备

2. **连接相机**
   - 通过 USB OTG 线连接 ToupCam 相机
   - 应用会检测到设备并弹出权限请求

3. **授予权限**
   - 点击"确定"授予 USB 访问权限
   - 应用会自动打开相机

4. **查看信息**
   - 状态栏显示连接状态
   - 信息区域显示相机详细信息：
     - VID (厂商 ID)
     - PID (产品 ID)
     - 设备名称
     - 相机型号
     - 连接状态
     - 预览尺寸

### 核心 API 说明

#### ToupCamHelper 类

```java
// 创建实例
ToupCamHelper cameraHelper = new ToupCamHelper(context, eventHandler);

// 打开设备
boolean success = cameraHelper.openDevice(vendorId, productId, fd);

// 获取预览尺寸
int[] size = cameraHelper.getPreviewSize();  // [width, height]

// 获取相机型号
String model = cameraHelper.getModelName(vendorId, productId);

// 检查连接状态
boolean alive = cameraHelper.isAlive();

// 释放相机
cameraHelper.releaseCamera();
```

#### 相机事件

```java
// 事件处理器
Handler eventHandler = new Handler(Looper.getMainLooper()) {
    @Override
    public void handleMessage(Message msg) {
        if (msg.what == ToupCamHelper.MSG_EVENT) {
            int event = msg.arg1;
            switch (event) {
                case ToupCamHelper.EVENT_IMAGE:
                    // 图像就绪
                    break;
                case ToupCamHelper.EVENT_EXPOSURE:
                    // 曝光改变
                    break;
                case ToupCamHelper.EVENT_DISCONNECTED:
                    // 设备断开
                    break;
            }
        }
    }
};
```

## 支持的相机

本示例支持 ToupCam 系列相机：
- **厂商 ID (VID)**: `0x0547` (十进制: 1351)
- **支持接口**: USB 2.0 / USB 3.0

如需支持其他厂商的相机，可以修改以下文件：
1. `MainActivity.java` 中的 `isToupCamDevice()` 方法
2. `res/xml/device_filter.xml` 中的 vendor-id

## 权限说明

应用需要以下权限：

- `android.hardware.usb.host` - USB 主机模式
- `android.permission.USB_PERMISSION` - USB 访问权限

这些权限已在 `AndroidManifest.xml` 中配置。

## 故障排除

### 问题 1: 检测不到相机
- 确认 Android 设备支持 USB OTG
- 检查 USB OTG 线缆是否正常
- 确认相机电源已开启
- 查看设备管理器中是否显示相机

### 问题 2: 权限请求失败
- 在设置中手动授予应用 USB 权限
- 尝试断开并重新连接相机
- 重启应用

### 问题 3: 原生库加载失败
- 确认 `libtoupcam.so` 已正确复制到 `jniLibs` 目录
- 检查对应架构的库文件是否存在
- 查看 Logcat 日志了解详细错误

### 问题 4: 编译错误
- 确认 Android SDK 和 NDK 版本匹配
- 清理并重新构建项目: `Build > Clean Project` > `Build > Rebuild Project`

## 进阶功能

要实现更多功能（如图像预览、拍照、参数调整等），请参考：

1. **完整示例项目**
   - 路径: `android/samples/demoandroid/`
   - 包含完整的 JNI 实现和图像渲染

2. **API 文档**
   - 中文: `doc/hans.html`
   - 英文: `doc/en.html`

3. **C/C++ API 头文件**
   - 文件: `inc/toupcam.h`
   - 包含所有 API 函数定义

## 开发环境

- **Android Studio**: 4.0 及以上（推荐最新版）
- **Gradle**: 7.0.4
- **Compile SDK**: 34 (Android 14)
- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **NDK**: 25.2.9519653 (可选，用于自定义 JNI)

### Android 14 特别说明
本项目已针对 Android 14 进行优化，详见 [ANDROID_14_NOTES.md](ANDROID_14_NOTES.md)

## 许可证

本示例代码基于 ToupCam SDK。
请参考 ToupCam SDK 的许可协议使用。

## 技术支持

- ToupCam 官网: [toupcam.com](https://www.toupcam.com)
- SDK 文档: 参见 `doc/` 目录
- GitHub Issues: 请在项目仓库提交问题

## 更新日志

### v1.0 (2025-01-26)
- 初始版本
- 实现基本的相机连接和状态获取
- 支持 USB 设备扫描和权限管理
- 显示相机基本信息

---

**作者**: ToupCam SDK 示例
**日期**: 2025-01-26
**SDK 版本**: 59.29176.20250806
