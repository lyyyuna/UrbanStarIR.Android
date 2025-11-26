package com.example.toupcamdemo;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;

/**
 * ToupCam Android 简单示例
 * 演示如何连接摄像头设备并获取状态
 */
public class MainActivity extends Activity {
    private static final String TAG = "ToupCamDemo";
    private static final String ACTION_USB_PERMISSION = "com.example.toupcamdemo.USB_PERMISSION";

    private TextView tvStatus;
    private TextView tvCameraInfo;
    private UsbManager usbManager;
    private ToupCamHelper cameraHelper;
    private boolean permissionRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvCameraInfo = findViewById(R.id.tv_camera_info);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        cameraHelper = new ToupCamHelper(this, eventHandler);

        // 注册 USB 权限广播接收器
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        updateStatus("应用启动，等待扫描 USB 设备...");

        // 扫描已连接的 USB 设备
        scanUsbDevices();
    }

    /**
     * 扫描 USB 设备
     */
    private void scanUsbDevices() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        if (deviceList.isEmpty()) {
            updateStatus("未检测到 USB 设备");
            updateCameraInfo("请连接 ToupCam 相机");
            return;
        }

        updateStatus("检测到 " + deviceList.size() + " 个 USB 设备");

        // 查找 ToupCam 设备
        UsbDevice toupCamDevice = null;
        for (UsbDevice device : deviceList.values()) {
            Log.d(TAG, String.format("发现设备: VID=0x%04X, PID=0x%04X, 名称=%s",
                    device.getVendorId(), device.getProductId(), device.getDeviceName()));

            // ToupCam 厂商 ID 通常是 0x0547
            // 这里可以根据实际设备调整 VID/PID 过滤条件
            if (isToupCamDevice(device)) {
                toupCamDevice = device;
                break;
            }
        }

        if (toupCamDevice != null) {
            updateStatus("发现 ToupCam 设备");
            requestPermissionAndOpen(toupCamDevice);
        } else {
            updateStatus("未发现 ToupCam 设备");
            updateCameraInfo("支持的设备:\n- VID: 0x0547 (ToupCam 系列)\n\n当前设备列表:");

            StringBuilder sb = new StringBuilder();
            for (UsbDevice device : deviceList.values()) {
                sb.append(String.format(Locale.US, "\nVID: 0x%04X, PID: 0x%04X",
                        device.getVendorId(), device.getProductId()));
            }
            updateCameraInfo(tvCameraInfo.getText().toString() + sb.toString());
        }
    }

    /**
     * 判断是否是 ToupCam 设备
     */
    private boolean isToupCamDevice(UsbDevice device) {
        // ToupCam 厂商 ID: 0x0547
        // 可以根据实际情况添加其他 VID
        int vid = device.getVendorId();
        return vid == 0x0547;  // ToupCam VID
    }

    /**
     * 请求 USB 权限并打开设备
     */
    private void requestPermissionAndOpen(UsbDevice device) {
        if (usbManager.hasPermission(device)) {
            openCamera(device);
        } else if (!permissionRequested) {
            permissionRequested = true;
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);
            updateStatus("等待 USB 权限授予...");
        }
    }

    /**
     * 打开相机
     */
    private void openCamera(UsbDevice device) {
        updateStatus("正在打开相机...");

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            updateStatus("无法打开设备连接");
            Toast.makeText(this, "打开设备失败", Toast.LENGTH_SHORT).show();
            return;
        }

        int fd = connection.getFileDescriptor();
        boolean success = cameraHelper.openDevice(
                device.getVendorId(),
                device.getProductId(),
                fd
        );

        if (success) {
            updateStatus("✓ 相机已连接");
            showCameraInfo(device);
        } else {
            updateStatus("✗ 相机打开失败");
            connection.close();
            Toast.makeText(this, "初始化相机失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示相机信息
     */
    private void showCameraInfo(UsbDevice device) {
        StringBuilder info = new StringBuilder();
        info.append("=== 设备信息 ===\n");
        info.append(String.format(Locale.US, "VID: 0x%04X\n", device.getVendorId()));
        info.append(String.format(Locale.US, "PID: 0x%04X\n", device.getProductId()));
        info.append("设备名: ").append(device.getDeviceName()).append("\n");

        String modelName = cameraHelper.getModelName(device.getVendorId(), device.getProductId());
        if (modelName != null) {
            info.append("型号: ").append(modelName).append("\n");
        }

        info.append("\n=== 相机状态 ===\n");
        info.append("连接状态: ").append(cameraHelper.isAlive() ? "已连接" : "未连接").append("\n");

        int[] previewSize = cameraHelper.getPreviewSize();
        if (previewSize != null && previewSize.length == 2) {
            info.append(String.format(Locale.US, "预览尺寸: %d × %d\n",
                    previewSize[0], previewSize[1]));
        }

        updateCameraInfo(info.toString());
    }

    /**
     * USB 事件接收器
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            updateStatus("USB 权限已授予");
                            openCamera(device);
                        }
                    } else {
                        updateStatus("USB 权限被拒绝");
                        Toast.makeText(context, "需要 USB 权限才能访问相机", Toast.LENGTH_SHORT).show();
                    }
                    permissionRequested = false;
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                updateStatus("检测到 USB 设备连接");
                scanUsbDevices();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                updateStatus("USB 设备已断开");
                cameraHelper.releaseCamera();
                updateCameraInfo("设备已断开");
            }
        }
    };

    /**
     * 相机事件处理器
     */
    private final Handler eventHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ToupCamHelper.MSG_EVENT) {
                int event = msg.arg1;
                onCameraEvent(event);
            }
        }
    };

    /**
     * 处理相机事件
     */
    private void onCameraEvent(int event) {
        String eventName;
        switch (event) {
            case ToupCamHelper.EVENT_IMAGE:
                eventName = "图像就绪";
                break;
            case ToupCamHelper.EVENT_EXPOSURE:
                eventName = "曝光改变";
                break;
            case ToupCamHelper.EVENT_ERROR:
                eventName = "错误";
                break;
            case ToupCamHelper.EVENT_DISCONNECTED:
                eventName = "设备断开";
                cameraHelper.releaseCamera();
                updateCameraInfo("设备已断开");
                break;
            default:
                eventName = "事件 " + event;
                break;
        }
        Log.d(TAG, "相机事件: " + eventName);
    }

    /**
     * 更新状态文本
     */
    private void updateStatus(final String status) {
        runOnUiThread(() -> {
            tvStatus.setText("状态: " + status);
            Log.d(TAG, status);
        });
    }

    /**
     * 更新相机信息文本
     */
    private void updateCameraInfo(final String info) {
        runOnUiThread(() -> tvCameraInfo.setText(info));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        if (cameraHelper != null) {
            cameraHelper.releaseCamera();
        }
    }
}
