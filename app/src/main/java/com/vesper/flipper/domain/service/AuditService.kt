package com.vesper.flipper.domain.service

import com.vesper.flipper.data.database.AuditDao
import com.vesper.flipper.data.database.AuditEntryEntity
import com.vesper.flipper.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audit logging service.
 * All agent actions are logged for accountability and replay.
 */
@Singleton
class AuditService @Inject constructor(
    private val auditDao: AuditDao
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var currentSession: AuditSession? = null

    private val _recentEntries = MutableStateFlow<List<AuditEntry>>(emptyList())
    val recentEntries: StateFlow<List<AuditEntry>> = _recentEntries.asStateFlow()

    /**
     * Start a new audit session
     */
    fun startSession(deviceName: String? = null): AuditSession {
        val session = AuditSession(
            deviceName = deviceName
        )
        currentSession = session

        log(
            AuditEntry(
                actionType = AuditActionType.SESSION_STARTED,
                sessionId = session.id,
                metadata = mapOf("device_name" to (deviceName ?: "unknown"))
            )
        )

        return session
    }

    /**
     * End the current session
     */
    fun endSession() {
        currentSession?.let { session ->
            log(
                AuditEntry(
                    actionType = AuditActionType.SESSION_ENDED,
                    sessionId = session.id
                )
            )
        }
        currentSession = null
    }

    /**
     * Log an audit entry
     */
    fun log(entry: AuditEntry) {
        scope.launch {
            val entity = AuditEntryEntity(
                id = entry.id,
                timestamp = entry.timestamp,
                actionType = entry.actionType.name,
                commandJson = entry.command?.let { json.encodeToString(it) },
                resultJson = entry.result?.let { json.encodeToString(it) },
                riskLevel = entry.riskLevel?.name,
                userApproved = entry.userApproved,
                approvalMethod = entry.approvalMethod?.name,
                sessionId = entry.sessionId,
                metadataJson = json.encodeToString(entry.metadata)
            )
            auditDao.insert(entity)
            refreshRecentEntries()
        }
    }

    /**
     * Get entries for a session
     */
    fun getEntriesForSession(sessionId: String): Flow<List<AuditEntry>> {
        return auditDao.getEntriesForSession(sessionId)
            .map { entities -> entities.map { it.toAuditEntry(json) } }
    }

    /**
     * Get recent entries across all sessions
     */
    fun getRecentEntries(limit: Int = 100): Flow<List<AuditEntry>> {
        return auditDao.getRecentEntries(limit)
            .map { entities -> entities.map { it.toAuditEntry(json) } }
    }

    /**
     * Get entries by action type
     */
    fun getEntriesByType(actionType: AuditActionType, limit: Int = 50): Flow<List<AuditEntry>> {
        return auditDao.getEntriesByType(actionType.name, limit)
            .map { entities -> entities.map { it.toAuditEntry(json) } }
    }

    /**
     * One-shot audit query used by the AUDIT_QUERY command action. Pulls up to a widened window
     * of recent rows and filters in memory — matches FlipperAgent's audit_query semantics without
     * requiring a specialized DAO index. [commandActionFilter] is compared against the serialized
     * CommandAction inside commandJson; [riskFilter] against the persisted risk level string.
     */
    suspend fun queryRecent(
        limit: Int,
        commandActionFilter: String?,
        riskFilter: RiskLevel?,
    ): List<AuditEntry> {
        val effectiveLimit = limit.coerceIn(1, 500)
        // Widen the DAO pull so filters have material to prune. Cap conservatively — this is
        // called on a background dispatcher from the executor and should stay cheap.
        val pulled = auditDao.getRecentEntriesSync(effectiveLimit * 5)
        val filtered = pulled.asSequence()
            .filter { row ->
                val actionOk = commandActionFilter?.let { needle ->
                    row.commandJson?.contains("\"action\":\"$needle\"", ignoreCase = true) == true
                } ?: true
                val riskOk = riskFilter?.let { rl -> row.riskLevel == rl.name } ?: true
                actionOk && riskOk
            }
            .take(effectiveLimit)
            .map { it.toAuditEntry(json) }
            .toList()
        return filtered
    }

    /**
     * Get summary statistics for a session
     */
    suspend fun getSessionSummary(sessionId: String): AuditSummary {
        val entries = auditDao.getEntriesForSessionSync(sessionId)

        val commandEntries = entries.filter {
            it.actionType in listOf(
                AuditActionType.COMMAND_EXECUTED.name,
                AuditActionType.COMMAND_FAILED.name,
                AuditActionType.COMMAND_BLOCKED.name
            )
        }

        val successful = commandEntries.count { it.actionType == AuditActionType.COMMAND_EXECUTED.name }
        val failed = commandEntries.count { it.actionType == AuditActionType.COMMAND_FAILED.name }
        val blocked = commandEntries.count { it.actionType == AuditActionType.COMMAND_BLOCKED.name }

        val approvalEntries = entries.filter {
            it.actionType in listOf(
                AuditActionType.APPROVAL_GRANTED.name,
                AuditActionType.APPROVAL_DENIED.name
            )
        }
        val approved = approvalEntries.count { it.actionType == AuditActionType.APPROVAL_GRANTED.name }
        val approvalRate = if (approvalEntries.isNotEmpty()) {
            approved.toFloat() / approvalEntries.size
        } else {
            1.0f
        }

        // Count actions
        val actionCounts = mutableMapOf<CommandAction, Int>()
        commandEntries.forEach { entity ->
            entity.commandJson?.let { cmdJson ->
                try {
                    val cmd = json.decodeFromString<ExecuteCommand>(cmdJson)
                    actionCounts[cmd.action] = (actionCounts[cmd.action] ?: 0) + 1
                } catch (e: Exception) {
                    // Ignore malformed historical rows and continue aggregation.
                }
            }
        }

        // Count risk levels
        val riskCounts = mutableMapOf<RiskLevel, Int>()
        commandEntries.forEach { entity ->
            entity.riskLevel?.let { risk ->
                try {
                    val level = RiskLevel.valueOf(risk)
                    riskCounts[level] = (riskCounts[level] ?: 0) + 1
                } catch (e: Exception) {
                    // Ignore malformed historical rows and continue aggregation.
                }
            }
        }

        val sessionStart = entries.minOfOrNull { it.timestamp } ?: 0
        val sessionEnd = entries.maxOfOrNull { it.timestamp } ?: 0

        return AuditSummary(
            totalCommands = commandEntries.size,
            successfulCommands = successful,
            failedCommands = failed,
            blockedCommands = blocked,
            approvalRate = approvalRate,
            mostCommonActions = actionCounts.toList().sortedByDescending { it.second },
            riskBreakdown = riskCounts,
            sessionDuration = sessionEnd - sessionStart
        )
    }

    /**
     * Clear all audit logs
     */
    suspend fun clearAll() {
        auditDao.deleteAll()
        _recentEntries.value = emptyList()
    }

    /**
     * Clear old audit logs (older than specified days)
     */
    suspend fun clearOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        auditDao.deleteOlderThan(cutoff)
        refreshRecentEntries()
    }

    private suspend fun refreshRecentEntries() {
        val entries = auditDao.getRecentEntriesSync(50)
        _recentEntries.value = entries.map { it.toAuditEntry(json) }
    }

    fun getCurrentSessionId(): String? = currentSession?.id
}

private fun AuditEntryEntity.toAuditEntry(json: Json): AuditEntry {
    return AuditEntry(
        id = id,
        timestamp = timestamp,
        actionType = AuditActionType.valueOf(actionType),
        command = commandJson?.let {
            try { json.decodeFromString<ExecuteCommand>(it) } catch (e: Exception) { null }
        },
        result = resultJson?.let {
            try { json.decodeFromString<CommandResult>(it) } catch (e: Exception) { null }
        },
        riskLevel = riskLevel?.let {
            try { RiskLevel.valueOf(it) } catch (e: Exception) { null }
        },
        userApproved = userApproved,
        approvalMethod = approvalMethod?.let {
            try { ApprovalMethod.valueOf(it) } catch (e: Exception) { null }
        },
        sessionId = sessionId,
        metadata = try {
            json.decodeFromString<Map<String, String>>(metadataJson)
        } catch (e: Exception) {
            emptyMap()
        }
    )
}
