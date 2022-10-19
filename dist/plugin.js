var capacitorUsbSerial = (function (exports, core) {
    'use strict';

    const UsbSerial = core.registerPlugin('UsbSerial', {
        web: () => Promise.resolve().then(function () { return web; }).then(m => new m.UsbSerialWeb()),
    });

    class UsbSerialWeb extends core.WebPlugin {
        connectedDevices() {
            throw new Error('Method not implemented.');
        }
        openSerial(options) {
            throw new Error('Method not implemented: ' + JSON.stringify(options));
        }
        closeSerial() {
            throw new Error('Method not implemented.');
        }
        readSerial() {
            throw new Error('Method not implemented.');
        }
        writeSerial(options) {
            throw new Error('Method not implemented: ' + JSON.stringify(options));
        }
        addListener(eventName, listenerFunc) {
            listenerFunc({});
            return Promise.reject(`Method '${eventName}' not implemented.`);
        }
    }

    var web = /*#__PURE__*/Object.freeze({
        __proto__: null,
        UsbSerialWeb: UsbSerialWeb
    });

    exports.UsbSerial = UsbSerial;

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map
