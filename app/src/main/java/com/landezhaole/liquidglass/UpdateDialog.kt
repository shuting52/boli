package com.landezhaole.liquidglass

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/** 液态玻璃更新弹窗：有新 APK 时可下载安装；否则展示网站最新更新公告。 */
@Composable
fun UpdateDialog(v: SiteVersion, backdrop: Backdrop, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val brand = LocalBrand.current.first
    val newer = v.hasApk && v.apkVersionCode > currentVersionCode(context)
    var progress by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, null) { if (!(newer && v.force)) onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(28.dp).widthIn(max = 420.dp)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(32.dp) },
                    effects = { vibrancy(); blur(16.dp.toPx()); lens(20.dp.toPx(), 40.dp.toPx(), chromaticAberration = true) },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(p.bg.copy(alpha = 0.6f)) }
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WaveLogo(48)
                Column {
                    BasicText(if (newer) "发现新版本" else "$brand 已更新", style = TextStyle(p.text, 20.sp, fontWeight = FontWeight.Black))
                    BasicText(
                        if (newer) "v${currentVersionName(context)} → ${v.apkVersionName.ifBlank { "#" + v.apkVersionCode }}"
                        else "网站 ${v.siteVersion} · ${v.date}",
                        style = TextStyle(p.accent, 13.sp)
                    )
                }
            }
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (v.notes.isNotBlank()) BasicText(v.notes, style = TextStyle(p.text, 14.sp))
                v.changes.forEach { c ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (c.tag.isNotBlank()) BasicText(c.tag, Modifier.clip(RoundedCornerShape(6.dp)).background(p.accent).padding(horizontal = 6.dp, vertical = 1.dp),
                            style = TextStyle(Color.White, 11.sp, fontWeight = FontWeight.Bold))
                        BasicText(c.text, style = TextStyle(p.text, 14.sp))
                    }
                }
                if (!v.hasApk) BasicText("网站暂未发布 App 安装包（顶栏「下载 Android App」显示待开发中），发布后这里会自动提示下载。",
                    style = TextStyle(p.muted, 12.sp))
            }
            if (progress.isNotBlank()) BasicText(progress, style = TextStyle(p.accent, 13.sp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                if (!(newer && v.force)) LiquidButton(onDismiss, backdrop, surfaceColor = p.text.copy(alpha = 0.1f)) {
                    BasicText(if (newer) "稍后" else "知道了", style = TextStyle(p.text, 15.sp))
                }
                if (newer) LiquidButton({ runCatching { downloadAndInstall(context, v.apkUrl) { progress = it } }
                    .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.apkUrl))) } }, backdrop, tint = p.accent) {
                    BasicText("立即更新", style = TextStyle(Color.White, 15.sp, fontWeight = FontWeight.Bold))
                } else LiquidButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SITE_URL))) }, backdrop, tint = p.accent) {
                    BasicText("打开官网", style = TextStyle(Color.White, 15.sp))
                }
            }
        }
    }
}
