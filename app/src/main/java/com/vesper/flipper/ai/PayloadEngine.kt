package com.vesper.flipper.ai

import com.vesper.flipper.ble.FlipperBleService
import com.vesper.flipper.ble.FlipperFileSystem
import com.vesper.flipper.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Payload Engine - 3-Step AI Generation Pipeline
 *
 * Implements an optimal workflow for generating BadUSB scripts and Evil Portal pages:
 *
 * STEP 1: GENERATE - Initial payload creation with detailed prompting
 * STEP 2: VALIDATE - Syntax check, security review, optimization
 * STEP 3: EXECUTE  - Save to Flipper, search web, execute commands via function calling
 *
 * Each step uses a separate, specialized prompt for optimal results.
 */
@Singleton
class PayloadEngine @Inject constructor(
    private val openRouterClient: OpenRouterClient,
    private val flipperFileSystem: FlipperFileSystem
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ============================================================
    // BADUSB 3-STEP PIPELINE
    // ============================================================

    /**
     * Complete BadUSB generation pipeline
     */
    suspend fun generateBadUsb(
        request: BadUsbRequest,
        onProgress: (PayloadProgress) -> Unit
    ): PayloadResult<String> = withContext(Dispatchers.IO) {
        try {
            // STEP 1: Generate
            onProgress(PayloadProgress.Generating("Creating initial script..."))
            val generated = step1GenerateBadUsb(request)
            if (generated.isFailure) {
                return@withContext PayloadResult.Error(generated.exceptionOrNull()?.message ?: "Generation failed")
            }
            val rawScript = generated.getOrThrow()
            onProgress(PayloadProgress.Generated(rawScript))

            // STEP 2: Validate & Clean
            onProgress(PayloadProgress.Validating("Validating syntax and optimizing..."))
            val validated = step2ValidateBadUsb(rawScript, request.platform)
            if (validated.isFailure) {
                // Return raw script if validation fails - still usable
                return@withContext PayloadResult.Success(
                    payload = rawScript,
                    warnings = listOf("Validation failed: ${validated.exceptionOrNull()?.message}. Using raw output.")
                )
            }
            val cleanScript = validated.getOrThrow()
            onProgress(PayloadProgress.Validated(cleanScript))

            // STEP 3: Ready for execution
            onProgress(PayloadProgress.Ready(cleanScript))

            PayloadResult.Success(
                payload = cleanScript.script,
                metadata = cleanScript.toMetadata(),
                suggestedActions = cleanScript.suggestedActions
            )
        } catch (e: Exception) {
            PayloadResult.Error(e.message ?: "Pipeline failed")
        }
    }

    /**
     * STEP 1: Generate initial BadUSB script
     */
    private suspend fun step1GenerateBadUsb(request: BadUsbRequest): Result<String> {
        val prompt = PayloadPrompts.BadUSB.step1Generate(
            description = request.description,
            platform = request.platform,
            executionMode = request.executionMode,
            constraints = request.constraints
        )

        val message = ChatMessage(role = MessageRole.USER, content = prompt)
        return openRouterClient.sendMessagesWithoutTools(
            messages = listOf(message)
        ).map { cleanCodeBlock(it) }
    }

    /**
     * Public entry point that runs only step 2 (validate + clean) against an already-authored
     * script. Used by BADUSB_VALIDATE (chat) to lint scripts without regenerating from scratch.
     */
    suspend fun validateBadUsb(
        script: String,
        platform: BadUsbPlatform,
    ): Result<ValidatedBadUsbScript> = step2ValidateBadUsb(script, platform)

    /**
     * STEP 2: Validate and clean BadUSB script
     */
    private suspend fun step2ValidateBadUsb(
        rawScript: String,
        platform: BadUsbPlatform
    ): Result<ValidatedBadUsbScript> {
        val prompt = PayloadPrompts.BadUSB.step2Validate(rawScript, platform)

        val message = ChatMessage(role = MessageRole.USER, content = prompt)
        return openRouterClient.sendMessagesWithoutTools(
            messages = listOf(message)
        ).mapCatching { response ->
            val cleaned = cleanCodeBlock(response)
            // Try to parse as JSON response
            val validationResult = try {
                json.decodeFromString<BadUsbValidationResponse>(cleaned)
            } catch (e: Exception) {
                // If not JSON, treat as cleaned script
                BadUsbValidationResponse(
                    isValid = true,
                    script = cleaned,
                    errors = emptyList(),
                    warnings = emptyList(),
                    optimizations = emptyList(),
                    suggestedActions = emptyList()
                )
            }
            ValidatedBadUsbScript(
                script = validationResult.script,
                isValid = validationResult.isValid,
                errors = validationResult.errors,
                warnings = validationResult.warnings,
                optimizations = validationResult.optimizations,
                suggestedActions = validationResult.suggestedActions
            )
        }
    }

    /**
     * STEP 3: Execute BadUSB actions (save, search, run)
     */
    suspend fun executeBadUsbAction(
        action: PayloadAction,
        script: String,
        filename: String
    ): Result<String> = withContext(Dispatchers.IO) {
        when (action) {
            is PayloadAction.SaveToFlipper -> {
                val path = "/ext/badusb/$filename.txt"
                flipperFileSystem.writeFile(path, script).map { "Saved to $path" }
            }
            is PayloadAction.WebSearch -> {
                // Return search query for UI to handle
                Result.success("SEARCH:${action.query}")
            }
            is PayloadAction.ExecuteFlipperCommand -> {
                // Execute CLI command on Flipper
                Result.success("COMMAND:${action.command}")
            }
            is PayloadAction.RunBadUsb -> {
                // Save and trigger execution
                val path = "/ext/badusb/$filename.txt"
                flipperFileSystem.writeFile(path, script)
                Result.success("EXECUTE:badusb $path")
            }
        }
    }

    // ============================================================
    // EVIL PORTAL 3-STEP PIPELINE
    // ============================================================

    /**
     * Complete Evil Portal generation pipeline
     */
    suspend fun generateEvilPortal(
        request: EvilPortalRequest,
        onProgress: (PayloadProgress) -> Unit
    ): PayloadResult<String> = withContext(Dispatchers.IO) {
        try {
            // STEP 1: Generate
            onProgress(PayloadProgress.Generating("Creating portal HTML..."))
            val generated = step1GeneratePortal(request)
            if (generated.isFailure) {
                return@withContext PayloadResult.Error(generated.exceptionOrNull()?.message ?: "Generation failed")
            }
            val rawHtml = generated.getOrThrow()
            onProgress(PayloadProgress.Generated(rawHtml))

            // STEP 2: Validate & Clean
            onProgress(PayloadProgress.Validating("Validating HTML and optimizing..."))
            val validated = step2ValidatePortal(rawHtml, request.portalType)
            if (validated.isFailure) {
                return@withContext PayloadResult.Success(
                    payload = rawHtml,
                    warnings = listOf("Validation failed: ${validated.exceptionOrNull()?.message}. Using raw output.")
                )
            }
            val cleanHtml = validated.getOrThrow()
            onProgress(PayloadProgress.Validated(cleanHtml))

            // STEP 3: Ready for execution
            onProgress(PayloadProgress.Ready(cleanHtml))

            PayloadResult.Success(
                payload = cleanHtml.html,
                metadata = cleanHtml.toMetadata(),
                suggestedActions = cleanHtml.suggestedActions
            )
        } catch (e: Exception) {
            PayloadResult.Error(e.message ?: "Pipeline failed")
        }
    }

    /**
     * STEP 1: Generate initial Evil Portal HTML
     */
    private suspend fun step1GeneratePortal(request: EvilPortalRequest): Result<String> {
        val prompt = if (request.screenshotBase64 != null) {
            PayloadPrompts.EvilPortal.step1GenerateFromScreenshot(
                additionalInstructions = request.description,
                portalType = request.portalType
            )
        } else {
            PayloadPrompts.EvilPortal.step1GenerateFromDescription(
                description = request.description,
                portalType = request.portalType,
                brandColors = request.brandColors
            )
        }

        val message = if (request.screenshotBase64 != null) {
            ChatMessage(
                role = MessageRole.USER,
                content = prompt,
                imageAttachments = listOf(ImageAttachment(
                    base64Data = request.screenshotBase64,
                    mimeType = "image/jpeg"
                ))
            )
        } else {
            ChatMessage(role = MessageRole.USER, content = prompt)
        }

        return openRouterClient.sendMessagesWithoutTools(
            messages = listOf(message)
        ).map { cleanCodeBlock(it) }
    }

    /**
     * STEP 2: Validate and clean Evil Portal HTML
     */
    private suspend fun step2ValidatePortal(
        rawHtml: String,
        portalType: PortalType
    ): Result<ValidatedPortalHtml> {
        val prompt = PayloadPrompts.EvilPortal.step2Validate(rawHtml, portalType)

        val message = ChatMessage(role = MessageRole.USER, content = prompt)
        return openRouterClient.sendMessagesWithoutTools(
            messages = listOf(message)
        ).mapCatching { response ->
            val cleaned = cleanCodeBlock(response)
            val validationResult = try {
                json.decodeFromString<PortalValidationResponse>(cleaned)
            } catch (e: Exception) {
                PortalValidationResponse(
                    isValid = true,
                    html = cleaned,
                    errors = emptyList(),
                    warnings = emptyList(),
                    optimizations = emptyList(),
                    sizeBytes = cleaned.toByteArray().size,
                    suggestedActions = emptyList()
                )
            }
            ValidatedPortalHtml(
                html = validationResult.html,
                isValid = validationResult.isValid,
                errors = validationResult.errors,
                warnings = validationResult.warnings,
                sizeBytes = validationResult.sizeBytes,
                suggestedActions = validationResult.suggestedActions
            )
        }
    }

    /**
     * STEP 3: Execute Evil Portal actions
     */
    suspend fun executePortalAction(
        action: PayloadAction,
        html: String,
        portalName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        when (action) {
            is PayloadAction.SaveToFlipper -> {
                val dir = "/ext/apps_data/evil_portal/portals/$portalName"
                val indexPath = "$dir/index.html"
                flipperFileSystem.createDirectory(dir)
                flipperFileSystem.writeFile(indexPath, html).map { "Saved to $indexPath" }
            }
            is PayloadAction.WebSearch -> {
                Result.success("SEARCH:${action.query}")
            }
            is PayloadAction.ExecuteFlipperCommand -> {
                Result.success("COMMAND:${action.command}")
            }
            is PayloadAction.RunBadUsb -> {
                Result.failure(Exception("Invalid action for Evil Portal"))
            }
        }
    }

    // ============================================================
    // UTILITY FUNCTIONS
    // ============================================================

    private fun cleanCodeBlock(content: String): String {
        return content
            .replace(Regex("```[a-zA-Z]*\\n?"), "")
            .replace("```", "")
            .trim()
    }
}

// ============================================================
// DATA CLASSES
// ============================================================

data class BadUsbRequest(
    val description: String,
    val platform: BadUsbPlatform,
    val executionMode: ExecutionMode = ExecutionMode.NORMAL,
    val constraints: List<String> = emptyList()
)

data class EvilPortalRequest(
    val description: String,
    val portalType: PortalType = PortalType.GENERIC,
    val screenshotBase64: String? = null,
    val brandColors: BrandColors? = null
)

data class BrandColors(
    val primary: String,
    val secondary: String? = null,
    val background: String? = null
)

enum class ExecutionMode {
    NORMAL,      // Standard execution with delays
    STEALTH,     // Hidden windows, clear history
    AGGRESSIVE   // Fast execution, no cleanup
}

enum class PortalType {
    GENERIC,
    CORPORATE_SSO,
    GOOGLE_WORKSPACE,
    MICROSOFT_365,
    SOCIAL_MEDIA,
    HOTEL_WIFI,
    COFFEE_SHOP,
    AIRPORT,
    BANKING
}

sealed class PayloadProgress {
    data class Generating(val message: String) : PayloadProgress()
    data class Generated(val rawPayload: String) : PayloadProgress()
    data class Validating(val message: String) : PayloadProgress()
    data class Validated(val result: Any) : PayloadProgress()
    data class Ready(val finalPayload: Any) : PayloadProgress()
}

sealed class PayloadResult<T> {
    data class Success<T>(
        val payload: T,
        val metadata: Map<String, Any> = emptyMap(),
        val warnings: List<String> = emptyList(),
        val suggestedActions: List<SuggestedAction> = emptyList()
    ) : PayloadResult<T>()

    data class Error<T>(val message: String) : PayloadResult<T>()
}

sealed class PayloadAction {
    data class SaveToFlipper(val path: String) : PayloadAction()
    data class WebSearch(val query: String) : PayloadAction()
    data class ExecuteFlipperCommand(val command: String) : PayloadAction()
    data class RunBadUsb(val filename: String) : PayloadAction()
}

@Serializable
data class SuggestedAction(
    val type: String,       // "save", "search", "execute", "run"
    val label: String,
    val value: String,
    val description: String? = null
)

// Validation response models
@Serializable
data class BadUsbValidationResponse(
    val isValid: Boolean,
    val script: String,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val optimizations: List<String> = emptyList(),
    val suggestedActions: List<SuggestedAction> = emptyList()
)

@Serializable
data class PortalValidationResponse(
    val isValid: Boolean,
    val html: String,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val optimizations: List<String> = emptyList(),
    val sizeBytes: Int = 0,
    val suggestedActions: List<SuggestedAction> = emptyList()
)

data class ValidatedBadUsbScript(
    val script: String,
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val optimizations: List<String> = emptyList(),
    val suggestedActions: List<SuggestedAction> = emptyList()
) {
    fun toMetadata(): Map<String, Any> = mapOf(
        "isValid" to isValid,
        "errors" to errors,
        "warnings" to warnings,
        "optimizations" to optimizations,
        "lineCount" to script.lines().size
    )
}

data class ValidatedPortalHtml(
    val html: String,
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val sizeBytes: Int = 0,
    val suggestedActions: List<SuggestedAction> = emptyList()
) {
    fun toMetadata(): Map<String, Any> = mapOf(
        "isValid" to isValid,
        "errors" to errors,
        "warnings" to warnings,
        "sizeBytes" to sizeBytes,
        "sizeKb" to (sizeBytes / 1024.0)
    )
}
