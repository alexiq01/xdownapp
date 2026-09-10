package com.xdown.app.data.remote

import android.util.Log
import com.xdown.app.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XScraper @Inject constructor() {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Cache-Control", "max-age=0")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun scrapeTweet(tweetUrl: String): TweetResponse? {
        return try {
            val normalizedUrl = normalizeTweetUrl(tweetUrl)
            val tweetId = extractTweetId(normalizedUrl) ?: return null
            Log.d("XDOWN", "Input URL=$tweetUrl normalized=$normalizedUrl tweetId=$tweetId")

            val syndicationResult = trySyndicationApi(tweetId)
            if (syndicationResult != null) return syndicationResult

            val fxtwitterResult = tryFxtwitterApi(tweetId, "2/status")
                ?: tryFxtwitterApi(tweetId, "status")
            if (fxtwitterResult != null) return fxtwitterResult

            val vxtwitterResult = tryVxtwitterApi(tweetId)
            if (vxtwitterResult != null) return vxtwitterResult

            val request = Request.Builder()
                .url(normalizedUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null

            extractTweetData(html)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun scrapeProfile(username: String): List<TweetResponse> {
        return try {
            val url = "https://x.com/$username"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return emptyList()

            extractProfileTweets(html)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun trySyndicationApi(tweetId: String): TweetResponse? {
        return try {
            val token = calculateSyndicationToken(tweetId)
            val url = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=en&token=$token"
            val response = client.newCall(
                Request.Builder().url(url).header("Accept", "application/json").get().build()
            ).execute()
            Log.d("XDOWN", "Syndication HTTP=${response.code} url=$url")
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            Log.d("XDOWN", "Syndication JSON=${body.take(500)}")
            parseSyndicationResponse(body, tweetId)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSyndicationResponse(body: String, tweetId: String): TweetResponse? {
        val root = try { JsonParser.parseString(body).asJsonObject } catch (_: Exception) { return null }
        val mediaJson = root.getAsJsonArray("mediaDetails") ?: return null
        val media = mediaJson.mapNotNull { element ->
            val item = element.asJsonObject
            val type = item.stringValue("type") ?: "photo"
            val url = item.stringValue("media_url_https") ?: return@mapNotNull null
            val original = item.getAsJsonObject("original_info")
            val variants = item.getAsJsonObject("video_info")
                ?.getAsJsonArray("variants")
                ?.mapNotNull { variantElement ->
                    val variant = variantElement.asJsonObject
                    VideoVariant(
                        contentType = variant.stringValue("content_type"),
                        url = variant.stringValue("url"),
                        bitrate = variant.intValue("bitrate"),
                        width = variant.intValue("width"),
                        height = variant.intValue("height")
                    )
                }
            MediaEntity(
                idStr = item.stringValue("id_str") ?: tweetId,
                mediaUrlHttps = url,
                type = type,
                originalInfo = OriginalInfo(
                    width = original?.intValue("width"),
                    height = original?.intValue("height"),
                    large = null,
                    medium = null,
                    small = null
                ),
                videoInfo = if (variants.isNullOrEmpty()) null else VideoInfo(
                    durationMillis = item.getAsJsonObject("video_info")?.intValue("duration_millis"),
                    variants = variants
                )
            )
        }
        if (media.isEmpty()) {
            Log.d("XDOWN", "Syndication parser failure: mediaDetails empty")
            return null
        }
        Log.d("XDOWN", "Syndication parsed media=${media.size}")
        val user = root.getAsJsonObject("user")
        val screenName = user?.stringValue("screen_name") ?: "unknown"
        return TweetResponse(
            data = TweetData(
                tweetResult = TweetResult(
                    result = TweetResultData(
                        typename = "Tweet",
                        restId = tweetId,
                        core = TweetCore(
                            userResults = UserResults(
                                result = UserResult(
                                    restId = user?.stringValue("id_str"),
                                    userLegacy = UserLegacy(
                                        screenName = screenName,
                                        name = user?.stringValue("name"),
                                        profileImageUrl = user?.stringValue("profile_image_url_https")
                                    )
                                )
                            )
                        ),
                        legacy = TweetLegacy(
                            fullText = root.stringValue("text"),
                            entities = null,
                            extendedEntities = ExtendedEntities(media = media)
                        ),
                        mediaDetails = media
                    )
                )
            ),
            errors = null
        )
    }

    private fun calculateSyndicationToken(tweetId: String): String {
        val number = tweetId.toDouble() / 1e15 * Math.PI
        val integerPart = number.toLong().toString(36)
        var fraction = number - number.toLong()
        val fractionPart = buildString {
            repeat(24) {
                if (fraction <= 0.0) return@repeat
                fraction *= 36
                val digit = fraction.toInt()
                append("0123456789abcdefghijklmnopqrstuvwxyz"[digit])
                fraction -= digit
            }
        }
        return (integerPart + fractionPart).replace("0", "")
    }

    private fun com.google.gson.JsonObject.stringValue(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    private fun com.google.gson.JsonObject.intValue(name: String): Int? =
        get(name)?.takeUnless { it.isJsonNull }?.asInt

    private suspend fun tryFxtwitterApi(tweetId: String, endpoint: String): TweetResponse? {
        return try {
            val url = "https://api.fxtwitter.com/$endpoint/$tweetId"
            Log.d("XDOWN", "FxTwitter request id=$tweetId url=$url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            val response = client.newCall(request).execute()
            Log.d("XDOWN", "FxTwitter HTTP=${response.code}")
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            Log.d("XDOWN", "FxTwitter JSON=${body.take(500)}")

            val tweetData = gson.fromJson(body, Map::class.java)

            val tweet = (tweetData["status"] as? Map<*, *>)
                ?: (tweetData["tweet"] as? Map<*, *>)
                ?: return null
            val author = (tweet["author"] as? Map<*, *>)
                ?: (tweetData["author"] as? Map<*, *>)
            val media = tweet["media"] as? Map<*, *>
            val allMedia = media?.get("all") as? List<*>
            val photos = media?.get("photos") as? List<*>
            val videos = media?.get("videos") as? List<*>
            Log.d(
                "XDOWN",
                "FxTwitter root status=${tweetData["status"] != null} tweet=${tweetData["tweet"] != null} " +
                    "media=${media != null} photos=${photos?.size ?: 0} videos=${videos?.size ?: 0} all=${allMedia?.size ?: 0}"
            )

            val mediaEntities = mutableListOf<MediaEntity>()

            if (!allMedia.isNullOrEmpty()) {
                allMedia.forEach { rawItem ->
                    val item = rawItem as? Map<*, *> ?: return@forEach
                    val itemType = item["type"] as? String ?: "photo"
                    val itemUrl = item["url"] as? String ?: return@forEach
                    val formats = item["formats"] as? List<*>
                    val variants = formats.orEmpty().mapNotNull { rawFormat ->
                        val format = rawFormat as? Map<*, *> ?: return@mapNotNull null
                        val formatUrl = format["url"] as? String ?: return@mapNotNull null
                        val container = (format["container"] as? String)?.lowercase()
                        val formatType = (format["type"] as? String)?.lowercase()
                        if (container != "mp4" && formatType != "mp4" && formatType != "video/mp4") {
                            return@mapNotNull null
                        }
                        VideoVariant(
                            contentType = "video/mp4",
                            url = formatUrl,
                            bitrate = (format["bitrate"] as? Number)?.toInt(),
                            width = (item["width"] as? Number)?.toInt(),
                            height = (item["height"] as? Number)?.toInt()
                        )
                    }
                    mediaEntities.add(
                        MediaEntity(
                            idStr = tweetId,
                            mediaUrlHttps = itemUrl,
                            type = if (itemType == "photo") "photo" else "video",
                            originalInfo = if (itemType == "photo") OriginalInfo(
                                width = (item["width"] as? Number)?.toInt(),
                                height = (item["height"] as? Number)?.toInt(),
                                large = null, medium = null, small = null
                            ) else null,
                            videoInfo = if (itemType == "photo") null else VideoInfo(
                                durationMillis = ((item["duration"] as? Number)?.toDouble()?.times(1000))?.toInt(),
                                variants = variants.ifEmpty {
                                    listOf(VideoVariant("video/mp4", itemUrl, null, null, null))
                                }
                            )
                        )
                    )
                }
            } else {
            photos?.forEach { photo ->
                val p = photo as? Map<*, *> ?: return@forEach
                val imgUrl = p["url"] as? String ?: p["thumbnail_url"] as? String
                mediaEntities.add(
                    MediaEntity(
                        idStr = tweetId,
                        mediaUrlHttps = imgUrl,
                        type = "photo",
                        originalInfo = OriginalInfo(
                            width = (p["width"] as? Number)?.toInt(),
                            height = (p["height"] as? Number)?.toInt(),
                            large = null, medium = null, small = null
                        ),
                        videoInfo = null
                    )
                )
            }

            videos?.forEach { video ->
                val v = video as? Map<*, *> ?: return@forEach
                val videoUrl = v["url"] as? String ?: v["embed_url"] as? String
                val duration = (v["duration"] as? Number)?.toInt()

                val variants = mutableListOf<VideoVariant>()
                if (videoUrl != null) {
                    variants.add(
                        VideoVariant(
                            contentType = "video/mp4",
                            url = videoUrl,
                            bitrate = null,
                            width = (v["width"] as? Number)?.toInt(),
                            height = (v["height"] as? Number)?.toInt()
                        )
                    )
                }

                mediaEntities.add(
                    MediaEntity(
                        idStr = tweetId,
                        mediaUrlHttps = videoUrl,
                        type = "video",
                        originalInfo = OriginalInfo(
                            width = (v["width"] as? Number)?.toInt(),
                            height = (v["height"] as? Number)?.toInt(),
                            large = null, medium = null, small = null
                        ),
                        videoInfo = VideoInfo(
                            durationMillis = duration,
                            variants = variants
                        )
                    )
                )
            }

            }

            if (mediaEntities.isEmpty()) {
                Log.d("XDOWN", "FxTwitter parser failure: no media extracted")
                return null
            }
            Log.d("XDOWN", "FxTwitter parsed media=${mediaEntities.size}")

            val screenName = author?.get("screen_name") as? String
                ?: author?.get("name") as? String
                ?: "unknown"

            TweetResponse(
                data = TweetData(
                    tweetResult = TweetResult(
                        result = TweetResultData(
                            typename = "Tweet",
                            restId = tweetId,
                            core = TweetCore(
                                userResults = UserResults(
                                    result = UserResult(
                                        restId = author?.get("id_str") as? String,
                                        userLegacy = UserLegacy(
                                            screenName = screenName,
                                            name = author?.get("name") as? String,
                                            profileImageUrl = author?.get("avatar_url") as? String
                                        )
                                    )
                                )
                            ),
                            legacy = TweetLegacy(
                                fullText = tweet["text"] as? String,
                                entities = null,
                                extendedEntities = ExtendedEntities(media = mediaEntities)
                            ),
                            mediaDetails = mediaEntities
                        )
                    )
                ),
                errors = null
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun tryVxtwitterApi(tweetId: String): TweetResponse? {
        return try {
            val url = "https://api.vxtwitter.com/status/$tweetId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val tweetData = gson.fromJson(body, Map::class.java)

            val tweet = tweetData["tweet"] as? Map<*, *> ?: tweetData
            val author = tweet["author"] as? Map<*, *>
            val media = tweet["media"] as? Map<*, *>
            val photos = media?.get("photos") as? List<*>
            val videos = media?.get("videos") as? List<*>

            val mediaEntities = mutableListOf<MediaEntity>()

            photos?.forEach { photo ->
                val p = photo as? Map<*, *> ?: return@forEach
                mediaEntities.add(
                    MediaEntity(
                        idStr = tweetId,
                        mediaUrlHttps = p["url"] as? String,
                        type = "photo",
                        originalInfo = OriginalInfo(
                            width = (p["width"] as? Number)?.toInt(),
                            height = (p["height"] as? Number)?.toInt(),
                            large = null, medium = null, small = null
                        ),
                        videoInfo = null
                    )
                )
            }

            videos?.forEach { video ->
                val v = video as? Map<*, *> ?: return@forEach
                val videoUrl = v["url"] as? String
                val variants = mutableListOf<VideoVariant>()
                if (videoUrl != null) {
                    variants.add(
                        VideoVariant(
                            contentType = "video/mp4",
                            url = videoUrl,
                            bitrate = null,
                            width = (v["width"] as? Number)?.toInt(),
                            height = (v["height"] as? Number)?.toInt()
                        )
                    )
                }

                mediaEntities.add(
                    MediaEntity(
                        idStr = tweetId,
                        mediaUrlHttps = videoUrl,
                        type = "video",
                        originalInfo = OriginalInfo(
                            width = (v["width"] as? Number)?.toInt(),
                            height = (v["height"] as? Number)?.toInt(),
                            large = null, medium = null, small = null
                        ),
                        videoInfo = VideoInfo(
                            durationMillis = (v["duration"] as? Number)?.toInt(),
                            variants = variants
                        )
                    )
                )
            }

            if (mediaEntities.isEmpty()) return null

            val screenName = author?.get("screen_name") as? String ?: "unknown"

            TweetResponse(
                data = TweetData(
                    tweetResult = TweetResult(
                        result = TweetResultData(
                            typename = "Tweet",
                            restId = tweetId,
                            core = TweetCore(
                                userResults = UserResults(
                                    result = UserResult(
                                        restId = author?.get("id_str") as? String,
                                        userLegacy = UserLegacy(
                                            screenName = screenName,
                                            name = author?.get("name") as? String,
                                            profileImageUrl = author?.get("avatar_url") as? String
                                        )
                                    )
                                )
                            ),
                            legacy = TweetLegacy(
                                fullText = tweet["text"] as? String,
                                entities = null,
                                extendedEntities = ExtendedEntities(media = mediaEntities)
                            ),
                            mediaDetails = mediaEntities
                        )
                    )
                ),
                errors = null
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTweetData(html: String): TweetResponse? {
        return try {
            val scriptPattern = """<script id="__NEXT_DATA__"[^>]*>(.*?)</script>"""
            val regex = Regex(scriptPattern, RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(html)

            if (match != null) {
                val jsonData = match.groupValues[1]
                val nextData = gson.fromJson(jsonData, Map::class.java)
                val props = nextData["props"] as? Map<*, *>
                val pageProps = props?.get("pageProps") as? Map<*, *>
                val timeline = pageProps?.get("timeline") as? Map<*, *>
                val instructions = timeline?.get("instructions") as? List<*>

                if (instructions != null) {
                    return parseTimelineInstructions(instructions)
                }
            }

            val metaPattern = """data-tweet-id="(\d+)"""".toRegex()
            val tweetId = metaPattern.find(html)?.groupValues?.get(1)

            if (tweetId != null) {
                extractFromMetaTags(html, tweetId)
            } else {
                extractFromOpenGraph(html)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTimelineInstructions(instructions: List<*>): TweetResponse? {
        for (instruction in instructions) {
            val instMap = instruction as? Map<*, *> ?: continue
            val entries = instMap["entries"] as? List<*> ?: continue

            for (entry in entries) {
                val entryMap = entry as? Map<*, *> ?: continue
                val content = entryMap["content"] as? Map<*, *> ?: continue
                val itemContent = content["itemContent"] as? Map<*, *> ?: continue
                val tweetResults = itemContent["tweet_results"] as? Map<*, *> ?: continue

                return TweetResponse(
                    data = TweetData(
                        tweetResult = TweetResult(
                            result = parseTweetResultData(tweetResults["result"] as? Map<*, *>)
                        )
                    ),
                    errors = null
                )
            }
        }
        return null
    }

    private fun parseTweetResultData(data: Map<*, *>?): TweetResultData? {
        if (data == null) return null

        val legacy = data["legacy"] as? Map<*, *>
        val core = data["core"] as? Map<*, *>

        val userResults = core?.get("user_results") as? Map<*, *>
        val userResult = userResults?.get("result") as? Map<*, *>
        val userLegacy = userResult?.get("legacy") as? Map<*, *>

        val mediaList = parseExtendedEntities(legacy?.get("extended_entities") as? Map<*, *>)

        return TweetResultData(
            typename = data["__typename"] as? String,
            restId = data["rest_id"] as? String,
            core = TweetCore(
                userResults = UserResults(
                    result = UserResult(
                        restId = userResult?.get("rest_id") as? String,
                        userLegacy = userLegacy?.let {
                            UserLegacy(
                                screenName = it["screen_name"] as? String,
                                name = it["name"] as? String,
                                profileImageUrl = it["profile_image_url_https"] as? String
                            )
                        }
                    )
                )
            ),
            legacy = TweetLegacy(
                fullText = legacy?.get("full_text") as? String,
                entities = null,
                extendedEntities = ExtendedEntities(media = mediaList)
            ),
            mediaDetails = mediaList
        )
    }

    private fun parseExtendedEntities(entities: Map<*, *>?): List<MediaEntity> {
        val mediaList = entities?.get("media") as? List<*> ?: return emptyList()

        return mediaList.mapNotNull { mediaItem ->
            val map = mediaItem as? Map<*, *> ?: return@mapNotNull null

            MediaEntity(
                idStr = map["id_str"] as? String,
                mediaUrlHttps = map["media_url_https"] as? String,
                type = map["type"] as? String,
                originalInfo = map["original_info"]?.let { parseOriginalInfo(it as Map<*, *>) },
                videoInfo = map["video_info"]?.let { parseVideoInfo(it as Map<*, *>) }
            )
        }
    }

    private fun parseOriginalInfo(info: Map<*, *>): OriginalInfo {
        return OriginalInfo(
            width = info["width"] as? Int,
            height = info["height"] as? Int,
            large = (info["large"] as? Map<*, *>)?.let {
                ImageInfo(it["w"] as? Int, it["h"] as? Int, it["resize"] as? String)
            },
            medium = (info["medium"] as? Map<*, *>)?.let {
                ImageInfo(it["w"] as? Int, it["h"] as? Int, it["resize"] as? String)
            },
            small = (info["small"] as? Map<*, *>)?.let {
                ImageInfo(it["w"] as? Int, it["h"] as? Int, it["resize"] as? String)
            }
        )
    }

    private fun parseVideoInfo(info: Map<*, *>): VideoInfo {
        val variants = info["variants"] as? List<*>
        return VideoInfo(
            durationMillis = info["duration_millis"] as? Int,
            variants = variants?.mapNotNull { v ->
                val vMap = v as? Map<*, *> ?: return@mapNotNull null
                VideoVariant(
                    contentType = vMap["content_type"] as? String,
                    url = vMap["url"] as? String,
                    bitrate = vMap["bitrate"] as? Int,
                    width = vMap["width"] as? Int,
                    height = vMap["height"] as? Int
                )
            }
        )
    }

    private fun extractFromMetaTags(html: String, tweetId: String): TweetResponse? {
        val ogDescription = extractMetaContent(html, "og:description")
        val ogImage = extractMetaContent(html, "og:image")
        val ogVideo = extractMetaContent(html, "og:video")
        val title = extractMetaContent(html, "twitter:title")

        val authorMatch = title?.let { """by (.+?) on X""".toRegex().find(it) }
        val author = authorMatch?.groupValues?.get(1) ?: "unknown"

        val mediaEntities = mutableListOf<MediaEntity>()

        if (ogVideo != null) {
            mediaEntities.add(
                MediaEntity(
                    idStr = tweetId,
                    mediaUrlHttps = ogVideo,
                    type = "video",
                    originalInfo = null,
                    videoInfo = VideoInfo(
                        durationMillis = null,
                        variants = listOf(
                            VideoVariant(
                                contentType = "video/mp4",
                                url = ogVideo,
                                bitrate = null,
                                width = null,
                                height = null
                            )
                        )
                    )
                )
            )
        } else if (ogImage != null) {
            mediaEntities.add(
                MediaEntity(
                    idStr = tweetId,
                    mediaUrlHttps = ogImage,
                    type = "photo",
                    originalInfo = OriginalInfo(
                        width = null,
                        height = null,
                        large = null,
                        medium = null,
                        small = null
                    ),
                    videoInfo = null
                )
            )
        }

        if (mediaEntities.isEmpty()) return null

        return TweetResponse(
            data = TweetData(
                tweetResult = TweetResult(
                    result = TweetResultData(
                        typename = "Tweet",
                        restId = tweetId,
                        core = TweetCore(
                            userResults = UserResults(
                                result = UserResult(
                                    restId = null,
                                    userLegacy = UserLegacy(
                                        screenName = author,
                                        name = author,
                                        profileImageUrl = null
                                    )
                                )
                            )
                        ),
                        legacy = TweetLegacy(
                            fullText = ogDescription,
                            entities = null,
                            extendedEntities = ExtendedEntities(media = mediaEntities)
                        ),
                        mediaDetails = mediaEntities
                    )
                )
            ),
            errors = null
        )
    }

    private fun extractFromOpenGraph(html: String): TweetResponse? {
        val ogImage = extractMetaContent(html, "og:image")
        val ogVideo = extractMetaContent(html, "og:video")
        val title = extractMetaContent(html, "og:title")

        if (ogImage == null && ogVideo == null) return null

        val mediaEntities = mutableListOf<MediaEntity>()

        if (ogVideo != null) {
            mediaEntities.add(
                MediaEntity(
                    idStr = null,
                    mediaUrlHttps = ogVideo,
                    type = "video",
                    originalInfo = null,
                    videoInfo = VideoInfo(
                        durationMillis = null,
                        variants = listOf(
                            VideoVariant(
                                contentType = "video/mp4",
                                url = ogVideo,
                                bitrate = null,
                                width = null,
                                height = null
                            )
                        )
                    )
                )
            )
        }

        if (ogImage != null) {
            mediaEntities.add(
                MediaEntity(
                    idStr = null,
                    mediaUrlHttps = ogImage,
                    type = "photo",
                    originalInfo = null,
                    videoInfo = null
                )
            )
        }

        return TweetResponse(
            data = TweetData(
                tweetResult = TweetResult(
                    result = TweetResultData(
                        typename = "Tweet",
                        restId = null,
                        core = TweetCore(
                            userResults = UserResults(
                                result = UserResult(
                                    restId = null,
                                    userLegacy = UserLegacy(
                                        screenName = title?.let { """by (.+?) on X""".toRegex().find(it)?.groupValues?.get(1) } ?: "unknown",
                                        name = null,
                                        profileImageUrl = null
                                    )
                                )
                            )
                        ),
                        legacy = TweetLegacy(
                            fullText = extractMetaContent(html, "og:description"),
                            entities = null,
                            extendedEntities = ExtendedEntities(media = mediaEntities)
                        ),
                        mediaDetails = mediaEntities
                    )
                )
            ),
            errors = null
        )
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val pattern1 = """<meta\s+property="$property"\s+content="([^"]+)"""".toRegex()
        val match1 = pattern1.find(html)
        if (match1 != null) return match1.groupValues[1]

        val pattern2 = """<meta\s+content="([^"]+)"\s+property="$property"""".toRegex()
        val match2 = pattern2.find(html)
        if (match2 != null) return match2.groupValues[1]

        val pattern3 = """<meta\s+name="$property"\s+content="([^"]+)"""".toRegex()
        return pattern3.find(html)?.groupValues?.get(1)
    }

    private fun extractProfileTweets(html: String): List<TweetResponse> {
        val tweets = mutableListOf<TweetResponse>()

        val scriptPattern = """<script id="__NEXT_DATA__"[^>]*>(.*?)</script>"""
        val regex = Regex(scriptPattern, RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(html)

        if (match != null) {
            try {
                val jsonData = match.groupValues[1]
                val nextData = gson.fromJson(jsonData, Map::class.java)
                val props = nextData["props"] as? Map<*, *>
                val pageProps = props?.get("pageProps") as? Map<*, *>
                val timeline = pageProps?.get("timeline") as? Map<*, *>
                val instructions = timeline?.get("instructions") as? List<*>

                if (instructions != null) {
                    for (instruction in instructions) {
                        val instMap = instruction as? Map<*, *> ?: continue
                        val entries = instMap["entries"] as? List<*> ?: continue

                        for (entry in entries) {
                            val entryMap = entry as? Map<*, *> ?: continue
                            val content = entryMap["content"] as? Map<*, *> ?: continue
                            val itemContent = content["itemContent"] as? Map<*, *> ?: continue
                            val tweetResults = itemContent["tweet_results"] as? Map<*, *> ?: continue

                            val tweetResponse = TweetResponse(
                                data = TweetData(
                                    tweetResult = TweetResult(
                                        result = parseTweetResultData(tweetResults["result"] as? Map<*, *>)
                                    )
                                ),
                                errors = null
                            )
                            tweets.add(tweetResponse)
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        return tweets
    }

    private fun normalizeTweetUrl(url: String): String {
        var normalized = url.trim()
        if (normalized.matches(Regex("\\d{15,20}"))) {
            return "https://x.com/i/status/$normalized"
        }

        if (!normalized.startsWith("http")) {
            normalized = "https://x.com/$normalized"
        }

        normalized = normalized.replace("twitter.com", "x.com")

        val statusPattern = """x\.com/[^/]+/status(?:es)?/(\d{15,20})""".toRegex()
        val match = statusPattern.find(normalized)
        if (match != null) {
            return normalized
        }

        return normalized
    }

    private fun extractTweetId(url: String): String? {
        val pattern = """(?:x\.com|twitter\.com)/[^/]+/status(?:es)?/(\d{15,20})(?:[^0-9]|$)""".toRegex()
        return pattern.find(url)?.groupValues?.get(1)
    }
}
