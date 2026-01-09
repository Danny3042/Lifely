package platform

/**
 * Register a listener for "ComposeRequestNewChat" events coming from the native host.
 * Returns a function that, when invoked, will unregister the listener.
 */
expect fun registerComposeNewChatListener(onNewChat: () -> Unit): () -> Unit

/**
 * Register a listener for "ComposeShowAddDialog" events coming from the native host.
 * When invoked, the listener should show the Compose add dialog (e.g. set showAddDialog = true).
 */
expect fun registerComposeShowAddDialogListener(onShow: () -> Unit): () -> Unit
