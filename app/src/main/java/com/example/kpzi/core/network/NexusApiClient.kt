package com.example.kpzi.core.network

import com.example.kpzi.domain.model.CompatibilityLevel
import com.example.kpzi.domain.model.ModCategory
import com.example.kpzi.domain.model.ModChangelogEntry
import com.example.kpzi.domain.model.NexusModDetail
import com.example.kpzi.domain.model.NexusModFile
import com.example.kpzi.domain.model.NexusModSummary
import com.example.kpzi.domain.model.NexusUserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Official Nexus Mods REST v1 & GraphQL v2 API client for the `stardewvalley` domain.
 * Strictly adheres to Nexus Mods API terms: no scraping, no bundled developer API keys,
 * and no bypassing Premium download restrictions.
 */
class NexusApiClient(
    private val quotaManager: NexusQuotaManager,
) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun validateApiKey(apiKey: String): NexusUserProfile = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$REST_BASE/users/validate.json")
            .header("apikey", apiKey.trim())
            .header("Application-Name", APP_NAME)
            .header("Application-Version", APP_VERSION)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            quotaManager.inspectResponseOrThrow(response)
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw NexusApiException(response.code, parseErrorMessage(body, "ไม่สามารถยืนยัน API Key ได้ (${response.code})"))
            }
            val json = JSONObject(body)
            NexusUserProfile(
                userId = json.optLong("user_id", 0L),
                username = json.optString("name", "Nexus User"),
                email = json.optString("email").takeIf { it.isNotBlank() && it != "null" },
                profileUrl = json.optString("profile_url").takeIf { it.isNotBlank() && it != "null" },
                isPremium = json.optBoolean("is_premium", false),
                isSupporter = json.optBoolean("is_supporter", false),
                isConnected = true,
            )
        }
    }

    suspend fun fetchFeed(
        feedType: String, // "trending", "latest_added", "latest_updated"
        apiKey: String,
    ): List<NexusModSummary> = quotaManager.executeDeduplicated("feed:$feedType") {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$REST_BASE/games/$GAME_DOMAIN/mods/$feedType.json")
                .header("apikey", apiKey)
                .header("Application-Name", APP_NAME)
                .header("Application-Version", APP_VERSION)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                quotaManager.inspectResponseOrThrow(response)
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw NexusApiException(response.code, parseErrorMessage(body, "โหลดรายการมอดไม่สำเร็จ (${response.code})"))
                }
                val arr = JSONArray(body)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val parsed = parseRestModSummary(obj, feedType)
                        if (parsed.modId > 0L) add(parsed)
                    }
                }
            }
        }
    }

    /**
     * Queries public mods via Nexus Mods GraphQL v2 API with pagination (35 items per page).
     * Works with or without a personal API key when the public GraphQL endpoint allows it.
     */
    suspend fun searchCatalogGraphQl(
        query: String,
        category: ModCategory,
        offset: Int,
        count: Int = 35,
        apiKey: String? = null,
    ): List<NexusModSummary> = quotaManager.executeDeduplicated("gql:${query.trim()}:${category.name}:$offset:$count") {
        withContext(Dispatchers.IO) {
            val effectiveSearch = listOfNotNull(
                query.trim().takeIf { it.isNotBlank() },
                category.queryKeyword,
            ).joinToString(" ").trim()

            val sortField = when (category) {
                ModCategory.NEW -> """{ createdAt: { direction: DESC } }"""
                ModCategory.RECENTLY_UPDATED -> """{ updatedAt: { direction: DESC } }"""
                ModCategory.POPULAR -> """{ downloads: { direction: DESC } }"""
                else -> """{ endorsements: { direction: DESC } }"""
            }

            val nameFilter = if (effectiveSearch.isNotBlank()) {
                val escaped = effectiveSearch.replace("\\", "\\\\").replace("\"", "\\\"")
                """, name: [{ value: "$escaped", op: WILDCARD }]"""
            } else {
                ""
            }

            val gqlQuery = """
                query KpziCatalogMods(${'$'}offset: Int!, ${'$'}count: Int!) {
                  mods(
                    filter: {
                      gameDomainName: [{ value: "$GAME_DOMAIN", op: EQUALS }]
                      $nameFilter
                    }
                    sort: [$sortField]
                    offset: ${'$'}offset
                    count: ${'$'}count
                  ) {
                    nodes {
                      modId
                      name
                      summary
                      version
                      author
                      downloads
                      endorsements
                      updatedAt
                      pictureUrl
                      modCategory {
                        name
                      }
                    }
                  }
                }
            """.trimIndent()

            val payload = JSONObject().apply {
                put("query", gqlQuery)
                put("variables", JSONObject().apply {
                    put("offset", offset)
                    put("count", count.coerceIn(30, 50))
                })
            }

            val reqBuilder = Request.Builder()
                .url(GRAPHQL_ENDPOINT)
                .header("Content-Type", "application/json")
                .header("Application-Name", APP_NAME)
                .header("Application-Version", APP_VERSION)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))

            if (!apiKey.isNullOrBlank()) {
                reqBuilder.header("apikey", apiKey)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                quotaManager.inspectResponseOrThrow(response)
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw NexusApiException(response.code, parseErrorMessage(body, "ค้นหามอดผ่าน GraphQL ไม่สำเร็จ (${response.code})"))
                }
                val root = JSONObject(body)
                val errors = root.optJSONArray("errors")
                if (errors != null && errors.length() > 0) {
                    val msg = errors.optJSONObject(0)?.optString("message").orEmpty()
                    throw NexusApiException(response.code, msg.ifBlank { "GraphQL query error" })
                }
                val nodes = root.optJSONObject("data")
                    ?.optJSONObject("mods")
                    ?.optJSONArray("nodes") ?: JSONArray()

                buildList {
                    for (i in 0 until nodes.length()) {
                        val node = nodes.optJSONObject(i) ?: continue
                        val modId = node.optLong("modId", 0L)
                        if (modId <= 0L) continue
                        val catName = node.optJSONObject("modCategory")?.optString("name").orEmpty()
                        add(
                            NexusModSummary(
                                modId = modId,
                                name = node.optString("name", "Mod #$modId"),
                                summary = node.optString("summary", ""),
                                author = node.optString("author", "Unknown"),
                                version = node.optString("version", "1.0"),
                                categoryName = catName.ifBlank { "Stardew Valley" },
                                pictureUrl = node.optString("pictureUrl").takeIf { it.isNotBlank() && it != "null" },
                                downloads = node.optLong("downloads", 0L),
                                endorsements = node.optLong("endorsements", 0L),
                                updatedEpochSec = parseIsoOrEpoch(node.opt("updatedAt")),
                                fileSizeBytes = null,
                                compatibility = CompatibilityLevel.UNVERIFIED,
                                feedTag = "graphql",
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun fetchModDetail(modId: Long, apiKey: String): NexusModDetail =
        quotaManager.executeDeduplicated("detail:$modId") {
            withContext(Dispatchers.IO) {
                val modJson = executeRestGet("$REST_BASE/games/$GAME_DOMAIN/mods/$modId.json", apiKey)
                val summary = parseRestModSummary(modJson, "detail")

                val filesJson = runCatching {
                    executeRestGet("$REST_BASE/games/$GAME_DOMAIN/mods/$modId/files.json", apiKey)
                }.getOrNull()

                val files = buildList {
                    val filesArr = filesJson?.optJSONArray("files") ?: JSONArray()
                    for (i in 0 until filesArr.length()) {
                        val f = filesArr.optJSONObject(i) ?: continue
                        add(
                            NexusModFile(
                                fileId = f.optLong("file_id", 0L),
                                name = f.optString("name", f.optString("file_name", "Archive")),
                                version = f.optString("version", summary.version),
                                fileName = f.optString("file_name", "mod.zip"),
                                sizeBytes = f.optLong("size_in_bytes", f.optLong("size_kb", 0L) * 1024L),
                                categoryName = f.optString("category_name", "MAIN"),
                                uploadedEpochSec = f.optLong("uploaded_timestamp", 0L),
                                isPrimary = f.optBoolean("is_primary", false),
                                contentPreviewLink = f.optString("content_preview_link").takeIf { it.isNotBlank() && it != "null" },
                            )
                        )
                    }
                }.sortedByDescending { it.isPrimary }

                val changelogJson = runCatching {
                    executeRestGet("$REST_BASE/games/$GAME_DOMAIN/mods/$modId/changelogs.json", apiKey)
                }.getOrNull()

                val changelogs = buildList {
                    if (changelogJson != null) {
                        val keys = changelogJson.keys()
                        while (keys.hasNext()) {
                            val ver = keys.next()
                            val arr = changelogJson.optJSONArray(ver) ?: continue
                            val lines = buildList {
                                for (i in 0 until arr.length()) {
                                    val line = arr.optString(i).trim()
                                    if (line.isNotEmpty()) add(line)
                                }
                            }
                            add(ModChangelogEntry(version = ver, changes = lines))
                        }
                    }
                }

                val rawDescription = modJson.optString("description", summary.summary)
                val cleanDescription = stripBbCode(rawDescription).ifBlank { summary.summary }
                val primaryFileSize = files.firstOrNull()?.sizeBytes

                NexusModDetail(
                    summary = summary.copy(fileSizeBytes = primaryFileSize ?: summary.fileSizeBytes),
                    description = cleanDescription,
                    previewImageUrls = listOfNotNull(summary.pictureUrl),
                    files = files,
                    dependencies = extractDependenciesHint(cleanDescription),
                    supportedGameVersions = extractGameVersionsHint(cleanDescription),
                    requiredSmapiVersion = extractSmapiVersionHint(cleanDescription),
                    changelogs = changelogs,
                    licenseName = modJson.optString("license").takeIf { it.isNotBlank() && it != "null" }
                        ?: "อ้างอิงสิทธิ์ตามหน้าต้นฉบับ Nexus Mods",
                    nexusUrl = "https://www.nexusmods.com/$GAME_DOMAIN/mods/$modId",
                )
            }
        }

    suspend fun requestDownloadLinks(
        modId: Long,
        fileId: Long,
        apiKey: String,
        nxmKey: String? = null,
        nxmExpires: String? = null,
    ): List<String> = quotaManager.executeDeduplicated("dl:$modId:$fileId:${nxmKey.orEmpty()}") {
        withContext(Dispatchers.IO) {
            val urlBuilder = StringBuilder("$REST_BASE/games/$GAME_DOMAIN/mods/$modId/files/$fileId/download_link.json")
            if (!nxmKey.isNullOrBlank() && !nxmExpires.isNullOrBlank()) {
                urlBuilder.append("?key=").append(nxmKey).append("&expires=").append(nxmExpires)
            }
            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("apikey", apiKey)
                .header("Application-Name", APP_NAME)
                .header("Application-Version", APP_VERSION)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                quotaManager.inspectResponseOrThrow(response)
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw NexusApiException(
                        response.code,
                        parseErrorMessage(body, "ขอ Download Link ไม่สำเร็จ (${response.code}) — บัญชี Free ต้องเปิดหน้าเว็บ Nexus Mods หรือใช้ NXM Link"),
                    )
                }
                val arr = JSONArray(body)
                buildList {
                    for (i in 0 until arr.length()) {
                        val uri = arr.optJSONObject(i)?.optString("URI")?.trim()
                        if (!uri.isNullOrEmpty()) add(uri)
                    }
                }
            }
        }
    }

    private fun executeRestGet(url: String, apiKey: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("apikey", apiKey)
            .header("Application-Name", APP_NAME)
            .header("Application-Version", APP_VERSION)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            quotaManager.inspectResponseOrThrow(response)
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw NexusApiException(response.code, parseErrorMessage(body, "คำขอ API ล้มเหลว (${response.code})"))
            }
            return JSONObject(body)
        }
    }

    private fun parseRestModSummary(obj: JSONObject, feedTag: String): NexusModSummary {
        val modId = obj.optLong("mod_id", 0L)
        val available = obj.optBoolean("available", true)
        val rawSummary = obj.optString("summary", "")
        return NexusModSummary(
            modId = modId,
            name = obj.optString("name", if (available) "Mod #$modId" else "Unavailable Mod #$modId"),
            summary = stripBbCode(rawSummary),
            author = obj.optString("author", obj.optString("uploaded_by", "Unknown")),
            version = obj.optString("version", "1.0"),
            categoryName = mapCategoryId(obj.optInt("category_id", 0)),
            pictureUrl = obj.optString("picture_url").takeIf { it.isNotBlank() && it != "null" },
            downloads = obj.optLong("mod_downloads", obj.optLong("mod_unique_downloads", 0L)),
            endorsements = obj.optLong("endorsement_count", 0L),
            updatedEpochSec = obj.optLong("updated_timestamp", 0L),
            fileSizeBytes = null,
            compatibility = CompatibilityLevel.UNVERIFIED,
            feedTag = feedTag,
        )
    }

    private fun parseErrorMessage(body: String, fallback: String): String {
        return runCatching {
            val obj = JSONObject(body)
            obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("error").takeIf { it.isNotBlank() }
                ?: fallback
        }.getOrDefault(fallback)
    }

    private fun parseIsoOrEpoch(value: Any?): Long {
        if (value is Number) return value.toLong()
        val str = value?.toString()?.trim() ?: return 0L
        return str.toLongOrNull() ?: 0L
    }

    private fun stripBbCode(input: String): String {
        return input
            .replace(Regex("\\[/?[^\\]]+\\]"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("<br />", "\n")
            .replace("<br>", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun extractDependenciesHint(text: String): List<String> {
        val knownFrameworks = listOf(
            "SMAPI",
            "Content Patcher",
            "Generic Mod Config Menu",
            "SpaceCore",
            "Farm Type Manager",
            "Json Assets",
            "Calcifer",
        )
        return knownFrameworks.filter { fw ->
            text.contains(fw, ignoreCase = true)
        }
    }

    private fun extractGameVersionsHint(text: String): List<String> {
        val versions = Regex("""1\.[56](?:\.\d+)?""").findAll(text).map { it.value }.distinct().toList()
        return versions.ifEmpty { listOf("ไม่ระบุ (ตรวจสอบในหน้าต้นฉบับ)") }
    }

    private fun extractSmapiVersionHint(text: String): String? {
        val match = Regex("""SMAPI\s*v?(\d+\.\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.getOrNull(1)
    }

    private fun mapCategoryId(id: Int): String = when (id) {
        2 -> "กราฟิก"
        3 -> "ตัวละคร"
        4 -> "ระบบเกม"
        5 -> "ฟาร์ม"
        6 -> "อาคาร"
        7 -> "ภาษาไทย"
        8 -> "เครื่องมือ"
        9 -> "มอดโกง"
        else -> "ทั่วไป"
    }

    companion object {
        const val GAME_DOMAIN = "stardewvalley"
        const val REST_BASE = "https://api.nexusmods.com/v1"
        const val GRAPHQL_ENDPOINT = "https://api.nexusmods.com/v2/graphql"
        const val APP_NAME = "KpziModManager"
        const val APP_VERSION = "1.0.0"

        fun parseModIdFromInput(input: String): Long? {
            val trimmed = input.trim()
            trimmed.toLongOrNull()?.let { if (it > 0L) return it }
            val regex = Regex("""nexusmods\.com/stardewvalley/mods/(\d+)""", RegexOption.IGNORE_CASE)
            return regex.find(trimmed)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }
    }
}
