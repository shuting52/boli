package com.landezhaole.liquidglass

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新约定（读取 https://landezhaole.pages.dev/version.json，网站已存在此文件）：
 *  - 网站现有字段：version（网站版本，如 v1.1.65）、date、changes[{tag,text}]、history[]
 *  - App 更新字段（网站发布 APK 时加上即可，任选一种写法）：
 *      "android": { "versionCode": 2, "versionName": "1.1.0", "apkUrl": "https://.../app.apk", "notes": "..." , "force": false }
 *    或顶层 "apkUrl" + "versionCode"
 *  - versionCode 大于本机 → 弹出「发现新版本」液态玻璃弹窗，可直接下载安装。
 *  - 没有 apk 字段时：网站 version 变化 → 弹「网站已更新」公告（内容同步），不提供下载。
 */
const val SITE_URL = "https://landezhaole.pages.dev/"
const val VERSION_URL = "https://landezhaole.pages.dev/version.json"

data class Change(val tag: String, val text: String)
data class SiteVersion(
    val siteVersion: String, val date: String, val changes: List<Change>,
    val apkVersionCode: Int, val apkVersionName: String, val apkUrl: String, val notes: String, val force: Boolean
) {
    val hasApk get() = apkUrl.isNotBlank()
}

private fun get(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 15000
    c.setRequestProperty("Cache-Control", "no-cache")
    return c.inputStream.bufferedReader().use { it.readText() }
}

suspend fun fetchSiteVersion(): SiteVersion = withContext(Dispatchers.IO) {
    val j = JSONObject(get("$VERSION_URL?t=${System.currentTimeMillis()}"))
    val ch = j.optJSONArray("changes")
    val a = j.optJSONObject("android")
    fun pick(k: String) = a?.optString(k).orEmpty().ifBlank { j.optString(k) }
    val apk = pick("apkUrl").ifBlank { pick("apk") }
    SiteVersion(
        siteVersion = j.optString("version"), date = j.optString("date"),
        changes = if (ch == null) emptyList() else (0 until ch.length()).map {
            val o = ch.optJSONObject(it); Change(o?.optString("tag").orEmpty(), o?.optString("text") ?: ch.optString(it))
        },
        apkVersionCode = a?.optInt("versionCode") ?: j.optInt("versionCode"),
        apkVersionName = pick("versionName"),
        apkUrl = if (apk.isBlank() || apk.startsWith("http")) apk else SITE_URL + apk.removePrefix("/"),
        notes = pick("notes"),
        force = a?.optBoolean("force") ?: false
    )
}

/** 从网站 <title> 同步 App 名称：「懒得找了 · 免费资源导航」→ 名称 + 副标题 */
suspend fun fetchSiteBrand(): Pair<String, String> = withContext(Dispatchers.IO) {
    val html = get(SITE_URL)
    val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.trim().orEmpty()
    val parts = title.split("·").map { it.trim() }.filter { it.isNotEmpty() }
    (parts.getOrNull(0) ?: "懒得找了") to (parts.getOrNull(1) ?: "免费资源导航")
}

fun currentVersionCode(context: Context): Long {
    val pi = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
}
fun currentVersionName(context: Context): String =
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"

private fun prefs(c: Context) = c.getSharedPreferences("site", Context.MODE_PRIVATE)
fun lastSeenSiteVersion(c: Context) = prefs(c).getString("seen", "") ?: ""
fun markSeen(c: Context, v: String) = prefs(c).edit().putString("seen", v).apply()
fun saveBrand(c: Context, name: String, sub: String) = prefs(c).edit().putString("name", name).putString("sub", sub).apply()
fun cachedBrand(c: Context) = (prefs(c).getString("name", null) ?: "懒得找了") to (prefs(c).getString("sub", null) ?: "免费资源导航")

/** 用系统 DownloadManager 下载 APK，完成后拉起安装器。 */
fun downloadAndInstall(context: Context, url: String, onProgress: (String) -> Unit) {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
    val file = File(dir, "landezhaole-update.apk").also { it.delete() }
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val id = dm.enqueue(
        DownloadManager.Request(Uri.parse(url))
            .setTitle("懒得找了 更新包")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
    )
    onProgress("正在下载…（可在通知栏查看进度）")
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
            runCatching { context.applicationContext.unregisterReceiver(this) }
            if (file.exists() && file.length() > 0) { onProgress("下载完成，正在打开安装…"); installApk(context, file) }
            else onProgress("下载失败，请稍后重试或去官网下载")
        }
    }
    ContextCompat.registerReceiver(context.applicationContext, receiver,
        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
}

fun installApk(context: Context, file: File) {
    if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
}
