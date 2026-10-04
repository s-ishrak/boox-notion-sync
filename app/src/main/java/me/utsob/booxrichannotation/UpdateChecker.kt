package me.utsob.booxrichannotation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val context: Context) {
    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_API_URL = "https://api.github.com/repos/uroybd/BooxRichAnnotations/releases/latest"
        private const val PREFS_NAME = "UpdateCheckerPrefs"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    data class UpdateInfo(
        val latestVersion: String,
        val currentVersion: String,
        val updateAvailable: Boolean,
        val releaseUrl: String
    )
    
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
            val now = System.currentTimeMillis()
            
            // Check if we should query GitHub (not checked in last 24 hours)
            val cachedVersion = prefs.getString(KEY_LATEST_VERSION, null)
            if (now - lastCheck < CHECK_INTERVAL_MS && cachedVersion != null) {
                Log.d(TAG, "Using cached version: $cachedVersion")
                return@withContext buildUpdateInfo(cachedVersion)
            }
            
            // Fetch latest release from GitHub
            Log.d(TAG, "Checking for updates from GitHub...")
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                
                // Extract version from tag_name (e.g., "v1.4.1" or "1.4.1")
                val latestVersion = jsonObject.getString("tag_name").removePrefix("v")
                
                // Save to cache
                prefs.edit().apply {
                    putLong(KEY_LAST_CHECK, now)
                    putString(KEY_LATEST_VERSION, latestVersion)
                    apply()
                }
                
                Log.d(TAG, "Latest version from GitHub: $latestVersion")
                return@withContext buildUpdateInfo(latestVersion)
            } else {
                Log.w(TAG, "GitHub API returned error: $responseCode")
                // Use cached version if available
                if (cachedVersion != null) {
                    return@withContext buildUpdateInfo(cachedVersion)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            // Try to use cached version
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedVersion = prefs.getString(KEY_LATEST_VERSION, null)
            if (cachedVersion != null) {
                return@withContext buildUpdateInfo(cachedVersion)
            }
        }
        return@withContext null
    }
    
    private fun buildUpdateInfo(latestVersion: String): UpdateInfo {
        val currentVersion = getCurrentVersion()
        val updateAvailable = isNewerVersion(latestVersion, currentVersion)
        val releaseUrl = "https://github.com/uroybd/BooxRichAnnotations/releases/latest"
        
        return UpdateInfo(
            latestVersion = latestVersion,
            currentVersion = currentVersion,
            updateAvailable = updateAvailable,
            releaseUrl = releaseUrl
        )
    }
    
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            
            // Compare major, minor, patch
            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val latestPart = latestParts.getOrNull(i) ?: 0
                val currentPart = currentParts.getOrNull(i) ?: 0
                
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            
            return false // Versions are equal
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
            return false
        }
    }
    
    fun clearCache() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
