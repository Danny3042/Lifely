package components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import keyboardUtil.hideKeyboard
import keyboardUtil.onDoneHideKeyboardAction
import tts.TtsSettings
import tts.getTtsService
import java.io.InputStream

@Composable
actual fun MessageInput(
    enabled: Boolean,
    onSendMessage: (prompt: String, image: ByteArray?) -> Unit,
    modifier: Modifier
) {
    var userMessage by rememberSaveable { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                inputStream?.use { stream ->
                    val bytes = stream.readBytes()
                    selectedImage = bytes
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    selectedImageBitmap = bitmap?.asImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Column {
            selectedImageBitmap?.let { image ->
                Box(Modifier.size(96.dp).padding(vertical = 4.dp, horizontal = 16.dp)) {
                    Image(
                        bitmap = image,
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    IconButton(
                        onClick = { selectedImage = null; selectedImageBitmap = null },
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
            ) {
                OutlinedTextField(
                    value = userMessage,
                    label = { Text("Talk to AI...") },
                    onValueChange = { userMessage = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = onDoneHideKeyboardAction(onDone = {
                        // submission handled by trailing icon; keep input cleared if desired
                    }),
                    leadingIcon = if (selectedImageBitmap == null) {
                        {
                            IconButton(onClick = { launcher.launch("image/*") }) {
                                Icon(Icons.Rounded.AttachFile, "Attach Image File")
                            }
                        }
                    } else {
                        null
                    },
                    trailingIcon = {
                        Row {
                            // TTS toggle button
                            IconButton(onClick = {
                                TtsSettings.enabled = !TtsSettings.enabled
                                if (!TtsSettings.enabled) try { getTtsService().stop() } catch (_: Throwable) {}
                            }) {
                                Icon(
                                    imageVector = if (TtsSettings.enabled) Icons.Default.AutoAwesome else Icons.Default.AutoAwesome,
                                    contentDescription = "Toggle TTS",
                                )
                            }

                            IconButton(
                                enabled = enabled,
                                onClick = {
                                    if (userMessage.isNotBlank() || selectedImage != null) {
                                        onSendMessage(userMessage, selectedImage)
                                        userMessage = ""
                                        selectedImage = null
                                        selectedImageBitmap = null
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
                    modifier = Modifier
                )
            }
        }
    }
}
