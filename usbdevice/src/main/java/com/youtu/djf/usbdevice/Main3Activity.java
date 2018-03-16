package com.youtu.djf.usbdevice;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.youtu.djf.utusbreader.USBReaderService;

public class Main3Activity extends AppCompatActivity implements USBReaderService.ServiceListener {
    private static final String TAG = "Main3Activity";
    ServiceConnection conn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        Intent intent = new Intent(Main3Activity.this, USBReaderService.class);
        conn = new ServiceConnection() {
            /**
             * 当启动源跟Service的连接意外丢失的时候会调用这个方法
             * ，比如Service崩溃了或者被强行杀死了
             */
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected: ");
            }

            /**
             * 当启动源跟Service成功连接之后将会调用这个方法
             */
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                USBReaderService service = ((USBReaderService.USBReaderBinder) binder).getService();
                if (service != null) {
                    service.setOnServiceListener(Main3Activity.this);
                    service.getDevice();
                }
            }
        };
        bindService(intent, conn, Service.BIND_AUTO_CREATE);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(conn);
    }

    @Override
    public void onServiceListen(int flag, String msg) {
        Log.d(TAG, "onServiceListen: " + flag + "   " + msg + "  " + Thread.currentThread()
                .getName());
    }
}
