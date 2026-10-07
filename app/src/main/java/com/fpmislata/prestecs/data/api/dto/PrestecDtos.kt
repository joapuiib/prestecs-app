package com.fpmislata.prestecs.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shapes of the prestecs JSON API (docs/index.html in the backend repository).

@Serializable
enum class Estat(val apiValue: String) {
    @SerialName("prestat")
    PRESTAT("prestat"),

    @SerialName("no-retornat")
    NO_RETORNAT("no-retornat"),

    @SerialName("retornat")
    RETORNAT("retornat"),
}

@Serializable
data class PrestecDto(
    val id: Long,
    val carro: String = "",
    val portatil: String,
    val estudiant: String,
    /** `YYYY-MM-DD hh:mm:ss`, Europe/Madrid. */
    @SerialName("prestec_data") val prestecData: String,
    @SerialName("prestec_professor") val prestecProfessor: String? = null,
    @SerialName("devolucio_data") val devolucioData: String? = null,
    @SerialName("devolucio_professor") val devolucioProfessor: String? = null,
    val estat: Estat,
)

@Serializable
data class PaginationDto(
    val page: Int,
    @SerialName("per_page") val perPage: Int,
    val total: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
data class PrestecsPageDto(val prestecs: List<PrestecDto>, val pagination: PaginationDto)

@Serializable
data class PrestecRowDto(val portatil: String, val estudiant: String)

@Serializable
data class CreatePrestecsRequest(val rows: List<PrestecRowDto>)

@Serializable
data class ReturnsRequest(val portatils: List<String>)

@Serializable
enum class Severity {
    @SerialName("success")
    SUCCESS,

    @SerialName("danger")
    DANGER,
}

/** One result line of a batch operation. Decide on [code]; display [message]. */
@Serializable
data class BatchMessageDto(
    val severity: Severity,
    val code: String,
    val message: String,
    val portatil: String? = null,
    /** 1-based position among the non-empty rows sent. */
    val row: Int? = null,
    val count: Int? = null,
)

@Serializable
data class CreatePrestecsResponse(
    val success: Boolean,
    @SerialName("created_count") val createdCount: Int,
    val messages: List<BatchMessageDto>,
)

@Serializable
data class ReturnsResponse(
    val success: Boolean,
    @SerialName("returned_count") val returnedCount: Int,
    val messages: List<BatchMessageDto>,
)

@Serializable
data class LookupDto(
    val found: Boolean,
    val estudiant: String? = null,
    @SerialName("prestec_data") val prestecData: String? = null,
)
