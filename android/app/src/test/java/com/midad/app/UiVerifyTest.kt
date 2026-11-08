package com.midad.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * تحقق واجهة مِداد في بيئة وهمية (Robolectric — إقلاع حقيقي للنشاط ورسم Skia)
 * يلتقط لقطات لكل الشاشات ويتأكد من الألوان الداكنة (لا أبيض) واكتمال العناصر.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class UiVerifyTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val shotDir = File("/tmp/midad-shots").apply { mkdirs() }

    /** رسم النافذة الحالية إلى صورة (بديل موثوق لـ captureToImage على Robolectric) */
    private fun saveShot(name: String): Bitmap {
        rule.waitForIdle()
        var decor: View? = null
        rule.activityRule.scenario.onActivity { decor = it.window.decorView }
        val view = decor!!
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        FileOutputStream(File(shotDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT $name ${bmp.width}x${bmp.height}")
        return bmp
    }

    private fun brightness(bmp: Bitmap, xFrac: Float, yFrac: Float, tag: String): Int {
        val x = (bmp.width * xFrac).roundToInt().coerceIn(0, bmp.width - 1)
        val y = (bmp.height * yFrac).roundToInt().coerceIn(0, bmp.height - 1)
        val c = bmp.getPixel(x, y)
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        println("PIXEL [$tag] x=$x y=$y rgb=($r,$g,$b)")
        return (r + g + b) / 3
    }

    private fun assertDark(name: String, xFrac: Float, yFrac: Float, tag: String, maxBrightness: Int) {
        val br = brightness(saveShot(name), xFrac, yFrac, tag)
        assertTrue("[$name] البكسل عند ($xFrac,$yFrac) فاتح جدًا ($br > $maxBrightness) — لون أبيض غير مرغوب!", br <= maxBrightness)
    }

    private fun hasText(t: String): Boolean =
        rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()

    /* ---- 1) الشاشة الرئيسية: ملاحظات ---- */
    @Test fun homeNotes_completeAndDark() {
        Thread.sleep(600)
        assertTrue(hasText("الملاحظات"))
        assertTrue(hasText("ابحث: العنوان أولًا ثم النص"))
        assertTrue(hasText("سكربتات مفيدة"))

        val bmp = saveShot("01-home-notes")
        val bg = brightness(bmp, 0.5f, 0.62f, "bg")
        assertTrue("الخلفية ليست داكنة ($bg)", bg < 70)
        val search = brightness(bmp, 0.5f, 0.135f, "search")
        assertTrue("شريط البحث أبيض! ($search)", search < 140)
        val nav = brightness(bmp, 0.22f, 0.945f, "nav")
        assertTrue("الشريط السفلي أبيض! ($nav)", nav < 140)
    }

    /* ---- 2) تبويب المهام ---- */
    @Test fun tasksTab_works() {
        Thread.sleep(400)
        rule.onAllNodesWithText("المهام")[0].performClick()
        Thread.sleep(500)
        assertTrue(hasText("جلسة تركيز"))
        val bmp = saveShot("02-home-tasks")
        val nav = brightness(bmp, 0.78f, 0.945f, "nav-tasks")
        assertTrue("الشريط السفلي أبيض في تبويب المهام! ($nav)", nav < 140)
    }

    /* ---- 3) المحرر ---- */
    @Test fun editor_opensAndDark() {
        Thread.sleep(400)
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(49.dp.toPx(), 770.dp.toPx())) }
        Thread.sleep(1000)
        assertTrue(hasText("اليوم"))
        val bmp = saveShot("03-editor")
        val bg = brightness(bmp, 0.5f, 0.6f, "editor-bg")
        assertTrue("خلفية المحرر ليست داكنة ($bg)", bg < 80)
        val toolbar = brightness(bmp, 0.5f, 0.955f, "toolbar")
        assertTrue("شريط أدوات المحرر أبيض! ($toolbar)", toolbar < 140)
    }

    /* ---- 4) الدرج (الإعدادات) ---- */
    @Test fun drawer_showsGlassSettings() {
        Thread.sleep(400)
        // زر النقاط أعلى اليسار — نجرب الموضعين (بحسب ارتفاع شريط الحالة في البيئة الوهمية)
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(24.dp.toPx(), 34.dp.toPx())) }
        Thread.sleep(500)
        if (!hasText("تأثير الزجاج السائل")) {
            rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(24.dp.toPx(), 58.dp.toPx())) }
            Thread.sleep(500)
        }
        assertTrue("الدرج لم يفتح — زر النقاط غير مصاب", hasText("تأثير الزجاج السائل"))
        assertTrue(hasText("تفعيل الزجاج السائل"))
        saveShot("04-drawer")
    }
}
