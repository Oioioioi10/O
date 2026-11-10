package com.avatarsdev.tiktokupgrade.data

import com.avatarsdev.tiktokupgrade.data.db.AppDatabase
import com.avatarsdev.tiktokupgrade.data.db.DataSource
import com.avatarsdev.tiktokupgrade.data.db.LikeEntity
import com.avatarsdev.tiktokupgrade.data.importer.ParseResult
import com.avatarsdev.tiktokupgrade.data.importer.TikTokExportParser
import com.avatarsdev.tiktokupgrade.data.sample.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class ImportOutcome(
    val parse: ParseResult,
    val inserted: Int,
    val duplicates: Int,
)

class LikeRepository(private val db: AppDatabase) {
    private val dao = db.likeDao()

    fun observe(source: String): Flow<List<LikeEntity>> = dao.observeBySource(source)
    fun observeCount(source: String): Flow<Int> = dao.observeCount(source)

    suspend fun importExport(bytes: ByteArray, fileName: String?, exportZone: ZoneId): ImportOutcome = withContext(Dispatchers.Default) {
        val parsed = TikTokExportParser.parse(bytes, fileName, exportZone)
        val now = System.currentTimeMillis()
        // Never let sample-looking links end up in the real data set.
        val rows = parsed.likes.filterNot { SampleData.isSampleUrl(it.videoUrl) }.map {
            LikeEntity(videoUrl = it.videoUrl, videoId = it.videoId, likedAt = it.likedAt, source = DataSource.REAL, importedAt = now)
        }
        var inserted = 0
        withContext(Dispatchers.IO) {
            rows.chunked(500).forEach { chunk -> inserted += dao.insertAll(chunk).count { it != -1L } }
        }
        ImportOutcome(parsed, inserted, rows.size - inserted)
    }

    suspend fun loadSample(zone: ZoneId = ZoneId.systemDefault()): Int = withContext(Dispatchers.IO) {
        dao.deleteBySource(DataSource.SAMPLE)
        val rows = SampleData.generate(Instant.now(), zone)
        dao.insertAll(rows).count { it != -1L }
    }

    suspend fun count(source: String) = withContext(Dispatchers.IO) { dao.count(source) }
    suspend fun clear(source: String) = withContext(Dispatchers.IO) { dao.deleteBySource(source) }
}
