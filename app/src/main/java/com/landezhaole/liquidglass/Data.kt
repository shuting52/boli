package com.landezhaole.liquidglass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 数据源：https://landezhaole.pages.dev/data.json（与网页版同一份）。启动时在线刷新，失败则用缓存/内置快照。 */
const val DATA_URL = "https://landezhaole.pages.dev/data.json"

data class Site(val title: String, val url: String, val desc: String, val badge: String, val sub: String, val icon: String) {
    val host: String get() = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("")
}
data class Sub(val id: String, val name: String)
data class Category(val id: String, val name: String, val desc: String, val subs: List<Sub>, val sites: List<Site>)

/** 与网页 matchSub 完全一致：ai_api 分类的子标签通过映射表归类。 */
private val AI_API_MAP = mapOf(
    "official" to listOf("openai","google","anthropic","aliyun","baidu","tencent","volc","zhipu","moonshot","deepseek","minimax","step","xfyun","huawei","baichuan","yi","kunlun","jimeng","github","perplexity"),
    "aggregate" to listOf("openrouter","groq","together","replicate","huggingface","api2d","gptapi","closeai","pandora","oneapi","newapi"),
    "cloud" to listOf("cloudflare","azure","aws","gcp"),
)
fun Category.sitesFor(subId: String): List<Site> = when {
    subId == "all" -> sites
    id == "ai_api" -> sites.filter { (AI_API_MAP[subId] ?: emptyList()).contains(it.sub) }
    else -> sites.filter { it.sub == subId }
}

fun parseCategories(text: String): List<Category> {
    val cats = JSONObject(text).getJSONArray("cats")
    return (0 until cats.length()).map { i ->
        val c = cats.getJSONObject(i)
        val subs = c.optJSONArray("subs")
        val cards = c.optJSONArray("cards")
        Category(
            id = c.optString("id"), name = c.optString("name"), desc = c.optString("desc"),
            subs = if (subs == null) emptyList() else (0 until subs.length()).map {
                val s = subs.getJSONObject(it); Sub(s.optString("id"), s.optString("name"))
            },
            sites = if (cards == null) emptyList() else (0 until cards.length()).map {
                val s = cards.getJSONObject(it)
                Site(s.optString("t"), s.optString("u"), s.optString("d"), s.optString("b"), s.optString("s"), s.optString("i"))
            }
        )
    }
}

fun loadCachedCategories(context: Context): List<Category> {
    val cache = File(context.filesDir, "data.json")
    val text = if (cache.exists()) cache.readText() else context.assets.open("data.json").bufferedReader().use { it.readText() }
    return runCatching { parseCategories(text) }.getOrElse {
        parseCategories(context.assets.open("data.json").bufferedReader().use { it.readText() })
    }
}

suspend fun fetchLiveCategories(context: Context): List<Category> = withContext(Dispatchers.IO) {
    val conn = URL("$DATA_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
    conn.connectTimeout = 8000; conn.readTimeout = 15000
    val text = conn.inputStream.bufferedReader().use { it.readText() }
    val cats = parseCategories(text)
    if (cats.isNotEmpty()) File(context.filesDir, "data.json").writeText(text)
    cats
}

// ---- 图标：与网页同样的回退链 i → Google favicon → apple-touch-icon → favicon.ico → 首字母头像 ----
private val iconCache = LruCache<String, Bitmap>(400)
private val failed = HashSet<String>()

suspend fun loadIcon(site: Site): Bitmap? = withContext(Dispatchers.IO) {
    val key = site.url
    iconCache.get(key)?.let { return@withContext it }
    if (synchronized(failed) { key in failed }) return@withContext null
    val host = site.host
    val candidates = buildList {
        if (site.icon.isNotBlank()) add(site.icon)
        add("https://www.google.com/s2/favicons?domain=$host&sz=128")
        add("https://$host/apple-touch-icon.png")
        add("https://$host/favicon.ico")
    }
    for (u in candidates) {
        val bmp = runCatching {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 8000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) LandezhaoleGlass")
            c.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        // 跳过 Google 兜底的 16px 小地球
        if (bmp != null && !(u.contains("google.com/s2") && bmp.width <= 16)) {
            iconCache.put(key, bmp); return@withContext bmp
        }
    }
    synchronized(failed) { failed += key }
    null
}
