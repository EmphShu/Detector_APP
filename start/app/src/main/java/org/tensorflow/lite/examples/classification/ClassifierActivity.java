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

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.util.Size;
import android.util.TypedValue;
import java.io.IOException;
import java.util.List;
import org.tensorflow.lite.examples.classification.env.BorderedText;
import org.tensorflow.lite.examples.classification.env.Logger;
import org.tensorflow.lite.examples.classification.tflite.Classifier;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Device;

public class ClassifierActivity extends CameraActivity implements OnImageAvailableListener {
  //继承CameraActivity，将相机功能与分类功能结合
  //程序开始执行之后，会先调用继承自CameraActivity的onCreate方法
  private static final Logger LOGGER = new Logger();
  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);//需要的预览尺寸
  private static final float TEXT_SIZE_DIP = 10;//待转换的DIP
  private Bitmap rgbFrameBitmap = null;//RGB帧图文件
  private long lastProcessingTimeMs;//持续处理时间
  private Integer sensorOrientation;//传感器朝向
  private Classifier classifier;//分类器
  private BorderedText borderedText;//
  /** Input image size of the model along x axis. */
  private int imageSizeX;
  /** Input image size of the model along y axis. */
  private int imageSizeY;

  @Override
  protected int getLayoutId() {
    return R.layout.tfe_ic_camera_connection_fragment;//返回碎片的布局
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override//最终实现的预览尺寸选择方法
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    //根据当前的显示量度，将TEXT_SIZE_DIP转换成COMPLEX_UNIT_DIP
    borderedText = new BorderedText(textSizePx);//根据textSizePx新建一个内白外黑的文本对象
    borderedText.setTypeface(Typeface.SANS_SERIF);//根据字体样式初始化文本对象（原本为MONOSPACE）
    recreateClassifier(getDevice(), getNumThreads());//根据选择的设备和线程数，重新创建分类器
    if (classifier == null) {
      LOGGER.e("No classifier on preview!");
      return;
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    //完成选择角度的初始化
    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);
    //完成Size的初始化
    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    //创建一个用于接收RGB图像的Bitmap
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
  }

  //完成帧处理之后的图像处理
  @Override
  protected void processImage() {
    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);//将rgbBytes中的颜色，跳过previewWidth宽度的像素，从原点开始读取长宽指定的color
    final int cropSize = Math.min(previewWidth, previewHeight);//选择高宽的最小值为Cropsize

    runInBackground(//在主线程中新建线程执行以下代码
        new Runnable() {
          @Override
          public void run() {
            if (classifier != null) {
              final long startTime = SystemClock.uptimeMillis();//获取当前系统时间
              final List<Classifier.Recognition> results = classifier.recognizeImage(rgbFrameBitmap, sensorOrientation);//运行分类器，获取结果数组
              lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;//得到图像处理时间
              LOGGER.v("Detect: %s", results);

              runOnUiThread(//在主线程中运行
                  new Runnable() {
                    @Override
                    public void run() {
                      showResultsInBottomSheet(results);//在主进程打印结果
                      showFrameInfo(previewWidth + "x" + previewHeight);//打印帧信息
                      showCropInfo(imageSizeX + "x" + imageSizeY);//打印图像信息
                      showCameraResolution(cropSize + "x" + cropSize);//
                      showRotationInfo(String.valueOf(sensorOrientation));//打印传感器方向
                      showInference(lastProcessingTimeMs + "ms");//打印处理时间
                    }
                  });
            }
            readyForNextImage();//准备下一帧
          }
        });
  }

  //参数更新之后的刷新方法
  @Override
  protected void onInferenceConfigurationChanged() {
    if (rgbFrameBitmap == null) {
      // Defer creation until we're getting camera frames.
      return;
    }
    final Device device = getDevice();
    final int numThreads = getNumThreads();
    runInBackground(() -> recreateClassifier(device, numThreads));//重新创建分类器
  }

  private void recreateClassifier(Device device, int numThreads) {
    if (classifier != null) {//如果当前没有分类器工作，则关闭TFlite
      LOGGER.d("Closing classifier.");
      classifier.close();
      classifier = null;
    }
    try {
      LOGGER.d(
          "Creating classifier (device=%s, numThreads=%d)", device, numThreads);
      classifier = Classifier.create(this, device, numThreads);//根据当前的设备和线程，创建基于Float MobileNet的分类器
    } catch (IOException e) {
      LOGGER.e(e, "Failed to create classifier.");
    }

    // Updates the input image size.
    imageSizeX = classifier.getImageSizeX();
    imageSizeY = classifier.getImageSizeY();
  }
}
