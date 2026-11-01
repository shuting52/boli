package com.landezhaole.liquidglass

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

private val AV_COLORS = listOf(0xFF16A34A, 0xFF0EA5E9, 0xFF8B5CF6, 0xFFF59E0B, 0xFFEF4444, 0xFFEC4899, 0xFF14B8E6, 0xFFF97316).map { Color(it) }
/** 与网页 avColor 相同的哈希取色。 */
fun avColor(t: String): Color { var h = 0L; for (c in t) h = (h * 31 + c.code) and 0xFFFFFFFFL; return AV_COLORS[(h % AV_COLORS.size).toInt()] }

/** 网页 .badge：HOT=橙、NEW=红、其他=主色；无角标显示「精选」。 */
@Composable
fun badgeColor(b: String): Color {
    val p = LocalPalette.current
    return when {
        b.contains("HOT", true) -> if (p == DarkPalette) Color(0xFFFF9F0A) else Color(0xFFF59E0B)
        b.contains("NEW", true) -> if (p == DarkPalette) Color(0xFFFF7B72) else Color(0xFFDC2626)
        else -> p.accent
    }
}

@Composable
fun NavigatorScreen(@Suppress("UNUSED_PARAMETER") rootBackdrop: Backdrop) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cats by remember { mutableStateOf(runCatching { loadCachedCategories(context) }.getOrDefault(emptyList())) }
    var status by remember { mutableStateOf("正在同步网页版数据…") }
    suspend fun refresh() {
        status = "正在同步网页版数据…"
        status = runCatching { cats = fetchLiveCategories(context); "已与 landezhaole.pages.dev 同步" }
            .getOrElse { "离线模式 · 使用本地数据" }
    }
    LaunchedEffect(Unit) { refresh() }

    var catIndex by rememberSaveable { mutableIntStateOf(0) }
    var subId by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    val total = cats.sumOf { it.sites.size }
    val cat = cats.getOrNull(catIndex.coerceAtMost((cats.size - 1).coerceAtLeast(0)))
    val searching = query.isNotBlank()

    data class Row(val site: Site, val catName: String)
    val rows = remember(cats, catIndex, subId, query) {
        if (searching) cats.flatMap { c -> c.sites.map { Row(it, c.name.substringBefore(" ·")) } }
            .filter { it.site.title.contains(query, true) || it.site.desc.contains(query, true) || it.site.url.contains(query, true) || it.site.badge.contains(query, true) }
            .distinctBy { it.site.url + it.site.title }
        else cat?.sitesFor(subId)?.map { Row(it, "") }.orEmpty()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(catIndex, subId, query) { listState.scrollToItem(0) }

    val density = LocalDensity.current
    var headerH by remember { mutableStateOf(260.dp) }
    val pageBackdrop = rememberLayerBackdrop()
    val bgBackdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
        DirectoryWallpaper(Modifier.layerBackdrop(bgBackdrop))
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = headerH + 4.dp, bottom = 128.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "section") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(p.accent))
                        val subName = cat?.subs?.firstOrNull { it.id == subId }?.name ?: "全部"
                        BasicText(
                            if (searching) "搜索 “$query”  ·  ${rows.size} 个结果"
                            else "${cat?.name.orEmpty()}  ·  $subName  ·  ${rows.size} 个站点",
                            style = TextStyle(p.muted, 13.sp)
                        )
                    }
                    if (!searching && !cat?.desc.isNullOrBlank()) BasicText(cat!!.desc, style = TextStyle(p.muted.copy(alpha = 0.6f), 12.sp))
                }
            }
            if (rows.isEmpty()) item { BasicText("暂无站点", Modifier.fillMaxWidth().padding(40.dp), style = TextStyle(p.muted, 14.sp)) }
            items(rows, key = { it.site.url + it.site.title + it.catName }) { r ->
                SiteCard(r.site, r.catName, bgBackdrop) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.site.url))) }
                }
            }
            item(key = "footer") {
                BasicText("本站共收录 ${cats.size} 个分类 · $total 个站点\n$status",
                    Modifier.fillMaxWidth().padding(16.dp), style = TextStyle(p.muted.copy(alpha = 0.6f), 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }

      }
        // ---------- 顶部液态玻璃头 ----------
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                .onSizeChanged { headerH = with(density) { it.height.toDp() } + 24.dp }
                .glass(pageBackdrop, 28f, if (p == DarkPalette) Color(0xFF121212).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.6f), blurDp = 14f)
                .border(1.dp, p.text.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WaveLogo(46)
                Column(Modifier.weight(1f)) {
                    BasicText(LocalBrand.current.first, style = TextStyle(p.text, 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp))
                    BasicText("${LocalBrand.current.second} · ${cats.size} 个分类 · $total 个精选站点", style = TextStyle(p.muted, 11.sp), maxLines = 1)
                }
                BasicText("↻", Modifier.clip(CircleShape).clickable { scope.launch { refresh() } }.padding(8.dp), style = TextStyle(p.accent, 20.sp, fontWeight = FontWeight.Bold))
            }
            Box(
                Modifier.fillMaxWidth().height(46.dp)
                    .drawBackdrop(pageBackdrop, { Capsule() }, effects = {
                        vibrancy(); blur(2.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx())
                    }, onDrawSurface = { drawRect(p.text.copy(alpha = 0.06f)) })
                    .border(1.dp, p.text.copy(alpha = 0.15f), RoundedCornerShape(23.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) BasicText("🔍  一搜即达…（全部 $total 个站点）", style = TextStyle(p.muted, 14.sp))
                BasicTextField(query, { query = it }, singleLine = true,
                    textStyle = TextStyle(p.text, 14.sp), cursorBrush = SolidColor(p.accent),
                    modifier = Modifier.fillMaxWidth().padding(end = if (query.isEmpty()) 0.dp else 28.dp))
                if (query.isNotEmpty()) BasicText("✕", Modifier.align(Alignment.CenterEnd).clickable { query = "" }.padding(4.dp), style = TextStyle(p.muted, 16.sp))
            }
            // 分类（全部 21 个）
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(cats, key = { _, c -> c.id }) { i, c ->
                    CatChip(c.name.substringBefore(" ·"), c.sites.size, i == catIndex && !searching) { catIndex = i; subId = "all"; query = "" }
                }
            }
            // 子分类（网页 .tabs 样式：胶囊底条 + 选中白色/高亮药丸）
            val subs = cat?.subs.orEmpty()
            if (subs.isNotEmpty() && !searching) LazyRow(
                Modifier.clip(RoundedCornerShape(999.dp)).background(p.text.copy(alpha = 0.05f)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(subs, key = { it.id }) { s ->
                    val on = s.id == subId
                    val n = cat!!.sitesFor(s.id).size
                    BasicText("${s.name} $n",
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (on) p.accent else Color.Transparent)
                            .clickable { subId = s.id }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        style = TextStyle(if (on) (if (p == DarkPalette) Color(0xFF0D0D0D) else Color.White) else p.muted, 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal))
                }
            }
        }
    }
}

@Composable
fun CatChip(text: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) p.accent else p.text.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val fg = if (selected) (if (p == DarkPalette) Color(0xFF0D0D0D) else Color.White) else p.text
        BasicText(text, style = TextStyle(fg, 14.sp, fontWeight = FontWeight.Medium))
        BasicText("$count", style = TextStyle(fg.copy(alpha = 0.6f), 11.sp))
    }
}

@Composable
fun SiteIcon(site: Site) {
    var bmp by remember(site.url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(site.url) { bmp = loadIcon(site)?.asImageBitmap() }
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(avColor(site.title)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Image(b, null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
        } else BasicText(site.title.trim().take(1).uppercase(), style = TextStyle(Color.White, 22.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun SiteCard(site: Site, catName: String, backdrop: Backdrop, onClick: () -> Unit) {
    val p = LocalPalette.current
    val dark = p == DarkPalette
    Row(
        Modifier.fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { com.kyant.shapes.RoundedRectangle(22.dp) },
                effects = { vibrancy(); blur(10.dp.toPx()); lens(10.dp.toPx(), 20.dp.toPx()) },
                highlight = { com.kyant.backdrop.highlight.Highlight.Plain },
                onDrawSurface = { drawRect(if (dark) Color(0xFF181818).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.6f)) }
            )
            .border(1.dp, p.text.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SiteIcon(site)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(site.title, style = TextStyle(p.text, 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                val b = site.badge.ifBlank { "精选" }
                BasicText(b,
                    Modifier.clip(RoundedCornerShape(8.dp)).background(if (site.badge.isBlank()) p.text.copy(alpha = 0.15f) else badgeColor(b)).padding(horizontal = 6.dp, vertical = 1.dp),
                    style = TextStyle(Color.White, 10.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1)
            }
            BasicText(site.desc, style = TextStyle(p.muted, 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (catName.isNotBlank()) BasicText("$catName · ${site.host}", style = TextStyle(p.accent.copy(alpha = 0.8f), 11.sp), maxLines = 1)
        }
    }
}


/** 导航页背景：深灰底 + 品牌色光斑，供玻璃卡片折射/模糊。 */
@Composable
fun DirectoryWallpaper(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
        drawRect(p.bg)
        val blobs = listOf(Color(0xFF16A34A), Color(0xFF0EA5E9), Color(0xFF55CFFF), Color(0xFF8B5CF6), Color(0xFFF59E0B), Color(0xFFEC4899))
        for (i in 0 until 9) {
            val c = blobs[i % blobs.size]
            val x = ((i * 53 + 17) % 100) / 100f * size.width
            val y = ((i * 37 + 11) % 100) / 100f * size.height
            val r = size.width * (0.18f + (i % 3) * 0.08f)
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.copy(alpha = if (p == DarkPalette) 0.35f else 0.25f), Color.Transparent), androidx.compose.ui.geometry.Offset(x, y), r), r, androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}
