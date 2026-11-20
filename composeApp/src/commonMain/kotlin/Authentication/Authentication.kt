package Authentication

import components.ui.ContentWidth
import components.ui.centeredMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import HeroScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mmk.kmpauth.firebase.apple.AppleButtonUiContainer
import com.mmk.kmpauth.firebase.google.GoogleButtonUiContainerFirebase
import com.mmk.kmpauth.google.GoogleAuthCredentials
import com.mmk.kmpauth.google.GoogleAuthProvider
import com.mmk.kmpauth.uihelper.apple.AppleSignInButton
import com.mmk.kmpauth.uihelper.google.GoogleSignInButton
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.auth
import keyboardUtil.hideKeyboard
import keyboardUtil.onDoneHideKeyboardAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

const val SignUpScreen = "SignUp"
const val LoginScreen = "Login"
const val ResetPasswordScreen = "ResetPassword"
const val HomePageScreen = "HomePage"

class Authentication {


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Login(navController: NavController) {
        val scope = rememberCoroutineScope()
        val auth = remember { Firebase.auth }
        var firebaseUser: FirebaseUser? by remember { mutableStateOf(auth.currentUser) }
        var userEmail by remember { mutableStateOf("") }
        var userPassword by remember { mutableStateOf("") }

        var isPasswordIncorrect by remember { mutableStateOf(false) }
        var isPasswordVisible by remember { mutableStateOf(false) }

        var showSnackbar by remember { mutableStateOf(false) }
        var snackbarMessage by remember { mutableStateOf("") }

        var authready by remember { mutableStateOf(false) }
        var onFirebaseResult: (Result<FirebaseUser?>) -> Unit = { result ->
            if (result.isSuccess) {
                val user = result.getOrNull()
                navController.navigate(HeroScreen)
            } else {
                val error = result.exceptionOrNull()
            }
        }

        LaunchedEffect(Unit) {
            GoogleAuthProvider.create(
                credentials = GoogleAuthCredentials(
                    serverId = "991501394909-ij12drqd040b9766t2t9s5d0itjs46h3.apps.googleusercontent.com"
                )
            )
            authready = true
        }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (firebaseUser != null) {
                navController.navigate(HomePageScreen)
            }

            if (firebaseUser == null) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .centeredMaxWidth(ContentWidth.Form)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AuthHeader(title = "Welcome back", subtitle = "Sign in to continue your wellness journey")

                    if (snackbarMessage.isNotEmpty()) {
                        AuthError(snackbarMessage)
                    }

                    OutlinedTextField(
                        value = userEmail,
                        onValueChange = { userEmail = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = userPassword,
                        onValueChange = { userPassword = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = onDoneHideKeyboardAction(onDone = {}),
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(image, contentDescription = "Toggle password visibility")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { navController.navigate(ResetPasswordScreen) },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Forgot password?") }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            hideKeyboard()
                            scope.launch {
                                try {
                                    val result = auth.signInWithEmailAndPassword(
                                        email = userEmail,
                                        password = userPassword
                                    )
                                    firebaseUser = result.user
                                } catch (e: FirebaseAuthInvalidUserException) {
                                    snackbarMessage = "No account found with this email. Please sign up."
                                    showSnackbar = true
                                } catch (e: FirebaseAuthInvalidCredentialsException) {
                                    snackbarMessage = "Invalid password, please try again"
                                    showSnackbar = true
                                } catch (e: Exception) {
                                    snackbarMessage = "An error occurred: ${e.message}"
                                    showSnackbar = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("Sign in", style = MaterialTheme.typography.titleMedium)
                    }

                    if (authready) {
                        OrDivider()
                        GoogleButtonUiContainerFirebase(onResult = onFirebaseResult) {
                            GoogleSignInButton(modifier = Modifier.fillMaxWidth().height(52.dp), fontSize = 16.sp) { this.onClick() }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        AppleButtonUiContainer(onResult = onFirebaseResult) {
                            AppleSignInButton(modifier = Modifier.fillMaxWidth().height(52.dp)) { this.onClick() }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("New here?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { navController.navigate(SignUpScreen) }) { Text("Create an account") }
                    }
                }
            }
            if (firebaseUser != null) {
                navController.navigate(HeroScreen)
            }
        }

    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun signUp(navController: NavController) {
        val scope = rememberCoroutineScope()
        val auth = remember { Firebase.auth }
        var firebaseUser: FirebaseUser? by remember { mutableStateOf(null) }
        var userEmail by remember { mutableStateOf("") }
        var userPassword by remember { mutableStateOf("") }
        var isPasswordVisible by remember { mutableStateOf(false) }

        if (firebaseUser == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                val keyboardController = LocalSoftwareKeyboardController.current
                Column(
                    modifier = Modifier
                        .centeredMaxWidth(ContentWidth.Form)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextField(
                        value = userEmail,
                        onValueChange = { userEmail = it },
                        placeholder = { Text("Email address") },
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = onDoneHideKeyboardAction(onDone = {}),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = userPassword,
                        onValueChange = { userPassword = it },
                        placeholder = { Text("Password") },
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = onDoneHideKeyboardAction(onDone = {}),
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image =
                                if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(image, contentDescription = "Toggle password visibility")
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        hideKeyboard()
                        scope.launch {
                            try {
                                val result = auth.createUserWithEmailAndPassword(
                                    email = userEmail,
                                    password = userPassword
                                )
                                firebaseUser = result.user
                                navController.navigate(HeroScreen)
                            } catch (e: Exception) {
                                val result = auth.signInWithEmailAndPassword(
                                    email = userEmail,
                                    password = userPassword
                                )
                                firebaseUser = result.user

                            }
                        }
                    }) {
                        Text("Sign up")
                    }
                    OutlinedButton(onClick = { navController.navigate(LoginScreen) }) {
                        Text("Login")
                    }
                }
            }
            if (firebaseUser != null) {
                navController.navigate(HeroScreen)
            }
        }
    }

    @Composable
    fun ResetPassword(navController: NavController) {
        val scope = rememberCoroutineScope()
        var email by remember { mutableStateOf("") }
        var message by remember { mutableStateOf<String?>(null) }
        val auth = Firebase.auth

        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .centeredMaxWidth(ContentWidth.Form)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val keyboardController = LocalSoftwareKeyboardController.current
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("Email address") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = onDoneHideKeyboardAction(onDone = {}),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            resetPassword(email).collect { result ->
                                message = if (result.isSuccess) {
                                    "Reset email sent successfully."
                                } else {
                                    "Failed to send reset email: ${result.exceptionOrNull()?.message}"
                                }
                            }
                        }
                    }
                ) {
                    Text("Send Reset Email")
                }
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = {
                    navController.navigate(LoginScreen)
                }) {
                    Text("Login")
                }
            }
        }
    }


    suspend fun resetPassword(email: String): Flow<Result<Boolean>> = callbackFlow {
        val auth = Firebase.auth
        try {
            auth.sendPasswordResetEmail(email)
            trySend(Result.success(true))
        } catch (e: Exception) {
            trySend(Result.failure(e))
        }
        close() // Close the flow after emitting the result
    }
}

@Composable
private fun AuthHeader(title: String, subtitle: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(80.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Spa, contentDescription = null, modifier = Modifier.size(40.dp))
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun AuthError(message: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "or continue with",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
