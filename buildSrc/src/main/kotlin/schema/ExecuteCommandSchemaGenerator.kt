package schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Emits `docs/execute_command_schema.json` from:
 *   - the `@SerialName("...")` values on `CommandAction` in `Command.kt` (source of truth for action names)
 *   - a per-action arg-shape spec declared below (source of truth for which args each action uses)
 *
 * The generator parses `Command.kt` as text rather than reflecting into the compiled app, because
 * the app's runtime classpath is Android (Room/Hilt/Compose) and cannot be loaded from buildSrc.
 * The parse is intentionally strict: each `CommandAction` entry must have a preceding
 * `@SerialName("literal")`, and the extracted literal is the JSON action string.
 *
 * Nothing in the codebase reads this JSON at runtime — it exists as a contract for humans and for
 * LLM tool definitions. The verify task therefore compares parsed JSON (semantic equivalence),
 * not raw text.
 */
object ExecuteCommandSchemaGenerator {

    // ─── Per-action arg-shape spec ────────────────────────────────────────────
    // Order matches the CommandAction enum in Command.kt; enforceCoverage() checks that.

    private val ACTIONS: List<String> = listOf(
        "list_directory",
        "read_file",
        "write_file",
        "create_directory",
        "delete",
        "move",
        "rename",
        "copy",
        "get_device_info",
        "get_storage_info",
        "search_faphub",
        "install_faphub_app",
        "push_artifact",
        "execute_cli",
        "forge_payload",
        "search_resources",
        "list_vault",
        "run_runbook",
        "launch_app",
        "subghz_transmit",
        "subghz_receive",
        "subghz_decode_raw",
        "ir_transmit",
        "ir_transmit_raw",
        "ir_receive",
        "nfc_emulate",
        "nfc_detect",
        "nfc_field",
        "rfid_emulate",
        "rfid_read",
        "rfid_write",
        "ibutton_emulate",
        "badusb_execute",
        "badusb_generate",
        "badusb_validate",
        "badusb_write",
        "badusb_diff",
        "vuln_submit",
        "vuln_validate",
        "vuln_list",
        "vuln_classify",
        "audit_query",
        "load_skill",
        "gpio_read",
        "gpio_set",
        "gpio_mode",
        "apps_list",
        "music_play",
        "music_get_format",
        "get_system_info",
        "ble_spam",
        "ble_scan_targets",
        "ble_enumerate",
        "ble_read_char",
        "ble_write_char",
        "ble_subscribe",
        "led_control",
        "vibro_control",
        "browse_repo",
        "download_resource",
        "github_search",
        "request_photo",
    )

    private data class ArgSpec(
        val name: String,
        val type: String,
        val description: String,
        val enum: List<String>? = null,
        val default: JsonPrimitive? = null,
    )

    private val ARGS: List<ArgSpec> = listOf(
        ArgSpec("command", "string",
            "Free-form command string. For execute_cli: the CLI command. For search_faphub/github_search/search_resources: the query. For install_faphub_app: the app id or name. For forge_payload: fallback natural-language prompt. For launch_app: fallback app name. For run_runbook: fallback runbook id. For browse_repo: fallback repo id. For load_skill: the skill id."),
        ArgSpec("path", "string",
            "File or directory path on Flipper (e.g., /ext/subghz/garage.sub). Also the source path for move/copy/rename, target path for push_artifact/download_resource, and signal file for subghz_transmit/ir_transmit/nfc_emulate/rfid_emulate/ibutton_emulate/badusb_execute."),
        ArgSpec("destination_path", "string", "Destination path for move/copy operations"),
        ArgSpec("content", "string",
            "Content to write to file (for write_file action). Also used as a fallback for execute_cli commands and install_faphub_app direct download URL."),
        ArgSpec("new_name", "string", "New name for rename operation"),
        ArgSpec("recursive", "boolean", "Whether to delete recursively (for delete action)",
            default = JsonPrimitive(false)),
        ArgSpec("artifact_type", "string", "Type of artifact being pushed (for push_artifact action)"),
        ArgSpec("artifact_data", "string", "Base64-encoded artifact data (for push_artifact action)"),
        ArgSpec("prompt", "string", "Natural-language specification for forge_payload"),
        ArgSpec("resource_type", "string", "Resource type filter for search_resources"),
        ArgSpec("runbook_id", "string", "Runbook identifier (for run_runbook action)"),
        ArgSpec("payload_type", "string", "Type of payload to generate (for forge_payload action)",
            enum = listOf("subghz", "ir", "badusb", "nfc")),
        ArgSpec("filter", "string", "Filter string for list_vault"),
        ArgSpec("app_name", "string", "Application name (for launch_app action)"),
        ArgSpec("app_args", "string", "Arguments passed to the launched app; also used as the ble_spam mode string"),
        ArgSpec("frequency", "integer", "Frequency in Hz for SubGHz operations"),
        ArgSpec("protocol", "string", "Protocol name for SubGHz/IR/NFC operations"),
        ArgSpec("address", "string", "Address/identifier: hex address for SubGHz transmit; BLE MAC (AA:BB:CC:DD:EE:FF) for ble_enumerate/read_char/write_char/subscribe"),
        ArgSpec("signal_name", "string", "Named signal for IR transmit operations"),
        ArgSpec("enabled", "boolean", "On/off state (for vibro_control action; defaults to true)"),
        ArgSpec("red", "integer", "Red LED intensity 0-255 (for led_control action)"),
        ArgSpec("green", "integer", "Green LED intensity 0-255 (for led_control action)"),
        ArgSpec("blue", "integer", "Blue LED intensity 0-255 (for led_control action)"),
        ArgSpec("repo_id", "string", "Resource repository identifier (for browse_repo action)"),
        ArgSpec("sub_path", "string", "Sub-path within a browsed repo (for browse_repo action)"),
        ArgSpec("download_url", "string",
            "Direct URL of resource to download (for download_resource, or direct .fap URL for install_faphub_app)"),
        ArgSpec("search_scope", "string",
            "GitHub search scope, e.g. \"code\" or \"repositories\" (for github_search action; defaults to \"code\")"),
        ArgSpec("photo_prompt", "string", "Prompt describing what to capture (for request_photo action)"),
        ArgSpec("pin", "string", "GPIO pin identifier (for gpio_read/gpio_set/gpio_mode; e.g. PA7, PC3)"),
        ArgSpec("value", "integer", "GPIO pin output value 0 or 1 (for gpio_set)"),
        ArgSpec("mode", "integer", "GPIO pin mode: 0 = input, 1 = output (for gpio_mode)"),
        ArgSpec("duty_cycle", "number", "IR carrier duty cycle 0.0–1.0 (for ir_transmit_raw; defaults to 0.33)"),
        ArgSpec("key_type", "string", "RFID key type, e.g. EM4100 or HIDProx (for rfid_write)"),
        ArgSpec("key_data", "string", "RFID key data payload (for rfid_write)"),
        ArgSpec("duration", "number", "Listen duration in seconds for passive capture actions (subghz_receive, ir_receive, nfc_detect, nfc_field, rfid_read); connection timeout for ble_enumerate/ble_read_char; listen window for ble_subscribe"),
        ArgSpec("uuid", "string", "GATT characteristic UUID for BLE recon (128-bit like 00002a00-… or 4-hex-digit short form)"),
        ArgSpec("hex", "boolean", "For ble_write_char: true if content is hex-encoded bytes (default), false for UTF-8 text"),
        ArgSpec("with_response", "boolean", "For ble_write_char: true = write-with-response (default), false = write-without-response"),
        ArgSpec("target", "string", "Target identifier (IP, MAC, hostname) — required by vuln_submit; substring filter for vuln_list"),
        ArgSpec("vuln_type", "string", "Vulnerability type keyword (e.g. default_creds, writable_ble, sqli). Required by vuln_submit and vuln_classify"),
        ArgSpec("description", "string", "Human-readable description — required by vuln_submit and badusb_generate"),
        ArgSpec("evidence", "string", "Raw supporting evidence (tool output, response body) — required by vuln_submit"),
        ArgSpec("severity", "string", "Vulnerability severity — required by vuln_submit; optional filter for vuln_list",
            enum = listOf("critical", "high", "medium", "low")),
        ArgSpec("complexity", "integer", "Exploitation complexity 1–10 (1 = trivial, 10 = expert). Required by vuln_submit"),
        ArgSpec("vuln_id", "string", "Vulnerability finding UUID prefix returned by vuln_submit. Required by vuln_validate; optional for vuln_classify (reclassify existing finding)"),
        ArgSpec("reproduced", "boolean", "Whether the vulnerability was reproduced during validation. Required by vuln_validate"),
        ArgSpec("notes", "string", "Free-form validation notes appended to the finding (for vuln_validate)"),
        ArgSpec("status", "string", "Finding status filter for vuln_list",
            enum = listOf("submitted", "confirmed", "rejected", "false_positive")),
        ArgSpec("limit", "integer", "Row limit for audit_query (default 20)"),
        ArgSpec("risk_level", "string", "Risk-level filter for audit_query",
            enum = listOf("LOW", "MEDIUM", "HIGH", "BLOCKED")),
        ArgSpec("platform", "string", "Target OS for badusb_generate / badusb_validate / badusb_write (default: windows)",
            enum = listOf("windows", "macos", "linux")),
        ArgSpec("filename", "string", "BadUSB script filename under /ext/badusb/ (e.g. 'demo.txt'). Required by badusb_write"),
        ArgSpec("proposed_content", "string", "Proposed replacement content for badusb_diff (also accepts `content`)"),
    )

    private data class Example(
        val action: String,
        val args: Map<String, JsonPrimitive>,
        val justification: String,
        val expectedEffect: String,
    )

    private fun s(v: String) = JsonPrimitive(v)
    private fun i(v: Int) = JsonPrimitive(v)
    private fun b(v: Boolean) = JsonPrimitive(v)

    private val EXAMPLES: List<Example> = listOf(
        Example("list_directory", mapOf("path" to s("/ext/subghz")),
            "User wants to see their SubGHz captures",
            "Return a list of .sub files in the SubGHz directory"),
        Example("read_file", mapOf("path" to s("/ext/subghz/garage.sub")),
            "User wants to inspect the garage remote configuration",
            "Return the contents of the garage.sub file"),
        Example("write_file", mapOf(
            "path" to s("/ext/subghz/garage.sub"),
            "content" to s("Filetype: Flipper SubGhz Key File\nVersion: 1\nFrequency: 315000000\n..."),
        ),
            "User wants to change the frequency from 390MHz to 315MHz",
            "Update the garage.sub file with the new frequency"),
        Example("subghz_transmit", mapOf("path" to s("/ext/subghz/garage.sub")),
            "User wants to transmit the garage signal",
            "Transmit the SubGHz signal stored in garage.sub"),
        Example("execute_cli", mapOf("command" to s("info")),
            "User wants detailed system information from the Flipper",
            "Return system info output from the Flipper CLI"),
        Example("launch_app", mapOf("app_name" to s("Sub-GHz"), "app_args" to s("")),
            "User wants to open the SubGHz app on the Flipper",
            "Launch the Sub-GHz application on the Flipper Zero"),
        Example("led_control", mapOf("red" to i(0), "green" to i(255), "blue" to i(0)),
            "User wants visual feedback from the Flipper",
            "Turn on the green LED on the Flipper Zero"),
        Example("vibro_control", mapOf("enabled" to b(true)),
            "User wants haptic feedback",
            "Turn on the vibration motor"),
        Example("search_faphub", mapOf("command" to s("wifi scanner")),
            "User is looking for a WiFi scanning app",
            "Return matching applications from the FapHub catalog"),
        Example("install_faphub_app", mapOf(
            "command" to s("wifi_marauder"),
            "download_url" to s("https://example.com/wifi_marauder.fap"),
        ),
            "User wants to install the selected FapHub app",
            "Install the WiFi Marauder .fap onto the Flipper"),
        Example("forge_payload", mapOf(
            "payload_type" to s("badusb"),
            "prompt" to s("Open a browser to example.com"),
        ),
            "User wants a generated BadUSB payload",
            "Return a forged BadUSB script matching the spec"),
        Example("search_resources", mapOf(
            "command" to s("garage"),
            "resource_type" to s("subghz"),
        ),
            "User is browsing community SubGHz resources",
            "Return matching resource repositories"),
        Example("browse_repo", mapOf(
            "repo_id" to s("unleashed-firmware"),
            "sub_path" to s("subghz"),
        ),
            "User wants to inspect a specific resource repo",
            "List contents at the given sub-path within the repo"),
        Example("download_resource", mapOf(
            "download_url" to s("https://example.com/garage.sub"),
            "path" to s("/ext/subghz/garage.sub"),
        ),
            "User wants to download a signal file",
            "Download the resource to the given Flipper path"),
        Example("github_search", mapOf(
            "command" to s("flipper subghz replay"),
            "search_scope" to s("code"),
        ),
            "User wants GitHub code search results",
            "Return matching GitHub code results"),
        Example("list_vault", mapOf(
            "path" to s("/ext"),
            "filter" to s("*.sub"),
        ),
            "User wants to browse vault contents",
            "Return filtered vault entries"),
        Example("run_runbook", mapOf("runbook_id" to s("recon_basic")),
            "User wants to run a pre-defined recon routine",
            "Execute the referenced runbook"),
        Example("ble_spam", mapOf("app_args" to s("apple")),
            "User wants to run a BLE spam mode",
            "Launch BLE spam with the given mode"),
        Example("request_photo", mapOf("photo_prompt" to s("capture the target device screen")),
            "User wants a picture taken via smartglasses",
            "Trigger a photo capture at the agent layer"),
        Example("get_device_info", emptyMap(),
            "User wants to check device status",
            "Return battery level, firmware version, and hardware info"),
        Example("subghz_receive", mapOf("frequency" to i(433920000)),
            "User wants to capture ambient Sub-GHz at the default ISM frequency",
            "Return captured signal data from Sub-GHz RX"),
        Example("gpio_read", mapOf("pin" to s("PA7")),
            "User wants to sample an external sensor wired to PA7",
            "Return the current logic level of pin PA7"),
        Example("gpio_set", mapOf("pin" to s("PC3"), "value" to i(1)),
            "User wants to drive PC3 high to enable an external LED",
            "Set PC3 output to 1"),
        Example("rfid_write", mapOf(
            "key_type" to s("EM4100"),
            "key_data" to s("aabbccddee"),
        ),
            "User wants to program a blank EM4100 tag with a captured code",
            "Write the given data to the RFID tag held near the Flipper"),
        Example("music_play", mapOf(
            "content" to s("Filetype: Flipper Music Format\nVersion: 0\nBPM: 120\nDuration: 4\nOctave: 4\nNotes: 4C, 4D, 4E, 4C\n"),
        ),
            "User wants the Flipper to play a short melody",
            "Write the FMF song to /ext/music_player/ and launch Music Player"),
        Example("ble_scan_targets", mapOf("duration" to i(5)),
            "Operator wants to enumerate nearby BLE devices before choosing a target",
            "Return device list (address, RSSI, name, advertised services)"),
        Example("ble_enumerate", mapOf("address" to s("AA:BB:CC:DD:EE:FF")),
            "Operator wants the full GATT attack surface for a scanned device",
            "Return services and characteristics with read/write/notify properties"),
        Example("ble_write_char", mapOf(
            "address" to s("AA:BB:CC:DD:EE:FF"),
            "uuid" to s("0000ff01-0000-1000-8000-00805f9b34fb"),
            "content" to s("01FF00"),
            "hex" to b(true),
        ),
            "Operator wants to trigger a state change on the authorized test device",
            "Write the hex payload to the characteristic; requires HIGH-risk approval"),
        Example("vuln_submit", mapOf(
            "target" to s("192.168.1.10"),
            "vuln_type" to s("default_creds"),
            "description" to s("SSH login succeeded with admin:admin"),
            "evidence" to s("<sshpass output showing successful auth>"),
            "severity" to s("critical"),
            "complexity" to i(1),
        ),
            "Operator records a discovered vulnerability during an engagement",
            "Assign an ID, persist the finding, mark status=submitted"),
        Example("vuln_list", mapOf("severity" to s("critical"), "status" to s("confirmed")),
            "Operator wants a summary of every confirmed critical finding",
            "Return matching findings sorted by severity"),
        Example("audit_query", mapOf("limit" to i(20), "risk_level" to s("HIGH")),
            "Operator wants to review recent HIGH-risk actions from this session",
            "Return the last 20 HIGH-risk audit entries"),
        Example("badusb_generate", mapOf(
            "description" to s("open notepad and type 'Hello, world'"),
            "platform" to s("windows"),
        ),
            "Operator wants an authored BadUSB script for a demo",
            "Return a validated DuckyScript payload; no device effect"),
        Example("load_skill", mapOf("command" to s("ble-exploitation")),
            "Model needs BLE GATT methodology guidance before crafting a write payload",
            "Return the ble-exploitation SKILL.md content as tool output"),
    )

    // ─── Public entry points ──────────────────────────────────────────────────

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** Verifies [commandKt]'s CommandAction enum matches [ACTIONS] exactly. */
    fun enforceCoverage(commandKt: File) {
        val enumSerialNames = parseCommandActionSerialNames(commandKt)
        val extra = enumSerialNames - ACTIONS.toSet()
        val missing = ACTIONS - enumSerialNames.toSet()
        val problems = buildList {
            if (extra.isNotEmpty()) add("enum has actions the generator doesn't describe: $extra")
            if (missing.isNotEmpty()) add("generator describes actions not present in enum: $missing")
            if (enumSerialNames.toSet() == ACTIONS.toSet() && enumSerialNames != ACTIONS) {
                add("enum action order differs from generator; enum: $enumSerialNames")
            }
        }
        if (problems.isNotEmpty()) {
            error(
                "CommandAction / schema generator drift:\n  - " +
                    problems.joinToString("\n  - ") +
                    "\nUpdate ACTIONS + ARGS + EXAMPLES in ExecuteCommandSchemaGenerator to match Command.kt."
            )
        }
    }

    /** Emits the JSON schema pretty-printed with a trailing newline. */
    fun render(): String = prettyJson.encodeToString(JsonElement.serializer(), build()) + "\n"

    /** Parse arbitrary JSON — used by verify to compare committed vs. generated semantically. */
    fun parse(text: String): JsonElement = Json.parseToJsonElement(text)

    // ─── Building the schema tree ─────────────────────────────────────────────

    private fun build(): JsonObject = buildJsonObject {
        put("\$schema", "http://json-schema.org/draft-07/schema#")
        put("title", "execute_command")
        put(
            "description",
            "Execute a command on the Flipper Zero device. All file operations, device queries, hardware control, and artifact pushes go through this interface. Mirrors com.vesper.flipper.domain.model.ExecuteCommand.",
        )
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { ACTIONS.forEach { add(it) } }
                put("description", "The action to perform on the Flipper Zero")
            }
            putJsonObject("args") {
                put("type", "object")
                put(
                    "description",
                    "Arguments for the action (varies by action type). Unused fields may be omitted.",
                )
                putJsonObject("properties") {
                    for (arg in ARGS) {
                        putJsonObject(arg.name) {
                            put("type", arg.type)
                            if (arg.enum != null) {
                                putJsonArray("enum") { arg.enum.forEach { add(it) } }
                            }
                            put("description", arg.description)
                            if (arg.default != null) {
                                put("default", arg.default)
                            }
                        }
                    }
                }
            }
            putJsonObject("justification") {
                put("type", "string")
                put("description", "Explanation of why this action is being taken")
            }
            putJsonObject("expected_effect") {
                put("type", "string")
                put("description", "What you expect this action to accomplish")
            }
        }
        putJsonArray("required") {
            add("action"); add("args"); add("justification"); add("expected_effect")
        }
        putJsonArray("examples") {
            for (ex in EXAMPLES) {
                addJsonObject {
                    put("action", ex.action)
                    putJsonObject("args") {
                        for ((k, v) in ex.args) put(k, v)
                    }
                    put("justification", ex.justification)
                    put("expected_effect", ex.expectedEffect)
                }
            }
        }
    }

    // ─── Parsing Command.kt ───────────────────────────────────────────────────

    private val serialNameRegex = Regex("""@SerialName\("([^"]+)"\)""")
    private val enumEntryRegex = Regex("""^\s*([A-Z][A-Z0-9_]*)\s*(?:,|$)""")

    private fun parseCommandActionSerialNames(commandKt: File): List<String> {
        val text = commandKt.readText()
        val enumStart = Regex("""enum\s+class\s+CommandAction\s*\{""").find(text)
            ?: error("CommandAction enum not found in ${commandKt.name}")
        val bodyStart = enumStart.range.last + 1
        var depth = 1
        var i = bodyStart
        while (i < text.length && depth > 0) {
            when (text[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) break
            i++
        }
        check(depth == 0) { "unbalanced braces in CommandAction enum body" }
        val body = text.substring(bodyStart, i)

        val result = mutableListOf<String>()
        var pendingSerialName: String? = null
        for (raw in body.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//")) continue
            serialNameRegex.find(line)?.let {
                pendingSerialName = it.groupValues[1]
                return@let
            }
            enumEntryRegex.find(line)?.let {
                val name = pendingSerialName
                    ?: error("CommandAction entry ${it.groupValues[1]} has no preceding @SerialName")
                result += name
                pendingSerialName = null
            }
        }
        return result
    }
}
