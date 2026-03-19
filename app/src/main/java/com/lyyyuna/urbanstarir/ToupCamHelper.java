package com.lyyyuna.urbanstarir;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * ToupCam 相机辅助类
 * 封装了 JNI 调用，简化相机操作
 */
public class ToupCamHelper {
    private static final String TAG = "ToupCamHelper";

    // 消息和事件常量
    public static final int MSG_EVENT = 100;

    // 相机事件
    public static final int EVENT_EXPOSURE = 0x0001;      // 曝光时间或增益改变
    public static final int EVENT_TEMPTINT = 0x0002;      // 白平衡改变
    public static final int EVENT_IMAGE = 0x0004;         // 图像就绪
    public static final int EVENT_STILLIMAGE = 0x0005;    // 静态图像就绪
    public static final int EVENT_ERROR = 0x0080;         // 通用错误
    public static final int EVENT_DISCONNECTED = 0x0081;  // 相机断开

    private static ToupCamHelper sInstance;
    private Handler eventHandler;
    private Context context;

    static {
        try {
            // 加载原生库
            System.loadLibrary("toupcam");
            System.loadLibrary("jnicam");
            Log.d(TAG, "原生库加载成功");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "原生库加载失败: " + e.getMessage());
        }
    }

    public ToupCamHelper(Context context, Handler handler) {
        this.context = context;
        this.eventHandler = handler;
        sInstance = this;
        init();
    }

    /**
     * 打开设备
     *
     * @param vendorId  USB 厂商 ID
     * @param productId USB 产品 ID
     * @param fd        文件描述符
     * @return 是否成功打开
     */
    public boolean openDevice(int vendorId, int productId, int fd) {
        Log.d(TAG, String.format("打开设备: VID=0x%04X, PID=0x%04X, FD=%d",
                vendorId, productId, fd));

        int result = openDeviceNative(vendorId, productId, fd);

        if (result == 0) {
            Log.d(TAG, "设备打开成功");
            return isAlive();
        } else {
            Log.e(TAG, "设备打开失败，错误码: " + result);
            return false;
        }
    }

    /**
     * 获取预览尺寸
     *
     * @return 尺寸数组 [width, height]
     */
    public int[] getPreviewSize() {
        try {
            return getPreviewSizeNative();
        } catch (Exception e) {
            Log.e(TAG, "获取预览尺寸失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取相机型号名称
     *
     * @param vendorId  厂商 ID
     * @param productId 产品 ID
     * @return 型号名称
     */
    public String getModelName(int vendorId, int productId) {
        try {
            return getModelNameNative(vendorId, productId);
        } catch (Exception e) {
            Log.e(TAG, "获取型号名称失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 拉取图像数据
     *
     * @param buffer 直接字节缓冲区
     */
    public void pullImage(ByteBuffer buffer) {
        if (buffer != null && buffer.isDirect()) {
            pullImageNative(buffer);
        } else {
            Log.w(TAG, "pullImage: 缓冲区必须是 DirectBuffer");
        }
    }

    /**
     * 检查相机是否存活
     *
     * @return 是否存活
     */
    public boolean isAlive() {
        try {
            return isAliveNative();
        } catch (Exception e) {
            Log.e(TAG, "检查存活状态失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 设置曝光时间
     *
     * @param timeUs 曝光时间（微秒）
     * @return 是否设置成功
     */
    public boolean setExposureTime(int timeUs) {
        try {
            return setExposureTimeNative(timeUs);
        } catch (Exception e) {
            Log.e(TAG, "设置曝光时间失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前曝光时间
     *
     * @return 曝光时间（微秒）
     */
    public int getExposureTime() {
        try {
            return getExposureTimeNative();
        } catch (Exception e) {
            Log.e(TAG, "获取曝光时间失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 设置增益
     *
     * @param gain 增益值（百分比，如 100 表示 100%）
     * @return 是否设置成功
     */
    public boolean setGain(int gain) {
        try {
            return setGainNative(gain);
        } catch (Exception e) {
            Log.e(TAG, "设置增益失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前增益
     *
     * @return 增益值（百分比）
     */
    public int getGain() {
        try {
            return getGainNative();
        } catch (Exception e) {
            Log.e(TAG, "获取增益失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 释放相机资源
     */
    public void releaseCamera() {
        try {
            Log.d(TAG, "释放相机资源");
            releaseCameraNative();
        } catch (Exception e) {
            Log.e(TAG, "释放相机失败: " + e.getMessage());
        }
    }

    /**
     * 事件回调（由 JNI 调用）
     *
     * @param event 事件码
     */
    public void onEvent(int event) {
        Log.d(TAG, "收到事件: " + event);

        if (eventHandler != null) {
            Message msg = eventHandler.obtainMessage();
            msg.what = MSG_EVENT;
            msg.arg1 = event;
            eventHandler.sendMessage(msg);
        }
    }

    // ========== Native 方法声明 ==========

    /**
     * 初始化
     */
    private native void init();

    /**
     * 打开设备
     */
    private native int openDeviceNative(int vendorId, int productId, int fd);

    /**
     * 获取预览尺寸
     */
    private native int[] getPreviewSizeNative();

    /**
     * 拉取图像
     */
    private native void pullImageNative(ByteBuffer directBuffer);

    /**
     * 检查是否存活
     */
    private native boolean isAliveNative();

    /**
     * 设置曝光时间（微秒）
     */
    private native boolean setExposureTimeNative(int timeUs);

    /**
     * 获取曝光时间（微秒）
     */
    private native int getExposureTimeNative();

    /**
     * 设置增益（百分比）
     */
    private native boolean setGainNative(int gain);

    /**
     * 获取增益（百分比）
     */
    private native int getGainNative();

    /**
     * 设置自动曝光开关
     *
     * @param enable true=开启自动曝光，false=关闭自动曝光
     * @return 是否设置成功
     */
    public boolean setAutoExposure(boolean enable) {
        try {
            return setAutoExposureNative(enable);
        } catch (Exception e) {
            Log.e(TAG, "设置自动曝光失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取自动曝光开关状态
     *
     * @return true=自动曝光开启，false=自动曝光关闭
     */
    public boolean getAutoExposure() {
        try {
            return getAutoExposureNative();
        } catch (Exception e) {
            Log.e(TAG, "获取自动曝光状态失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 释放相机
     */
    private native void releaseCameraNative();

    /**
     * 设置自动曝光（Native）
     */
    private native boolean setAutoExposureNative(boolean enable);

    /**
     * 获取自动曝光状态（Native）
     */
    private native boolean getAutoExposureNative();

    /**
     * 获取型号名称（静态方法）
     */
    private static native String getModelNameNative(int vendorId, int productId);
}
