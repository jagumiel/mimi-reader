package com.emogoth.android.phone.mimi.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import androidx.work.Configuration
import com.emogoth.android.phone.mimi.BuildConfig
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.autorefresh.RefreshScheduler2
import com.emogoth.android.phone.mimi.db.DatabaseUtils.applySingleSchedulers
import com.emogoth.android.phone.mimi.db.HiddenThreadTableConnection
import com.emogoth.android.phone.mimi.db.UserPostTableConnection
import com.emogoth.android.phone.mimi.util.MimiUtil
import com.novoda.simplechromecustomtabs.SimpleChromeCustomTabs
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException


open class MimiApplication : Application(), Configuration.Provider, LifecycleObserver {
    var background = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        handleRxExceptions()

        MimiUtil.getInstance().init(this)
        createLifecycleListener()

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val notificationLevel = preferences.getString(getString(R.string.background_notification_pref), "0")?.toInt()
                ?: 0
        val defaultSet = preferences.getBoolean(getString(R.string.crappy_samsung_default_set), false)
        if (!defaultSet) {
            val useCrappyVideoPlayer = MimiUtil.isCrappySamsung()
            preferences.edit()
                    .putBoolean(getString(R.string.crappy_samsung_default_set), true)
                    .putBoolean(getString(R.string.use_crappy_video_player), useCrappyVideoPlayer)
                    .apply()
        }
        if (notificationLevel == 0) {
            preferences.edit().putString(getString(R.string.background_notification_pref), "3").apply()
        }

        val historyPruneDays = preferences.getString(getString(R.string.history_prune_time_pref), "0")?.toInt()
                ?: 0
        val disposable = MimiUtil.pruneHistory(historyPruneDays)
                .flatMap { HiddenThreadTableConnection.prune(5) }
                .flatMap { UserPostTableConnection.prune(7) }
                .flatMap { MimiUtil.removeStalePosts() }
                .compose(applySingleSchedulers())
                .subscribe({ aBoolean: Boolean ->
                    if (aBoolean) {
                        Log.d(LOG_TAG, "Pruned history")
                    } else {
                        Log.e(LOG_TAG, "Failed to prune history")
                    }
                }) { throwable: Throwable? -> Log.e(LOG_TAG, "Caught exception while setting up database", throwable) }

        SimpleChromeCustomTabs.initialize(this)
    }

    private fun createLifecycleListener() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            fun foreground() {
                background = false
                RefreshScheduler2.instance.foreground()
                Log.d(LOG_TAG, "Foregrounded app")
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
            fun background() {
                background = true
                RefreshScheduler2.instance.background()
                Log.d(LOG_TAG, "Backgrounded app")
            }
        })
    }

    fun handleRxExceptions() {
        RxJavaPlugins.setErrorHandler { e: Throwable ->

            if (e is UndeliverableException) {
                Log.w(LOG_TAG, "Undeliverable exception received", e.cause)
                return@setErrorHandler
            }
            if (e is IOException || e is SocketException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (e is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (e is NullPointerException || e is IllegalArgumentException) {
                // that's likely a bug in the application
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            if (e is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            Log.w(LOG_TAG, "Unknown exception received, not sure what to do", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
                .setMinimumLoggingLevel(Log.VERBOSE)
                .build()

    companion object {
        private val LOG_TAG = MimiApplication::class.java.simpleName

        @JvmStatic
        lateinit var instance: MimiApplication
            private set
    }
}
