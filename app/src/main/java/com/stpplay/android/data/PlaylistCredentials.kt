package com.stpplay.android.data

import kotlinx.serialization.Serializable

data class PlaylistCredentials(
    val name: String,
    val user: String,
    val pass: String,
    val url: String,
    val userInfo: UserInfo? = null
)

@Serializable
data class UserInfo(
    val username: String,
    val status: String,
    val expiryDate: String,
    val createdAt: String,
    val isTrial: Boolean = false,
    val activeConnections: Int = 0,
    val maxConnections: Int = 1,
    val expiryTimestamp: Long? = null
)
