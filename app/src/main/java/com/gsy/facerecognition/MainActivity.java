package com.gsy.facerecognition;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gsy.facerecognition.utils.BitmapUtils;
import com.gsy.facerecognition.view.MyImageView;

public class MainActivity extends Activity {

    private MyImageView mMyImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initPhoto();
    }

    private void initView() {
        mMyImageView = (MyImageView) findViewById(R.id.main_image);
    }

    private void initPhoto() {
        String picPath = "/storage/emulated/0/Tencent/QQ_Images/-663c6adb1540c36f.jpg";
        Bitmap srcBitmap = BitmapUtils.loadBitmap(picPath, 1000, 1000, true);
        if (srcBitmap == null) {
            Toast.makeText(getApplicationContext(), "处理图片失败", Toast.LENGTH_SHORT).show();
        } else {
            // 图片的宽必须为偶数，不然系统无法进行人脸识别
            if (srcBitmap.getWidth() % 2 != 0) {
                srcBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth() - 1
                        , srcBitmap.getHeight());
            }
            // 最多的人脸数
            int maxCount = 50;
            FaceDetector.Face[] faces = new FaceDetector.Face[maxCount];
            FaceDetector faceDetector = new FaceDetector(srcBitmap.getWidth(), srcBitmap.getHeight(), maxCount);
            // 这一步比较耗时间，大概一秒左右，跟bitmap的大小有关(1000左右最佳，识别结果准确并且时间较少)，建议使用EventBus异步处理
            int faceCount = faceDetector.findFaces(srcBitmap, faces);
            PointF pointF = new PointF();
            // 过滤原本就不完整的脸
            for (int i = 0; i < faceCount; i++) {
                float eyesDistance = faces[i].eyesDistance();
                faces[i].getMidPoint(pointF);
                if (pointF.x < eyesDistance                                              // 左边超出
                        || pointF.y < eyesDistance * 1.8f                                // 上边超出
                        || srcBitmap.getWidth() - pointF.x < eyesDistance                // 右边超出
                        || srcBitmap.getHeight() - pointF.y < eyesDistance * 1.8f) {     // 下边超出
                    faces[i] = null;
                }
            }
            // 必须设置LayoutParams，这样在自定义ImageView中使用getLayoutParams才能得到正确的params
            FrameLayout.LayoutParams photoParams = new FrameLayout.LayoutParams(mMyImageView.getLayoutParams());
            photoParams.gravity = Gravity.CENTER;
            photoParams.width = 1000;
            photoParams.height = 1000;
            mMyImageView.setLayoutParams(photoParams);
            mMyImageView.setImageBitmap(srcBitmap, faces, 1f);
        }
    }

}
