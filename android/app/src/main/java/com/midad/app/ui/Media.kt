package com.midad.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midad.app.store.AppStore

/** حشوات آمنة أعلى/أسفل للنوافذ المنبثقة */
fun Modifier.statusBarsPaddingSafe(): Modifier = this.then(
    androidx.compose.ui.Modifier.statusBarsPadding()
)

fun Modifier.navigationBarsPaddingSafe(): Modifier = this.then(
    androidx.compose.ui.Modifier.navigationBarsPadding()
)

/** CompositionLocal للمخزن */
val LocalStore = staticCompositionLocalOf<AppStore> { error("AppStore غير متوفر") }

/** صورة مخزنة بمُعرّف img:<id> — تحميل غير مانع مع ذاكرة مؤقتة */
@Composable
fun MidadImage(store: AppStore, ref: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bmp by remember(ref) { mutableStateOf(store.loadBitmap(ref)) }
    LaunchedEffect(ref) {
        if (bmp == null) {
            bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                store.loadBitmap(ref)
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(modifier.background(Color(0xFF272219)))
    }
}

/** حالة الفراغ — نفس نصوص الويب */
@Composable
fun EmptyState(icon: String, title: String, sub: String) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MidadIcon(icon, 34.dp, colors.inkFaint)
        Spacer(Modifier.height(12.dp))
        MText(title, color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans)
        MText(
            sub, color = colors.inkSoft, fontSize = 12.5.sp, fontFamily = fonts.sans,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
