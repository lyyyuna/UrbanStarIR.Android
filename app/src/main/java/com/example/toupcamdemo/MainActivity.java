package com.example.toupcamdemo;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * ToupCam 拍照程序
 */
public class MainActivity extends Activity {
    private static final String TAG = "ToupCamDemo";
    private static final String ACTION_USB_PERMISSION = "com.example.toupcamdemo.USB_PERMISSION";
    private static final int REQUEST_STORAGE_PERMISSION = 100;

    // UI 控件
    private TextView tvStatus;
    private ImageView ivPreview;
    private TextView tvExposureValue;
    private TextView tvGainValue;
    private SeekBar seekExposure;
    private SeekBar seekGain;
    private Button btnCapture;
    private Button btnPreviewToggle;
    private TextView tvIntervalValue;
    private TextView tvFramesValue;
    private TextView tvVideoDuration;
    private TextView tvRealDuration;
    private SeekBar seekInterval;
    private SeekBar seekFrames;
    private Button btnTimelapse;

    // 相机相关
    private UsbManager usbManager;
    private ToupCamHelper cameraHelper;
    private boolean permissionRequested = false;
    private boolean isPreviewRunning = false;
    private ByteBuffer imageBuffer;
    private int[] previewSize;

    // 曝光和增益参数
    private int currentExposureUs = 10000; // 默认10ms
    private int currentGain = 100; // 默认100%

    // 延时摄影参数
    private int frameIntervalMs = 1000; // 默认1秒间隔
    private int totalFrames = 100; // 默认100帧
    private boolean isTimelapsing = false;
    private int currentFrameCount = 0;

    // 视频编码相关（边拍边编码，不在内存中保存所有帧）
    private android.media.MediaCodec videoEncoder;
    private android.media.MediaMuxer videoMuxer;
    private int videoTrackIndex = -1;
    private boolean muxerStarted = false;
    private long videoPresentationTimeUs = 0;

    // 图像就绪标志（用于预览）
    private volatile boolean imageReady = false;

    // 图像捕获同步对象（用于拍照和延时摄影）
    private final Object captureLock = new Object();
    private volatile boolean captureImageReady = false;

    // 延时摄影预览帧（延时摄影期间预览显示这个，避免与captureFrame竞争）
    private Bitmap timelapsePreviewFrame = null;
    private final Object previewFrameLock = new Object();

    // USB 设备广播接收器
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.d(TAG, "USB 权限已授予");
                            openCamera(device);
                        }
                    } else {
                        Log.d(TAG, "USB 权限被拒绝");
                        tvStatus.setText("状态: USB 权限被拒绝");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && cameraHelper != null) {
                    Log.d(TAG, "USB 设备断开");
                    closeCamera();
                }
            }
        }
    };

    // 事件处理器
    private Handler eventHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ToupCamHelper.MSG_EVENT) {
                int event = msg.arg1;

                switch (event) {
                    case ToupCamHelper.EVENT_IMAGE:
                        // 图像就绪，同时通知预览和捕获
                        imageReady = true;

                        // 通知等待捕获的线程
                        synchronized (captureLock) {
                            captureImageReady = true;
                            captureLock.notifyAll();
                        }

                        // 更新预览
                        if (isPreviewRunning) {
                            updatePreview();
                        }
                        break;
                    case ToupCamHelper.EVENT_ERROR:
                        tvStatus.setText("状态: 相机错误");
                        break;
                    case ToupCamHelper.EVENT_DISCONNECTED:
                        tvStatus.setText("状态: 相机断开连接");
                        closeCamera();
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 保持屏幕常亮
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 初始化 UI
        initViews();

        // 初始化 USB 管理器
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // 注册 USB 广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        // 检查并请求存储权限
        checkStoragePermission();

        // 扫描 USB 设备
        scanUsbDevices();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        ivPreview = findViewById(R.id.iv_preview);
        tvExposureValue = findViewById(R.id.tv_exposure_value);
        tvGainValue = findViewById(R.id.tv_gain_value);
        seekExposure = findViewById(R.id.seek_exposure);
        seekGain = findViewById(R.id.seek_gain);
        btnCapture = findViewById(R.id.btn_capture);
        btnPreviewToggle = findViewById(R.id.btn_preview_toggle);

        // 曝光时间控制
        Button btnExposureMinus = findViewById(R.id.btn_exposure_minus);
        Button btnExposurePlus = findViewById(R.id.btn_exposure_plus);

        seekExposure.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentExposureUs = progressToExposureTime(progress);
                updateExposureDisplay();
                updateTimelapseDisplay(); // 更新延时摄影时间显示
                if (cameraHelper != null && cameraHelper.isAlive()) {
                    cameraHelper.setExposureTime(currentExposureUs);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnExposureMinus.setOnClickListener(v -> {
            int progress = seekExposure.getProgress();
            if (progress > 0) {
                seekExposure.setProgress(progress - 1);
            }
        });

        btnExposurePlus.setOnClickListener(v -> {
            int progress = seekExposure.getProgress();
            if (progress < seekExposure.getMax()) {
                seekExposure.setProgress(progress + 1);
            }
        });

        // 增益控制
        Button btnGainMinus = findViewById(R.id.btn_gain_minus);
        Button btnGainPlus = findViewById(R.id.btn_gain_plus);

        seekGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentGain = progress * 10; // 步进10
                updateGainDisplay();
                if (cameraHelper != null && cameraHelper.isAlive()) {
                    cameraHelper.setGain(currentGain);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnGainMinus.setOnClickListener(v -> {
            int progress = seekGain.getProgress();
            if (progress > 0) {
                seekGain.setProgress(progress - 1);
            }
        });

        btnGainPlus.setOnClickListener(v -> {
            int progress = seekGain.getProgress();
            if (progress < seekGain.getMax()) {
                seekGain.setProgress(progress + 1);
            }
        });

        // 预览切换按钮
        btnPreviewToggle.setOnClickListener(v -> {
            if (isPreviewRunning) {
                stopPreview();
            } else {
                startPreview();
            }
        });

        // 拍照按钮
        btnCapture.setOnClickListener(v -> captureImage());

        // 延时摄影控件初始化
        tvIntervalValue = findViewById(R.id.tv_interval_value);
        tvFramesValue = findViewById(R.id.tv_frames_value);
        tvVideoDuration = findViewById(R.id.tv_video_duration);
        tvRealDuration = findViewById(R.id.tv_real_duration);
        seekInterval = findViewById(R.id.seek_interval);
        seekFrames = findViewById(R.id.seek_frames);
        btnTimelapse = findViewById(R.id.btn_timelapse);

        // 帧间隔控制 (0.1s - 6s, 进度0-60对应0.1s-6s)
        Button btnIntervalMinus = findViewById(R.id.btn_interval_minus);
        Button btnIntervalPlus = findViewById(R.id.btn_interval_plus);

        seekInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                frameIntervalMs = (progress + 1) * 100; // 0.1s - 6.1s
                updateTimelapseDisplay();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnIntervalMinus.setOnClickListener(v -> {
            int progress = seekInterval.getProgress();
            if (progress > 0) {
                seekInterval.setProgress(progress - 1);
            }
        });

        btnIntervalPlus.setOnClickListener(v -> {
            int progress = seekInterval.getProgress();
            if (progress < seekInterval.getMax()) {
                seekInterval.setProgress(progress + 1);
            }
        });

        // 总帧数控制 (10 - 3000 帧，进度0-300对应10-3000帧)
        Button btnFramesMinus = findViewById(R.id.btn_frames_minus);
        Button btnFramesPlus = findViewById(R.id.btn_frames_plus);

        seekFrames.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                totalFrames = (progress + 1) * 10; // 10 - 3000 帧
                updateTimelapseDisplay();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnFramesMinus.setOnClickListener(v -> {
            int progress = seekFrames.getProgress();
            if (progress > 0) {
                seekFrames.setProgress(progress - 1);
            }
        });

        btnFramesPlus.setOnClickListener(v -> {
            int progress = seekFrames.getProgress();
            if (progress < seekFrames.getMax()) {
                seekFrames.setProgress(progress + 1);
            }
        });

        // 延时摄影按钮
        btnTimelapse.setOnClickListener(v -> {
            if (isTimelapsing) {
                stopTimelapse();
            } else {
                startTimelapse();
            }
        });

        // 初始化显示
        updateExposureDisplay();
        updateGainDisplay();
        updateTimelapseDisplay();
    }

    /**
     * 将进度条值转换为曝光时间（微秒）
     * 0-100: 0-1秒，步进10ms
     * 101-1000: 1-120秒，步进约133ms
     */
    private int progressToExposureTime(int progress) {
        if (progress <= 100) {
            // 0-1秒范围，步进10ms
            return progress * 10000; // 10ms = 10000us
        } else {
            // 1-120秒范围
            int remaining = progress - 100;
            int maxRemaining = 1000 - 100; // 900步
            int timeAbove1s = (int) ((remaining / (float) maxRemaining) * 119000000); // 119秒的微秒数
            return 1000000 + timeAbove1s; // 1秒 + 额外时间
        }
    }

    /**
     * 将曝光时间（微秒）转换为进度条值
     */
    private int exposureTimeToProgress(int timeUs) {
        if (timeUs <= 1000000) {
            // 0-1秒范围
            return timeUs / 10000;
        } else {
            // 1-120秒范围
            int timeAbove1s = timeUs - 1000000;
            int maxRemaining = 1000 - 100;
            int progress = (int) ((timeAbove1s / 119000000.0) * maxRemaining);
            return 100 + progress;
        }
    }

    private void updateExposureDisplay() {
        double seconds = currentExposureUs / 1000000.0;
        if (seconds < 1.0) {
            tvExposureValue.setText(String.format(Locale.getDefault(), "%.2f 秒", seconds));
        } else {
            tvExposureValue.setText(String.format(Locale.getDefault(), "%.1f 秒", seconds));
        }
    }

    private void updateGainDisplay() {
        tvGainValue.setText(String.valueOf(currentGain));
    }

    private void updateTimelapseDisplay() {
        // 更新帧间隔显示
        double intervalSec = frameIntervalMs / 1000.0;
        tvIntervalValue.setText(String.format(Locale.getDefault(), "%.1f 秒", intervalSec));

        // 更新总帧数显示
        tvFramesValue.setText(String.format(Locale.getDefault(), "%d 帧", totalFrames));

        // 计算并显示视频时长（30fps）
        double videoDuration = totalFrames / 30.0;
        if (videoDuration < 60) {
            tvVideoDuration.setText(String.format(Locale.getDefault(), "视频时长: %.1f 秒", videoDuration));
        } else {
            int minutes = (int) (videoDuration / 60);
            double seconds = videoDuration % 60;
            tvVideoDuration.setText(String.format(Locale.getDefault(), "视频时长: %d分%.1f秒", minutes, seconds));
        }

        // 计算并显示真实拍摄时间跨度：(曝光时间 + 帧间隔) * 总帧数
        double exposureSec = currentExposureUs / 1000000.0; // 曝光时间（秒）
        double realDurationSec = (exposureSec + intervalSec) * totalFrames;

        if (realDurationSec < 60) {
            tvRealDuration.setText(String.format(Locale.getDefault(), "拍摄耗时: %.1f 秒", realDurationSec));
        } else if (realDurationSec < 3600) {
            int minutes = (int) (realDurationSec / 60);
            double seconds = realDurationSec % 60;
            tvRealDuration.setText(String.format(Locale.getDefault(), "拍摄耗时: %d分%.1f秒", minutes, seconds));
        } else {
            int hours = (int) (realDurationSec / 3600);
            int minutes = (int) ((realDurationSec % 3600) / 60);
            tvRealDuration.setText(String.format(Locale.getDefault(), "拍摄耗时: %d小时%d分", hours, minutes));
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用 READ_MEDIA_IMAGES
                if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                            REQUEST_STORAGE_PERMISSION);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10-12 不需要额外权限（使用 MediaStore）
            } else {
                // Android 6-9 需要 WRITE_EXTERNAL_STORAGE
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_STORAGE_PERMISSION);
                }
            }
        }
    }

    private void scanUsbDevices() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            tvStatus.setText("状态: 未检测到 USB 设备");
            Log.d(TAG, "未检测到 USB 设备");
            return;
        }

        for (UsbDevice device : deviceList.values()) {
            Log.d(TAG, String.format("发现设备: VID=0x%04X, PID=0x%04X",
                    device.getVendorId(), device.getProductId()));

            // 检查是否是相机设备（根据实际设备调整）
            if (isCameraDevice(device)) {
                tvStatus.setText(String.format("状态: 发现相机 (VID=0x%04X, PID=0x%04X)",
                        device.getVendorId(), device.getProductId()));
                requestUsbPermission(device);
                return;
            }
        }

        tvStatus.setText("状态: 未检测到相机设备");
    }

    private boolean isCameraDevice(UsbDevice device) {
        // ToupCam 设备通常使用特定的 VID
        // 这里需要根据实际设备调整
        int vid = device.getVendorId();

        // 常见的相机设备 VID
        return vid == 0x0547 ||  // ToupTek
               vid == 0x1f4d ||  // Other common camera VID
               vid == 0x0403;    // FTDI (有些相机使用)
    }

    private void requestUsbPermission(UsbDevice device) {
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "已有 USB 权限");
            openCamera(device);
        } else if (!permissionRequested) {
            Log.d(TAG, "请求 USB 权限");
            permissionRequested = true;
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);
        }
    }

    private void openCamera(UsbDevice device) {
        try {
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection == null) {
                tvStatus.setText("状态: 无法打开 USB 设备");
                Log.e(TAG, "无法打开 USB 设备");
                return;
            }

            int fd = connection.getFileDescriptor();
            Log.d(TAG, String.format("USB 连接成功，FD=%d", fd));

            // 初始化相机辅助类
            if (cameraHelper == null) {
                cameraHelper = new ToupCamHelper(this, eventHandler);
            }

            // 打开相机设备
            boolean success = cameraHelper.openDevice(
                    device.getVendorId(),
                    device.getProductId(),
                    fd);

            if (success) {
                // 获取预览尺寸
                previewSize = cameraHelper.getPreviewSize();
                if (previewSize != null) {
                    Log.d(TAG, String.format("预览尺寸: %dx%d", previewSize[0], previewSize[1]));

                    // 分配图像缓冲区（RGB24格式，每像素3字节）
                    int bufferSize = previewSize[0] * previewSize[1] * 3;
                    imageBuffer = ByteBuffer.allocateDirect(bufferSize);

                    // 获取相机型号
                    String modelName = cameraHelper.getModelName(
                            device.getVendorId(), device.getProductId());

                    tvStatus.setText(String.format("状态: 相机已连接 (%s)",
                            modelName != null ? modelName : "未知型号"));

                    // 禁用自动曝光，以便手动控制曝光时间和增益
                    cameraHelper.setAutoExposure(false);
                    Log.d(TAG, "自动曝光已禁用");

                    // 设置初始参数
                    cameraHelper.setExposureTime(currentExposureUs);
                    cameraHelper.setGain(currentGain);

                    // 启用按钮
                    btnPreviewToggle.setEnabled(true);
                    btnCapture.setEnabled(true);
                    btnTimelapse.setEnabled(true);

                    // 自动开始预览
                    startPreview();
                } else {
                    tvStatus.setText("状态: 无法获取预览尺寸");
                    Log.e(TAG, "无法获取预览尺寸");
                }
            } else {
                tvStatus.setText("状态: 相机打开失败");
                Log.e(TAG, "相机打开失败");
            }
        } catch (Exception e) {
            tvStatus.setText("状态: 打开相机时出错");
            Log.e(TAG, "打开相机时出错: " + e.getMessage(), e);
        }
    }

    private void startPreview() {
        if (cameraHelper == null || !cameraHelper.isAlive()) {
            Toast.makeText(this, "相机未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        isPreviewRunning = true;
        btnPreviewToggle.setText("停止预览");
        Log.d(TAG, "预览已启动（事件驱动模式）");
    }

    private void stopPreview() {
        isPreviewRunning = false;
        btnPreviewToggle.setText("开始预览");
        Log.d(TAG, "预览已停止");
    }

    private void updatePreview() {
        if (cameraHelper == null || previewSize == null) {
            return;
        }

        try {
            // 如果正在延时摄影，显示保存的预览帧，不从imageBuffer读取
            if (isTimelapsing) {
                synchronized (previewFrameLock) {
                    if (timelapsePreviewFrame != null && !timelapsePreviewFrame.isRecycled()) {
                        ivPreview.setImageBitmap(timelapsePreviewFrame);
                    }
                }
                return;
            }

            // 正常预览模式：从imageBuffer读取
            if (!imageReady || imageBuffer == null) {
                return;
            }

            // 拉取图像数据
            imageBuffer.clear();
            cameraHelper.pullImage(imageBuffer);
            imageReady = false; // 消费标志

            // 转换为 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(previewSize[0], previewSize[1], Bitmap.Config.ARGB_8888);
            imageBuffer.rewind();

            int[] pixels = new int[previewSize[0] * previewSize[1]];
            for (int i = 0; i < pixels.length; i++) {
                int r = imageBuffer.get() & 0xFF;
                int g = imageBuffer.get() & 0xFF;
                int b = imageBuffer.get() & 0xFF;
                pixels[i] = Color.rgb(r, g, b);
            }
            bitmap.setPixels(pixels, 0, previewSize[0], 0, 0, previewSize[0], previewSize[1]);

            // 显示到 ImageView
            ivPreview.setImageBitmap(bitmap);
        } catch (Exception e) {
            Log.e(TAG, "更新预览失败: " + e.getMessage());
        }
    }

    private void captureImage() {
        if (cameraHelper == null || !cameraHelper.isAlive() || imageBuffer == null || previewSize == null) {
            Toast.makeText(this, "相机未准备好", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 拉取当前图像
            imageBuffer.clear();
            cameraHelper.pullImage(imageBuffer);

            // 转换为 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(previewSize[0], previewSize[1], Bitmap.Config.ARGB_8888);
            imageBuffer.rewind();

            int[] pixels = new int[previewSize[0] * previewSize[1]];
            for (int i = 0; i < pixels.length; i++) {
                int r = imageBuffer.get() & 0xFF;
                int g = imageBuffer.get() & 0xFF;
                int b = imageBuffer.get() & 0xFF;
                pixels[i] = Color.rgb(r, g, b);
            }
            bitmap.setPixels(pixels, 0, previewSize[0], 0, 0, previewSize[0], previewSize[1]);

            // 保存图像
            saveImage(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "拍照失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "拍照失败: " + e.getMessage(), e);
        }
    }

    private void saveImage(Bitmap bitmap) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "ToupCam_" + timestamp + ".jpg";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/ToupCam");

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out != null) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                            Toast.makeText(this, "照片已保存: " + fileName, Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "照片已保存: " + uri);
                        }
                    }
                }
            } else {
                // Android 9 及以下使用传统方式
                File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                File appDir = new File(dcimDir, "ToupCam");
                if (!appDir.exists()) {
                    appDir.mkdirs();
                }

                File imageFile = new File(appDir, fileName);
                try (FileOutputStream out = new FileOutputStream(imageFile)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);

                    // 通知媒体库
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri contentUri = Uri.fromFile(imageFile);
                    mediaScanIntent.setData(contentUri);
                    sendBroadcast(mediaScanIntent);

                    Toast.makeText(this, "照片已保存: " + imageFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "照片已保存: " + imageFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "保存照片失败: " + e.getMessage(), e);
        }
    }

    private void closeCamera() {
        stopPreview();

        if (cameraHelper != null) {
            cameraHelper.releaseCamera();
            cameraHelper = null;
        }

        imageBuffer = null;
        previewSize = null;

        btnPreviewToggle.setEnabled(false);
        btnCapture.setEnabled(false);
        btnTimelapse.setEnabled(false);
        btnPreviewToggle.setText("开始预览");

        tvStatus.setText("状态: 相机已断开");
        ivPreview.setImageBitmap(null);

        Log.d(TAG, "相机已关闭");
    }

    private void startTimelapse() {
        if (cameraHelper == null || !cameraHelper.isAlive()) {
            Toast.makeText(this, "相机未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        // 保持预览运行
        if (!isPreviewRunning) {
            startPreview();
        }

        currentFrameCount = 0;
        isTimelapsing = true;

        // 更新UI
        btnTimelapse.setText("停止延时摄影");
        btnCapture.setEnabled(false);
        btnPreviewToggle.setEnabled(false);
        seekExposure.setEnabled(false);
        seekGain.setEnabled(false);
        seekInterval.setEnabled(false);
        seekFrames.setEnabled(false);

        tvStatus.setText(String.format("延时摄影中: 0/%d", totalFrames));

        Log.d(TAG, String.format("开始延时摄影: %d帧, 间隔%dms", totalFrames, frameIntervalMs));

        // 启动延时摄影（边拍边编码）
        new Thread(() -> {
            try {
                // 初始化视频编码器
                if (!initVideoEncoder()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "初始化视频编码器失败", Toast.LENGTH_SHORT).show();
                        stopTimelapse();
                    });
                    return;
                }

                // 记录上次捕获时间
                long lastCaptureTime = System.currentTimeMillis();

                // 逐帧捕获并编码
                for (int i = 0; i < totalFrames && isTimelapsing; i++) {
                    // 计算目标捕获时间
                    long targetTime = lastCaptureTime + frameIntervalMs;
                    long now = System.currentTimeMillis();

                    // 等待到目标时间
                    if (now < targetTime) {
                        Thread.sleep(targetTime - now);
                    }

                    // 捕获新帧
                    Bitmap frame = captureFrame();
                    lastCaptureTime = System.currentTimeMillis();

                    if (frame != null) {
                        // 保存一份用于预览显示（避免与captureFrame竞争imageBuffer）
                        synchronized (previewFrameLock) {
                            if (timelapsePreviewFrame != null && !timelapsePreviewFrame.isRecycled()) {
                                timelapsePreviewFrame.recycle();
                            }
                            // 创建副本用于预览
                            timelapsePreviewFrame = frame.copy(frame.getConfig(), false);
                        }

                        // 直接编码此帧（不保存到内存）
                        encodeFrame(frame);
                        frame.recycle(); // 立即释放
                        currentFrameCount = i + 1;

                        // 计算渲染进度（边拍边渲染）
                        int captureProgress = (int) ((currentFrameCount * 100.0) / totalFrames);
                        final int frameNum = currentFrameCount;
                        final int progress = captureProgress;
                        runOnUiThread(() -> {
                            tvStatus.setText(String.format("拍摄: %d/%d, 渲染: %d%%", frameNum, totalFrames, progress));
                        });

                        Log.d(TAG, String.format("已编码第 %d/%d 帧 (渲染进度: %d%%)", currentFrameCount, totalFrames, captureProgress));
                    } else {
                        Log.e(TAG, "帧捕获失败");
                        break;
                    }
                }

                // 完成编码（无论是正常完成还是用户主动停止，只要有帧就生成视频）
                if (currentFrameCount > 0) {
                    runOnUiThread(() -> tvStatus.setText("正在完成视频渲染..."));
                    finishVideoEncoding();
                } else {
                    // 没有捕获任何帧，直接释放编码器
                    releaseVideoEncoder();
                }

            } catch (InterruptedException e) {
                Log.d(TAG, "延时摄影被中断");
                // 中断时如果有帧也尝试完成视频
                if (currentFrameCount > 0) {
                    try {
                        finishVideoEncoding();
                    } catch (Exception ex) {
                        releaseVideoEncoder();
                    }
                } else {
                    releaseVideoEncoder();
                }
            } catch (Exception e) {
                Log.e(TAG, "延时摄影出错: " + e.getMessage(), e);
                releaseVideoEncoder();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "延时摄影出错: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                runOnUiThread(() -> stopTimelapse());
            }
        }).start();
    }

    private void stopTimelapse() {
        isTimelapsing = false;
        // 不要在这里释放编码器，让startTimelapse线程完成视频编码后自己释放

        // 清理预览帧
        synchronized (previewFrameLock) {
            if (timelapsePreviewFrame != null && !timelapsePreviewFrame.isRecycled()) {
                timelapsePreviewFrame.recycle();
                timelapsePreviewFrame = null;
            }
        }

        // 恢复UI（如果正在渲染，按钮会在finishVideoEncoding中被禁用，所以这里可以安全恢复）
        btnTimelapse.setText("开始延时摄影");

        // 只在没有正在渲染时才启用按钮（渲染完成后会自动启用）
        if (videoEncoder == null) {
            btnCapture.setEnabled(true);
            btnPreviewToggle.setEnabled(true);
        }

        seekExposure.setEnabled(true);
        seekGain.setEnabled(true);
        seekInterval.setEnabled(true);
        seekFrames.setEnabled(true);

        // 保持预览运行
        if (!isPreviewRunning) {
            startPreview();
        }

        Log.d(TAG, "延时摄影已停止");
    }

    private Bitmap captureFrame() {
        if (cameraHelper == null || imageBuffer == null || previewSize == null) {
            return null;
        }

        try {
            // 使用wait/notify机制等待新的图像就绪事件
            synchronized (captureLock) {
                // 如果标志还是false，才等待新事件
                if (!captureImageReady) {
                    // 等待EVENT_IMAGE事件（最多等待5秒）
                    long startTime = System.currentTimeMillis();
                    while (!captureImageReady) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        if (elapsed >= 5000) {
                            Log.e(TAG, "等待图像超时");
                            return null;
                        }
                        captureLock.wait(5000 - elapsed);
                    }
                }
                // 消费标志
                captureImageReady = false;
            }

            // 图像已就绪，立即读取
            imageBuffer.clear();
            cameraHelper.pullImage(imageBuffer);

            // 转换为 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(previewSize[0], previewSize[1], Bitmap.Config.ARGB_8888);
            imageBuffer.rewind();

            int[] pixels = new int[previewSize[0] * previewSize[1]];
            for (int i = 0; i < pixels.length; i++) {
                int r = imageBuffer.get() & 0xFF;
                int g = imageBuffer.get() & 0xFF;
                int b = imageBuffer.get() & 0xFF;
                pixels[i] = Color.rgb(r, g, b);
            }
            bitmap.setPixels(pixels, 0, previewSize[0], 0, 0, previewSize[0], previewSize[1]);

            return bitmap;
        } catch (InterruptedException e) {
            Log.d(TAG, "捕获帧被中断");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "捕获帧失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 初始化视频编码器
     */
    private boolean initVideoEncoder() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "Timelapse_" + timestamp + ".mp4";

            // 获取预览尺寸
            int width = previewSize[0];
            int height = previewSize[1];

            // 确保宽高是偶数（H.264要求）
            if (width % 2 != 0) width--;
            if (height % 2 != 0) height--;

            Log.d(TAG, String.format("初始化视频编码器: %dx%d, %d帧", width, height, totalFrames));

            // 创建输出文件
            File videoFile = null;
            Uri videoUri = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/ToupCam");
                videoUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            } else {
                File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                File appDir = new File(dcimDir, "ToupCam");
                if (!appDir.exists()) {
                    appDir.mkdirs();
                }
                videoFile = new File(appDir, fileName);
            }

            // 创建编码器
            videoEncoder = android.media.MediaCodec.createEncoderByType("video/avc");
            android.media.MediaFormat format = android.media.MediaFormat.createVideoFormat("video/avc", width, height);
            format.setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT,
                    android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            format.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 2000000);
            format.setInteger(android.media.MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(android.media.MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            videoEncoder.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoEncoder.start();

            // 创建muxer
            if (videoUri != null) {
                videoMuxer = new android.media.MediaMuxer(
                        getContentResolver().openFileDescriptor(videoUri, "w").getFileDescriptor(),
                        android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } else {
                videoMuxer = new android.media.MediaMuxer(videoFile.getAbsolutePath(),
                        android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }

            videoTrackIndex = -1;
            muxerStarted = false;
            videoPresentationTimeUs = 0;

            Log.d(TAG, "视频编码器初始化成功");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "初始化视频编码器失败: " + e.getMessage(), e);
            releaseVideoEncoder();
            return false;
        }
    }

    /**
     * 编码一帧
     */
    private void encodeFrame(Bitmap bitmap) {
        if (videoEncoder == null || bitmap == null) {
            return;
        }

        try {
            int width = previewSize[0];
            int height = previewSize[1];
            if (width % 2 != 0) width--;
            if (height % 2 != 0) height--;

            // 转换为NV12
            byte[] nv12 = bitmapToNV12(bitmap, width, height);

            // 处理所有可用输出
            drainEncoder(false);

            // 送入输入buffer
            int inputBufferIndex = videoEncoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = videoEncoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(nv12);
                long frameDurationUs = 1000000 / 30; // 30fps
                videoEncoder.queueInputBuffer(inputBufferIndex, 0, nv12.length, videoPresentationTimeUs, 0);
                videoPresentationTimeUs += frameDurationUs;
            }

        } catch (Exception e) {
            Log.e(TAG, "编码帧失败: " + e.getMessage(), e);
        }
    }

    /**
     * 完成视频编码
     */
    private void finishVideoEncoding() {
        if (videoEncoder == null) {
            return;
        }

        try {
            runOnUiThread(() -> {
                tvStatus.setText("正在完成视频渲染: 处理中...");
                // 渲染期间禁用拍照和预览切换
                btnCapture.setEnabled(false);
                btnPreviewToggle.setEnabled(false);
                // 保持停止按钮可用（此时已停止捕获，正在完成剩余渲染）
                btnTimelapse.setEnabled(true);
            });

            // 发送EOS信号
            int inputBufferIndex = videoEncoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                videoEncoder.queueInputBuffer(inputBufferIndex, 0, 0, videoPresentationTimeUs,
                        android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }

            // 处理剩余输出直到EOS，显示进度
            drainEncoderWithProgress();

            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "视频已保存", Toast.LENGTH_LONG).show();
                tvStatus.setText("状态: 视频生成完成");
                // 渲染完成，恢复按钮状态
                btnCapture.setEnabled(true);
                btnPreviewToggle.setEnabled(true);
                btnTimelapse.setEnabled(true);
            });

            Log.d(TAG, "视频编码完成");

        } catch (Exception e) {
            Log.e(TAG, "完成视频编码失败: " + e.getMessage(), e);
            runOnUiThread(() -> {
                tvStatus.setText("状态: 视频生成失败");
                // 即使失败也要恢复按钮状态
                btnCapture.setEnabled(true);
                btnPreviewToggle.setEnabled(true);
                btnTimelapse.setEnabled(true);
            });
        } finally {
            releaseVideoEncoder();
        }
    }

    /**
     * 处理编码器输出
     */
    private void drainEncoder(boolean endOfStream) {
        android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();

        while (true) {
            int outputBufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, endOfStream ? 10000 : 0);

            if (outputBufferIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    android.media.MediaFormat newFormat = videoEncoder.getOutputFormat();
                    videoTrackIndex = videoMuxer.addTrack(newFormat);
                    videoMuxer.start();
                    muxerStarted = true;
                    Log.d(TAG, "Muxer started");
                }
            } else if (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = videoEncoder.getOutputBuffer(outputBufferIndex);

                if ((bufferInfo.flags & android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (muxerStarted && bufferInfo.size != 0) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        videoMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                    }
                }

                videoEncoder.releaseOutputBuffer(outputBufferIndex, false);

                if ((bufferInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            } else {
                break;
            }
        }
    }

    /**
     * 处理编码器输出并显示进度（用于最终渲染）
     */
    private void drainEncoderWithProgress() {
        android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();
        int renderedFrames = 0;
        int estimatedTotalFrames = currentFrameCount; // 估计需要渲染的帧数

        while (true) {
            int outputBufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 10000);

            if (outputBufferIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    android.media.MediaFormat newFormat = videoEncoder.getOutputFormat();
                    videoTrackIndex = videoMuxer.addTrack(newFormat);
                    videoMuxer.start();
                    muxerStarted = true;
                    Log.d(TAG, "Muxer started");
                }
            } else if (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = videoEncoder.getOutputBuffer(outputBufferIndex);

                if ((bufferInfo.flags & android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (muxerStarted && bufferInfo.size != 0) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        videoMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);

                        // 更新渲染进度（处理编码器缓冲区中的剩余帧）
                        renderedFrames++;
                        if (renderedFrames % 5 == 0 || renderedFrames == estimatedTotalFrames) {
                            final int finalRendered = renderedFrames;
                            final int finalTotal = estimatedTotalFrames;
                            runOnUiThread(() -> {
                                tvStatus.setText(String.format("正在完成视频渲染: %d/%d", finalRendered, finalTotal));
                            });
                        }
                    }
                }

                videoEncoder.releaseOutputBuffer(outputBufferIndex, false);

                if ((bufferInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    final int finalTotal = estimatedTotalFrames;
                    runOnUiThread(() -> tvStatus.setText(String.format("正在完成视频渲染: %d/%d", finalTotal, finalTotal)));
                    break;
                }
            }
        }
    }

    /**
     * 释放视频编码器资源
     */
    private void releaseVideoEncoder() {
        try {
            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder.release();
                videoEncoder = null;
            }
            if (videoMuxer != null) {
                if (muxerStarted) {
                    videoMuxer.stop();
                }
                videoMuxer.release();
                videoMuxer = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "释放编码器失败: " + e.getMessage(), e);
        }

        muxerStarted = false;
        videoTrackIndex = -1;
        videoPresentationTimeUs = 0;
    }

    private byte[] bitmapToNV12(Bitmap bitmap, int width, int height) {
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);

        byte[] yuv = new byte[width * height * 3 / 2];
        int yIndex = 0;
        int uvIndex = width * height;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int index = j * width + i;
                int R = (argb[index] >> 16) & 0xff;
                int G = (argb[index] >> 8) & 0xff;
                int B = argb[index] & 0xff;

                // RGB转YUV
                int Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                int U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                int V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                yuv[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));

                // NV12格式: UV交错排列 (U在前，V在后)
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }
            }
        }
        return yuv;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCamera();
        unregisterReceiver(usbReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "存储权限已授予");
            } else {
                Toast.makeText(this, "需要存储权限才能保存照片", Toast.LENGTH_LONG).show();
            }
        }
    }
}