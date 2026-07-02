package tk.glucodata.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Wear stub of the phone FloatingGlucoseService.
 *
 * The floating on-screen glucose overlay is a phone-only feature (it depends on
 * the mobile-only overlay UI). Shared code (Applic) references this class to
 * build the start intent, guarded by the `floating_glucose_enabled` preference
 * which is off on the watch, so this service never actually starts here.
 */
class FloatingGlucoseService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
