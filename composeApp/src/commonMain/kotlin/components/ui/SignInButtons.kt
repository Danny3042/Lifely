package components.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * Sign-in buttons drawn by the app instead of KMPAuth's uihelper module. That module was built
 * against an older Material 3 and crashes on iOS with the current one (IrLinkageError on
 * ButtonDefaults$stable) as soon as the Login screen appears, e.g. right after signing out.
 * The sign-in logic still comes from KMPAuth's GoogleButtonUiContainerFirebase / AppleButtonUiContainer.
 */

@Composable
fun GoogleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 52.dp)) {
        Text("Continue with Google", style = MaterialTheme.typography.titleSmall)
    }
}

/** Native ASAuthorizationAppleIDButton on iOS (as Apple's guidelines ask); a Material button on Android. */
@Composable
expect fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier)
