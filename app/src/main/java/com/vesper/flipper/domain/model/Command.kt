package com.vesper.flipper.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The single command interface for AI agent interaction.
 * All Flipper operations go through this unified structure.
 */
@Serializable
data class ExecuteCommand(
    val action: CommandAction,
    val args: CommandArgs,
    val justification: String,
    @SerialName("expected_effect")
    val expectedEffect: String
)

@Serializable
enum class CommandAction {
    @SerialName("list_directory")
    LIST_DIRECTORY,

    @SerialName("read_file")
    READ_FILE,

    @SerialName("write_file")
    WRITE_FILE,

    @SerialName("create_directory")
    CREATE_DIRECTORY,

    @SerialName("delete")
    DELETE,

    @SerialName("move")
    MOVE,

    @SerialName("rename")
    RENAME,

    @SerialName("copy")
    COPY,

    @SerialName("get_device_info")
    GET_DEVICE_INFO,

    @SerialName("get_storage_info")
    GET_STORAGE_INFO,

    @SerialName("search_faphub")
    SEARCH_FAPHUB,

    @SerialName("install_faphub_app")
    INSTALL_FAPHUB_APP,

    @SerialName("push_artifact")
    PUSH_ARTIFACT,

    @SerialName("execute_cli")
    EXECUTE_CLI,

    @SerialName("forge_payload")
    FORGE_PAYLOAD,

    @SerialName("search_resources")
    SEARCH_RESOURCES,

    @SerialName("list_vault")
    LIST_VAULT,

    @SerialName("run_runbook")
    RUN_RUNBOOK,

    // ── Hardware control actions ──────────────────────────────

    @SerialName("launch_app")
    LAUNCH_APP,

    @SerialName("subghz_transmit")
    SUBGHZ_TRANSMIT,

    @SerialName("subghz_receive")
    SUBGHZ_RECEIVE,

    @SerialName("subghz_decode_raw")
    SUBGHZ_DECODE_RAW,

    @SerialName("ir_transmit")
    IR_TRANSMIT,

    @SerialName("ir_transmit_raw")
    IR_TRANSMIT_RAW,

    @SerialName("ir_receive")
    IR_RECEIVE,

    @SerialName("nfc_emulate")
    NFC_EMULATE,

    @SerialName("nfc_detect")
    NFC_DETECT,

    @SerialName("nfc_field")
    NFC_FIELD,

    @SerialName("rfid_emulate")
    RFID_EMULATE,

    @SerialName("rfid_read")
    RFID_READ,

    @SerialName("rfid_write")
    RFID_WRITE,

    @SerialName("ibutton_emulate")
    IBUTTON_EMULATE,

    @SerialName("badusb_execute")
    BADUSB_EXECUTE,

    @SerialName("gpio_read")
    GPIO_READ,

    @SerialName("gpio_set")
    GPIO_SET,

    @SerialName("gpio_mode")
    GPIO_MODE,

    @SerialName("apps_list")
    APPS_LIST,

    @SerialName("music_play")
    MUSIC_PLAY,

    @SerialName("music_get_format")
    MUSIC_GET_FORMAT,

    @SerialName("get_system_info")
    GET_SYSTEM_INFO,

    @SerialName("ble_spam")
    BLE_SPAM,

    @SerialName("led_control")
    LED_CONTROL,

    @SerialName("vibro_control")
    VIBRO_CONTROL,

    @SerialName("browse_repo")
    BROWSE_REPO,

    @SerialName("download_resource")
    DOWNLOAD_RESOURCE,

    @SerialName("github_search")
    GITHUB_SEARCH,

    // ── Smartglasses camera ───────────────────────────────────

    @SerialName("request_photo")
    REQUEST_PHOTO
}

@Serializable
data class CommandArgs(
    val command: String? = null,
    val path: String? = null,
    @SerialName("destination_path")
    val destinationPath: String? = null,
    val content: String? = null,
    @SerialName("new_name")
    val newName: String? = null,
    val recursive: Boolean = false,
    @SerialName("artifact_type")
    val artifactType: String? = null,
    @SerialName("artifact_data")
    val artifactData: String? = null,
    val prompt: String? = null,
    @SerialName("resource_type")
    val resourceType: String? = null,
    @SerialName("runbook_id")
    val runbookId: String? = null,
    @SerialName("payload_type")
    val payloadType: String? = null,
    val filter: String? = null,

    // Hardware control fields
    @SerialName("app_name")
    val appName: String? = null,
    @SerialName("app_args")
    val appArgs: String? = null,
    val frequency: Long? = null,
    val protocol: String? = null,
    val address: String? = null,
    @SerialName("signal_name")
    val signalName: String? = null,
    val enabled: Boolean? = null,
    val red: Int? = null,
    val green: Int? = null,
    val blue: Int? = null,
    @SerialName("repo_id")
    val repoId: String? = null,
    @SerialName("sub_path")
    val subPath: String? = null,
    @SerialName("download_url")
    val downloadUrl: String? = null,
    @SerialName("search_scope")
    val searchScope: String? = null,
    @SerialName("photo_prompt")
    val photoPrompt: String? = null,

    // Hardware CLI-wrapper args (GPIO, RFID, NFC/IR/SubGHz timing, IR raw)
    val pin: String? = null,
    val value: Int? = null,
    val mode: Int? = null,
    @SerialName("duty_cycle")
    val dutyCycle: Double? = null,
    @SerialName("key_type")
    val keyType: String? = null,
    @SerialName("key_data")
    val keyData: String? = null,
    val duration: Double? = null
)

/**
 * Result returned after command execution
 */
@Serializable
data class CommandResult(
    val success: Boolean,
    val action: CommandAction,
    val data: CommandResultData? = null,
    val error: String? = null,
    @SerialName("execution_time_ms")
    val executionTimeMs: Long = 0,
    @SerialName("requires_confirmation")
    val requiresConfirmation: Boolean = false,
    @SerialName("pending_approval_id")
    val pendingApprovalId: String? = null
)

@Serializable
data class CommandResultData(
    val entries: List<FileEntry>? = null,
    val content: String? = null,
    @SerialName("bytes_written")
    val bytesWritten: Long? = null,
    @SerialName("device_info")
    val deviceInfo: DeviceInfo? = null,
    @SerialName("storage_info")
    val storageInfo: StorageInfo? = null,
    val diff: FileDiff? = null,
    val message: String? = null
)

@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    @SerialName("is_directory")
    val isDirectory: Boolean,
    val size: Long = 0,
    @SerialName("modified_timestamp")
    val modifiedTimestamp: Long? = null
)

@Serializable
data class DeviceInfo(
    val name: String,
    @SerialName("firmware_version")
    val firmwareVersion: String,
    @SerialName("hardware_version")
    val hardwareVersion: String,
    @SerialName("battery_level")
    val batteryLevel: Int,
    @SerialName("is_charging")
    val isCharging: Boolean
)

@Serializable
data class StorageInfo(
    @SerialName("internal_total")
    val internalTotal: Long,
    @SerialName("internal_free")
    val internalFree: Long,
    @SerialName("external_total")
    val externalTotal: Long? = null,
    @SerialName("external_free")
    val externalFree: Long? = null,
    @SerialName("has_sd_card")
    val hasSdCard: Boolean
)

@Serializable
data class FileDiff(
    @SerialName("original_content")
    val originalContent: String?,
    @SerialName("new_content")
    val newContent: String,
    @SerialName("lines_added")
    val linesAdded: Int,
    @SerialName("lines_removed")
    val linesRemoved: Int,
    @SerialName("unified_diff")
    val unifiedDiff: String
)
