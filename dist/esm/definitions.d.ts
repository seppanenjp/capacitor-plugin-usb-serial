import { PluginListenerHandle } from '@capacitor/core';
export interface UsbSerialOptions {
    deviceId: number;
    portNum: number;
    baudRate?: number;
    dataBits?: number;
    stopBits?: number;
    parity?: number;
    dtr?: boolean;
    rts?: boolean;
}
export interface UsbSerialDevice {
    pid: number;
    vid: number;
    did: number;
}
export interface UsbSerialDeviceInfo {
    device: {
        productId: number;
        productName: string;
        vendorId: number;
        deviceId: number;
    };
    port: number;
    driver: string;
}
export interface UsbSerialPlugin {
    connectedDevices(): Promise<{
        devices: UsbSerialDeviceInfo[];
    }>;
    openSerial(options: UsbSerialOptions): Promise<void>;
    closeSerial(): Promise<void>;
    readSerial(): Promise<{
        data: string;
    }>;
    writeSerial(options: {
        data: string;
    }): Promise<void>;
    addListener(eventName: 'log', listenerFunc: (data: {
        text: string;
        tag: string;
    }) => void): Promise<PluginListenerHandle> & PluginListenerHandle;
    addListener(eventName: 'connected', listenerFunc: (data: UsbSerialDevice) => void): Promise<PluginListenerHandle> & PluginListenerHandle;
    addListener(eventName: 'attached', listenerFunc: (data: UsbSerialDevice) => void): Promise<PluginListenerHandle> & PluginListenerHandle;
    addListener(eventName: 'detached', listenerFunc: (data: UsbSerialDevice) => void): Promise<PluginListenerHandle> & PluginListenerHandle;
    addListener(eventName: 'data', listenerFunc: (data: {
        data: string;
    }) => void): Promise<PluginListenerHandle> & PluginListenerHandle;
    addListener(eventName: 'error', listenerFunc: (data: {
        error: string;
    }) => void): Promise<PluginListenerHandle> & PluginListenerHandle;
}
