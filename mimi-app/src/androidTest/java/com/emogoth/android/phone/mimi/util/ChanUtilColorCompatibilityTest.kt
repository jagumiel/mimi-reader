package com.emogoth.android.phone.mimi.util

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mimireader.chanlib.util.ChanUtil
import java.util.Locale
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChanUtilColorCompatibilityTest {
    @Test
    fun packedColorsMatchTheLegacyAndroidParser() {
        val names = mutableListOf("", "a", "hello", "Aa", "BB",
            "polygenelubricants", "zzzzzz", "\ud83d\ude00")
        val random = Random(2026L)
        repeat(128) {
            names.add(String(CharArray(16) { random.nextInt(0x10000).toChar() }))
        }
        for (name in names) {
            val legacyColor = Color.parseColor(
                String.format(Locale.ROOT, "#88%06X", name.hashCode() and 0x00ffffff))
            assertEquals("hash=${name.hashCode()}", legacyColor, ChanUtil.calculateColorBase(name))
        }
    }
}
