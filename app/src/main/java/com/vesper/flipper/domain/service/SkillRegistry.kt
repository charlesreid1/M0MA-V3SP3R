package com.vesper.flipper.domain.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates the loadable-skill markdown assets bundled under
 * `app/src/main/assets/skills/<id>/SKILL.md`.
 *
 * A "skill" is a methodology guide the LLM loads on demand via the LOAD_SKILL command action.
 * This keeps [VesperPrompts.SYSTEM_PROMPT] lean — the model picks a skill by ID from a short
 * catalog and pulls the full content into its next turn as the tool result.
 *
 * Skills are read-only assets, enumerated once at startup. There is no hot-reload path.
 */
@Singleton
class SkillRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class SkillMeta(
        /** Directory name under `assets/skills/`. This is also the `LOAD_SKILL` argument. */
        val id: String,
        /** One-line description parsed from YAML frontmatter. Used in the prompt catalog. */
        val description: String,
        /** Approximate size of the SKILL.md content (bytes). Shown in the load result. */
        val bytes: Int,
    )

    private val cache: Map<String, SkillMeta> by lazy { discover() }

    /**
     * List every bundled skill. Order is asset enumeration order; not guaranteed to be stable
     * across runs, but consistent within a run. Callers that need a canonical ordering
     * should sort by [SkillMeta.id].
     */
    fun list(): List<SkillMeta> = cache.values.toList()

    /**
     * Load a skill's markdown body (frontmatter stripped). Returns `null` if the skill id is
     * unknown or the asset fails to read. Enforces two guardrails:
     *  - refuses [MAX_SKILL_BYTES]+ content outright.
     *  - if content is between [TRUNCATE_AT_BYTES] and [MAX_SKILL_BYTES], truncates and appends
     *    a note so the LLM knows something was cut.
     */
    fun load(id: String): String? {
        if (cache[id] == null) return null
        val raw = try {
            context.assets.open("skills/$id/SKILL.md").use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read skill $id: ${e.message}")
            return null
        }
        if (raw.size > MAX_SKILL_BYTES) {
            return "Skill $id is too large (${raw.size} bytes > $MAX_SKILL_BYTES). Refused."
        }
        val text = raw.toString(Charsets.UTF_8)
        val stripped = stripFrontmatter(text)
        return if (raw.size > TRUNCATE_AT_BYTES) {
            val cut = stripped.take(TRUNCATE_AT_BYTES)
            cut + "\n\n<!-- truncated: original was ${raw.size} bytes -->"
        } else {
            stripped
        }
    }

    // ─── Discovery ────────────────────────────────────────────────────────────

    private fun discover(): Map<String, SkillMeta> {
        val out = linkedMapOf<String, SkillMeta>()
        val skillDirs = try {
            context.assets.list("skills") ?: emptyArray()
        } catch (e: Exception) {
            Log.w(TAG, "assets/skills/ is not readable: ${e.message}")
            return emptyMap()
        }
        for (id in skillDirs) {
            val bytes = try {
                context.assets.open("skills/$id/SKILL.md").use { it.readBytes() }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping skill $id (no SKILL.md): ${e.message}")
                continue
            }
            val text = bytes.toString(Charsets.UTF_8)
            val description = parseDescription(text) ?: continue
            out[id] = SkillMeta(id = id, description = description, bytes = bytes.size)
        }
        return out
    }

    // ─── Frontmatter parsing ──────────────────────────────────────────────────

    /**
     * Extracts the `description:` value from a `---`-delimited YAML frontmatter block. The
     * value may be quoted; unquoted values run to end-of-line. Returns `null` when the file
     * has no frontmatter or no description field.
     */
    private fun parseDescription(text: String): String? {
        if (!text.startsWith("---")) return null
        val end = text.indexOf("\n---", startIndex = 3)
        if (end < 0) return null
        val front = text.substring(3, end)
        for (raw in front.lines()) {
            val line = raw.trim()
            if (!line.startsWith("description:")) continue
            val rhs = line.removePrefix("description:").trim()
            return rhs.trim('"').trim('\'')
        }
        return null
    }

    /** Drops the leading `---\n...\n---\n` block if present; returns unchanged otherwise. */
    private fun stripFrontmatter(text: String): String {
        if (!text.startsWith("---")) return text
        val end = text.indexOf("\n---", startIndex = 3)
        if (end < 0) return text
        val afterCloser = end + "\n---".length
        return text.substring(afterCloser).trimStart('\n', '\r')
    }

    companion object {
        private const val TAG = "SkillRegistry"
        private const val TRUNCATE_AT_BYTES = 30 * 1024
        private const val MAX_SKILL_BYTES = 60 * 1024
    }
}
