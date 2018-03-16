package com.youtu.djf.utusbreader;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class USBReaderService extends Service {

    private static final String TAG = "USBReaderService";
    private PendingIntent mPermissionIntent;
    public static final String ACTION_DEVICE_PERMISSION = "com.youtu.djf.usbdevice.USB_PERMISSION";
    private UsbManager manager;
    private Thread thread;
    private volatile UsbDeviceConnection mDeviceConnection;
    private UsbInterface mInterface;

    public USBReaderService() {
    }

    private static ServiceConnection con;
    private static Context mcontext;
    private static boolean stopReading;

    public static void bindService(Context context, final ServiceListener listener) {
        Log.d(TAG, "bindService: ");
        mcontext = context;
        con = new ServiceConnection() {
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
                USBReaderService service = ((USBReaderService.USBReaderBinder) binder)
                        .getService();
                Log.d(TAG, "onServiceConnected: " + service.toString());
                if (service != null) {
                    service.setOnServiceListener(listener);
                    service.getDevice();
                }
            }
        };
        if (context != null && listener != null) {
            context.bindService(new Intent(context, USBReaderService.class),
                    con, Service.BIND_AUTO_CREATE);
        } else {
            throw new NumberFormatException();
        }
    }

    public static void unbindService() {
        Log.d(TAG, "unbindService: ");
        if (mcontext != null && con != null) {
            mcontext.unbindService(con);
        }

    }

    private ExecutorService cachedThreadPool;

    @Override
    public void onCreate() {
        super.onCreate();
        stopReading = false;
        Log.d(TAG, "onCreate: " + this.toString());
        cachedThreadPool = Executors.newCachedThreadPool();
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

//        getDevice(manager);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: ");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {//
        stopReading = true;
        if (mDeviceConnection != null) {
            if (mInterface != null)
                mDeviceConnection.releaseInterface(mInterface);
            mDeviceConnection.close();
        }

        cachedThreadPool.shutdownNow();
        unregisterReceiver(mUsbPermissionReceiver);
        unregisterReceiver(mUsbReceiver);
        mcontext = null;
        con = null;
        Log.d(TAG, "onDestroy:读卡服务关闭 " + this.toString());
        super.onDestroy();
        ShowToListener(SERVICE_DESTROY, "读卡服务关闭");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new USBReaderBinder();
    }

    public class USBReaderBinder extends Binder {
        public USBReaderService getService() {
            return USBReaderService.this;
        }
    }

    /**
     * 获取usb设备
     */
    public void getDevice() {
        if (manager != null) {
            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
            for (String s : deviceList.keySet()) {
                UsbDevice usbDevice = deviceList.get(s);
                Log.d(TAG, "getDevice: "+ usbDevice.toString());
                //获取设备接口
                if (usbDevice.getVendorId() == 5050 && usbDevice.getProductId() == 24) {
                    Log.d(TAG, "getDevice: 发现设备");
                    ShowToListener(GOT_DEVICE, "找到指定设备");
                    // 判断是否有权限
                    if (manager.hasPermission(usbDevice)) {
                        Log.d(TAG, "getDevice:  拥有权限");
                        ShowToListener(HAS_PERMISSION, "拥有设备访问权限");
                        getUsbInterface(usbDevice);
                    } else {
                        Log.d(TAG, "getDevice:  没有权限,准备申请权限");
                        ShowToListener(HAS_NO_PERSSION, "没有设备访问权限");
                        manager.requestPermission(usbDevice, mPermissionIntent);
                    }
                    break;
                }
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
//        for (int i = 0; i < mUsbDevice.getInterfaceCount(); i++) {
//            Log.e(TAG, "getUsbInterface: 接口 " +i+"  "+ mUsbDevice
//                    .getInterface(i).toString());
//        }
        // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
        mDeviceConnection = manager.openDevice(mUsbDevice);
        if (mDeviceConnection == null) {
            Log.e(TAG, "getUsbInterface: 设备连接失败");
            ShowToListener(DEVICE_CONNECT_FAILED, "设备连接失败");
            return;
        }

        mInterface = mUsbDevice.getInterface(0);
        for (int i = 0; i < mInterface.getEndpointCount(); i++) {
            Log.e(TAG, "getUsbInterface: 端点 " + i + "  " + mInterface.getEndpoint(i).toString());
        }
        //用UsbDeviceConnection 与 UsbInterface 进行端点设置和通讯
        UsbEndpoint usbEpIn = mInterface.getEndpoint(0);
        if (mDeviceConnection.claimInterface(mInterface, true)) {
            Log.d(TAG, "getUsbInterface: 找到设备数据接口");
            ShowToListener(GOT_DEVICE_DATA_INTER, "找到设备数据接口");
            readFromUsb(usbEpIn, mDeviceConnection);
        } else {
            Log.d(TAG, "getUsbInterface:  找不到设备数据接口");
            ShowToListener(GOT_DEVICE_DATA_INTER_NULL, "找不到设备数据接口");
            if (mDeviceConnection != null)
                mDeviceConnection.close();
        }
    }

    private void readFromUsb(UsbEndpoint usbEpIn, final UsbDeviceConnection mDeviceConnection) {
        Log.e(TAG, "readFromUsb: 准备读取数据");
        ShowToListener(READY_FOR_DEVICE_DATA, "设备数据获取准备就绪");
        final int inMax = usbEpIn.getMaxPacketSize();
        final UsbRequest request = new UsbRequest();
        request.initialize(mDeviceConnection, usbEpIn);
        final ByteBuffer buffer = ByteBuffer.allocate(inMax);
        final ArrayList<Byte> ids = new ArrayList<>();
        //                                if (sb.length() >= 11) {
//                                    sb.delete(0, sb.length());
//                                    buffer.clear();
//                                }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (true) {
                    synchronized (this) {
                        if (stopReading) {
                            break;
                        }
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
                                                sb2.toString().length() + "  " + Thread
                                                .currentThread()
                                                .getName());

                                        ShowToListener(GET_CARDID, sb2.toString());

                                        ids.clear();
                                    }
                                }
//                                if (sb.length() >= 11) {
//                                    sb.delete(0, sb.length());
//                                    buffer.clear();
//                                }
                            }
                        }
                    }
                }

                mDeviceConnection.close();
                Log.d(TAG, "run: 数据读取线程结束" + "  " + Thread.currentThread()
                        .getName());

            }
        };
        thread = new Thread(runnable);
        cachedThreadPool.execute(thread);
    }

    private ServiceListener mListener;

    public interface ServiceListener {
        void onServiceListen(int flag, String msg);
    }

    public void setOnServiceListener(ServiceListener lis) {
        this.mListener = lis;
    }

    /**
     * 读卡器获取到手环id
     */
    public static int GET_CARDID = 10,
    /**
     * 找到读卡器设备
     */
    GOT_DEVICE = 11,
    /**
     * 拥有权限
     */
    HAS_PERMISSION = 12,
    /**
     * 没有权限
     */
    HAS_NO_PERSSION = 13,
    /**
     * 设备接入
     */
    DEVICE_ATTACHED = 14,
    /**
     * 设备移除
     */
    DEVICE_DETACHED = 15,
    /**
     * 获取权限成功
     */
    PERMISSION_GRANTED = 16,

    /**
     * 获取权限失败
     */
    PERMISSION_UNGRANTED = 17,
    /**
     * 设备连接失败
     */
    DEVICE_CONNECT_FAILED = 18,
    /**
     * 获取设备数据接口成功
     */
    GOT_DEVICE_DATA_INTER = 19,
    /**
     * 获取设备数据接口失败
     */
    GOT_DEVICE_DATA_INTER_NULL = 20,
    /**
     * 设备数据获取准备就绪
     */
    READY_FOR_DEVICE_DATA = 21,
    /**
     * 读卡器服务关闭
     */
    SERVICE_DESTROY = 22;
    ;

    /**
     * 显示监听信息
     *
     * @param flag
     * @param msg
     */
    private void ShowToListener(int flag, String msg) {
        if (mListener != null) {
            mListener.onServiceListen(flag, msg);
        }
    }

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
                            ShowToListener(PERMISSION_GRANTED, "获取设备访问权限成功");
                            getUsbInterface(device);
                        }
                    } else {
                        Log.e(TAG, "onReceive:usb EXTRA_PERMISSION_GRANTED null!!!");
                        ShowToListener(PERMISSION_UNGRANTED, "获取设备访问权限失败");

                    }
                }
            }
        }
    }

    /**
     * usb设备插拔广播
     */
    private UsbBroadcastReceiver mUsbReceiver;

    private class UsbBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.e(TAG, "onReceive: ACTION_USB_DEVICE_ATTACHED" + intent.toString());
                if (device != null) {
                    Log.e(TAG, "onReceive: 设备接入" + device.toString());
                    ShowToListener(DEVICE_ATTACHED, "USB设备接入");
                    getDevice();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.e(TAG, "onReceive:ACTION_USB_DEVICE_DETACHED\n");
                if (device != null && device.getProductId() == 24 && device.getVendorId()
                        == 5050) {
                    Log.e(TAG, "onReceive: 设备移除" + device.toString());
                    ShowToListener(DEVICE_DETACHED, "USB设备移除");
                    if (cachedThreadPool != null) {
                        cachedThreadPool.shutdownNow();
                    }
                }
            }
        }
    }
}
