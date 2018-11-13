package com.mvcoder.rxbusdemo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.mvcoder.rxbus.RxBus;
import com.mvcoder.rxbus.Subscribe;
import com.mvcoder.rxbus.ThreadMode;

public class MainActivity extends AppCompatActivity {

    private final String tag = MainActivity.class.getSimpleName();

    private Button btJoin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RxBus.getDefault().register(this);
        initView();
    }

    private void initView() {
        btJoin = findViewById(R.id.btJoin);
        btJoin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, RxbusPostActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RxBus.getDefault().unRegister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN, code = 100)
    public void receiveMsgInMainThread(String msg){
        Log.d(tag,  "code :" + 100 + " , msg : " + msg + " , thread : " + Thread.currentThread());
    }

    @Subscribe(threadMode = ThreadMode.IO, code = 101)
    public void receiveMsgInIOThread(String msg){
        Log.d(tag,  "code :" + 100 + " , msg : " + msg + " , thread : " + Thread.currentThread());
    }

    @Subscribe(threadMode = ThreadMode.MAIN, code = 100)
    public void receiveMsgInSameCodeDifferentType(Integer msg){
        Log.d(tag,  "code :" + 100 + " , msg : " + msg + " , thread : " + Thread.currentThread());
    }

}
