package com.vesper.flipper.data.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        AuditEntryEntity::class,
        ChatMessageEntity::class,
        VulnFindingEntity::class,
        CampaignStateEntity::class,
        CampaignFindingEntity::class,
    ],
    version = 4,
    exportSchema = false
)
abstract class VesperDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao
    abstract fun chatDao(): ChatDao
    abstract fun vulnDao(): VulnDao
    abstract fun campaignDao(): CampaignDao

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

/**
 * A Ralph autonomous campaign — an unattended engagement that walks through
 * recon -> research -> enumerate -> exploit -> report against a declared scope.
 *
 * Statuses: PENDING (created but not started), RUNNING (a phase worker is active),
 * AWAITING_APPROVAL (paused at a HIGH-risk action or at the exploit gate),
 * PAUSED (user paused or a cap was hit), DONE, FAILED.
 *
 * Mode: AUTONOMOUS_SAFE inherits the user's autoApproveMedium setting for MEDIUM
 * actions and always pauses on HIGH. AUTONOMOUS_TRUSTED auto-executes MEDIUM
 * regardless of the user's setting but still always pauses on HIGH. Neither mode
 * ever executes HIGH without a human — that invariant is enforced at
 * CommandExecutor, not at the campaign layer.
 */
@Entity(tableName = "campaign_state")
data class CampaignStateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scope: String,
    /** JSON array of in-scope target identifiers (MAC, BSSID, freq, …). */
    val scopeTargetsJson: String,
    /** JSON array of explicit out-of-scope target identifiers. */
    val outOfScopeJson: String,
    val mode: String,
    val status: String,
    val currentPhase: String,
    val iterationCount: Int = 0,
    val maxIterations: Int = 10,
    val createdAt: Long,
    val updatedAt: Long,
    val pauseReason: String? = null,
    val failureReason: String? = null,
)

/**
 * Worker output — one row per finding produced by a phase worker within a
 * campaign. Distinct from [VulnFindingEntity]: campaign findings are the
 * worker's persistent scratch memory (passed between phases within one
 * campaign), while [VulnFindingEntity] rows are operator-visible security
 * findings that may span campaigns.
 *
 * A worker typically produces multiple campaign findings per phase — one per
 * discovered target, per enumerated service, per attack vector, etc. — and the
 * next phase reads its predecessors' campaign findings to build its own context
 * without inheriting an ever-growing LLM conversation.
 */
@Entity(tableName = "campaign_finding")
data class CampaignFindingEntity(
    @PrimaryKey val id: String,
    val campaignId: String,
    val phase: String,
    /**
     * Optional target identifier — matches against campaign scope targets. May be
     * null for phase-summary rows or findings that don't attach to a single
     * target.
     */
    val targetId: String? = null,
    val payloadJson: String,
    val riskLevel: String? = null,
    val createdAt: Long,
)

@Dao
interface CampaignDao {
    // ─── State ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: CampaignStateEntity)

    @Query("SELECT * FROM campaign_state WHERE id = :id LIMIT 1")
    suspend fun getState(id: String): CampaignStateEntity?

    @Query("SELECT * FROM campaign_state WHERE id = :id LIMIT 1")
    fun observeState(id: String): Flow<CampaignStateEntity?>

    @Query("SELECT * FROM campaign_state ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CampaignStateEntity>>

    @Query("SELECT * FROM campaign_state ORDER BY updatedAt DESC")
    suspend fun getAll(): List<CampaignStateEntity>

    @Query("SELECT * FROM campaign_state WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun getByStatus(status: String): List<CampaignStateEntity>

    @Query("DELETE FROM campaign_state WHERE id = :id")
    suspend fun deleteState(id: String)

    // ─── Findings ───────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: CampaignFindingEntity)

    @Query(
        "SELECT * FROM campaign_finding WHERE campaignId = :campaignId " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getFindingsForCampaign(campaignId: String): List<CampaignFindingEntity>

    @Query(
        "SELECT * FROM campaign_finding WHERE campaignId = :campaignId " +
            "ORDER BY createdAt ASC"
    )
    fun observeFindingsForCampaign(campaignId: String): Flow<List<CampaignFindingEntity>>

    @Query(
        "SELECT * FROM campaign_finding WHERE campaignId = :campaignId " +
            "AND phase IN (:phases) ORDER BY createdAt ASC"
    )
    suspend fun getFindingsForCampaignAndPhases(
        campaignId: String,
        phases: List<String>,
    ): List<CampaignFindingEntity>

    @Query(
        "SELECT COUNT(*) FROM campaign_finding WHERE campaignId = :campaignId AND phase = :phase"
    )
    suspend fun countFindingsInPhase(campaignId: String, phase: String): Int

    @Query("DELETE FROM campaign_finding WHERE campaignId = :campaignId")
    suspend fun deleteFindingsForCampaign(campaignId: String)
}
