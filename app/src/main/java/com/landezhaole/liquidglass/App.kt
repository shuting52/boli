package com.landezhaole.liquidglass

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/** 品牌色取自 landezhaole.pages.dev 的 CSS 变量（libraries-light / libraries-dark 两套主题）。 */
data class Palette(
    val bg: Color, val card: Color, val text: Color, val muted: Color, val accent: Color, val glass: Color
)
val LightPalette = Palette(Color(0xFFFDFDFD), Color.White, Color(0xFF0D0D0D), Color(0xFF6C6C6C), Color(0xFF0073E5), Color.White.copy(alpha = 0.5f))
val DarkPalette = Palette(Color(0xFF121212), Color(0xFF181818), Color.White, Color(0xB3CACACA), Color(0xFF55CFFF), Color(0xFF121212).copy(alpha = 0.4f))
val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalBrand = staticCompositionLocalOf { "懒得找了" to "免费资源导航" }
val BrandGradient = Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF0EA5E9)))

@Composable
fun LandezhaoleApp() {
    var dark by rememberSaveable { mutableStateOf(true) } // 默认深色，与网站 libraries-dark 一致
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val palette = if (dark) DarkPalette else LightPalette
    val context = LocalContext.current
    var brand by remember { mutableStateOf(cachedBrand(context)) }
    var update by remember { mutableStateOf<SiteVersion?>(null) }
    var checkMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun check(manual: Boolean) {
        runCatching { fetchSiteBrand() }.onSuccess { brand = it; saveBrand(context, it.first, it.second) }
        runCatching { fetchSiteVersion() }.onSuccess { v ->
            val newer = v.hasApk && v.apkVersionCode > currentVersionCode(context)
            val siteChanged = v.siteVersion.isNotBlank() && v.siteVersion != lastSeenSiteVersion(context)
            if (newer || siteChanged || manual) update = v
            checkMsg = if (newer) "发现新版本 ${v.apkVersionName}" else "已是最新版 · 网站 ${v.siteVersion}"
        }.onFailure { checkMsg = "检查更新失败：无法连接 landezhaole.pages.dev" }
    }
    LaunchedEffect(Unit) { check(false) }

    CompositionLocalProvider(LocalPalette provides palette, LocalBrand provides brand) {
        // 核心写法：根部记住一层背景，页面内容挂到 layerBackdrop 上，玻璃组件再 drawBackdrop
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().background(palette.bg)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                when (tab) {
                    0 -> NavigatorScreen(backdrop)
                    1 -> PlaygroundScreen(backdrop)
                    else -> AboutScreen(backdrop, dark, checkMsg, { scope.launch { check(true) } }) { dark = it }
                }
            }
            // 底部液态玻璃 Tab（演示里的 LiquidBottomTabs）
            val labels = listOf("导航" to "🧭", "玻璃实验室" to "🫧", "关于" to "ℹ️")
            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { tab = it },
                backdrop = backdrop,
                tabsCount = labels.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 36.dp, vertical = 12.dp)
            ) {
                labels.forEachIndexed { i, (label, icon) ->
                    LiquidBottomTab({ tab = i }) {
                        BasicText(icon, style = TextStyle(fontSize = 20.sp))
                        BasicText(label, style = TextStyle(palette.text, 12.sp))
                    }
                }
            }
            update?.let { v -> UpdateDialog(v, backdrop) { markSeen(context, v.siteVersion); update = null } }
        }
    }
}

/** 统一的玻璃面板：vibrancy + blur + lens，正是技能文档的核心写法。 */
fun Modifier.glass(backdrop: Backdrop, radius: Float, surface: Color, blurDp: Float = 4f, refraction: Pair<Float, Float> = 16f to 32f) =
    this.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedRectangle(radius.dp) },
        effects = {
            vibrancy()
            blur(blurDp.dp.toPx())
            lens(refraction.first.dp.toPx(), refraction.second.dp.toPx())
        },
        highlight = { Highlight.Plain },
        onDrawSurface = { drawRect(surface) }
    )

@Composable
fun WaveLogo(size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(BrandGradient),
        contentAlignment = Alignment.Center
    ) {
        BasicText("懒", style = TextStyle(Color.White, (size / 2.2f).sp, fontWeight = FontWeight.Black))
    }
}


// ---------------- 玻璃实验室（GlassPlayground 风格） ----------------
@Composable
fun Wallpaper(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF0EA5E9), Color(0xFF6366F1)), Offset.Zero, Offset(size.width, size.height)))
        val colors = listOf(Color(0xFFFFD60A), Color(0xFFFF375F), Color(0xFF30D158), Color(0xFFFFFFFF), Color(0xFFFF9F0A))
        for (i in 0 until 14) {
            val x = ((i * 137) % 100) / 100f * size.width
            val y = ((i * 71) % 100) / 100f * size.height
            drawCircle(colors[i % colors.size].copy(alpha = 0.8f), radius = 30f + (i % 5) * 28f, center = Offset(x, y))
        }
        val step = 48.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(Color.White.copy(alpha = 0.25f), Offset(gx, 0f), Offset(gx, size.height), 2f); gx += step }
    }
}

@Composable
fun PlaygroundScreen(@Suppress("UNUSED_PARAMETER") rootBackdrop: Backdrop) {
    val p = LocalPalette.current
    val backdrop = rememberLayerBackdrop()
    var corner by remember { mutableFloatStateOf(0.5f) }
    var blurDp by remember { mutableFloatStateOf(4f) }
    var height by remember { mutableFloatStateOf(0.2f) }
    var amount by remember { mutableFloatStateOf(0.25f) }
    var chroma by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(true) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(Modifier.layerBackdrop(backdrop))
        // 可拖动的大玻璃块
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 48.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(128.dp * corner) },
                    effects = {
                        val m = size.minDimension
                        vibrancy()
                        blur(blurDp.dp.toPx())
                        lens(height * m * 0.5f, amount * m, depthEffect = true, chromaticAberration = chroma)
                    },
                    highlight = { Highlight.Plain },
                    layerBlock = { translationX = offset.x; translationY = offset.y }
                )
                .androidx_drag { offset += it }
                .size(256.dp)
        )
        if (showSheet) {
            val sheetBackdrop = rememberLayerBackdrop()
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 168.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(32.dp) },
                        effects = { vibrancy(); blur(4.dp.toPx()); lens(16.dp.toPx(), 32.dp.toPx()) },
                        highlight = { Highlight.Plain },
                        exportedBackdrop = sheetBackdrop,
                        onDrawSurface = { drawRect(p.glass) }
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LabeledSlider("圆角", corner, 0f..1f, sheetBackdrop) { corner = it }
                LabeledSlider("模糊 ${blurDp.toInt()}dp", blurDp, 0f..32f, sheetBackdrop) { blurDp = it }
                LabeledSlider("折射高度", height, 0f..1f, sheetBackdrop) { height = it }
                LabeledSlider("折射强度", amount, 0f..1f, sheetBackdrop) { amount = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("色散", Modifier.weight(1f), style = TextStyle(p.text, 14.sp))
                    LiquidToggle({ chroma }, { chroma = it }, sheetBackdrop)
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LiquidButton({ showSheet = !showSheet }, backdrop, tint = Color(0xFF0073E5)) {
                BasicText(if (showSheet) "收起面板" else "展开面板", style = TextStyle(Color.White, 15.sp))
            }
            LiquidButton({ corner = 0.5f; blurDp = 4f; height = 0.2f; amount = 0.25f; chroma = false; offset = Offset.Zero }, backdrop, tint = Color(0xFF16A34A)) {
                BasicText("重置", style = TextStyle(Color.White, 15.sp))
            }
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, backdrop: Backdrop, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(label, style = TextStyle(p.text, 13.sp))
        com.kyant.backdrop.catalog.components.LiquidSlider(
            value = { value }, onValueChange = onChange, valueRange = range,
            visibilityThreshold = 0.001f, backdrop = backdrop
        )
    }
}

// ---------------- 关于 ----------------
@Composable
fun AboutScreen(backdrop: Backdrop, dark: Boolean, checkMsg: String, onCheck: () -> Unit, onDark: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val local = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Wallpaper(Modifier.layerBackdrop(local))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(16.dp).padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().glass(local, 32f, p.glass).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WaveLogo(88)
                BasicText(LocalBrand.current.first, style = TextStyle(p.text, 30.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp))
                BasicText(LocalBrand.current.second + " · 一搜即达 · v" + currentVersionName(context), style = TextStyle(p.muted, 14.sp))
            }
            Row(Modifier.fillMaxWidth().glass(local, 24f, p.glass).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("深色主题 (libraries-dark)", Modifier.weight(1f), style = TextStyle(p.text, 15.sp))
                LiquidToggle({ dark }, onDark, local)
            }
            Column(Modifier.fillMaxWidth().glass(local, 24f, p.glass).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("液态玻璃引擎", style = TextStyle(p.text, 16.sp, fontWeight = FontWeight.Bold))
                BasicText("基于 AndroidLiquidGlass 2.0.1（io.github.kyant0:backdrop）。折射需 Android 13+，模糊需 Android 12+，更低版本自动降级。", style = TextStyle(p.muted, 13.sp))
            }
            LiquidButton(onCheck, local, Modifier.align(Alignment.CenterHorizontally), tint = Color(0xFF16A34A)) {
                BasicText("检查更新", style = TextStyle(Color.White, 15.sp))
            }
            if (checkMsg.isNotBlank()) BasicText(checkMsg, Modifier.align(Alignment.CenterHorizontally), style = TextStyle(Color.White, 12.sp))
            LiquidButton({ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://landezhaole.pages.dev/"))) } },
                local, Modifier.align(Alignment.CenterHorizontally), tint = p.accent) {
                BasicText("打开网页版 landezhaole.pages.dev", style = TextStyle(Color.White, 15.sp))
            }
        }
    }
}
