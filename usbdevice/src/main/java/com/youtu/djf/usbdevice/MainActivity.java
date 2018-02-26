package com.youtu.djf.usbdevice;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private PendingIntent mPermissionIntent;
    public static final String ACTION_DEVICE_PERMISSION = "com.youtu.djf.usbdevice.USB_PERMISSION";
    private UsbManager manager;
    private Thread mThread;
    private TextView tv_id;
    private Button bt_id, bt_id2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e(TAG, "onCreate:-------------------------------- " + this.toString());

        if (!isTaskRoot()) {
            finish();
            return;
        }
        initView();


//        startUSBReader();

    }

    private void startUSBReader() {
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        mUsbReceiver = new UsbBroadcastReceiver();
        registerReceiver(mUsbReceiver, usbFilter);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent
                (ACTION_DEVICE_PERMISSION), 0);
        IntentFilter permissionFilter = new IntentFilter(ACTION_DEVICE_PERMISSION);
        mUsbPermissionReceiver = new UsbPermissionBroadcastReceiver();
        registerReceiver(mUsbPermissionReceiver, permissionFilter);
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        getDevice(manager);
    }

    /**
     * 获取usb设备
     *
     * @param manager
     */
    private void getDevice(UsbManager manager) {
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        for (String s : deviceList.keySet()) {
            UsbDevice usbDevice = deviceList.get(s);
            Log.e(TAG, "UsbDevice: " + usbDevice.toString());
            //获取设备接口
            if (usbDevice.getVendorId() == 5050 && usbDevice.getProductId() == 24) {
                Log.e(TAG, "onCreate: 发现设备");
                // 判断是否有权限
                if (manager.hasPermission(usbDevice)) {
                    Log.e(TAG, "onCreate: 拥有权限");
                    getUsbInterface(usbDevice);
                } else {
                    Log.e(TAG, "onCreate: 没有权限,准备申请权限");
                    manager.requestPermission(usbDevice, mPermissionIntent);
                }
                break;
            }
        }
    }

    /**
     * 获取设备接口
     *
     * @param mUsbDevice
     */
    private void getUsbInterface(UsbDevice mUsbDevice) {
        Log.e(TAG, "getUsbInterface:++++++++++++++++++++++++++++++++++++++ ");
        Log.e(TAG, "getUsbInterface: 获取设备数据接口：设备" + mUsbDevice.toString());
//
//        for (int i = 0; i < mUsbDevice.getInterfaceCount(); i++) {
//            Log.e(TAG, "getUsbInterface: 接口 " +i+"  "+ mUsbDevice
//                    .getInterface(i).toString());
//        }
        // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
        UsbDeviceConnection mDeviceConnection = manager.openDevice(mUsbDevice);
        if (mDeviceConnection == null) {
            return;
        }

        UsbInterface mInterface = mUsbDevice.getInterface(0);
        for (int i = 0; i < mInterface.getEndpointCount(); i++) {
            Log.e(TAG, "getUsbInterface: 端点 " + i + "  " + mInterface.getEndpoint(i).toString());
        }
        //用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
        UsbEndpoint usbEpIn = mInterface.getEndpoint(0);
        if (mDeviceConnection.claimInterface(mInterface, true)) {
            Log.e(TAG, "onCreate: 找到设备接口");
            readFromUsb(usbEpIn, mDeviceConnection);
        } else {
            mDeviceConnection.close();
        }
    }

    private void readFromUsb(UsbEndpoint usbEpIn, final UsbDeviceConnection mDeviceConnection) {
        Log.e(TAG, "readFromUsb: 准备读取数据");
        final int inMax = usbEpIn.getMaxPacketSize();
        final UsbRequest request = new UsbRequest();
        request.initialize(mDeviceConnection, usbEpIn);
        final ByteBuffer buffer = ByteBuffer.allocate(inMax);
        final ArrayList<Byte> ids = new ArrayList<>();
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
                                for (int i = 0; i < data.length; i++) {
                                    if (data[i] == 0)
                                        continue;
                                    else if (data[i] != 40)
                                        ids.add(data[i]);
                                    else {
                                        final StringBuilder sb = new StringBuilder();
                                        final StringBuilder sb2 = new StringBuilder();
                                        for (int j = 0; j < ids.size(); j++) {
                                            sb.append(ids.get(j) + " ");
                                            sb2.append(EncodeByte2Integer.getInteger(ids.get(j)));
                                        }
                                        sb.append(40);
                                        Log.e(TAG, "readFromUsb: 收到数据：" + sb.toString() + "   " +
                                                "长度：" + (ids.size() + 1)
                                                + "  格式化：" + sb2.toString() + "   长度：" +
                                                sb2.toString().length());
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                tv_id.setText("卡片原始数据:" + sb.toString() + "  " +
                                                        "格式化后ID：" + sb2.toString());

                                            }
                                        });
                                        ids.clear();
                                    }
                                }
//                                if (sb.length() >= 11) {
//                                    sb.delete(0, sb.length());
//                                    buffer.clear();
//                                }
                            }
                        } else {
                            Log.e(TAG, "run: 数据读取结束");
                            mDeviceConnection.close();
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
        Log.e(TAG, "onDestroy: --------------------------------" + this.toString() + "  " +
                getIntent().toString());
        if (mUsbReceiver != null)
            unregisterReceiver(mUsbReceiver);
        if (mUsbPermissionReceiver != null)
            unregisterReceiver(mUsbPermissionReceiver);
        if (mThread != null && mThread.isAlive()) {
            mThread.stop();
        }
    }

    /**
     * usb设备插拔广播
     */
    private UsbBroadcastReceiver mUsbReceiver;

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_id:
                startActivity(new Intent(this, Main3Activity.class));
                break;
            case R.id.bt_id2:
                startActivity(new Intent(this, Main4Activity.class));
                break;
        }
    }

    private class UsbBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.e(TAG, "onReceive: ACTION_USB_DEVICE_ATTACHED" + intent.toString());
                if (device != null) {
                    Log.e(TAG, "onReceive: 设备接入" + device.toString());
                    getDevice(manager);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.e(TAG, "onReceive:ACTION_USB_DEVICE_DETACHED\n");
                if (device != null) {
                    Log.e(TAG, "onReceive: 设备移除" + device.toString());
                    if (mThread != null && mThread.isAlive()) {
                        mThread.stop();
                    }
                }
            }
        }
    }

    ;
    /**
     * 权限申请广播
     */
    private UsbPermissionBroadcastReceiver mUsbPermissionReceiver;

    private class UsbPermissionBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_DEVICE_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null && device.getProductId() == 24 && device.getVendorId()
                                == 5050) {
                            Log.e(TAG, "onReceive: usb EXTRA_PERMISSION_GRANTED 获取权限成功" + device
                                    .toString());
                            getUsbInterface(device);
                        }
                    } else {
                        Log.e(TAG, "onReceive:usb EXTRA_PERMISSION_GRANTED null!!!");
                    }
                }
            }
        }
    }

    ;

    private void initView() {
        tv_id = (TextView) findViewById(R.id.tv_id);
        bt_id = (Button) findViewById(R.id.bt_id);
        bt_id.setOnClickListener(this);
        bt_id2 = (Button) findViewById(R.id.bt_id2);
        bt_id2.setOnClickListener(this);
    }
}
