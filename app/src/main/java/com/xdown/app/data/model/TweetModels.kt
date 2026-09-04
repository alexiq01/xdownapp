package com.xdown.app.data.model

import com.google.gson.annotations.SerializedName

data class TweetResponse(
    @SerializedName("data") val data: TweetData?,
    @SerializedName("errors") val errors: List<TweetError>?
)

data class TweetData(
    @SerializedName("tweetResult") val tweetResult: TweetResult?
)

data class TweetResult(
    @SerializedName("result") val result: TweetResultData?
)

data class TweetResultData(
    @SerializedName("__typename") val typename: String?,
    @SerializedName("rest_id") val restId: String?,
    @SerializedName("core") val core: TweetCore?,
    @SerializedName("legacy") val legacy: TweetLegacy?,
    @SerializedName("mediaDetails") val mediaDetails: List<MediaDetail>?
)

data class TweetCore(
    @SerializedName("user_results") val userResults: UserResults?
)

data class UserResults(
    @SerializedName("result") val result: UserResult?
)

data class UserResult(
    @SerializedName("rest_id") val restId: String?,
    @SerializedName("legacy") val userLegacy: UserLegacy?
)

data class UserLegacy(
    @SerializedName("screen_name") val screenName: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("profile_image_url_https") val profileImageUrl: String?
)

data class TweetLegacy(
    @SerializedName("full_text") val fullText: String?,
    @SerializedName("entities") val entities: TweetEntities?,
    @SerializedName("extended_entities") val extendedEntities: ExtendedEntities?
)

data class TweetEntities(
    @SerializedName("media") val media: List<MediaEntity>?
)

data class ExtendedEntities(
    @SerializedName("media") val media: List<MediaEntity>?
)

data class MediaEntity(
    @SerializedName("id_str") val idStr: String?,
    @SerializedName("media_url_https") val mediaUrlHttps: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("original_info") val originalInfo: OriginalInfo?,
    @SerializedName("video_info") val videoInfo: VideoInfo?
)

data class OriginalInfo(
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("large") val large: ImageInfo?,
    @SerializedName("medium") val medium: ImageInfo?,
    @SerializedName("small") val small: ImageInfo?
)

data class ImageInfo(
    @SerializedName("w") val w: Int?,
    @SerializedName("h") val h: Int?,
    @SerializedName("resize") val resize: String?
)

data class VideoInfo(
    @SerializedName("duration_millis") val durationMillis: Int?,
    @SerializedName("variants") val variants: List<VideoVariant>?
)

data class VideoVariant(
    @SerializedName("content_type") val contentType: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("bitrate") val bitrate: Int?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

data class MediaDetail(
    @SerializedName("id_str") val idStr: String?,
    @SerializedName("media_url_https") val mediaUrlHttps: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("original_info") val originalInfo: OriginalInfo?,
    @SerializedName("video_info") val videoInfo: VideoInfo?
)

data class TweetError(
    @SerializedName("message") val message: String?,
    @SerializedName("locations") val locations: List<Any>?,
    @SerializedName("path") val path: List<String>?
)
