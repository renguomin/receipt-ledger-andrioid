package com.receiptledger.engine.model

import kotlinx.serialization.Serializable

@Serializable
enum class FieldSource {
    GEOMETRY,
    GEMINI,
    ALIAS,
    USER,
    UNKNOWN,
}

data class Field<T>(
    val value: T?,
    val confidence: Float,
    val source: FieldSource,
) {
    val isUncertain: Boolean get() = value == null || confidence < UNCERTAIN_BELOW

    companion object {
        const val UNCERTAIN_BELOW = 0.7f

        fun <T> missing(): Field<T> = Field(null, 0f, FieldSource.UNKNOWN)

        fun <T> of(
            value: T?,
            confidence: Float,
            source: FieldSource,
        ): Field<T> = Field(value, confidence, source)
    }
}
