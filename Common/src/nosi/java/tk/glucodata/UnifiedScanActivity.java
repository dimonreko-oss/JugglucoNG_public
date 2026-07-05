package tk.glucodata;

// Non-Sibionics stub. The shared setup UI (UnifiedQrScanLauncher) references the
// scan-intent extra keys below. The nosi variant never launches the Sibionics
// scanner (PhotoScan.createUnifiedScanIntent returns null here), so this only needs
// to satisfy compilation — it is intentionally not an Activity.
public final class UnifiedScanActivity {
    public static final String EXTRA_SCAN_TEXT = "tk.glucodata.extra.scan_text";
    public static final String EXTRA_SENSOR_PTR = "tk.glucodata.extra.sensor_ptr";
    public static final String EXTRA_SCAN_REQUEST = "tk.glucodata.extra.scan_request";
    public static final String EXTRA_SCAN_CONTEXT = "tk.glucodata.extra.scan_context";
    public static final String EXTRA_SCAN_TITLE = "tk.glucodata.extra.scan_title";

    private UnifiedScanActivity() {}
}
