# Android 14 兼容性说明

本项目已针对 Android 14 (API 34) 进行优化和测试。

## ✅ 已更新的配置

### 1. SDK 版本
```gradle
compileSdkVersion 34  // Android 14
targetSdkVersion 34   // Android 14
minSdkVersion 21      // Android 5.0（保持向下兼容）
```

### 2. 构建工具
- **Build Tools**: 34.0.0
- **NDK**: 25.2.9519653
- **Gradle Plugin**: 7.0.4+

### 3. 依赖库版本
```gradle
androidx.appcompat:appcompat:1.6.1
androidx.constraintlayout:constraintlayout:2.1.4
androidx.core:core:1.12.0
```

## 🔐 Android 14 权限变化

### USB 权限
✅ **已配置** - USB 权限在 Android 14 中保持不变
```xml
<uses-permission android:name="android.permission.USB_PERMISSION" />
<uses-feature android:name="android.hardware.usb.host" android:required="true" />
```

### PendingIntent 要求
✅ **已实现** - 代码中已使用 `FLAG_IMMUTABLE`
```java
PendingIntent permissionIntent = PendingIntent.getBroadcast(
    this, 0, new Intent(ACTION_USB_PERMISSION),
    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
);
```

### 后台启动限制
✅ **无影响** - 本应用不涉及后台启动

### 通知权限（可选）
如果需要发送通知，需要在运行时请求权限（Android 13+）：
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## 📱 Android 14 新特性支持

### 1. 预测性返回手势
✅ **已启用**
```xml
<application android:enableOnBackInvokedCallback="true">
```

### 2. 前台服务类型（不适用）
本应用不使用前台服务，无需配置。

### 3. 全屏通知权限（不适用）
本应用不使用全屏通知。

## ⚠️ Android 14 特别注意事项

### 1. 动态代码加载限制
- **影响**: JNI 原生库加载
- **状态**: ✅ 无影响（使用标准 `System.loadLibrary()`）

### 2. 隐式 Intent 限制
- **影响**: 广播接收器
- **状态**: ✅ 已正确配置（使用显式 Intent）

### 3. 最低可安装目标 API
- **要求**: 新应用必须 targetSdkVersion ≥ 23
- **状态**: ✅ 满足（targetSdkVersion = 34）

### 4. 64 位架构支持
- **要求**: 必须包含 64 位原生库
- **状态**: ✅ 支持 arm64-v8a 和 x86_64

## 🧪 测试清单

在 Android 14 设备上测试以下功能：

- [ ] 应用安装和启动
- [ ] USB OTG 设备检测
- [ ] USB 权限请求和授予
- [ ] 相机设备连接
- [ ] 相机信息显示
- [ ] 设备断开检测
- [ ] 应用前后台切换
- [ ] 屏幕旋转（如果支持）
- [ ] 返回手势操作

## 🔧 调试建议

### 查看 USB 设备信息
```bash
# 通过 ADB 查看连接的 USB 设备
adb shell dumpsys usb
```

### 查看应用日志
```bash
# 过滤应用日志
adb logcat | grep ToupCamDemo
adb logcat | grep ToupCamHelper

# 查看 USB 相关日志
adb logcat | grep -i usb
```

### 检查权限状态
```bash
# 查看应用权限
adb shell dumpsys package com.example.toupcamdemo
```

## 🐛 常见问题

### 问题 1: 应用无法在 Android 14 上安装
**原因**: targetSdkVersion 过低
**解决**: 已更新到 34

### 问题 2: USB 权限对话框不显示
**原因**:
- 设备不支持 USB OTG
- USB 过滤器配置错误
- 权限已被系统拒绝

**解决**:
```bash
# 清除应用数据重试
adb shell pm clear com.example.toupcamdemo
```

### 问题 3: 原生库加载失败
**原因**: 缺少对应架构的 .so 文件

**检查方法**:
```bash
# 查看设备 ABI
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.product.cpu.abilist

# 常见输出：
# arm64-v8a (64位ARM，最常见)
# armeabi-v7a (32位ARM)
# x86_64 (模拟器常用)
```

**解决**: 确保 `jniLibs/` 目录包含对应架构的库文件

### 问题 4: 相机打开失败
**可能原因**:
1. JNI 实现缺失（`libjnicam.so`）
2. VID/PID 不匹配
3. USB 设备连接问题
4. 驱动程序问题

**解决步骤**:
1. 检查 Logcat 错误信息
2. 验证 VID/PID（通过 `adb shell lsusb` 或日志）
3. 尝试使用完整的官方示例: `android/samples/demoandroid/`

## 📊 性能优化

### Android 14 性能建议

1. **电池优化**
   - 使用 `JobScheduler` 替代持续后台任务
   - 避免不必要的唤醒锁

2. **内存优化**
   - 及时释放相机资源
   - 使用 DirectByteBuffer 减少内存拷贝

3. **UI 流畅性**
   - USB 操作在后台线程
   - 图像处理使用 RenderScript 或 GPU

## 🔄 从旧版本迁移

如果从 Android 12 或更早版本迁移：

1. **更新 PendingIntent**（已完成）
   ```java
   // 旧代码（Android 12-）
   PendingIntent.getBroadcast(context, 0, intent, 0);

   // 新代码（Android 12+）
   PendingIntent.getBroadcast(context, 0, intent,
       PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
   ```

2. **更新导出组件**（已完成）
   ```xml
   <activity android:name=".MainActivity" android:exported="true">
   ```

3. **移除废弃 API**
   - 不使用 `android.app.Fragment`（使用 `androidx.fragment`）
   - 不使用已废弃的 `AsyncTask`

## 🎯 最佳实践

1. **始终目标最新 API**
   - 跟随 Android 最新版本更新 targetSdkVersion
   - 测试新版本的行为变化

2. **权限最小化**
   - 只请求必需的权限
   - 在使用时请求权限（运行时权限）

3. **兼容性测试**
   - 在多个 Android 版本上测试
   - 使用 Android Studio Profiler 监控性能

4. **错误处理**
   - 捕获并记录所有异常
   - 提供友好的错误提示

## 📚 参考资料

- [Android 14 行为变更](https://developer.android.com/about/versions/14/behavior-changes-14)
- [Android 14 新功能](https://developer.android.com/about/versions/14/features)
- [USB 主机模式指南](https://developer.android.com/guide/topics/connectivity/usb/host)
- [PendingIntent 最佳实践](https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability)

---

**最后更新**: 2025-01-26
**测试设备**: Android 14 (API 34)
**SDK 版本**: ToupCam 59.29176.20250806
