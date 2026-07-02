package tk.glucodata.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tk.glucodata.Log

/**
 * Wear stub of the phone AlarmLaunchReceiver.
 *
 * Shared code (Notify) references the class and ACTION_LAUNCH_ALARM_ACTIVITY
 * to build the full-screen alarm launch intent. On the watch the full-screen
 * AlarmActivity is phone-only, so this is a no-op for now.
 */
class AlarmLaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO(wear): launch the watch alarm UI here.
        Log.i(LOG_ID, "wear AlarmLaunchReceiver received ${intent.action}")
    }

    companion object {
        private const val LOG_ID = "AlarmLaunchReceiver"
        const val ACTION_LAUNCH_ALARM_ACTIVITY = "tk.glucodata.action.LAUNCH_ALARM_ACTIVITY"
    }
}
