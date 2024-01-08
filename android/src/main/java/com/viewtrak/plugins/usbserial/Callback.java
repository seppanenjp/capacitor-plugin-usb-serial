package com.viewtrak.plugins.usbserial;


import android.hardware.usb.UsbDevice;

import com.hoho.android.usbserial.driver.UsbSerialPort;

public interface Callback {
    void log(String TAG, String text);


    void connected(UsbDevice device);

    void usbDeviceAttached(UsbDevice device);
    void usbDeviceDetached(UsbDevice device);

    void receivedData(String Data);

    void error(Error error);
}


