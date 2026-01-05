# Diagnostic Checklist for "Meditate" Crash

## Immediate Steps to Debug

### 1. Check Xcode Console Output

When the crash happens, look for these specific patterns:

```
❌ Fatal error patterns to look for:
- "Attempt to invoke virtual method"
- "NullPointerException"
- "IndexOutOfBoundsException"
- "kotlin.UninitializedPropertyAccessException"
- "IllegalStateException"
```

### 2. Enable Kotlin Exception Breakpoints

In Xcode:
1. Open the Breakpoint Navigator (⌘8)
2. Click "+" at bottom left
3. Select "Exception Breakpoint"
4. Set for "All Kotlin Exceptions"

### 3. Test Variations

Try these one by one to narrow down the issue:

| Test Case | Expected Result | Notes |
|-----------|-----------------|-------|
| Type "med" | Should work | Tests partial match |
| Type "MEDITATE" | ? | Tests case sensitivity |
| Type "meditation" | ? | Tests similar word |
| Type "meditate" in Chat tab | ? | Tests if issue is screen-specific |
| Type any other word in Meditate tab | Should work | Tests if it's the word or the screen |

### 4. Common Crash Scenarios

#### Scenario A: Navigation Loop
**Symptom:** App crashes immediately or freezes
**Cause:** The word "meditate" triggers navigation to the meditation screen while already on it

**Fix Location:** Look for code like:
```kotlin
// In your chat/gemini handler:
if (userInput.contains("meditate")) {
    navController.navigate("meditation")  // ⚠️ Causes loop if already on meditation screen
}
```

**Fix:**
```kotlin
if (userInput.contains("meditate")) {
    val currentRoute = navController.currentDestination?.route
    if (currentRoute != "meditation") {
        navController.navigate("meditation")
    } else {
        // Already on meditation screen, handle differently
        showInlineResponse("You're already on the meditation screen!")
    }
}
```

#### Scenario B: Uninitialized ViewModel
**Symptom:** Crash happens after typing a few characters
**Cause:** ViewModel or state is not initialized

**Fix Location:** Your `MeditationScreen.kt` or `MeditationViewModel.kt`
```kotlin
// Look for:
lateinit var geminiClient: GeminiApi  // ⚠️ Might not be initialized

// Fix:
private var geminiClient: GeminiApi? = null

init {
    try {
        geminiClient = GeminiApi()
    } catch (e: Exception) {
        println("Failed to initialize Gemini: ${e.message}")
    }
}
```

#### Scenario C: Gemini API Key Issue
**Symptom:** Crash after pressing send or during text processing
**Cause:** Missing or invalid API key

**Fix Location:** Look for API key initialization
```kotlin
// Check for:
val apiKey = BuildConfig.GEMINI_API_KEY  // ⚠️ Might be null or empty

// Fix:
val apiKey = try {
    BuildConfig.GEMINI_API_KEY
} catch (e: Exception) {
    null
} ?: run {
    println("⚠️ Gemini API key not found")
    return@launch
}
```

#### Scenario D: Coroutine Scope Issue
**Symptom:** Crash when sending message or immediate crash
**Cause:** Using wrong coroutine scope or scope is cancelled

**Fix Location:** Message sending code
```kotlin
// Look for:
GlobalScope.launch {  // ⚠️ Bad practice
    sendToGemini(message)
}

// Fix:
viewModelScope.launch {
    try {
        sendToGemini(message)
    } catch (e: CancellationException) {
        // Scope was cancelled, don't rethrow
        println("Coroutine cancelled")
    } catch (e: Exception) {
        e.printStackTrace()
        // Show error to user
    }
}
```

#### Scenario E: State Collection Issue
**Symptom:** Crash during typing or state updates
**Cause:** State flow not properly initialized or collected

**Fix Location:** In your screen composable
```kotlin
// Look for:
val messages = viewModel.messages.collectAsState()  // ⚠️ Might crash if not initialized

// Fix:
val messages by viewModel.messages.collectAsStateWithLifecycle(
    initialValue = emptyList()
)
// OR
val messages = viewModel.messages.collectAsState(initial = emptyList())
```

## Kotlin Files to Check (Priority Order)

1. **MeditationScreen.kt** (or similar name)
   - Text field implementation
   - Message sending logic
   - State collection

2. **MeditationViewModel.kt** (or ChatViewModel.kt)
   - ViewModel initialization
   - Message processing
   - Gemini API calls

3. **GeminiRepository.kt** or **GeminiApi.kt**
   - API initialization
   - Request/response handling
   - Error handling

4. **Navigation.kt** or **AppNavigation.kt**
   - Route definitions
   - Navigation graph
   - Deep link handling

5. **App.kt** or **Main.kt**
   - App initialization
   - Platform bridge setup
   - ViewModel factory

## Quick Diagnostic Code to Add

Add this to your `MeditationScreen.kt` or wherever the text field is:

```kotlin
@Composable
fun MeditationScreen() {
    println("🔍 MeditationScreen composed")
    
    var userInput by remember { 
        println("🔍 Initializing userInput state")
        mutableStateOf("") 
    }
    
    OutlinedTextField(
        value = userInput,
        onValueChange = { newValue ->
            println("🔍 TextField onChange: '$newValue'")
            try {
                // Special logging for "meditate"
                if (newValue.contains("meditate", ignoreCase = true)) {
                    println("⚠️ Detected 'meditate' keyword")
                }
                
                userInput = newValue
                println("✅ TextField state updated successfully")
            } catch (e: Exception) {
                println("❌ TextField onChange crashed: ${e.message}")
                e.printStackTrace()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        placeholder = { Text("Type a message...") }
    )
    
    println("🔍 MeditationScreen rendered successfully")
}
```

## Crash Log Analysis

When you get the crash, copy the relevant parts and look for:

1. **Stack trace lines mentioning "meditate" or "meditation"**
   - Shows if crash is in specific meditation-related code

2. **Kotlin class names before the crash**
   - Example: `com.yourapp.meditation.MeditationViewModel.sendMessage`
   - This tells you exactly where to look

3. **Native crash vs Kotlin crash**
   - Native: Look for Swift/ObjC frames
   - Kotlin: Look for Kotlin class names

4. **Exception type**
   - `NullPointerException`: Something is null
   - `IllegalStateException`: Invalid state (often uninitialized)
   - `IndexOutOfBoundsException`: Array/list access error
   - `UninitializedPropertyAccessException`: lateinit var not initialized

## Emergency Workaround

If you need the app working NOW, add this temporary filter:

```kotlin
OutlinedTextField(
    value = userInput,
    onValueChange = { newValue ->
        // TEMPORARY: Block the problematic word
        val filtered = newValue.replace("meditate", "med***te", ignoreCase = true)
        
        if (filtered != newValue) {
            println("⚠️ Filtered 'meditate' keyword to prevent crash")
            // Optional: Show a toast/snackbar
            showMessage("The word 'meditate' is temporarily disabled due to a known issue")
        }
        
        userInput = filtered
    }
)
```

## Reporting the Issue

If you need help from your team or want to file a bug report, include:

1. **Exact steps to reproduce:**
   - "Open app → Go to Meditate tab → Tap message input → Type 'meditate'"

2. **Device info:**
   - iOS version
   - Device model
   - App version

3. **Full crash log** from Xcode

4. **Screenshots** of the screen before crash

5. **Console output** with the diagnostic logging enabled

## Next Steps After Finding the Issue

Once you identify the crash location:

1. Add proper error handling at that location
2. Add null checks for any potentially null values
3. Add try-catch around the problematic code
4. Test thoroughly with various inputs
5. Remove temporary workarounds
6. Add unit tests to prevent regression

## Still Stuck?

If none of this helps, the issue might be in:
- Build configuration (check your `build.gradle.kts`)
- Dependency versions (check for version conflicts)
- Platform-specific implementation missing
- Native iOS bridge issue

Check the `CRASH_FIX_GUIDE.md` for more detailed Kotlin code fixes.
