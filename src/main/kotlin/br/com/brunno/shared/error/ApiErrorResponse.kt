package br.com.brunno.shared.error

data class ApiErrorResponse(
    val title: String,
    val status: Int,
    val detail: String
)
