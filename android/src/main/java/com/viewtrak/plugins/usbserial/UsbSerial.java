package com.viewtrak.plugins.usbserial;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
//import com.google.common.util.concurrent.RateLimiter;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.Ch34xSerialDriver;
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver;
import com.hoho.android.usbserial.driver.FtdiSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.ProlificSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.viewtrak.plugins.usbserial.Utils.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.Error;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UsbSerial implements SerialInputOutputManager.Listener {
    private Context context;
    // call that will be used to send back usb device attached/detached event
    private PluginCall usbAttachedDetachedCall;
    // call that will be used to send back data to the capacitor app
    private PluginCall readCall;
    // activity reference from UsbSerialPlugin
//    private AppCompatActivity mActivity;
    // call that will have data to open connection
    private PluginCall openSerialCall;

    // usb permission tag name
    public static final String USB_PERMISSION = "com.viewtrak.plugins.usbserial.USB_PERMISSION";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;

    private enum UsbPermission {Unknown, Requested, Granted, Denied}

    // logging tag
    private final String TAG = UsbSerial.class.getSimpleName();

    private boolean sleepOnPause;
    // I/O manager to handle new incoming serial data
    private SerialInputOutputManager usbIoManager;
    // Default Usb permission state
    private UsbPermission usbPermission = UsbPermission.Unknown;
    // The serial port that will be used in this plugin
    private UsbSerialPort usbSerialPort;
    // Usb serial port connection status
    private boolean connected = false;
    // USB permission broadcastreceiver
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    String messageNMEA = "";


//    private RateLimiter throttle = RateLimiter.create(1.0);

    public UsbSerial(Context context) {
        super();
        this.context = context;

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (USB_PERMISSION.equals(action)) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    if (openSerialCall != null) {
                        openSerial(openSerialCall);
                        context.unregisterReceiver(this);
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra("device");
                    if (usbAttachedDetachedCall != null) {
                        JSObject jsObject = new JSObject();
                        usbAttachedDetachedCall.setKeepAlive(true);
                        jsObject.put("success", true);
                        jsObject.put("data", "NEW_USB_DEVICE_ATTACHED");
                        if (usbDevice != null) {
                            jsObject.put("pid", usbDevice.getProductId());
                            jsObject.put("vid", usbDevice.getVendorId());
                        }
                        usbAttachedDetachedCall.resolve(jsObject);
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    if (usbAttachedDetachedCall != null) {
                        JSObject jsObject = new JSObject();
                        usbAttachedDetachedCall.setKeepAlive(true);
                        jsObject.put("success", true);
                        jsObject.put("data", "USB_DEVICE_DETACHED");
                        usbAttachedDetachedCall.resolve(jsObject);
                    }
                }
            }
        };
        mainLooper = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> {
            updateReceivedData(data);
        });
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            updateReadDataError(e);
            disconnect();
        });
    }

    public void openSerial(PluginCall openSerialCall) {
        JSObject obj = new JSObject();
        try {
            disconnect();

            this.openSerialCall = openSerialCall;

            int deviceId = openSerialCall.hasOption("deviceId") ? openSerialCall.getInt("deviceId") : 0;
            int portNum = openSerialCall.hasOption("portNum") ? openSerialCall.getInt("portNum") : 0;
            int baudRate = openSerialCall.hasOption("baudRate") ? openSerialCall.getInt("baudRate") : 9600;
            int dataBits = openSerialCall.hasOption("dataBits") ? openSerialCall.getInt("dataBits") : UsbSerialPort.DATABITS_8;
            int stopBits = openSerialCall.hasOption("stopBits") ? openSerialCall.getInt("stopBits") : UsbSerialPort.STOPBITS_1;
            int parity = openSerialCall.hasOption("parity") ? openSerialCall.getInt("parity") : UsbSerialPort.PARITY_NONE;
            boolean setDTR = openSerialCall.hasOption("dtr") && openSerialCall.getBoolean("dtr");
            boolean setRTS = openSerialCall.hasOption("rts") && openSerialCall.getBoolean("rts");
            // Sleep On Pause defaults to true
            this.sleepOnPause = openSerialCall.hasOption("sleepOnPause") ? openSerialCall.getBoolean("sleepOnPause") : true;

            UsbDevice device = null;
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            for (UsbDevice v : usbManager.getDeviceList().values()) {
                if (v.getDeviceId() == deviceId)
                    device = v;
            }
            if (device == null) {
                obj.put("success", false);
                obj.put("error", new Error("connection failed: device not found", new Throwable("connectionFailed:DeviceNotFound")));
                this.openSerialCall.resolve(obj);
                return;
            }
            UsbSerialDriver driver = getProper().probeDevice(device);
            if (driver == null) {
                // tyring custom
                driver = getDriverClass(device);
            }
            if (driver == null) {
                obj.put("success", false);
                obj.put("error", new Error("connection failed: no driver for device", new Throwable("connectionFailed:NoDriverForDevice")));
                this.openSerialCall.resolve(obj);
                return;
            }
            if (driver.getPorts().size() < portNum) {
                obj.put("success", false);
                obj.put("error", new Error("connection failed: not enough ports at device", new Throwable("connectionFailed:NoAvailablePorts")));
                this.openSerialCall.resolve(obj);
                return;
            }
            usbSerialPort = driver.getPorts().get(portNum);
            UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
            if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
                usbPermission = UsbPermission.Requested;
                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(USB_PERMISSION), 0);
                context.registerReceiver(broadcastReceiver, new IntentFilter(USB_PERMISSION));
                usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
                return;
            }
            if (usbConnection == null) {
                if (!usbManager.hasPermission(driver.getDevice())) {
                    obj.put("success", false);
                    obj.put("error", new Error("connection failed: permission denied", new Throwable("connectionFailed:UsbConnectionPermissionDenied")));
                } else {
                    obj.put("success", false);
                    obj.put("error", new Error("connection failed: Serial open failed", new Throwable("connectionFailed:SerialOpenFailed")));
                }
                this.openSerialCall.resolve(obj);
                return;
            }
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, dataBits, stopBits, parity);
            if (setDTR) usbSerialPort.setDTR(true);
            if (setRTS) usbSerialPort.setRTS(true);
            obj.put("success", true);
            obj.put("data", "connection succeeded: Connection open");
            usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
            usbIoManager.start();
            connected = true;
        } catch (Exception exception) {
            obj.put("success", false);
            obj.put("error", new Error(exception.getMessage(), exception.getCause()));
            disconnect();
        }
        this.openSerialCall.resolve(obj);
    }

    public JSObject closeSerial() {
        JSObject jsObject = new JSObject();
        if (readCall != null) {
            jsObject.put("success", false);
            readCall.resolve();
        }
        // Make sure we don't die if we try to close an non-existing port!
        disconnect();
        jsObject.put("success", true);
        jsObject.put("data", "Connection Closed");
        return jsObject;
    }

    JSObject readSerial() {
        JSObject jsObject = new JSObject();
        if (!connected) {
            jsObject.put("error", new Error("not connected", new Throwable("NOT_CONNECTED")));
            jsObject.put("success", false);
            return jsObject;
        }
        try {
            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
            String str = HexDump.toHexString(Arrays.copyOf(buffer, len));
            str.concat("\n");
            jsObject.put("data", str);
            jsObject.put("success", true);
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            jsObject.put("success", false);
            jsObject.put("error", new Error("connection lost: " + e.getMessage(), e.getCause()));
            disconnect();
        }
        return jsObject;
    }

    JSObject writeSerial(String str) {
        JSObject jsObject = new JSObject();
        if (!connected) {
            jsObject.put("error", new Error("not connected", new Throwable("NOT_CONNECTED")));
            jsObject.put("success", false);
            return jsObject;
        }
        if (str.length() == 0) {
            jsObject.put("error", new Error("can't send empty string to device", new Throwable("EMPTY_STRING")));
            jsObject.put("success", false);
            return jsObject;
        }
        try {
            byte[] data = (str + "\r\n").getBytes();
            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
            jsObject.put("data", str);
            jsObject.put("success", true);
            return jsObject;
        } catch (Exception e) {
            jsObject.put("success", false);
            jsObject.put("error", new Error("connection lost: " + e.getMessage(), e.getCause()));
            disconnect();
            return jsObject;
        }
    }


    void onResume() {
        if (sleepOnPause) {
            if (usbPermission == UsbPermission.Unknown || usbPermission == UsbPermission.Granted)
                mainLooper.post(() -> {
                    openSerial(this.openSerialCall);
                });
        }
    }

    void onPause() {
        if (connected && sleepOnPause) {
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        if (usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        usbPermission = UsbPermission.Unknown;
        try {
            if (usbSerialPort != null)
                usbSerialPort.close();
        } catch (IOException ignored) {
        }
        usbSerialPort = null;
    }

    public JSObject readCall(PluginCall call) {
        JSObject jsObject = new JSObject();
        this.readCall = call;
        call.setKeepAlive(true);
        jsObject.put("success", true);
        jsObject.put("data", "REGISTERED".getBytes(Charset.defaultCharset()));
        return jsObject;
    }

    public JSObject usbAttachedDetached(PluginCall call) {
        JSObject jsObject = new JSObject();
        usbAttachedDetachedCall = call;
        call.setKeepAlive(true);
        context.registerReceiver(broadcastReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        context.registerReceiver(broadcastReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
        jsObject.put("success", true);
        jsObject.put("data", "REGISTERED");
        return jsObject;
    }

    private void updateReceivedData(byte[] data) {
        if (this.readCall != null) {
            JSObject jsObject = new JSObject();
            this.readCall.setKeepAlive(true);
            try {
                messageNMEA += new String(data);

                int eol = messageNMEA.indexOf(0x0a);
                if (-1 != eol) {
                    String sentence = messageNMEA.substring(0, eol + 1);
                    messageNMEA = messageNMEA.substring(eol + 1);

//                    Boolean allowed = throttle.tryAcquire();
//                    if (!allowed) {
//                        return;
//                    }

                    jsObject.put("data", sentence);
                    jsObject.put("success", true);
                } else if (messageNMEA.length() > 128) {
                    throw new Exception("invalid NMEA string");
                } else {
                    return;
                }
            } catch (Exception exception) {
                jsObject.put("error", new Error(exception.getMessage(), exception.getCause()));
                jsObject.put("success", false);
            }
            readCall.resolve(jsObject);
        }
    }

    private void updateReadDataError(Exception exception) {
        if (readCall != null) {
            JSObject jsObject = new JSObject();
            jsObject.put("error", new Error(exception.getMessage(), exception.getCause()));
            jsObject.put("success", false);
            readCall.resolve(jsObject);
        }
    }

    UsbSerialDriver getDriverClass(final UsbDevice usbDevice) {
        Class<? extends UsbSerialDriver> driverClass = null;

        final int vid = usbDevice.getVendorId();
        final int pid = usbDevice.getProductId();

        if (vid == 1027) {
            switch (pid) {
                case 24577:
                case 24592:
                case 24593:
                case 24596:
                case 24597:
                    driverClass = FtdiSerialDriver.class;
            }
        } else if (vid == 4292) {
            switch (pid) {
                case 60000:
                case 60016:
                case 60017:
                    driverClass = Cp21xxSerialDriver.class;
            }
        } else if (vid == 1659) {
            switch (pid) {
                case 8963:
                case 9123:
                case 9139:
                case 9155:
                case 9171:
                case 9187:
                case 9203:
                    driverClass = ProlificSerialDriver.class;
            }
        } else if (vid == 6790) {
            switch (pid) {
                case 21795:
                case 29987:
                    driverClass = Ch34xSerialDriver.class;
            }
        } else {
            if (vid == 9025 || vid == 5446 || vid == 3725
                    || (vid == 5824 && pid == 1155)
                    || (vid == 1003 && pid == 8260)
                    || (vid == 7855 && pid == 4)
                    || (vid == 3368 && pid == 516)
                    || (vid == 1155 && pid == 22336)
            )
                driverClass = CdcAcmSerialDriver.class;
        }

        if (driverClass != null) {
            final UsbSerialDriver driver;
            try {
                final Constructor<? extends UsbSerialDriver> ctor =
                        driverClass.getConstructor(UsbDevice.class);
                driver = ctor.newInstance(usbDevice);
            } catch (NoSuchMethodException | IllegalArgumentException | InstantiationException |
                    IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            return driver;
        }

        return null;
    }

    private UsbSerialProber getProper() {
        ProbeTable customTable = UsbSerialProber.getDefaultProbeTable();

        // 0x0403 / 0x60??: FTDI
        customTable.addProduct(1027, 24577, FtdiSerialDriver.class); // 0x6001: FT232R
        customTable.addProduct(1027, 24592, FtdiSerialDriver.class); // 0x6010: FT2232H
        customTable.addProduct(1027, 24593, FtdiSerialDriver.class); // 0x6011: FT4232H
        customTable.addProduct(1027, 24596, FtdiSerialDriver.class); // 0x6014: FT232H
        customTable.addProduct(1027, 24597, FtdiSerialDriver.class); // 0x6015: FT230X, FT231X, FT234XD

        // 0x10C4 / 0xEA??: Silabs CP210x
        customTable.addProduct(4292, 60000, Cp21xxSerialDriver.class); // 0xea60: CP2102 and other CP210x single port devices
        customTable.addProduct(4292, 60016, Cp21xxSerialDriver.class); // 0xea70: CP2105
        customTable.addProduct(4292, 60017, Cp21xxSerialDriver.class); // 0xea71: CP2108

        // 0x067B / 0x23?3: Prolific PL2303x
        customTable.addProduct(1659, 8963, ProlificSerialDriver.class); // 0x2303: PL2303HX, HXD, TA, ...
        customTable.addProduct(1659, 9123, ProlificSerialDriver.class); // 0x23a3: PL2303GC
        customTable.addProduct(1659, 9139, ProlificSerialDriver.class); // 0x23b3: PL2303GB
        customTable.addProduct(1659, 9155, ProlificSerialDriver.class); // 0x23c3: PL2303GT
        customTable.addProduct(1659, 9171, ProlificSerialDriver.class); // 0x23d3: PL2303GL
        customTable.addProduct(1659, 9187, ProlificSerialDriver.class); // 0x23e3: PL2303GE
        customTable.addProduct(1659, 9203, ProlificSerialDriver.class); // 0x23f3: PL2303GS

        // 0x1a86 / 0x?523: Qinheng CH34x
        customTable.addProduct(6790, 21795, Ch34xSerialDriver.class); // 0x5523: CH341A
        customTable.addProduct(6790, 29987, Ch34xSerialDriver.class); // 0x7523: CH340

        // CDC driver
        // customTable.addProduct(9025,      , driver)  // 0x2341 / ......: Arduino
        customTable.addProduct(5824, 1155, CdcAcmSerialDriver.class); // 0x16C0 / 0x0483: Teensyduino
        customTable.addProduct(1003, 8260, CdcAcmSerialDriver.class); // 0x03EB / 0x2044: Atmel Lufa
        customTable.addProduct(7855, 4, CdcAcmSerialDriver.class); // 0x1eaf / 0x0004: Leaflabs Maple
        customTable.addProduct(3368, 516, CdcAcmSerialDriver.class); // 0x0d28 / 0x0204: ARM mbed
        customTable.addProduct(1155, 22336, CdcAcmSerialDriver.class); // 0x0483 / 0x5740: ST CDC

        return new UsbSerialProber(customTable);
    }

    public JSObject devices() {
        JSObject jsObject = new JSObject();
        try {
            List<DeviceItem> listItems = new ArrayList();
            UsbSerialProber usbProber = getProper();
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                UsbSerialDriver driver = usbProber.probeDevice(device);
                if (driver != null) {
                    for (int port = 0; port < driver.getPorts().size(); port++)
                        listItems.add(new DeviceItem(device, port, driver));
                } else {
                    listItems.add(new DeviceItem(device, 0, getDriverClass(device)));
                }
            }
            JSONArray jsonArray = Utils.deviceListToJsonConvert(listItems);
            JSONObject data = new JSONObject();
            data.put("devices", jsonArray);
            jsObject.put("data", data);
            jsObject.put("success", true);
        } catch (Exception exception) {
            jsObject.put("error", new Error(exception.getMessage(), exception.getCause()));
        }
        return jsObject;
    }
}
