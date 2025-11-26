#!/bin/bash

# ToupCam Android Demo - 库文件安装脚本
# 用于快速复制原生库到项目

echo "=========================================="
echo "ToupCam Android Demo 库文件安装"
echo "=========================================="

# 检查是否在正确的目录
if [ ! -d "app" ]; then
    echo "❌ 错误: 请在 android_demo 目录下运行此脚本"
    exit 1
fi

# 创建目标目录
echo "📁 创建 jniLibs 目录..."
mkdir -p app/src/main/jniLibs/{armeabi-v7a,arm64-v8a,x86,x86_64}

# 复制库文件
echo "📦 复制原生库文件..."

if [ -f "../android/arm/libtoupcam.so" ]; then
    cp ../android/arm/libtoupcam.so app/src/main/jniLibs/armeabi-v7a/
    echo "  ✅ armeabi-v7a/libtoupcam.so"
else
    echo "  ⚠️  未找到 armeabi-v7a 库"
fi

if [ -f "../android/arm64/libtoupcam.so" ]; then
    cp ../android/arm64/libtoupcam.so app/src/main/jniLibs/arm64-v8a/
    echo "  ✅ arm64-v8a/libtoupcam.so"
else
    echo "  ⚠️  未找到 arm64-v8a 库"
fi

if [ -f "../android/x86/libtoupcam.so" ]; then
    cp ../android/x86/libtoupcam.so app/src/main/jniLibs/x86/
    echo "  ✅ x86/libtoupcam.so"
else
    echo "  ⚠️  未找到 x86 库"
fi

if [ -f "../android/x64/libtoupcam.so" ]; then
    cp ../android/x64/libtoupcam.so app/src/main/jniLibs/x86_64/
    echo "  ✅ x86_64/libtoupcam.so"
else
    echo "  ⚠️  未找到 x86_64 库"
fi

echo ""
echo "=========================================="
echo "✅ 库文件安装完成"
echo "=========================================="
echo ""
echo "📝 下一步:"
echo "   1. 实现 JNI 代码 (libjnicam.so)"
echo "   2. 或使用完整示例: ../android/samples/demoandroid/"
echo "   3. 用 Android Studio 打开项目"
echo "   4. 连接 Android 14 设备测试"
echo ""
