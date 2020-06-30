package org.tensorflow.lite.examples.classification;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class SplashActivity extends Activity {
    private TextView version;
    private Button RunDetector;
    private Button Dictionary;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        version= findViewById(R.id.tv_version);
        RunDetector=findViewById(R.id.detect_button);
        Dictionary=findViewById(R.id.dictionary);
        version.setText("Version_3.1  @Copyright Shu Botao ZJU");
        //设置全屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //handler.sendEmptyMessageDelayed(0,2000);
        RunDetector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(SplashActivity.this,ClassifierActivity.class);
                startActivity(intent);
            }
        });
        Dictionary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(SplashActivity.this,DictionaryActivity.class);
                startActivity(intent);
            }
        });
    }

//    private Handler handler=new Handler(){
//        @Override
//        public void handleMessage(Message msg){            //实现页面的跳转
//            Intent intent=new Intent(SplashActivity.this,ClassifierActivity.class);
//            startActivity(intent);
//            finish();
//            super.handleMessage(msg);
//        }
//    };
}