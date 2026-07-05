package tk.glucodata;

// Non-DexCom stub. main/SensorBluetooth instantiates AccuGattCallback inside a
// `BuildConfig.DexCom == 1` guard (never reached in nodex builds), and the shared
// GlucoseMeterGatt statically imports ManufacturerNameCharUUID, so the class and the
// constant must exist for the nodex variants to compile. Mirrors the nosi SiGattCallback
// / libreOld Libre3GattCallback stub pattern. Version tag 0x20 matches the dex build.
public class AccuGattCallback extends SuperGattCallback {
    static final String ManufacturerNameCharUUID = "00002a29-0000-1000-8000-00805f9b34fb";

    public AccuGattCallback(String SerialNumber, long dataptr) {
        super(SerialNumber, dataptr, 0x20);
    }
}
