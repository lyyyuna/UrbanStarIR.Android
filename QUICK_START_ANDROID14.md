# 🚀 Android 14 快速开始指南

针对您的 Android 14 测试设备的快速入门指南。

## 📋 准备工作

### 设备要求
- ✅ Android 14 设备
- ✅ 支持 USB OTG
- ✅ 开启开发者选项和 USB 调试
- ✅ USB OTG 转接线

### 开发环境
- Android Studio（最新版）
- JDK 11 或更高版本
- ADB 工具

## 🎯 两种使用方式

### 方式一：使用完整示例（⭐ 推荐）

**这是最快的方式，可以直接运行！**

```bash
# 1. 进入完整示例目录
cd android/samples/demoandroid/

# 2. 用 Android Studio 打开项目
# File > Open > 选择 demoandroid 目录

# 3. 连接 Android 14 设备

# 4. 点击 Run 按钮
```

✅ 优点：
- 开箱即用，包含完整 JNI 实现
- 有图像预览功能
- 代码完整，可以学习参考

---

### 方式二：使用简化 Demo

**适合学习和自定义开发**

#### 步骤 1: 安装原生库

```bash
# 进入 demo 目录
cd android_demo/

# 运行安装脚本
./setup_libs.sh
```

#### 步骤 2: 实现 JNI（必需）

您需要实现 JNI 原生代码。有两个选择：

**选择 A**: 参考完整示例实现
```bash
# 参考文件位置
../android/samples/demoandroid/app/src/main/jni/jnicam.cpp
```

**选择 B**: 简化实现（仅测试连接）
创建基本的 JNI 绑定来测试设备连接。

#### 步骤 3: 用 Android Studio 打开

```bash
# 在 Android Studio 中
File > Open > 选择 android_demo 目录
```

#### 步骤 4: 同步和构建

1. 等待 Gradle 同步完成
2. 如果提示更新 Gradle，点击更新
3. Build > Make Project

#### 步骤 5: 运行

1. 连接 Android 14 设备
2. 点击 Run 按钮
3. 选择设备并安装

---

## 🔌 连接相机测试

### 1. 准备相机
- ToupCam 相机
- USB OTG 转接线
- 确保相机电源开启

### 2. 连接流程

```
Android 14 设备 <--[USB OTG 线]--> ToupCam 相机
```

### 3. 启动应用

1. 打开 ToupCam Demo 应用
2. 连接相机到手机
3. 应用会自动检测并弹出权限请求
4. 点击"确定"授予 USB 权限
5. 查看相机信息显示

### 4. 预期结果

```
状态: ✓ 相机已连接

=== 设备信息 ===
VID: 0x0547
PID: 0xXXXX
设备名: /dev/bus/usb/xxx/xxx
型号: [相机型号]

=== 相机状态 ===
连接状态: 已连接
预览尺寸: XXXX × XXXX
```

---

## 🐛 调试技巧

### 查看日志

```bash
# 实时查看应用日志
adb logcat | grep -E "ToupCam|USB"

# 过滤主要信息
adb logcat ToupCamDemo:D ToupCamHelper:D *:S

# 查看所有 USB 事件
adb logcat | grep -i "usb"
```

### 检查设备 ABI

```bash
# 查看设备支持的架构
adb shell getprop ro.product.cpu.abilist

# 典型输出（Android 14）:
# arm64-v8a,armeabi-v7a,armeabi
```

### 检查 USB 设备

```bash
# 查看连接的 USB 设备
adb shell ls -l /dev/bus/usb/

# 查看 USB 管理器状态
adb shell dumpsys usb
```

### 验证库文件

```bash
# 检查 APK 中的库文件
# 构建后，APK 位于: app/build/outputs/apk/debug/

# 解压 APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "\.so$"

# 应该看到:
# lib/arm64-v8a/libtoupcam.so
# lib/armeabi-v7a/libtoupcam.so
# lib/x86/libtoupcam.so
# lib/x86_64/libtoupcam.so
```

---

## ❓ 常见问题

### Q1: 应用无法检测到相机
**解决步骤:**
1. 确认 OTG 线缆正常工作
2. 尝试其他 USB 设备验证 OTG 功能
3. 检查相机电源
4. 查看 logcat 是否有 USB 设备连接日志

```bash
adb logcat | grep "ACTION_USB_DEVICE_ATTACHED"
```

### Q2: 权限对话框不显示
**解决步骤:**
1. 清除应用数据
```bash
adb shell pm clear com.example.toupcamdemo
```

2. 重新安装应用
```bash
adb uninstall com.example.toupcamdemo
adb install app/build/outputs/apk/debug/app-debug.apk
```

3. 断开相机，先启动应用，再连接相机

### Q3: 相机打开失败
**可能原因:**
- ❌ 缺少 JNI 实现 (libjnicam.so)
- ❌ VID/PID 不匹配
- ❌ 原生库不兼容

**解决:**
- 使用完整示例: `android/samples/demoandroid/`
- 检查 logcat 中的详细错误

```bash
adb logcat | grep -E "UnsatisfiedLinkError|JNI|native"
```

### Q4: Android 14 特定问题
**症状**: 在 Android 14 上行为异常

**检查:**
```bash
# 验证 targetSdkVersion
./gradlew :app:dependencies | grep compileSdkVersion

# 应该显示: compileSdkVersion 34
```

**参考**: [ANDROID_14_NOTES.md](ANDROID_14_NOTES.md)

---

## 📊 性能测试

### 测试连接速度

在 MainActivity 的 `openCamera()` 方法中添加计时：

```java
long startTime = System.currentTimeMillis();
boolean success = cameraHelper.openDevice(...);
long duration = System.currentTimeMillis() - startTime;
Log.d(TAG, "相机打开耗时: " + duration + "ms");
```

### 监控内存使用

```bash
# 监控应用内存
adb shell dumpsys meminfo com.example.toupcamdemo

# 实时监控
watch -n 1 "adb shell dumpsys meminfo com.example.toupcamdemo | head -20"
```

---

## 🎓 学习路径

### 1. 理解基础连接（当前 Demo）
- USB 设备枚举
- 权限管理
- 基本状态获取

### 2. 学习完整功能（官方示例）
- 图像预览
- 参数调整
- OpenGL 渲染

### 3. 深入 API（文档）
- 阅读 `doc/hans.html`
- 研究 `inc/toupcam.h`
- 实现自定义功能

---

## 📞 获取帮助

### 日志文件位置
应用日志可以通过以下方式导出：

```bash
# 导出日志
adb logcat -d > toupcam_debug.log

# 过滤并保存
adb logcat -d | grep -E "ToupCam|USB" > toupcam_usb.log
```

### 系统信息
提问时提供以下信息：

```bash
# 设备信息
adb shell getprop | grep -E "ro.build.version|ro.product.model|ro.product.cpu"

# Android 版本
adb shell getprop ro.build.version.release

# ABI 架构
adb shell getprop ro.product.cpu.abi
```

---

## ✅ 测试检查清单

在提交问题前，请确认：

- [ ] Android Studio 已更新到最新版
- [ ] Gradle 同步成功
- [ ] 原生库已正确复制
- [ ] 设备支持 USB OTG（通过其他设备验证）
- [ ] USB 调试已开启
- [ ] 已查看 logcat 日志
- [ ] 已尝试完整示例项目
- [ ] 已阅读 [ANDROID_14_NOTES.md](ANDROID_14_NOTES.md)

---

## 🎉 成功标志

当您看到以下日志时，说明一切正常：

```
D/ToupCamDemo: 发现设备: VID=0x0547, PID=0xXXXX
D/ToupCamDemo: 打开设备: VID=0x0547, PID=0xXXXX, FD=XX
D/ToupCamHelper: 原生库加载成功
D/ToupCamHelper: 设备打开成功
D/ToupCamDemo: ✓ 相机已连接
```

---

**祝您在 Android 14 上开发顺利！** 🚀

如有问题，请参考：
- [README.md](README.md) - 完整文档
- [ANDROID_14_NOTES.md](ANDROID_14_NOTES.md) - Android 14 详细说明
- `doc/hans.html` - ToupCam API 文档
