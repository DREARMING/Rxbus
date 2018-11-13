package com.mvcoder.rxbusdemo;

import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.mvcoder.rxbus.Subscribe;
import com.mvcoder.rxbus.ThreadMode;

public abstract class BaseActivit extends AppCompatActivity {

    @Subscribe(code = 102, threadMode = ThreadMode.MAIN)
    public void superMethod(String msg){
        Log.d("Main", msg);
    }
}
