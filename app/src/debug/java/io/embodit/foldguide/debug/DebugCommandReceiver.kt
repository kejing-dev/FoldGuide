package io.embodit.foldguide.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.embodit.foldguide.platform.FoldLog

/**
 * Debug builds only. Forwards adb broadcasts to the running Stage (action `io.embodit.foldguide.DEBUG`);
 * see [DebugCommands] for the command list.
 *
 *   adb -s emulator-5562 shell "am broadcast -a io.embodit.foldguide.DEBUG -p io.embodit.foldguide --es cmd 'state S12'"
 *
 * Extras: `cmd` = command name with its arguments after it, or `a` = comma-separated numbers and `s` = text.
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val a = intent.getStringExtra("a")
        val s = intent.getStringExtra("s")
        FoldLog.i("CMD recv '$cmd' a='$a' s='$s'")
        DebugCommands.run(context.applicationContext, cmd, a, s)
    }
}
