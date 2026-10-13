package com.emogoth.android.phone.mimi.prefs

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.MimiActivity
import com.emogoth.android.phone.mimi.util.MimiUtil

class SettingsActivity : MimiActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    override val pageName: String? = "app_settings"

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(MimiUtil.getInstance().themeResourceId)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        val toolbar = findViewById<Toolbar>(R.id.mimi_toolbar)

        toolbar.setTitle(R.string.settings)
        toolbar.logo = null
        toolbar.setNavigationIcon(R.drawable.ic_nav_arrow_back)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()

    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat?, pref: Preference?): Boolean {
        if (pref == null) {
            return false
        }

        Log.d("SettingsActivity", "Opening ${pref.fragment}...")
        if (classLoader == null) {
            Log.e("SettingsActivity", "Classloader is null", Exception())
            return false
        }

        // Instantiate the new Fragment
        val args = pref.extras
//        val fragment = supportFragmentManager.fragmentFactory.instantiate(
//                classLoader,
//                pref.fragment)

        val fragment = when(pref.fragment) {
            "com.emogoth.android.phone.mimi.prefs.GeneralPrefsFragment" -> GeneralPrefsFragment()
            "com.emogoth.android.phone.mimi.prefs.GalleryPrefsFragment" -> GalleryPrefsFragment()
            "com.emogoth.android.phone.mimi.prefs.AppearancePrefsFragment" -> AppearancePrefsFragment()
            "com.emogoth.android.phone.mimi.prefs.HistoryPrefsFragment" -> HistoryPrefsFragment()
            "com.emogoth.android.phone.mimi.prefs.AdvancedPrefsFragment" -> AdvancedPrefsFragment()
            "com.emogoth.android.phone.mimi.prefs.MoreInfoPrefsFragment" -> MoreInfoPrefsFragment()
            else -> throw Exception("Class not found: ${pref.fragment}")
        }

        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
                .replace(R.id.settings, fragment)
                .addToBackStack(null)
                .commit()
        return true

    }
}
