package tk.glucodata;

// Non-DexCom stub — see AccuGattCallback. main/SensorBluetooth instantiates
// DexGattCallback only inside a `BuildConfig.DexCom == 1` guard, so nodex builds
// never reach it, but the class must exist to compile. Version tag 0x40 matches dex.
public class DexGattCallback extends SuperGattCallback {
    public DexGattCallback(String SerialNumber, long dataptr) {
        super(SerialNumber, dataptr, 0x40);
    }
}
