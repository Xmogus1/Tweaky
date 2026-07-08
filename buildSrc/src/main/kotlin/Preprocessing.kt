import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.File

private val directiveRegex = "^//\\s*".toRegex()
private const val marker = "//$"

private data class StackFrame(val outerInclude: Boolean, val branchWasTrue: Boolean)

private fun processDirectives(sourceFile: File, variantName: String, stripLineComments: Boolean = false): List<String> {
    val isCheat = variantName == "cheat"
    // The "curseforge" variant is a legit build (isCheat=false, so it still keeps all #if LEGIT code) with
    // the extra CURSEFORGE flag set. It is used to strip functionality distribution reviewers reject
    // (PowerShell process-spawning, external screenshot uploads). See ScreenshotActions/ScreenshotsScreen.
    val isCurseforge = variantName == "curseforge"
    val stack = mutableListOf<StackFrame>()
    val output = mutableListOf<String>()
    var include = true

    sourceFile.readLines().forEachIndexed { index, line ->
        val lineNumber = index + 1
        val directive = line.trim().replaceFirst(directiveRegex, "")

        when (directive) {
            "#if CHEAT" -> {
                stack += StackFrame(outerInclude = include, branchWasTrue = isCheat)
                include = isCheat && include
            }

            "#if LEGIT" -> {
                stack += StackFrame(outerInclude = include, branchWasTrue = ! isCheat)
                include = ! isCheat && include
            }

            "#if CURSEFORGE" -> {
                stack += StackFrame(outerInclude = include, branchWasTrue = isCurseforge)
                include = isCurseforge && include
            }

            "#else" -> {
                if (stack.isEmpty()) throw GradleException("Unmatched #else in $sourceFile at line $lineNumber")
                val frame = stack.last()
                include = ! frame.branchWasTrue && frame.outerInclude
            }

            "#endif" -> {
                if (stack.isEmpty()) throw GradleException("Unmatched #endif in $sourceFile at line $lineNumber")
                include = stack.removeAt(stack.lastIndex).outerInclude
            }

            else -> if (include) {
                val trimmed = line.trim()
                if (stripLineComments && trimmed.startsWith("//") && ! trimmed.startsWith(marker)) return@forEachIndexed

                val markerIndex = line.indexOf(marker)
                output += if (markerIndex >= 0) line.removeRange(markerIndex, markerIndex + marker.length) else line
            }
        }
    }

    if (stack.isNotEmpty()) throw GradleException("Unclosed #if in $sourceFile")
    return output
}

fun Project.preprocessSources(variantName: String, outRoot: File) {
    fun processTree(inputRoot: File, outputRoot: File) {
        if (! inputRoot.exists()) return

        fileTree(inputRoot).matching { include("**/*.kt", "**/*.java") }.files.forEach { sourceFile ->
            val outFile = File(outputRoot, inputRoot.toPath().relativize(sourceFile.toPath()).toString())
            outFile.parentFile.mkdirs()
            outFile.writeText(processDirectives(sourceFile, variantName).joinToString(System.lineSeparator()))
        }
    }

    processTree(file("src/main/kotlin"), File(outRoot, "kotlin"))
    processTree(file("src/main/java"), File(outRoot, "java"))
}

fun Project.preprocessResources(variantName: String, outRoot: File) {
    val resourcesIn = file("src/main/resources")
    if (! resourcesIn.exists()) return

    fun preprocessFile(srcName: String, outName: String, expandVersion: Boolean) {
        val srcFile = File(resourcesIn, srcName)
        if (! srcFile.exists()) return

        val outFile = File(outRoot, outName).also { it.parentFile.mkdirs() }
        var content = processDirectives(srcFile, variantName, stripLineComments = true).joinToString(System.lineSeparator())
        if (expandVersion) content = content.replace("\${version}", project.version.toString())
        outFile.writeText(content)
    }

    preprocessFile("fabric.mod.json5", "fabric.mod.json", expandVersion = true)
    preprocessFile("tweaky.mixins.json5", "tweaky.mixins.json", expandVersion = false)
}

fun Project.registerPreprocessTask(variantName: String): TaskProvider<Task> {
    val capitalised = variantName.replaceFirstChar { it.uppercase() }
    return tasks.register("preprocess$capitalised") {
        val outRoot = layout.buildDirectory.dir("preprocessed/$variantName")
        val resourcesOut = layout.buildDirectory.dir("preprocessed/$variantName/resources")

        inputs.dir("src/main/kotlin")
        inputs.dir("src/main/java")
        inputs.file("src/main/resources/fabric.mod.json5")
        inputs.file("src/main/resources/tweaky.mixins.json5")
        outputs.dir(outRoot)
        outputs.dir(resourcesOut)

        doFirst { delete(outRoot.get().asFile) }
        doLast {
            preprocessSources(variantName, outRoot.get().asFile)
            preprocessResources(variantName, resourcesOut.get().asFile)
        }
    }
}