package com.example.tthellopackage

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface Api {
    @Headers("Content-Type: application/json")
    @POST("/v1/client/rooms")
    suspend fun getRoomAsync(@Body roomRequest: RoomRequest): Response<RoomResponse>
}