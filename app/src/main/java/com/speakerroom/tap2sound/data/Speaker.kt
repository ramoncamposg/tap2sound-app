package com.speakerroom.tap2sound.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speakers")
data class Speaker(
    @PrimaryKey
    val id: String,
    val nfcUid: String,
    val btMac: String,
    val name: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
