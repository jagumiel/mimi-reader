package com.emogoth.android.phone.mimi.autorefresh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.emogoth.android.phone.mimi.BuildConfig
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.StartupActivity
import com.emogoth.android.phone.mimi.app.MimiApplication.Companion.instance
import com.emogoth.android.phone.mimi.db.DatabaseUtils
import com.emogoth.android.phone.mimi.db.RefreshQueueTableConnection
import com.emogoth.android.phone.mimi.db.models.QueueItem
import com.emogoth.android.phone.mimi.util.Extras
import com.emogoth.android.phone.mimi.util.MimiPrefs
import com.emogoth.android.phone.mimi.util.NotificationUtils
import com.emogoth.android.phone.mimi.util.Pages
import io.reactivex.Flowable
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable

object RefreshNotification {
    private val LOG_DEBUG = BuildConfig.DEBUG
    private val LOG_TAG = RefreshNotification::class.java.simpleName

    const val NOTIFICATION_GROUP = "mimi_autorefresh"
    const val NOTIFICATION_ID = 1013
    const val REFRESH_CHANNEL_ID = "mimi_autorefresh_channel"

    const val NOTIFICATIONS_NONE = 1
    const val NOTIFICATIONS_ONLY_ME = 2
    const val NOTIFICATIONS_ALL = 3

    const val NOTIFICATIONS_KEY_THREAD_SIZE = "mimi_thread_size"

    private fun bookmarksPendingIntent(context: Context): PendingIntent {
        val openActivityIntent = Intent(context, StartupActivity::class.java)
        val args = Bundle()

        args.putString(Extras.OPEN_PAGE, Pages.BOOKMARKS.name)
        openActivityIntent.putExtras(args)

        return PendingIntent.getActivity(
                context,
                0,
                openActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun show() {
        if (!instance.background || !NotificationUtils.canPostNotifications(instance)) {
            return
        }

        val notificationLevel = MimiPrefs.refreshNotificationLevel()
        if (notificationLevel == NOTIFICATIONS_NONE) {
            return
        }

        buildNotification(notificationLevel)
    }

    private fun buildNotification(notificationLevel: Int = NOTIFICATIONS_ALL) {
        val context = instance.applicationContext
        val notificationBuilder = NotificationCompat.Builder(instance, REFRESH_CHANNEL_ID)
        val style = NotificationCompat.InboxStyle()

        notificationBuilder.setStyle(style)
        notificationBuilder.setContentIntent(bookmarksPendingIntent(context))
        notificationBuilder.setSmallIcon(R.drawable.ic_stat_leaf_icon)

        RefreshQueueTableConnection.fetchAllItems()
                .compose(DatabaseUtils.applySingleSchedulers())
                .toFlowable()
                .flatMapIterable { it }
                .flatMap {
                    if (it.unread > 0) {
                        val c = instance.applicationContext
                        val hasUnread: String = c.resources.getQuantityString(R.plurals.has_unread_plural, it.unread, it.unread)
                        val notificationTitle = "/${it.boardName}/${it.threadId} $hasUnread"
                        style.addLine(notificationTitle)

                        if (LOG_DEBUG) {
                            Log.d(LOG_TAG, "Creating notification for /" + it.boardName + "/" + it.threadId + ": id=" + NOTIFICATION_ID)
                        }
                    }

                    Flowable.just(it)
                }
                .toList()
                .subscribe(object : SingleObserver<List<QueueItem>> {
                    override fun onSuccess(queueItems: List<QueueItem>) {
                        var totalThreads = 0
                        var totalReplyCount = 0
                        var newPostCount = 0

                        for (queueItem in queueItems) {
                            if (queueItem.unread > 0) totalThreads++
                            totalReplyCount += queueItem.replyCount
                            newPostCount += queueItem.unread
                        }

                        if (notificationLevel == NOTIFICATIONS_ONLY_ME && totalReplyCount <= 0) {
                            return
                        }

                        val threadsUpdated: String = context.resources.getQuantityString(R.plurals.threads_updated_plural, totalThreads, totalThreads)
                        val repliesToYou: String = context.resources.getQuantityString(R.plurals.replies_to_you_plurals, totalReplyCount, totalReplyCount)
                        val newPostsText: String = context.resources.getQuantityString(R.plurals.new_post_plural, newPostCount, newPostCount)

                        val contentTitle = "$threadsUpdated with $newPostsText"

                        notificationBuilder.setContentTitle(newPostsText)
                        notificationBuilder.setContentText(context.getString(R.string.tap_to_open_bookmarks))

                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                        if (notificationManager != null) {
                            style.setBigContentTitle(contentTitle)
                            style.setSummaryText(repliesToYou)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val channelName: String = context.getString(R.string.mimi_thread_watcher)
                                val autoRefreshChannel = NotificationChannel(REFRESH_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT)
                                notificationBuilder.setChannelId(REFRESH_CHANNEL_ID)
                                notificationManager.createNotificationChannel(autoRefreshChannel)
                            }
                            val notification: Notification = notificationBuilder.build()
                            notificationManager.notify(NOTIFICATION_ID, notification)

                        }
                    }

                    override fun onSubscribe(d: Disposable) {
                        // no op
                    }

                    override fun onError(e: Throwable) {
                        Log.e(LOG_TAG, "Error building notification", e)
                    }
                })
    }
}
