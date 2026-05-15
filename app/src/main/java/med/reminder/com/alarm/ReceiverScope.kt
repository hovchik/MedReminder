package med.reminder.com.alarm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Shared application-lifetime scope for BroadcastReceiver background work.
 * Avoids creating a new top-level Job per onReceive (which would leak until
 * the process dies). Each launched coroutine still has its own Job that is
 * cleaned up on completion.
 */
internal object ReceiverScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
