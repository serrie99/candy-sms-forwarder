package com.mfdev.candysms.model

import kotlinx.serialization.Serializable

@Serializable
data class ForwardTask(
    val messageBody: String,
    val from: String,
    val timestamp: Long = System.currentTimeMillis(),
    val random: String,
    val pdu: String,
    val date: String,
    val uniqueId: String
)