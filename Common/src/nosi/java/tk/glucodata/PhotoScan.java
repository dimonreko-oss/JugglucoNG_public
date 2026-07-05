package tk.glucodata;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;

public class PhotoScan {
public static void scan(Activity act, int type) { }
public static void scan(Activity act, int type, String title) { }
static void connectSensor(final String scantag) {}
public static void connectSensor(final String scantag, MainActivity act, int request, long sensorptr) {}
static boolean handleUnifiedScanResult(int resultCode, Intent data, MainActivity act, int type) { return false; }
public static Intent createUnifiedScanIntent(Context context, int type, long sensorptr) { return null; }
public static Intent createUnifiedScanIntent(Context context, int type, long sensorptr, String title) { return null; }
// The shared setup UI (InlineQrScannerCard/UnifiedQrScanLauncher/SibionicsSetupWizard)
// calls these; the non-Sibionics nosi variant only needs them to compile. Whitespace
// trimming is a generic scanner utility, the Sibionics-specific ones are inert here.
public static String trimOuterScannerWhitespace(String value) { return value == null ? null : value.trim(); }
public static String buildSibionics2TransmitterPayload(String input) { return input; }
public static void scanner(MainActivity act, int type, long sensorptr) {}
public static void scanner(MainActivity act, int type, long sensorptr, String title) {}
//static boolean zXingResult(int resultCode, Object data) {return false;}
};
