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
            if (srcBitmap.getWidth() % 2 != 0) {
                srcBitmap = Bitmap.createBitmap(srcBitmap, 0, 0, srcBitmap.getWidth() - 1
                        , srcBitmap.getHeight());
            }
            // 最多的人脸数
            int maxCount = 50;
            FaceDetector.Face[] faces = new FaceDetector.Face[maxCount];
            FaceDetector faceDetector = new FaceDetector(srcBitmap.getWidth(), srcBitmap.getHeight(), maxCount);
            int faceCount = faceDetector.findFaces(srcBitmap, faces);
            PointF pointF = new PointF();
            // 过滤原本就不完整的脸
            for (int i = 0; i < faceCount; i++) {
                float eyesDistance = faces[i].eyesDistance();
                faces[i].getMidPoint(pointF);
                if (pointF.x < eyesDistance                                                         // 左
                        || pointF.y < eyesDistance * 1.8f                                           // 上
                        || srcBitmap.getWidth() - pointF.x < eyesDistance                           // 右
                        || srcBitmap.getHeight() - pointF.y < eyesDistance * 1.8f) {                // 下
                    faces[i] = null;
                }
            }

            FrameLayout.LayoutParams photoParams = new FrameLayout.LayoutParams(mMyImageView.getLayoutParams());
            photoParams.gravity = Gravity.CENTER;
            photoParams.width = 1000;
            photoParams.height = 1000;
            mMyImageView.setLayoutParams(photoParams);
            mMyImageView.setImageBitmap(srcBitmap, faces, 1f);
        }


    }

}
