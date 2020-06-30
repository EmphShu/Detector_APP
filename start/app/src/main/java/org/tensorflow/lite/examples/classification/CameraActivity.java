/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.classification;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.nio.ByteBuffer;
import java.util.List;
import org.tensorflow.lite.examples.classification.env.ImageUtils;
import org.tensorflow.lite.examples.classification.env.Logger;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Device;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Recognition;

public abstract class CameraActivity extends AppCompatActivity
    implements OnImageAvailableListener,
        Camera.PreviewCallback,//支持API21之前的设备，实现了onPreviewFrame
        View.OnClickListener,
        AdapterView.OnItemSelectedListener {
  private static final Logger LOGGER = new Logger();

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  protected int previewWidth = 0;
  protected int previewHeight = 0;
  private Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  private Runnable postInferenceCallback;
  private Runnable imageConverter;
  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private BottomSheetBehavior<LinearLayout> sheetBehavior;
  protected TextView recognitionTextView,
      recognition1TextView,
      recognition2TextView,
      recognitionValueTextView,
      recognition1ValueTextView,
      recognition2ValueTextView;
  protected TextView frameValueTextView,
      cropValueTextView,
      cameraResolutionTextView,
      rotationTextView,
      inferenceTimeTextView;
  protected ImageView bottomSheetArrowImageView;
  private ImageView plusImageView, minusImageView;
  private Spinner deviceSpinner;
  private TextView threadsTextView;

  private Device device = Device.CPU;
  private int numThreads = -1;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    //首先调用onCreate方法
    LOGGER.d("onCreate " + this);
    //通过Logcat打印调试信息
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//程序运行时设备屏幕常亮

    setContentView(R.layout.tfe_ic_activity_camera);
    //设置当前Activity的布局，本程序仅支持一种屏幕类型的布局

    if (hasPermission()) {//如果拥有权限
      setFragment();//初始化碎片，并将碎片替换到布局中
    } else {//否则，请求权限
      requestPermission();
    }

    //与tfe_ic_layout_bottom_sheet中的各个控件绑定
    threadsTextView = findViewById(R.id.threads);
    plusImageView = findViewById(R.id.plus);
    minusImageView = findViewById(R.id.minus);
    deviceSpinner = findViewById(R.id.device_spinner);
    bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);//最底层的线性布局
    gestureLayout = findViewById(R.id.gesture_layout);//手势上滑的线性布局
    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);//子布局管理工具
    bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);//指示箭头

    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();//新建一个视图树观察器，可以获得布局发生变化时的通知
    //设置手势监听
    vto.addOnGlobalLayoutListener(
            //添加一个当全局布局状态或者视图树的可见性产生变化时的回调方法
            //参数是一个OnGlobalLayoutListener对象
            //用内部类的方式提供参数
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            //当观察到布局变化时。先解除监听
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {//如果安卓版本低于4.1
              gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            } else {//其他版本调用不同的API
              gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
            //                int width = bottomSheetLayout.getMeasuredWidth();
            int height = gestureLayout.getMeasuredHeight();//获取布局的高度

            sheetBehavior.setPeekHeight(height);//设置折叠时底部图纸的高度
          }
        });

    sheetBehavior.setHideable(false);//设置sheet向下滑动时不可隐藏

    //设置回调
    sheetBehavior.setBottomSheetCallback(
            //设置匿名内部类用于执行回调方法
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          //当底部折叠状态发生变化
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
              case BottomSheetBehavior.STATE_HIDDEN:
                break;
              case BottomSheetBehavior.STATE_EXPANDED://当折叠被抽出
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);//箭头指向下
                }
                break;
              case BottomSheetBehavior.STATE_COLLAPSED://折叠
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);//箭头向上
                }
                break;
              case BottomSheetBehavior.STATE_DRAGGING://在拖拉过程中
                  bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
              case BottomSheetBehavior.STATE_SETTLING://保持
                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);//箭头向上
                break;
            }
          }
          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });

    //绑定三组识别结果和识别数据的控件
    recognitionTextView = findViewById(R.id.detected_item);
    recognitionValueTextView = findViewById(R.id.detected_item_value);
    recognition1TextView = findViewById(R.id.detected_item1);
    recognition1ValueTextView = findViewById(R.id.detected_item1_value);
    recognition2TextView = findViewById(R.id.detected_item2);
    recognition2ValueTextView = findViewById(R.id.detected_item2_value);

    //绑定显示识别过程和识别结果的相关控件
    frameValueTextView = findViewById(R.id.frame_info);
    cropValueTextView = findViewById(R.id.crop_info);
    cameraResolutionTextView = findViewById(R.id.view_info);
    rotationTextView = findViewById(R.id.rotation_info);
    inferenceTimeTextView = findViewById(R.id.inference_info);

    deviceSpinner.setOnItemSelectedListener(this);//注册本类的OnItemSelectedListener为选中回调

    plusImageView.setOnClickListener(this);//注册本类中的OnClick为增减的点击回调
    minusImageView.setOnClickListener(this);

    device = Device.valueOf(deviceSpinner.getSelectedItem().toString());//选择一个待选的子项并转为String
    numThreads = Integer.parseInt(threadsTextView.getText().toString().trim());//去除String前后的空格
  }

  protected int[] getRgbBytes() {
    imageConverter.run();//运行转换器，完成帧到图像的转换并返回
    return rgbBytes;
  }

  protected int getLuminanceStride() {
    return yRowStride;
  }

  protected byte[] getLuminance() {
    return yuvBytes[0];
  }

  /** Callback for android.hardware.Camera API */
  //在预览的时候不停的调用这个函数，有一个自己的子线程。
  //data数组中保存的就是每一帧的数据，动态覆盖掉上一帧的数据
  //如需保存则将每一帧的信息取出来另存
  //完成OnCreate之后，回调方法开始工作
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    if (isProcessingFrame) {//如果正在处理图像（初始为false）
      LOGGER.w("Dropping frame!");//拥塞废弃的帧
      return;
    }

    try {
      // Initialize the storage bitmaps once when the resolution is known.
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();//获取Size
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;//设置状态Flag
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
        new Runnable() {
          @Override
          public void run() {
            ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);//将图像的颜色空间进行转换，输出到rgbByte中
          }
        };

    postInferenceCallback =
        new Runnable() {//新建一个可执行类
          @Override
          public void run() {
            //当一次识别完成之后，回到这个位置，继续处理
            camera.addCallbackBuffer(bytes);//把转换后的RGB图像加入到相机回调的缓冲区队列中
            isProcessingFrame = false;//帧处理完毕
          }
        };
    processImage();//开始处理图像
  }

  /** Callback for Camera2 API */
  @Override
  public void onImageAvailable(final ImageReader reader) {
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();

      imageConverter =
          new Runnable() {
            @Override
            public void run() {
              ImageUtils.convertYUV420ToARGB8888(
                  yuvBytes[0],
                  yuvBytes[1],
                  yuvBytes[2],
                  previewWidth,
                  previewHeight,
                  yRowStride,
                  uvRowStride,
                  uvPixelStride,
                  rgbBytes);
            }
          };

      postInferenceCallback =
          new Runnable() {
            @Override
            public void run() {
              image.close();
              isProcessingFrame = false;
            }
          };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  /*变为可见时*/
  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  /*进入前台工作时*/
  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");//创建一个inference线程
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
    //返回与这个线程相关的Looper，并用Looper去创建一个关联handler
  }

  /*工作暂停时*/
  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  /*变为不可见时*/
  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  /*销毁前*/
  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {//添加一个可运行对象到handler的队列中
    if (handler != null) {
      handler.post(r);
    }
  }

  //请求权限的回调函数
  @Override
  public void onRequestPermissionsResult(
      final int requestCode, final String[] permissions, final int[] grantResults) {
    if (requestCode == PERMISSIONS_REQUEST) {//请求的权限被授予
      if (allPermissionsGranted(grantResults)) {//确认是否所有权限都已被授予
        setFragment();//开始初始化Fragment
      } else {
        requestPermission();//再次请求权限
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {//遍历所有已经授予的权限
      if (result != PackageManager.PERMISSION_GRANTED) {//如果有尚未获得的权限，就返回false
        return false;
      }
    }
    return true;
  }

  //检查是否拥有权限
  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//如果当前硬件运行的版本高于Marshroom(Android6.0)
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;//如果有相机权限则返回true，否则false
    } else {
      return true;//如果版本不高于Marshroom，默认拥有权限
    }
  }

  //在该CameraActivity中索取相机权限
  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {//向用户请求相机权限
        Toast.makeText(
                CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
            .show();
      }
      //弹窗请求权限，结果会触发回调函数onRequestPermissionsResult
      requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  // Returns true if the device supports the required hardware level, or better.
  private boolean isHardwareLevelSupported(
      CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    //INFO_SUPPORTED_HARDWARE_LEVEL可以分为LEGACY、LIMITED、FULL、LEVEL3
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {//如果设备支持等级是LEGACY，返回false（不能支持）
      return requiredLevel == deviceLevel;//返回false
    }
    // deviceLevel is not LEGACY, can use numerical sort
    return requiredLevel <= deviceLevel;//返回True，能够支持
  }

  private String chooseCamera() {
    //CameraManager是一个用于探测、定义、连接相机的系统服务
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);//将CAMERA_SERVICE服务的句柄返回manager
    try {
      //查询系统中所有的Camera
      for (final String cameraId : manager.getCameraIdList()) {//遍历连接的相机列表
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);//返回相机的特征参数

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        //LEN_FACING是相机的朝向，包括前置后置和外部
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {//如果相机相对于屏幕向前，则跳过
          continue;
        }

        final StreamConfigurationMap map =//获得摄像头支持的流配置，包括分辨率清晰度等等
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {//无法获得则跳过
          continue;
        }

        // Fallback to camera1 API for internal cameras that don't have full support.
        // This should help with legacy situations where using the camera2 API causes
        // distorted or otherwise broken previews.
        useCamera2API =//判断是否能够得到CameraAPI2的完整支持
            (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)//是外部相机，能够使用API2
                || isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);//如果硬件能够完全支持，也使用API2
        LOGGER.i("Camera API lv2?: %s", useCamera2API);//false为使用CameraAPI1，True为API2
        return cameraId;//返回相机ID，也就是最终调用的相机
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  //设置碎片
  protected void setFragment() {
    String cameraId = chooseCamera();//选择Camera，返回选择的相机的ID
    Fragment fragment;
    //useCamera2API=true;//强制使用API2
    if (useCamera2API) {//如果是使用Camera2API
      Toast.makeText(CameraActivity.this,"You are using CameraAPI2",Toast.LENGTH_LONG);
      CameraConnectionFragment camera2Fragment =//创建一个连接Camera的碎片，提供的回调方法是onPreviewSizeChosen，listener
          CameraConnectionFragment.newInstance(//用CameraConnectionFragment创建新实例的方法，创建一个碎片对象
              new CameraConnectionFragment.ConnectionCallback() {//对ConnectionCallback接口进行实例化
                @Override
                public void onPreviewSizeChosen(final Size size, final int rotation) {//对onPreviewSizeChosen方法进行实现
                  previewHeight = size.getHeight();
                  previewWidth = size.getWidth();
                  CameraActivity.this.onPreviewSizeChosen(size, rotation);
                }
              },
              this,
              getLayoutId(),//在ClassifierAcitivity中实现，获取fragment的布局
              getDesiredPreviewFrameSize());//获取ClassifierActivity中定义的需要的预览尺寸

      camera2Fragment.setCamera(cameraId);//将CameraID绑定给Fragment
      fragment = camera2Fragment;
    } else {//如果不是使用Camera2API，对安卓5.0以下的设备进行支持
      Toast.makeText(CameraActivity.this,"You are using legacyCam",Toast.LENGTH_LONG);
      fragment = new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
      //注册本活动为Callback
      //提供Fragment的布局
      //提供PreviewFrame的理想Size
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    //将布局tfe_ic_activity_camera.xml中预留的container替换成当前的碎片
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  //等待下一帧
  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }

  //在BottomSheet中打印识别结果
  @UiThread
  protected void showResultsInBottomSheet(List<Recognition> results) {
    if (results != null && results.size() >= 3) {
      Recognition recognition = results.get(0);
      if (recognition != null) {
        if (recognition.getTitle() != null) recognitionTextView.setText(recognition.getTitle());
        if (recognition.getConfidence() != null)
          recognitionValueTextView.setText(
              String.format("%.2f", (100 * recognition.getConfidence())) + "%");
      }

      Recognition recognition1 = results.get(1);
      if (recognition1 != null) {
        if (recognition1.getTitle() != null) recognition1TextView.setText(recognition1.getTitle());
        if (recognition1.getConfidence() != null)
          recognition1ValueTextView.setText(
              String.format("%.2f", (100 * recognition1.getConfidence())) + "%");
      }

      Recognition recognition2 = results.get(2);
      if (recognition2 != null) {
        if (recognition2.getTitle() != null) recognition2TextView.setText(recognition2.getTitle());
        if (recognition2.getConfidence() != null)
          recognition2ValueTextView.setText(
              String.format("%.2f", (100 * recognition2.getConfidence())) + "%");
      }
    }
  }

  //在BottomSheet中打印帧信息
  protected void showFrameInfo(String frameInfo) {
    frameValueTextView.setText(frameInfo);
  }

  protected void showCropInfo(String cropInfo) {
    cropValueTextView.setText(cropInfo);
  }

  protected void showCameraResolution(String cameraInfo) {
    cameraResolutionTextView.setText(cameraInfo);
  }

  protected void showRotationInfo(String rotation) {
    rotationTextView.setText(rotation);
  }

  protected void showInference(String inferenceTime) {
    inferenceTimeTextView.setText(inferenceTime);
  }

  protected Device getDevice() {
    return device;
  }

  private void setDevice(Device device) {
    if (this.device != device) {
      LOGGER.d("Updating  device: " + device);
      this.device = device;
      final boolean threadsEnabled = device == Device.CPU;
      plusImageView.setEnabled(threadsEnabled);
      minusImageView.setEnabled(threadsEnabled);
      threadsTextView.setText(threadsEnabled ? String.valueOf(numThreads) : "N/A");
      onInferenceConfigurationChanged();
    }
  }

  protected int getNumThreads() {
    return numThreads;
  }

  private void setNumThreads(int numThreads) {
    if (this.numThreads != numThreads) {
      LOGGER.d("Updating  numThreads: " + numThreads);
      this.numThreads = numThreads;
      onInferenceConfigurationChanged();//刷新
    }
  }

  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

  protected abstract void onInferenceConfigurationChanged();

  //线程数量调节的点击监听回调函数
  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.plus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads >= 9) return;
      setNumThreads(++numThreads);//设置线程
      threadsTextView.setText(String.valueOf(numThreads));
    } else if (v.getId() == R.id.minus) {
      String threads = threadsTextView.getText().toString().trim();
      int numThreads = Integer.parseInt(threads);
      if (numThreads == 1) {
        return;
      }
      setNumThreads(--numThreads);
      threadsTextView.setText(String.valueOf(numThreads));
    }
  }

  @Override
  public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
    if (parent == deviceSpinner) {
      setDevice(Device.valueOf(parent.getItemAtPosition(pos).toString()));
    }
  }

  @Override
  public void onNothingSelected(AdapterView<?> parent) {
    // Do nothing.
  }
}
