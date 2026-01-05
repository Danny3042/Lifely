package platform

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

actual fun registerComposeNewChatListener(onNewChat: () -> Unit): () -> Unit {
    val name = "ComposeRequestNewChat"
    val observer = NSNotificationCenter.defaultCenter.addObserverForName(
        name = name,
        `object` = null,
        queue = NSOperationQueue.mainQueue
    ) { _ ->
        try {
            onNewChat()
        } catch (_: Throwable) {}
    }

    return {
        NSNotificationCenter.defaultCenter.removeObserver(observer)
    }
}
