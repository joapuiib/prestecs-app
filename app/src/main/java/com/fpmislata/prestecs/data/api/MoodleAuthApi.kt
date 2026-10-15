package com.fpmislata.prestecs.data.api

import com.fpmislata.prestecs.data.api.dto.MoodleTokenDto
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface MoodleAuthApi {
    @FormUrlEncoded
    @POST("login/token.php")
    suspend fun token(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("service") service: String = "moodle_mobile_app",
    ): Response<MoodleTokenDto>
}
