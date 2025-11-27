package com.example.mindvault.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mindvault.MindVaultApplication
import com.example.mindvault.R

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.gson.Gson
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


data class AuthResult(
    val isSuccess: Boolean,
    val user: User? = null,
    val errorMessage: String? = null
)

object AuthManager {
    private const val TAG = "AuthManager"
    private val gson = Gson()
    

    private lateinit var googleSignInClient: GoogleSignInClient
    
    private val _authState = MutableStateFlow<AuthResult?>(null)
    val authState = _authState.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    fun init(appContext: Context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(appContext.getString(R.string.default_web_client_id))
            .build()
            
        googleSignInClient = GoogleSignIn.getClient(appContext, gso)
        
        // Check if user is already signed in
        val account = GoogleSignIn.getLastSignedInAccount(appContext)
        if (account != null) {
            Log.d(TAG, "Found existing Google account: ${account.email}, handling sign in.")
            // Use a coroutine to handle the sign-in, as it's a suspend function
            MindVaultApplication.instance.applicationScope.launch {
                handleSignInResult(appContext, account)
            }
        }
        
        Log.d(TAG, "AuthManager initialized")
    }
    
    fun getGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    suspend fun handleGoogleSignInResult(data: Intent?): AuthResult {
        _isLoading.value = true
        
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            // Since we no longer store context, we need to get it from the application
            val appContext = MindVaultApplication.instance.applicationContext
            
            if (account != null) {
                handleSignInResult(appContext, account)
            } else {
                AuthResult(false, errorMessage = "Failed to get account information")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign in failed", e)
            AuthResult(false, errorMessage = "Sign in failed: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }
    
            private suspend fun handleSignInResult(appContext: Context, account: GoogleSignInAccount): AuthResult {
        // 1. Authenticate with Firebase using the Google account credentials so that we have a valid UID
        try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).await()
            Log.d(TAG, "Firebase authentication completed for ${account.email}")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase authentication failed", e)
            return AuthResult(false, errorMessage = "Firebase auth failed: ${e.message}")
        }

        // 2. Now that we are authenticated, restore the backup from Firestore (if any)
        syncUserDataFromCloud()

        return try {
            
            // Create or get user from UserManager
            val user = UserManager.createUser(
                name = account.displayName ?: "Google User",
                email = account.email ?: "",
                role = UserRole.PREMIUM,
                profilePicture = account.photoUrl?.toString()
            )
            
            if (user != null) {
                UserManager.loginUser(user.email)
                
                // Data is now synced, proceed with login
                
                val result = AuthResult(true, user)
                _authState.value = result
                result
            } else {
                AuthResult(false, errorMessage = "Failed to create user")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle sign in result", e)
            AuthResult(false, errorMessage = "Authentication failed: ${e.message}")
        }
    }
    

    
    fun signInAsGuest(): AuthResult {
        _isLoading.value = true
        
        return try {
            val guestEmail = "guest_${System.currentTimeMillis()}@mindvault.com"
            val user = UserManager.createUser(
                name = "Guest User",
                email = guestEmail,
                role = UserRole.STANDARD
            )
            
            if (user != null) {
                UserManager.loginUser(user.email)
                val result = AuthResult(true, user)
                _authState.value = result
                result
            } else {
                AuthResult(false, errorMessage = "Failed to create guest user")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Guest sign in failed", e)
            AuthResult(false, errorMessage = "Guest sign in failed: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }
    
            fun signOut() {
        MindVaultApplication.instance.applicationScope.launch {
            _isLoading.value = true
            try {
                // 1. Sync data to the cloud before signing out
                val syncSuccess = syncUserDataToCloud()
                if (!syncSuccess) {
                    Log.w(TAG, "Cloud sync failed during sign out, but proceeding with sign out anyway.")
                }

                // 2. Sign out from Google
                googleSignInClient.signOut().addOnCompleteListener { 
                    // 3. Clear local user session
                    UserManager.logoutUser()
                    _authState.value = null
                    Log.d(TAG, "User signed out successfully.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Sign out failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    

    
        suspend fun syncUserDataToCloud(): Boolean {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) return false
            val firestore = FirebaseFirestore.getInstance()
            val appContext = MindVaultApplication.instance.applicationContext
            val backupData = mutableMapOf<String, String>()
            backupData["user_prefs"] = getPrefsAsJson(appContext, "mindvault_users")
            backupData["focus_prefs"] = getPrefsAsJson(appContext, "FocusModePrefs")
            backupData["stats_prefs"] = getPrefsAsJson(appContext, "mindvault_stats")
            firestore.collection("backups").document(user.uid)
                .set(backupData)
                .await()
            Log.d(TAG, "Backup uploaded to Firestore for user: ${user.uid}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync data to Firestore", e)
            false
        }
    }

    suspend fun syncUserDataFromCloud(): Boolean {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) return false
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("backups").document(user.uid).get().await()
            if (!snapshot.exists()) {
                Log.d(TAG, "No backup data found in Firestore. Skipping restore.")
                return true
            }
            val backupData = snapshot.data as? Map<String, String> ?: return false
            val appContext = MindVaultApplication.instance.applicationContext
            restorePrefsFromJson(appContext, "mindvault_users", backupData["user_prefs"])
            restorePrefsFromJson(appContext, "FocusModePrefs", backupData["focus_prefs"])
            restorePrefsFromJson(appContext, "mindvault_stats", backupData["stats_prefs"])
            UserManager.init(appContext)
            FocusManager.init(appContext)
            StatisticsManager.init(appContext)
            Log.d(TAG, "Backup restored from Firestore for user: ${user.uid}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync data from Firestore", e)
            false
        }
    }

    /**
     * Enqueue a background retry for backup if foreground sync fails.
     */
    fun enqueueBackupRetry() {
        try {
            val wm = androidx.work.WorkManager.getInstance(MindVaultApplication.instance)
            val req = androidx.work.OneTimeWorkRequestBuilder<com.example.mindvault.data.BackupSyncWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            wm.enqueue(req)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue backup retry", e)
        }
    }

    private fun getPrefsAsJson(context: Context, prefsName: String): String {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return gson.toJson(prefs.all)
    }

    private fun restorePrefsFromJson(context: Context, prefsName: String, json: String?) {
        if (json == null) return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear() // Clear existing data

                @Suppress("UNCHECKED_CAST")
        val dataMap = gson.fromJson(json, Map::class.java) as Map<String, Any?>

        for ((key, value) in dataMap) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
            }
        }
        editor.apply()
    }
    

    

}
