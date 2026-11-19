package io.disruption.focal.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.disruption.focal.FocalApplication

/**
 * Re-applies running Focus Sessions after a reboot or an app update (T13). The process starts, so
 * `FocalApplication` has already wired Koin; this just asks it to restore blocking.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> (context.applicationContext as? FocalApplication)?.restoreSessions()
        }
    }
}
