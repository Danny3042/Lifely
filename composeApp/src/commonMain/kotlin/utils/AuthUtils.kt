package utils

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun signOut(auth: FirebaseAuth, navController: NavController, coroutineScope: CoroutineScope) {
    coroutineScope.launch {
        auth.signOut()
        navController.navigate("LoginScreen")
    }
}

suspend fun deleteUser(auth: FirebaseAuth, navController: NavController, snackBarHostState: SnackbarHostState) {
    val user = auth.currentUser
    if (user != null) {
        try {
            user.delete()
            snackBarHostState.showSnackbar("User account deleted.")
            navController.navigate("LoginScreen")
        } catch (e: Exception) {
            snackBarHostState.showSnackbar("User account deletion failed")
        }
    }
}