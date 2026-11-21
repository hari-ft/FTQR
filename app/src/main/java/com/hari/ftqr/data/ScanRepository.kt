package com.hari.ftqr.data

import android.content.Context
import com.hari.ftqr.parse.ContentParser
import com.hari.ftqr.parse.kind
import com.hari.ftqr.parse.title
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScanRepository(private val dao: ScanDao) {

    fun observe(query: String, kind: String): Flow<List<ScanEntity>> = dao.observe(escapeLike(query.trim()), kind)

    fun observeKinds(): Flow<List<String>> = dao.observeKinds()

    fun observeById(id: Long): Flow<ScanEntity?> = dao.observeById(id)

    suspend fun record(rawValue: String, format: Int, now: Long = System.currentTimeMillis()): Long = recordLock.withLock {
        val latest = dao.latest()
        if (latest != null && latest.rawValue == rawValue && now - latest.createdAt < REPEAT_WINDOW_MILLIS) {
            dao.touch(latest.id, now)
            return@withLock latest.id
        }
        val content = ContentParser.parse(rawValue, format)
        dao.insert(
            ScanEntity(
                rawValue = rawValue,
                format = format,
                kind = content.kind().name,
                title = content.title(),
                createdAt = now,
            )
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun restore(scan: ScanEntity) {
        dao.insertOrReplace(scan)
    }

    suspend fun deleteAll() = dao.deleteAll()

    companion object {
        private const val REPEAT_WINDOW_MILLIS = 60_000L
        private val recordLock = Mutex()

        private fun escapeLike(value: String): String =
            value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        fun from(context: Context) = ScanRepository(AppDatabase.get(context).scanDao())
    }
}
