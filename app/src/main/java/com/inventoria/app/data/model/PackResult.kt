package com.inventoria.app.data.model

sealed class PackResult {
    data class Success(val message: String) : PackResult()
    data class Partial(val message: String, val errors: List<String>) : PackResult()
    data class Error(val message: String) : PackResult()
}
