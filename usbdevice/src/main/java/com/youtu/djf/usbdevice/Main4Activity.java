package com.youtu.djf.usbdevice;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.youtu.djf.utusbreader.USBReaderService;

public class Main4Activity extends AppCompatActivity implements USBReaderService.ServiceListener {
    private static final String TAG = "Main4Activity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main4);

        USBReaderService.bindService(this, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        USBReaderService.unbindService();
    }

    @Override
    public void onServiceListen(int flag, String msg) {
        Log.d(TAG, "onServiceListen: " + flag + "   " + msg + "  " + Thread.currentThread()
                .getName());
    }
}
