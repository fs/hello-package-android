package com.example.tthellopackage

import com.google.gson.annotations.SerializedName

data class RoomResponse(
        @SerializedName("room") val room: Room,
        @SerializedName("meta") val meta: Meta
)

data class Room(
        @SerializedName("id") val id: Int,
        @SerializedName("name") val name: String
)

data class Meta(@SerializedName("user_token") val token: String)