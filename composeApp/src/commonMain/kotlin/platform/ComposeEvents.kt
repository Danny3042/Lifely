package platform

/**
 * Register a listener for "ComposeRequestNewChat" events coming from the native host.
 * Returns a function that, when invoked, will unregister the listener.
 */
expect fun registerComposeNewChatListener(onNewChat: () -> Unit): () -> Unit

