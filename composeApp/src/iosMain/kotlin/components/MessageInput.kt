package components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import keyboardUtil.hideKeyboard
import keyboardUtil.onDoneHideKeyboardAction
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.posix.memcpy
import tts.TtsSettings
import tts.getTtsService

@OptIn(ExperimentalForeignApi::class)
private fun nsDataToByteArray(nsData: NSData): ByteArray {
    val length = nsData.length.toInt()
    val bytes = ByteArray(length)
    if (length > 0 && nsData.bytes != null) {
        bytes.usePinned { pinned ->
            // addressOf usage is inside this opted-in helper
            memcpy(pinned.addressOf(0), nsData.bytes, length.convert())
        }
    }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MessageInput(
    enabled: Boolean,
    onSendMessage: (prompt: String, image: ByteArray?) -> Unit,
    modifier: Modifier
) {
    var userMessage by rememberSaveable { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<ByteArray?>(null) }

    // Observe ImagePicked notifications from Swift side
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "ImagePicked",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { note ->
            val userInfo = note?.userInfo
            // Expect NSData under "data"
            val nsData = userInfo?.get("data") as? NSData
            nsData?.let {
                // use the opted-in helper to convert
                val bytes = nsDataToByteArray(it)
                selectedImage = bytes
            }
        }

        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Column {
            // Simple attached image indicator
            selectedImage?.let {
                Box(Modifier.size(96.dp).padding(vertical = 4.dp, horizontal = 16.dp)) {
                    Text("Attached image", modifier = Modifier.align(Alignment.Center))
                    IconButton(
                        onClick = { selectedImage = null },
                        modifier = Modifier.size(48.dp).align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Rounded.Cancel, "Remove attached Image File")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = userMessage,
                    label = { Text("Talk to AI...") },
                    onValueChange = { userMessage = it },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = onDoneHideKeyboardAction(onDone = {
                        // submission handled by trailing icon
                    }),
                    leadingIcon = if (selectedImage == null) {
                        {
                            IconButton(onClick = {
                                // Request native Swift side to show photo picker
                                NSNotificationCenter.defaultCenter.postNotificationName("RequestImagePick", null, null)
                            }) {
                                Icon(Icons.Rounded.AttachFile, "Attach Image File")
                            }
                        }
                    } else {
                        null
                    },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                TtsSettings.enabled = !TtsSettings.enabled
                                if (!TtsSettings.enabled) try { getTtsService().stop() } catch (_: Throwable) {}
                            }) {
                                Icon(Icons.Default.AutoAwesome, "Toggle TTS")
                            }

                            IconButton(
                                enabled = enabled,
                                onClick = {
                                    if (userMessage.isNotBlank() || selectedImage != null) {
                                        onSendMessage(userMessage, selectedImage)
                                        userMessage = ""
                                        selectedImage = null
                                        hideKeyboard()
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send message",
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
