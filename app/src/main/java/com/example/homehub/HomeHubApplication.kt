package com.example.homehub

import android.app.Application
import android.util.Log
import com.example.homehub.utils.ThemeHelper
import com.example.homehub.auth.SessionRestoreHelper
import com.example.homehub.utils.GlobalDataCache
import com.example.homehub.utils.NetworkUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import org.osmdroid.config.Configuration
import android.preference.PreferenceManager

class HomeHubApplication : Application() {

    companion object {
        private const val TAG = "HomeHubApp"
    }

    override fun onCreate() {
        super.onCreate()
        
        // 1. Configure Firestore Offline Persistence FIRST before any code uses it
        // NOTE: Settings must be applied before ANY other Firestore call.
        // If Firestore was already auto-started (e.g. by Firebase init), skip gracefully.
        try {
            val db = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                    .setSizeBytes(100 * 1024 * 1024) // 100 MB
                    .build())
                .build()
            db.firestoreSettings = settings
        } catch (e: IllegalStateException) {
            // Firestore already started — persistence is enabled by default, so this is fine
            Log.w(TAG, "Firestore already initialized, using default settings: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Firestore settings could not be applied: ${e.message}")
        }

        Log.d(TAG, "=== APPLICATION STARTING ===")

        // 2. Apply saved theme
        ThemeHelper.applyTheme(ThemeHelper.loadTheme(this))

        // 3. Reset restore count on fresh app start
        SessionRestoreHelper.resetRestoreCount(this)

        // 4. Initialize global data cache for instant dashboard loading
        GlobalDataCache.initialize()
        
        // 5. Initialize network monitoring
        NetworkUtils.initialize(this)

        // Global OSMDroid Config
        Configuration.getInstance().apply {
            userAgentValue = packageName
            // We'll use a specific preference file for map settings to survive updates
            load(this@HomeHubApplication, getSharedPreferences("osm_global", MODE_PRIVATE))
            
            // Map Caching Optimizations for weak network (Meru/Institutional areas)
            cacheMapTileCount = 100
            cacheMapTileOvershoot = 20
            // Keep tiles for up to 30 days even if they theoretically "expire" to save bandwidth
            expirationExtendedDuration = 30 * 24 * 60 * 60 * 1000L
            tileDownloadThreads = 4
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "=== APPLICATION TERMINATING ===")
    }
}