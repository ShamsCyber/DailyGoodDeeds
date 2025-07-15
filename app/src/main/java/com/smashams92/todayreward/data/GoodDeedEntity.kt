package com.smashams92.todayreward.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deeds")
data class GoodDeedEntity(
    @PrimaryKey val id: Int,
    val fa: String,
    val en: String,
    val ar: String,
    val isCompleted: Boolean = false
)
