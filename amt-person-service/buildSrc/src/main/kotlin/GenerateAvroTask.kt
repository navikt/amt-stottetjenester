
import org.apache.avro.tool.IdlTool
import org.apache.avro.tool.SpecificCompilerTool
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateAvroTask : DefaultTask() {
	@get:InputDirectory
	abstract val avroSchemasDir: DirectoryProperty

	@get:OutputDirectory
	abstract val avroCodeGenerationDir: DirectoryProperty

	@TaskAction
	fun generate() {
		val schemaDir = avroSchemasDir.get().asFile
		val outputDir = avroCodeGenerationDir.get().asFile
		val protocolDir = File(outputDir.parentFile, "protocols")

		schemaDir
			.walkTopDown()
			.filter { it.isFile && it.extension == "avdl" }
			.forEach { idlFile ->
				val outputFile = File(protocolDir, "${idlFile.nameWithoutExtension}.avpr")
				outputFile.parentFile.mkdirs()

				IdlTool().run(
					System.`in`,
					System.out,
					System.err,
					listOf(idlFile.absolutePath, outputFile.absolutePath),
				)
			}

		protocolDir
			.walkTopDown()
			.filter { it.isFile && it.extension == "avpr" }
			.forEach { protocolFile ->
				SpecificCompilerTool().run(
					System.`in`,
					System.out,
					System.err,
					listOf(
						"protocol",
						protocolFile.absolutePath,
						outputDir.absolutePath,
						"-encoding",
						"UTF-8",
						"-string",
						"-fieldVisibility",
						"private",
						"-noSetters",
					),
				)
			}
	}
}
