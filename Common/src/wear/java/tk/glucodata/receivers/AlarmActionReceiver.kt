package tk.glucodata.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tk.glucodata.Log

/**
 * Wear stub of the phone AlarmActionReceiver.
 *
 * Only the action constants are referenced from shared code (Notify), which
 * builds the snooze/dismiss notification actions. The full snooze/dismiss
 * routing (SnoozeManager / CustomAlertManager) is phone-only for now; wiring
 * it to the shared alert engine on the watch is a follow-up. See the
 * mobile source set for the full implementation.
 */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO(wear): route ACTION_SNOOZE / ACTION_DISMISS into the shared
        // alert engine (SnoozeManager / AlertRepository live in main).
        Log.i(LOG_ID, "wear AlarmActionReceiver received ${intent.action}")
    }

    companion object {
        private const val LOG_ID = "AlarmActionReceiver"
        const val ACTION_SNOOZE = "tk.glucodata.ACTION_SNOOZE"
        const val ACTION_DISMISS = "tk.glucodata.ACTION_DISMISS"
        const val ACTION_IGNORE = "tk.glucodata.ACTION_IGNORE"
        const val EXTRA_ALERT_TYPE_ID = "alert_type_id"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }
}
