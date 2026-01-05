# Fix for Meditate Tab Crash

## Problem
The app crashes when typing "meditate" in the Gemini message bar on the meditate tab.

## iOS Changes Made
- Added error handler in `ContentView.swift` to catch and display Compose errors
- The error will now show a snackbar instead of crashing silently

## Kotlin/Compose Fixes Needed

### 1. Check MeditationScreen.kt (or similar file)

**Look for the text field/chat input and ensure it has proper state handling:**

```kotlin
// BEFORE (likely causing crash):
var userInput by remember { mutableStateOf("") }

OutlinedTextField(
    value = userInput,
    onValueChange = { userInput = it },
    // ... other params
)

// AFTER (add null safety and error handling):
var userInput by remember { mutableStateOf("") }
var isProcessing by remember { mutableStateOf(false) }

OutlinedTextField(
    value = userInput,
    onValueChange = { newValue ->
        try {
            if (!isProcessing) {
                userInput = newValue
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Post error to iOS
            PlatformBridge.postError("TextField error", e.message ?: "Unknown error")
        }
    },
    enabled = !isProcessing,
    // ... other params
)
```

### 2. Check Gemini API Integration

**Look for where messages are sent to Gemini:**

```kotlin
// BEFORE (likely crashing on null or unexpected response):
fun sendMessage(text: String) {
    viewModelScope.launch {
        val response = geminiApi.sendMessage(text)
        messages.add(response)
    }
}

// AFTER (add error handling):
fun sendMessage(text: String) {
    viewModelScope.launch {
        try {
            isProcessing.value = true
            
            // Validate input
            if (text.isBlank()) {
                return@launch
            }
            
            // Add timeout
            withTimeout(30_000) {
                val response = geminiApi.sendMessage(text)
                if (response != null) {
                    messages.add(response)
                } else {
                    // Handle null response
                    PlatformBridge.postError("Empty response from Gemini", "")
                }
            }
        } catch (e: TimeoutCancellationException) {
            PlatformBridge.postError("Request timeout", "Gemini API took too long")
        } catch (e: Exception) {
            e.printStackTrace()
            PlatformBridge.postError("Send message failed", e.message ?: "Unknown error")
        } finally {
            isProcessing.value = false
        }
    }
}
```

### 3. Add PlatformBridge Error Method

**In your `PlatformBridge.kt` or `PlatformIos.kt`:**

```kotlin
// In shared code (commonMain):
expect object PlatformBridge {
    fun postError(error: String, details: String)
    // ... other methods
}

// In iOS implementation (iosMain):
actual object PlatformBridge {
    actual fun postError(error: String, details: String) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            name = "ComposeError",
            `object` = null,
            userInfo = mapOf(
                "error" to error,
                "details" to details
            )
        )
    }
    // ... other methods
}
```

### 4. Check ViewModel Initialization

**Ensure the meditation screen's ViewModel is properly initialized:**

```kotlin
// In MeditationScreen.kt or similar:

@Composable
fun MeditationScreen(
    viewModel: MeditationViewModel = remember { MeditationViewModel() }
) {
    // Make sure this doesn't crash if viewModel is null
    val uiState by viewModel.uiState.collectAsState()
    
    // Add loading state check
    if (uiState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    // Rest of your UI
}
```

### 5. Check for Keyword Triggers

**The word "meditate" might be triggering navigation logic:**

```kotlin
// Look for code like this and add error handling:
fun processUserInput(input: String) {
    try {
        when {
            input.contains("meditate", ignoreCase = true) -> {
                // This might be crashing
                navigationController?.navigate("meditation")
            }
            // ... other cases
        }
    } catch (e: Exception) {
        e.printStackTrace()
        PlatformBridge.postError("Navigation error", e.message ?: "")
    }
}
```

## Debugging Steps

1. **Enable Kotlin debugging:**
   - In Xcode, go to Debug > Attach to Process
   - Look for crash logs in Console.app
   - Filter by your app name

2. **Add extensive logging:**
   ```kotlin
   fun onTextFieldChange(newValue: String) {
       println("🔍 TextField changed: $newValue")
       try {
           // ... your code
           println("✅ TextField change successful")
       } catch (e: Exception) {
           println("❌ TextField change failed: ${e.message}")
           e.printStackTrace()
       }
   }
   ```

3. **Test in isolation:**
   - Try typing "meditate" in the Chat tab (if it has similar functionality)
   - Try typing other words in the meditate tab
   - This helps determine if it's the word "meditate" or the meditation screen specifically

## Quick Workaround

If you need a quick fix while investigating:

```kotlin
// In MeditationScreen.kt
OutlinedTextField(
    value = userInput,
    onValueChange = { newValue ->
        // Filter out the problematic word temporarily
        if (newValue.contains("meditate", ignoreCase = true)) {
            println("⚠️ Skipping 'meditate' keyword to prevent crash")
            return@OutlinedTextField
        }
        userInput = newValue
    }
)
```

## Common Crash Locations

Check these files in your `composeApp` module:
- `MeditationScreen.kt` or similar
- `ChatViewModel.kt` or `MeditationViewModel.kt`
- `GeminiApi.kt` or Gemini integration code
- `Navigation.kt` or route handling code
- Any file handling text input in the meditation tab

## Testing

After implementing fixes:
1. Clean and rebuild the project
2. Test typing "meditate" slowly (one letter at a time)
3. Test typing "meditate" quickly
4. Test on different iOS versions if possible
5. Check Xcode console for any warnings or errors

## Need More Help?

If the crash persists, collect this information:
1. Full crash log from Xcode
2. Console output when the crash occurs
3. The exact text that causes the crash
4. Any error messages shown

The iOS side now has error handling in place, so you should see error messages in the snackbar instead of crashes.
