package com.gsy.facerecognition;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.gsy.facerecognition.view.MyImageView;

public class MainActivity extends AppCompatActivity {

    private MyImageView mMyImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        mMyImageView = (MyImageView) findViewById(R.id.main_image);
    }
}
