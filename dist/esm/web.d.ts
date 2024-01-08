import type { PluginListenerHandle } from '@capacitor/core';
import { WebPlugin } from '@capacitor/core';
import type { UsbSerialOptions, UsbSerialPlugin } from './definitions';
export declare class UsbSerialWeb extends WebPlugin implements UsbSerialPlugin {
    connectedDevices(): Promise<{
        devices: [];
    }>;
    openSerial(options: UsbSerialOptions): Promise<void>;
    closeSerial(): Promise<void>;
    readSerial(): Promise<{
        data: string;
    }>;
    writeSerial(options: {
        data: string;
    }): Promise<void>;
    addListener(eventName: 'log' | 'connected' | 'attached' | 'detached' | 'data' | 'error', listenerFunc: (data: any) => void): Promise<PluginListenerHandle> & PluginListenerHandle;
}
