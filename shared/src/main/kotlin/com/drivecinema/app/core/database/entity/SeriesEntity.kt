package com.drivecinema.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series",
    indices = [
        Index("title")
    ]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val title: String,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val genres: String? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null
)
