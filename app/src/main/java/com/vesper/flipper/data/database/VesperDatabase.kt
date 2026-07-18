package com.vesper.flipper.data.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [AuditEntryEntity::class, ChatMessageEntity::class, VulnFindingEntity::class],
    version = 3,
    exportSchema = false
)
abstract class VesperDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao
    abstract fun chatDao(): ChatDao
    abstract fun vulnDao(): VulnDao

    companion object {
        @Volatile
        private var INSTANCE: VesperDatabase? = null

        fun getDatabase(context: Context): VesperDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VesperDatabase::class.java,
                    "vesper_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "audit_entries")
data class AuditEntryEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val actionType: String,
    val commandJson: String?,
    val resultJson: String?,
    val riskLevel: String?,
    val userApproved: Boolean?,
    val approvalMethod: String?,
    val sessionId: String,
    val metadataJson: String
)

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AuditEntryEntity>)

    @Query("SELECT * FROM audit_entries WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getEntriesForSession(sessionId: String): Flow<List<AuditEntryEntity>>

    @Query("SELECT * FROM audit_entries WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getEntriesForSessionSync(sessionId: String): List<AuditEntryEntity>

    @Query("SELECT * FROM audit_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEntries(limit: Int): Flow<List<AuditEntryEntity>>

    @Query("SELECT * FROM audit_entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEntriesSync(limit: Int): List<AuditEntryEntity>

    @Query("SELECT * FROM audit_entries WHERE actionType = :actionType ORDER BY timestamp DESC LIMIT :limit")
    fun getEntriesByType(actionType: String, limit: Int): Flow<List<AuditEntryEntity>>

    @Query("DELETE FROM audit_entries WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM audit_entries")
    suspend fun deleteAll()
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val toolCallsJson: String?,
    val toolResultsJson: String?,
    val status: String,
    val metadataJson: String?,
    val sessionId: String,
    val imageAttachmentsJson: String? = null
)

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSessionSync(sessionId: String): List<ChatMessageEntity>

    @Query("""
        SELECT sessionId, MIN(timestamp) AS firstTimestamp, MAX(timestamp) AS lastTimestamp,
               COUNT(*) AS messageCount
        FROM chat_messages
        GROUP BY sessionId
        ORDER BY lastTimestamp DESC
    """)
    fun getAllSessions(): Flow<List<ChatSessionSummary>>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}

data class ChatSessionSummary(
    val sessionId: String,
    val firstTimestamp: Long,
    val lastTimestamp: Long,
    val messageCount: Int
)

/**
 * Vulnerability finding recorded during an offensive-security engagement.
 * Ported from FlipperAgent's vuln_triage module (JSON file → Room table).
 * Statuses: submitted, confirmed, rejected, false_positive.
 * Severities: critical, high, medium, low.
 */
@Entity(tableName = "vuln_findings")
data class VulnFindingEntity(
    @PrimaryKey val id: String,
    val submittedAt: Long,
    val target: String,
    val vulnType: String,
    val description: String,
    val evidence: String,
    val severity: String,
    val complexity: Int,
    val status: String,
    val reproductionAttempts: Int = 0,
    val validatedAt: Long? = null,
    /** JSON array of {timestamp, reproduced, notes} objects, appended per vuln_validate call. */
    val validationNotesJson: String = "[]"
)

@Dao
interface VulnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(finding: VulnFindingEntity)

    @Query("SELECT * FROM vuln_findings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VulnFindingEntity?

    @Query("SELECT * FROM vuln_findings ORDER BY submittedAt DESC")
    suspend fun getAll(): List<VulnFindingEntity>

    @Query("DELETE FROM vuln_findings")
    suspend fun deleteAll()
}
