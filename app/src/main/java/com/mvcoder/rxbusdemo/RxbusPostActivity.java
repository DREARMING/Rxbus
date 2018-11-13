package com.mvcoder.rxbusdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.mvcoder.rxbus.RxBus;

public class RxbusPostActivity extends AppCompatActivity implements View.OnClickListener {

    private Button bt1;
    private Button bt2;
    private Button bt3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rxbus_post);
        initView();
    }

    private void initView() {
        bt1 = findViewById(R.id.bt1);
        bt2 = findViewById(R.id.bt2);
        bt3 = findViewById(R.id.bt3);
        bt1.setOnClickListener(this);
        bt2.setOnClickListener(this);
        bt3.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.bt1:
                RxBus.getDefault().post(100, "Hello Rxbus(100 - String type)");
                break;
            case R.id.bt2:
                RxBus.getDefault().post(101, "Hello Rxbus(101 - String type)");
                break;
            case R.id.bt3:
                RxBus.getDefault().post(100, 520);
                break;
        }
    }
}
