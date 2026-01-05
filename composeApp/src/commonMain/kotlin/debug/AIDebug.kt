package debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import service.GenerativeAiService

@Composable
fun AIDebug() {
    var output by remember { mutableStateOf<String?>(null) }
    val scope: CoroutineScope = MainScope()

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = {
            output = "Loading..."
            scope.launch {
                try {
                    val resp = GenerativeAiService.instance.getSuggestions(listOf("Give a one-line test suggestion."))
                    output = resp ?: "(no response)"
                } catch (e: Exception) {
                    output = "Error: ${e.message}"
                }
            }
        }) {
            Text("Test Gemini")
        }

        output?.let {
            Text(text = it, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
