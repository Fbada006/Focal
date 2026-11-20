package io.disruption.focal.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.disruption.focal.FocalApplication

/**
 * Re-applies running Focus Sessions, catches Schedules up and re-arms the Schedule alarm after a
 * reboot or an app update (T13, T30). The process starts, so `FocalApplication` has already wired
 * Koin; this asks it to restore and holds the broadcast open until that finishes, so Android
 * doesn't kill the process mid-restore.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val restore = (context.applicationContext as? FocalApplication)?.restoreSessions() ?: return
                val pending = goAsync()
                restore.invokeOnCompletion { pending.finish() }
            }
        }
    }
}
