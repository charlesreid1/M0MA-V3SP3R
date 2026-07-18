package schema

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Writes the schema to [outputFile], overwriting whatever's there. */
abstract class GenerateExecuteCommandSchemaTask : DefaultTask() {
    @get:InputFile
    abstract val commandKtFile: org.gradle.api.file.RegularFileProperty

    @get:OutputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun run() {
        val commandKt = commandKtFile.get().asFile
        ExecuteCommandSchemaGenerator.enforceCoverage(commandKt)
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(ExecuteCommandSchemaGenerator.render())
        logger.lifecycle("Wrote ${out.relativeTo(project.rootDir)}")
    }
}

/**
 * Fails if the committed schema doesn't semantically match the generator's output.
 * Compares parsed JSON (order- and whitespace-insensitive) since nothing reads this file at runtime.
 */
abstract class VerifyExecuteCommandSchemaTask : DefaultTask() {
    @get:InputFile
    abstract val commandKtFile: org.gradle.api.file.RegularFileProperty

    @get:InputFile
    abstract val committedFile: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun run() {
        val commandKt = commandKtFile.get().asFile
        ExecuteCommandSchemaGenerator.enforceCoverage(commandKt)
        val committed = committedFile.get().asFile
        val committedJson = ExecuteCommandSchemaGenerator.parse(committed.readText())
        val generatedJson = ExecuteCommandSchemaGenerator.parse(ExecuteCommandSchemaGenerator.render())
        if (committedJson != generatedJson) {
            throw RuntimeException(
                "${committed.relativeTo(project.rootDir)} is out of date. " +
                    "Run `./gradlew generateExecuteCommandSchema` and commit the result."
            )
        }
        logger.lifecycle("${committed.relativeTo(project.rootDir)} is in sync with CommandAction.")
    }
}
