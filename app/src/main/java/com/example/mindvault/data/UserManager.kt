package com.example.mindvault.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class User(
    val id: String,
    val name: String,
    val email: String,
    val profilePicture: String? = null,
    val role: UserRole,
    val createdAt: LocalDateTime,
    val lastActiveAt: LocalDateTime,
    val isActive: Boolean = true,
    val preferences: UserPreferences = UserPreferences()
)

enum class UserRole {
    ADMIN,      // Full access to all features and user management
    PREMIUM,    // Access to all productivity features
    STANDARD    // Basic features only
}

data class UserPreferences(
    val theme: String = "dark",
    val notifications: Boolean = true,
    val weeklyGoal: Long = 1200L, // minutes
    val monthlyGoal: Long = 5000L, // minutes
    val reminderEnabled: Boolean = true,
    val dataExportEnabled: Boolean = true,
    val analyticsEnabled: Boolean = true
)

data class UserSession(
    val userId: String,
    val sessionToken: String,
    val loginTime: LocalDateTime,
    val expiresAt: LocalDateTime,
    val deviceInfo: String
)

object UserManager {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()
    
    private val _userSessions = MutableStateFlow<List<UserSession>>(emptyList())
    val userSessions = _userSessions.asStateFlow()
    
    fun init(context: Context) {
        this.context = context.applicationContext
        this.prefs = context.getSharedPreferences("mindvault_users", Context.MODE_PRIVATE)
        loadCurrentUser()
        Log.d("UserManager", "User Manager initialized")
    }
    
    private fun loadCurrentUser() {
        val userId = prefs.getString("current_user_id", null)
        if (userId != null) {
            val user = getUserById(userId)
            if (user != null && user.isActive) {
                _currentUser.value = user
                _isLoggedIn.value = true
                updateLastActive(userId)
            }
        }
    }
    
    fun createUser(name: String, email: String, role: UserRole = UserRole.STANDARD, profilePicture: String? = null): User? {
        val userId = generateUserId()
        val now = LocalDateTime.now()
        
        val user = User(
            id = userId,
            name = name,
            email = email,
            profilePicture = profilePicture,
            role = role,
            createdAt = now,
            lastActiveAt = now
        )
        
        return if (saveUser(user)) {
            Log.d("UserManager", "Created user: $name ($email)")
            user
        } else {
            null
        }
    }
    
    fun loginUser(email: String): User? {
        val user = getUserByEmail(email)
        return if (user != null && user.isActive) {
            _currentUser.value = user
            _isLoggedIn.value = true
            prefs.edit().putString("current_user_id", user.id).apply()
            updateLastActive(user.id)
            createSession(user.id)
            Log.d("UserManager", "User logged in: ${user.name}")
            user
        } else {
            Log.w("UserManager", "Login failed for email: $email")
            null
        }
    }
    
    fun logoutUser() {
        // existing logout implementation below
        val currentUser = _currentUser.value
        if (currentUser != null) {
            invalidateUserSessions(currentUser.id)
            _currentUser.value = null
            _isLoggedIn.value = false
            prefs.edit().remove("current_user_id").apply()
            Log.d("UserManager", "User logged out: ${currentUser.name}")
        }
    }

    /**
     * Update current user's editable fields such as name, profile picture and goals.
     * Any null parameter will be ignored.
     */
    fun updateCurrentUser(
        newName: String? = null,
        newProfilePicture: String? = null,
        newWeeklyGoal: Long? = null,
        newMonthlyGoal: Long? = null
    ): Boolean {
        val user = _currentUser.value ?: return false
        var updatedUser = user
        if (newName != null) {
            updatedUser = updatedUser.copy(name = newName)
        }
        if (newProfilePicture != null) {
            updatedUser = updatedUser.copy(profilePicture = newProfilePicture)
        }
        if (newWeeklyGoal != null || newMonthlyGoal != null) {
            val prefsObj = updatedUser.preferences.copy(
                weeklyGoal = newWeeklyGoal ?: updatedUser.preferences.weeklyGoal,
                monthlyGoal = newMonthlyGoal ?: updatedUser.preferences.monthlyGoal
            )
            updatedUser = updatedUser.copy(preferences = prefsObj)
        }
        val success = saveUser(updatedUser)
        if (success) {
            _currentUser.value = updatedUser
        }
        return success
    }

    fun updateUserRole(userId: String, newRole: UserRole): Boolean {
        val user = getUserById(userId) ?: return false
        val updatedUser = user.copy(role = newRole)
        return saveUser(updatedUser)
    }
    
    fun updateUserPreferences(userId: String, preferences: UserPreferences): Boolean {
        val user = getUserById(userId) ?: return false
        val updatedUser = user.copy(preferences = preferences)
        return saveUser(updatedUser)
    }
    
    fun deactivateUser(userId: String): Boolean {
        val user = getUserById(userId) ?: return false
        val deactivatedUser = user.copy(isActive = false)
        return saveUser(deactivatedUser)
    }
    
    fun getAllUsers(): List<User> {
        val userIds = prefs.getStringSet("all_user_ids", emptySet()) ?: emptySet()
        return userIds.mapNotNull { getUserById(it) }
    }
    
    fun getActiveUsers(): List<User> {
        return getAllUsers().filter { it.isActive }
    }
    
    fun getUsersByRole(role: UserRole): List<User> {
        return getAllUsers().filter { it.role == role }
    }
    
    fun hasPermission(permission: String): Boolean {
        val user = _currentUser.value ?: return false
        
        return when (permission) {
            "EXPORT_DATA" -> user.role != UserRole.STANDARD || user.preferences.dataExportEnabled
            "VIEW_ANALYTICS" -> user.preferences.analyticsEnabled
            "MANAGE_USERS" -> user.role == UserRole.ADMIN
            "PREMIUM_FEATURES" -> user.role in listOf(UserRole.PREMIUM, UserRole.ADMIN)
            "BASIC_FEATURES" -> true
            else -> false
        }
    }
    
    fun getCurrentUserRole(): UserRole {
        return _currentUser.value?.role ?: UserRole.STANDARD
    }
    
    fun isCurrentUserPremium(): Boolean {
        val role = getCurrentUserRole()
        return role in listOf(UserRole.PREMIUM, UserRole.ADMIN)
    }
    
    fun isCurrentUserAdmin(): Boolean {
        return getCurrentUserRole() == UserRole.ADMIN
    }
    
    private fun saveUser(user: User): Boolean {
        return try {
            val editor = prefs.edit()
            
            // Save user data
            editor.putString("user_${user.id}_name", user.name)
            editor.putString("user_${user.id}_email", user.email)
            editor.putString("user_${user.id}_role", user.role.name)
            editor.putString("user_${user.id}_created", user.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            editor.putString("user_${user.id}_last_active", user.lastActiveAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            editor.putBoolean("user_${user.id}_active", user.isActive)
        editor.putString("user_${user.id}_profile_pic", user.profilePicture)
            
            // Save preferences
            editor.putString("user_${user.id}_theme", user.preferences.theme)
            editor.putBoolean("user_${user.id}_notifications", user.preferences.notifications)
            editor.putLong("user_${user.id}_weekly_goal", user.preferences.weeklyGoal)
            editor.putLong("user_${user.id}_monthly_goal", user.preferences.monthlyGoal)
            editor.putBoolean("user_${user.id}_reminder", user.preferences.reminderEnabled)
            editor.putBoolean("user_${user.id}_export", user.preferences.dataExportEnabled)
            editor.putBoolean("user_${user.id}_analytics", user.preferences.analyticsEnabled)
            
            // Add to user list
            val userIds = prefs.getStringSet("all_user_ids", mutableSetOf()) ?: mutableSetOf()
            userIds.add(user.id)
            editor.putStringSet("all_user_ids", userIds)
            
            editor.apply()
            
            // Update current user if it's the same
            if (_currentUser.value?.id == user.id) {
                _currentUser.value = user
            }
            
            true
        } catch (e: Exception) {
            Log.e("UserManager", "Failed to save user: ${e.message}")
            false
        }
    }
    
    private fun getUserById(userId: String): User? {
        return try {
            val name = prefs.getString("user_${userId}_name", null) ?: return null
            val email = prefs.getString("user_${userId}_email", null) ?: return null
            val roleString = prefs.getString("user_${userId}_role", UserRole.STANDARD.name) ?: UserRole.STANDARD.name
            val role = UserRole.valueOf(roleString)
            val createdString = prefs.getString("user_${userId}_created", null) ?: return null
            val lastActiveString = prefs.getString("user_${userId}_last_active", null) ?: return null
            val isActive = prefs.getBoolean("user_${userId}_active", true)
            val profilePic = prefs.getString("user_${userId}_profile_pic", null)
            
            val preferences = UserPreferences(
                theme = prefs.getString("user_${userId}_theme", "dark") ?: "dark",
                notifications = prefs.getBoolean("user_${userId}_notifications", true),
                weeklyGoal = prefs.getLong("user_${userId}_weekly_goal", 1200L),
                monthlyGoal = prefs.getLong("user_${userId}_monthly_goal", 5000L),
                reminderEnabled = prefs.getBoolean("user_${userId}_reminder", true),
                dataExportEnabled = prefs.getBoolean("user_${userId}_export", true),
                analyticsEnabled = prefs.getBoolean("user_${userId}_analytics", true)
            )
            
            User(
                id = userId,
                name = name,
                email = email,
                profilePicture = profilePic,
                role = role,
                createdAt = LocalDateTime.parse(createdString, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                lastActiveAt = LocalDateTime.parse(lastActiveString, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                isActive = isActive,
                preferences = preferences
            )
        } catch (e: Exception) {
            Log.e("UserManager", "Failed to load user $userId: ${e.message}")
            null
        }
    }
    
    private fun getUserByEmail(email: String): User? {
        val userIds = prefs.getStringSet("all_user_ids", emptySet()) ?: emptySet()
        return userIds
            .mapNotNull { getUserById(it) }            // load all stored users
            .filter { it.email == email && it.isActive } // only consider active accounts matching email
            // Prefer users that already have a profile picture, then the most recently created
            .sortedWith(
                compareByDescending<User> { !it.profilePicture.isNullOrBlank() }
                    .thenByDescending { it.createdAt }
            )
            .firstOrNull()
    }
    
    private fun updateLastActive(userId: String) {
        val user = getUserById(userId) ?: return
        val updatedUser = user.copy(lastActiveAt = LocalDateTime.now())
        saveUser(updatedUser)
    }
    
    private fun createSession(userId: String) {
        val sessionToken = generateSessionToken()
        val now = LocalDateTime.now()
        val session = UserSession(
            userId = userId,
            sessionToken = sessionToken,
            loginTime = now,
            expiresAt = now.plusDays(30), // 30 day session
            deviceInfo = "Android Device"
        )
        
        val sessions = _userSessions.value.toMutableList()
        sessions.add(session)
        _userSessions.value = sessions
        
        // Save session
        prefs.edit()
            .putString("session_${sessionToken}_user", userId)
            .putString("session_${sessionToken}_login", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .putString("session_${sessionToken}_expires", session.expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .apply()
    }
    
    private fun invalidateUserSessions(userId: String) {
        val sessions = _userSessions.value.filter { it.userId != userId }
        _userSessions.value = sessions
        
        // Remove from preferences
        val editor = prefs.edit()
        _userSessions.value.forEach { session ->
            if (session.userId == userId) {
                editor.remove("session_${session.sessionToken}_user")
                editor.remove("session_${session.sessionToken}_login")
                editor.remove("session_${session.sessionToken}_expires")
            }
        }
        editor.apply()
    }
    
    private fun generateUserId(): String {
        return "user_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun generateSessionToken(): String {
        return "session_${System.currentTimeMillis()}_${(10000..99999).random()}"
    }
}
