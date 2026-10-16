package com.emogoth.android.phone.mimi.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.preference.PreferenceManager
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.app.MimiApplication
import com.emogoth.android.phone.mimi.autorefresh.RefreshNotification

class MimiPrefs {
    companion object {
        @JvmStatic
        fun navDrawerBookmarkCount(context: Context): Int {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val countString = prefs.getString(context.getString(R.string.nav_drawer_bookmark_count_pref), "5") ?: "5"
            return Integer.parseInt(countString)
        }

        @JvmStatic
        fun wifiConnected(app: Context): Boolean {
            val connManager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val wifi = connManager.activeNetwork
                if (wifi != null) {
                    val nc = connManager.getNetworkCapabilities(wifi)
                    if (nc != null) {
                        return (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                    }

                    return false
                }

                return false
            } else {
                val wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
                return wifi?.isConnected ?: false
            }
        }

        @JvmStatic
        fun preloadEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val pref = prefs.getString(context.getString(R.string.gallery_preload_pref), "2")

            return when (pref?.toInt() ?: 2) {
                2 -> {
                    wifiConnected(context)
                }
                1 -> true
                0 -> false
                else -> false
            }
        }

        @JvmStatic
        fun scrollThreadWithGallery(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(context.getString(R.string.scroll_thread_with_gallery_pref), true)
        }

        @JvmStatic
        fun closeGalleryOnClick(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(context.getString(R.string.close_gallery_on_click_pref), true)
        }

        @JvmStatic
        fun galleryAutoplayEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(context.getString(R.string.gallery_autoplay_pref), false)
        }

        @JvmStatic
        fun refreshInterval(): Int {
            val context = MimiApplication.instance.applicationContext
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString(context.getString(R.string.app_auto_refresh_time), "10")?.toInt() ?: 10
        }

        @JvmStatic
        fun backgroundRefreshInterval(): Int {
            val context = MimiApplication.instance.applicationContext
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString(context.getString(R.string.background_auto_refresh_time), "120")?.toInt() ?: 120
        }

        @JvmStatic
        fun userOriginalFilename(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(context.getString(R.string.use_original_filename_pref), false)
        }

        @JvmStatic
        fun imageSpoilersEnabled(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(context.getString(R.string.enable_image_spoilers_pref), true)
        }

        @JvmStatic
        fun refreshNotificationLevel(): Int {
            val context = MimiApplication.instance.applicationContext
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString(context.getString(R.string.background_notification_pref), RefreshNotification.NOTIFICATIONS_ALL.toString())?.toInt() ?: RefreshNotification.NOTIFICATIONS_ALL
        }

        fun removeWatch(threadId: Long) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(MimiApplication.instance.applicationContext)
            prefs.edit().remove("${RefreshNotification.NOTIFICATIONS_KEY_THREAD_SIZE}.$threadId").apply()
        }
    }
}
