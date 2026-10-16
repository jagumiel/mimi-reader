package com.emogoth.android.phone.mimi.util

import android.app.Activity
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.emogoth.android.phone.mimi.R
import java.util.WeakHashMap

/** Keeps content clear of system bars across legacy and enforced edge-to-edge Android versions. */
object SystemBarInsets {
    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private val initialPaddings = WeakHashMap<View, Padding>()

    @JvmStatic
    fun enable(activity: Activity, lightTheme: Boolean) {
        if (usesEnforcedEdgeToEdge()) {
            WindowCompat.enableEdgeToEdge(activity.window)
        } else {
            // Android 10-14 can reserve the system-bar area itself. Keeping decor fitting enabled
            // avoids a transient toolbar/status-bar overlap while insets are being dispatched.
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        }
        updateIconAppearance(activity, lightTheme, hasTopBar = false)
    }

    @JvmStatic
    fun apply(activity: Activity, lightTheme: Boolean) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        val primaryTopBar: View? = activity.findViewById(R.id.mimi_toolbar)
        val galleryTopBar: View? = activity.findViewById(R.id.toolbar)
        val topBar = primaryTopBar ?: galleryTopBar
        val primaryAppBar: View? = activity.findViewById(R.id.appbar)
        val galleryAppBar: View? = activity.findViewById(R.id.appBarLayout)
        val topInsetTarget = primaryAppBar ?: galleryAppBar ?: topBar

        updateIconAppearance(activity, lightTheme, topBar != null)
        if (!usesEnforcedEdgeToEdge()) {
            return
        }

        if (root is DrawerLayout) {
            val mainContent = root.children().firstOrNull { child ->
                val params = child.layoutParams as? DrawerLayout.LayoutParams
                params == null || params.gravity == Gravity.NO_GRAVITY
            }
            mainContent?.let {
                applyPadding(it, left = true, top = topBar == null, right = true, bottom = true)
            }
            root.children()
                    .filter { child ->
                        val params = child.layoutParams as? DrawerLayout.LayoutParams
                        params != null && params.gravity != Gravity.NO_GRAVITY
                    }
                    .forEach {
                        applyPadding(it, left = true, top = true, right = true, bottom = true)
                    }
        } else {
            applyPadding(root, left = true, top = topBar == null, right = true, bottom = true)
        }

        topInsetTarget?.let {
            applyPadding(it, left = false, top = true, right = false, bottom = false)
        }
    }

    private fun updateIconAppearance(activity: Activity, lightTheme: Boolean, hasTopBar: Boolean) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = lightTheme && !hasTopBar
            isAppearanceLightNavigationBars = lightTheme
        }
    }

    private fun usesEnforcedEdgeToEdge(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    private fun applyPadding(
            view: View,
            left: Boolean,
            top: Boolean,
            right: Boolean,
            bottom: Boolean
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val original = initialPaddings.getOrPut(target) {
                Padding(target.paddingLeft, target.paddingTop, target.paddingRight, target.paddingBottom)
            }
            val bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            target.setPadding(
                    original.left + if (left) bars.left else 0,
                    original.top + if (top) bars.top else 0,
                    original.right + if (right) bars.right else 0,
                    original.bottom + if (bottom) bars.bottom else 0
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun ViewGroup.children(): Sequence<View> = sequence {
        for (index in 0 until childCount) {
            yield(getChildAt(index))
        }
    }
}
