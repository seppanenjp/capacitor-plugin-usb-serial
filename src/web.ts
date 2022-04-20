import { WebPlugin } from '@capacitor/core';

import type { UsbSerialOptions, UsbSerialPlugin, } from './definitions';

export class UsbSerialWeb extends WebPlugin implements UsbSerialPlugin {
  connectedDevices(): Promise<{ devices: [] }> {
    throw new Error('Method not implemented.');
  }
  openSerial(options: UsbSerialOptions): Promise<void> {
    throw new Error('Method not implemented: ' + JSON.stringify(options));
  }
  closeSerial(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  readSerial(): Promise<{ data: string; }> {
    throw new Error('Method not implemented.');
  }
  writeSerial(options: { data: string; }): Promise<void> {
    throw new Error('Method not implemented: ' + JSON.stringify(options));
  }
  addListener(eventName: 'log' | 'connected' | 'attached' | 'detached' | 'data' | 'error', listenerFunc: (data: any) => void) {
    listenerFunc({});
    return Promise.reject(`Method '${eventName}' not implemented.`) as any;
  }
}
