package com.fintrack.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_transactions",
    indices = [Index(value = ["sender", "body", "occurredAt"], unique = true)]
)
data class SmsTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val amount: Double,
    val type: String,
    val channel: String,
    val title: String,
    val occurredAt: Long
)
