package com.youtu.djf.usbdevice;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private UsbInterface mInterface;
    private UsbEndpoint usbEpIn;
    private UsbDeviceConnection mDeviceConnection;
    private UsbDevice mUsbDevice;
    private PendingIntent mPermissionIntent;
    public static final String ACTION_DEVICE_PERMISSION = "com.youtu.djf.usbdevice.USB_PERMISSION";
    private UsbManager manager;
    private Thread mThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, usbFilter);

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent
                (ACTION_DEVICE_PERMISSION), 0);
        IntentFilter permissionFilter = new IntentFilter(ACTION_DEVICE_PERMISSION);
        registerReceiver(mUsbPermissionReceiver, permissionFilter);
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        getDevice();

    }

    private void getDevice() {
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        for (String s : deviceList.keySet()) {
            mUsbDevice = deviceList.get(s);
            Log.e(TAG, "UsbDevice: " + mUsbDevice.toString());
            //获取设备接口
            if (mUsbDevice.getVendorId() == 5050 && mUsbDevice.getProductId() == 24) {
                Log.e(TAG, "onCreate: 发现设备");
                // 判断是否有权限
                if (manager.hasPermission(mUsbDevice)) {
                    Log.e(TAG, "onCreate: 拥有权限");
                    getUsbInterface();
                } else {
                    Log.e(TAG, "onCreate: 没有权限,准备申请权限");
                    manager.requestPermission(mUsbDevice, mPermissionIntent);
                }
                break;
            }
        }
    }

    private void getUsbInterface() {
        Log.e(TAG, "getUsbInterface: 获取数据接口");
        mInterface = mUsbDevice.getInterface(0);
        //用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
        usbEpIn = mInterface.getEndpoint(0);
        // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
        mDeviceConnection = manager.openDevice(mUsbDevice);
        if (mDeviceConnection == null) {
            return;
        }
        if (mDeviceConnection.claimInterface(mInterface, true)) {
            Log.e(TAG, "onCreate: 找到设备接口");
            readFromUsb();
        } else {
            mDeviceConnection.close();
        }
    }

    private void readFromUsb() {
        Log.e(TAG, "readFromUsb: 准备读取数据");
        final int inMax = usbEpIn.getMaxPacketSize();
        final UsbRequest request = new UsbRequest();
        request.initialize(mDeviceConnection, usbEpIn);
        final ByteBuffer buffer = ByteBuffer.allocate(inMax);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (true) {
                    synchronized (this) {
                        request.queue(buffer, inMax);
                        UsbRequest usbRequest = mDeviceConnection.requestWait();
                        if (usbRequest != null) {
                            if (usbRequest.equals(request)) {
                                byte[] data = buffer.array();
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < data.length; i++) {
                                    sb.append(data[i] + "  ");
                                }
                                Log.e(TAG, "readFromUsb: 收到数据：" + sb.toString());
                            }
                        } else {
                            Log.e(TAG, "run: 数据读取结束" );
                            break;
                        }
                    }
                }

            }
        };
        mThread = new Thread(runnable);
        mThread.start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
        unregisterReceiver(mUsbPermissionReceiver);
        if (mThread != null && mThread.isAlive()) {
            mThread.stop();
        }
    }

    /**
     * usb设备插拔广播
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.e(TAG, "onReceive: ACTION_USB_DEVICE_ATTACHED" + intent.toString());
                if (device!=null){
                    getDevice();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.e(TAG, "onReceive:ACTION_USB_DEVICE_DETACHED\n");
                if (device != null) {
                    Log.e(TAG, "onReceive: 设备" + device.toString() + "移除");
                }
            }
        }
    };
    /**
     * 权限申请广播
     */
    private final BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_DEVICE_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.e(TAG, "onReceive: usb EXTRA_PERMISSION_GRANTED 获取权限成功");
                            getUsbInterface();
                        }
                    } else {
                        Log.e(TAG, "onReceive:usb EXTRA_PERMISSION_GRANTED null!!!");
                    }
                }
            }
        }
    };
}
