package com.alphawallet.app.entity

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * Represents a release item retrieved from the GitHub API along with nested asset metadata.
 */
data class GitHubRelease(
    @SerializedName("url") @Expose val url: String? = null,
    @SerializedName("assets_url") @Expose val assetsUrl: String? = null,
    @SerializedName("upload_url") @Expose val uploadUrl: String? = null,
    @SerializedName("html_url") @Expose val htmlUrl: String? = null,
    @SerializedName("id") @Expose val id: Int? = null,
    @SerializedName("author") @Expose val author: Author? = null,
    @SerializedName("node_id") @Expose val nodeId: String? = null,
    @SerializedName("tag_name") @Expose val tagName: String? = null,
    @SerializedName("target_commitish") @Expose val targetCommitish: String? = null,
    @SerializedName("name") @Expose val name: String? = null,
    @SerializedName("draft") @Expose val draft: Boolean? = null,
    @SerializedName("prerelease") @Expose val prerelease: Boolean? = null,
    @SerializedName("created_at") @Expose val createdAt: String? = null,
    @SerializedName("published_at") @Expose val publishedAt: String? = null,
    @SerializedName("assets") @Expose val assets: List<Asset>? = null,
    @SerializedName("tarball_url") @Expose val tarballUrl: String? = null,
    @SerializedName("zipball_url") @Expose val zipballUrl: String? = null,
    @SerializedName("body") @Expose val body: String = "",
) {
    constructor() : this(
        body = "",
    )
}

/**
 * Asset metadata for a GitHub release, including uploader details when available.
 */
data class Asset(
    @SerializedName("url") @Expose val url: String? = null,
    @SerializedName("id") @Expose val id: Int? = null,
    @SerializedName("node_id") @Expose val nodeId: String? = null,
    @SerializedName("name") @Expose val name: String? = null,
    @SerializedName("label") @Expose val label: Any? = null,
    @SerializedName("uploader") @Expose val uploader: Uploader? = null,
    @SerializedName("content_type") @Expose val contentType: String? = null,
    @SerializedName("state") @Expose val state: String? = null,
    @SerializedName("size") @Expose val size: Int? = null,
    @SerializedName("download_count") @Expose val downloadCount: Int? = null,
    @SerializedName("created_at") @Expose val createdAt: String? = null,
    @SerializedName("updated_at") @Expose val updatedAt: String? = null,
    @SerializedName("browser_download_url") @Expose val browserDownloadUrl: String? = null,
) {
    constructor() : this(
        url = null,
        id = null,
        nodeId = null,
        name = null,
        label = null,
        uploader = null,
        contentType = null,
        state = null,
        size = null,
        downloadCount = null,
        createdAt = null,
        updatedAt = null,
        browserDownloadUrl = null,
    )
}

/**
 * Describes the GitHub user that uploaded a specific release asset.
 */
data class Uploader(
    @SerializedName("login") @Expose val login: String? = null,
    @SerializedName("id") @Expose val id: Int? = null,
    @SerializedName("node_id") @Expose val nodeId: String? = null,
    @SerializedName("avatar_url") @Expose val avatarUrl: String? = null,
    @SerializedName("gravatar_id") @Expose val gravatarId: String? = null,
    @SerializedName("url") @Expose val url: String? = null,
    @SerializedName("html_url") @Expose val htmlUrl: String? = null,
    @SerializedName("followers_url") @Expose val followersUrl: String? = null,
    @SerializedName("following_url") @Expose val followingUrl: String? = null,
    @SerializedName("gists_url") @Expose val gistsUrl: String? = null,
    @SerializedName("starred_url") @Expose val starredUrl: String? = null,
    @SerializedName("subscriptions_url") @Expose val subscriptionsUrl: String? = null,
    @SerializedName("organizations_url") @Expose val organizationsUrl: String? = null,
    @SerializedName("repos_url") @Expose val reposUrl: String? = null,
    @SerializedName("events_url") @Expose val eventsUrl: String? = null,
    @SerializedName("received_events_url") @Expose val receivedEventsUrl: String? = null,
    @SerializedName("type") @Expose val type: String? = null,
    @SerializedName("site_admin") @Expose val siteAdmin: Boolean? = null,
) {
    constructor() : this(
        login = null,
        id = null,
        nodeId = null,
        avatarUrl = null,
        gravatarId = null,
        url = null,
        htmlUrl = null,
        followersUrl = null,
        followingUrl = null,
        gistsUrl = null,
        starredUrl = null,
        subscriptionsUrl = null,
        organizationsUrl = null,
        reposUrl = null,
        eventsUrl = null,
        receivedEventsUrl = null,
        type = null,
        siteAdmin = null,
    )
}

/**
 * Represents the author of the release entry returned by the GitHub API.
 */
data class Author(
    @SerializedName("login") @Expose val login: String? = null,
    @SerializedName("id") @Expose val id: Int? = null,
    @SerializedName("node_id") @Expose val nodeId: String? = null,
    @SerializedName("avatar_url") @Expose val avatarUrl: String? = null,
    @SerializedName("gravatar_id") @Expose val gravatarId: String? = null,
    @SerializedName("url") @Expose val url: String? = null,
    @SerializedName("html_url") @Expose val htmlUrl: String? = null,
    @SerializedName("followers_url") @Expose val followersUrl: String? = null,
    @SerializedName("following_url") @Expose val followingUrl: String? = null,
    @SerializedName("gists_url") @Expose val gistsUrl: String? = null,
    @SerializedName("starred_url") @Expose val starredUrl: String? = null,
    @SerializedName("subscriptions_url") @Expose val subscriptionsUrl: String? = null,
    @SerializedName("organizations_url") @Expose val organizationsUrl: String? = null,
    @SerializedName("repos_url") @Expose val reposUrl: String? = null,
    @SerializedName("events_url") @Expose val eventsUrl: String? = null,
    @SerializedName("received_events_url") @Expose val receivedEventsUrl: String? = null,
    @SerializedName("type") @Expose val type: String? = null,
    @SerializedName("site_admin") @Expose val siteAdmin: Boolean? = null,
) {
    constructor() : this(
        login = null,
        id = null,
        nodeId = null,
        avatarUrl = null,
        gravatarId = null,
        url = null,
        htmlUrl = null,
        followersUrl = null,
        followingUrl = null,
        gistsUrl = null,
        starredUrl = null,
        subscriptionsUrl = null,
        organizationsUrl = null,
        reposUrl = null,
        eventsUrl = null,
        receivedEventsUrl = null,
        type = null,
        siteAdmin = null,
    )
}
