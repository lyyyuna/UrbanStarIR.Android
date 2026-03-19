#!/bin/bash
# UrbanStarIR Android 命令行编译环境搭建脚本
# 适用于 macOS (Apple Silicon / Intel)
# 用法: bash setup-env.sh

set -e

echo "====================================="
echo " UrbanStarIR Android 环境搭建"
echo "====================================="

# ---------- 1. 安装 JDK 17 ----------
echo ""
echo "[1/4] 检查 JDK 17..."
if java -version 2>&1 | grep -q 'version "17'; then
    echo "  ✓ JDK 17 已安装"
else
    echo "  → 通过 Homebrew 安装 JDK 17..."
    brew install --cask temurin@17
    echo "  ✓ JDK 17 安装完成"
fi

# 设置 JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "")
if [ -z "$JAVA_HOME" ]; then
    echo "  ✗ 无法找到 JDK 17，请手动安装后重试"
    exit 1
fi
echo "  JAVA_HOME=$JAVA_HOME"

# ---------- 2. 安装 Android Command Line Tools ----------
echo ""
echo "[2/4] 安装 Android SDK..."

ANDROID_HOME="$HOME/Library/Android/sdk"
mkdir -p "$ANDROID_HOME"

if [ ! -d "$ANDROID_HOME/cmdline-tools/latest" ]; then
    echo "  → 下载 Android Command Line Tools..."
    CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
    TEMP_ZIP="/tmp/android-cmdline-tools.zip"
    curl -L -o "$TEMP_ZIP" "$CMDLINE_TOOLS_URL"

    echo "  → 解压..."
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    unzip -q -o "$TEMP_ZIP" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -f "$TEMP_ZIP"
    echo "  ✓ Command Line Tools 安装完成"
else
    echo "  ✓ Command Line Tools 已存在"
fi

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

# ---------- 3. 安装 SDK 组件 ----------
echo ""
echo "[3/4] 安装 SDK 组件..."

# 接受所有许可
yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true

# 安装必需组件
echo "  → 安装 platform-tools, build-tools, SDK platforms, NDK..."
"$SDKMANAGER" \
    "platform-tools" \
    "build-tools;35.0.0" \
    "platforms;android-35" \
    "ndk;25.2.9519653" \
    "cmake;3.22.1"

echo "  ✓ SDK 组件安装完成"

# ---------- 4. 配置环境变量 ----------
echo ""
echo "[4/4] 配置环境变量..."

# 检测 shell 配置文件
if [ -n "$ZSH_VERSION" ] || [ "$SHELL" = "/bin/zsh" ]; then
    SHELL_RC="$HOME/.zshrc"
else
    SHELL_RC="$HOME/.bashrc"
fi

# 写入环境变量（如果尚未存在）
ENV_MARKER="# Android SDK (UrbanStarIR)"
if ! grep -q "$ENV_MARKER" "$SHELL_RC" 2>/dev/null; then
    cat >> "$SHELL_RC" << 'ENVEOF'

# Android SDK (UrbanStarIR)
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$PATH"
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
ENVEOF
    echo "  ✓ 环境变量已写入 $SHELL_RC"
    echo "  → 请运行: source $SHELL_RC"
else
    echo "  ✓ 环境变量已存在于 $SHELL_RC"
fi

# 立即生效
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$PATH"

echo ""
echo "====================================="
echo " 环境搭建完成！"
echo "====================================="
echo ""
echo "已安装："
echo "  • JDK 17 (Temurin)"
echo "  • Android SDK (API 35)"
echo "  • Build Tools 35.0.0"
echo "  • NDK 25.2.9519653"
echo "  • CMake 3.22.1"
echo "  • Platform Tools (adb)"
echo ""
echo "编译命令："
echo "  cd $(pwd)"
echo "  ./gradlew assembleDebug    # Debug APK"
echo "  ./gradlew assembleRelease  # Release APK"
echo ""
echo "安装到手机："
echo "  adb install -r app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "⚠️  首次使用请先运行: source $SHELL_RC"
