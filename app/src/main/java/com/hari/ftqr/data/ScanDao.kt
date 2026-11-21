package com.hari.ftqr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query(
        "SELECT * FROM scans WHERE (:query = '' OR title LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR rawValue LIKE '%' || :query || '%' ESCAPE '\\') " +
            "AND (:kind = '' OR kind = :kind) ORDER BY createdAt DESC"
    )
    fun observe(query: String, kind: String): Flow<List<ScanEntity>>

    @Query("SELECT DISTINCT kind FROM scans")
    fun observeKinds(): Flow<List<String>>

    @Query("SELECT * FROM scans WHERE id = :id")
    fun observeById(id: Long): Flow<ScanEntity?>

    @Query("SELECT * FROM scans ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): ScanEntity?

    @Insert
    suspend fun insert(scan: ScanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(scan: ScanEntity): Long

    @Query("UPDATE scans SET createdAt = :createdAt WHERE id = :id")
    suspend fun touch(id: Long, createdAt: Long)

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scans")
    suspend fun deleteAll()
}
