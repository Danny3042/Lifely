package platform

actual fun registerComposeNewChatListener(onNewChat: () -> Unit): () -> Unit {
    // No-op on Android; we don't need a native host to trigger compose new chat here.
    return {}
}

actual fun registerComposeShowAddDialogListener(onShow: () -> Unit): () -> Unit {
    // No-op on Android; provided so common code can register without platform checks.
    return {}
}
