import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

abstract class GenerateIconPackTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xmlDirectory: DirectoryProperty

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val targetActivityClassName: Property<String>

    @get:OutputDirectory
    abstract val resourceOutputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val assetOutputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val manifestOutputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val metadata = parseMetadata()
        val resourcesDirectory = resourceOutputDirectory.get().asFile
        val assetsDirectory = assetOutputDirectory.get().asFile
        val manifestFile = manifestOutputFile.get().asFile

        resourcesDirectory.deleteRecursively()
        assetsDirectory.deleteRecursively()
        resourcesDirectory.mkdirs()
        assetsDirectory.mkdirs()
        manifestFile.parentFile.mkdirs()

        val seenIds = mutableSetOf<String>()
        val seenHashes = mutableSetOf<String>()
        val catalog =
            metadata.mapIndexed { index, rawEntry ->
                val entry = rawEntry as? Map<*, *>
                    ?: throw GradleException("IconPack metadata entry $index must be an object.")
                val id = entry.requiredString("Id", index)
                val name = entry.requiredString("Name", index)
                val author = entry.requiredString("Author", index)
                val source = entry.requiredString("Source", index)
                val link = entry.optionalString("Link")

                if (id == DefaultIconId) {
                    throw GradleException("IconPack Id \"$DefaultIconId\" is reserved.")
                }
                if (!seenIds.add(id)) {
                    throw GradleException("IconPack metadata contains duplicate Id \"$id\".")
                }

                val hash = id.sha256Prefix()
                if (!seenHashes.add(hash)) {
                    throw GradleException("IconPack generated identifier collision for Id \"$id\".")
                }

                val sourceFile = resolveSource(source)
                validateVector(sourceFile)

                val drawableName = "icon_pack_$hash"
                val targetFile = File(resourcesDirectory, "drawable/$drawableName.xml")
                targetFile.parentFile.mkdirs()
                sourceFile.copyTo(target = targetFile, overwrite = true)

                mapOf(
                    "id" to id,
                    "name" to name,
                    "author" to author,
                    "link" to link,
                    "source" to source,
                    "drawableResourceName" to drawableName,
                    "aliasClassName" to "${applicationId.get()}.launcher.IconAlias$hash",
                )
            }

        File(assetsDirectory, "icon_pack/catalog.json").apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(catalog)) + System.lineSeparator())
        }
        manifestFile.writeText(buildManifest(catalog))
    }

    private fun parseMetadata(): List<*> {
        val parsed =
            try {
                JsonSlurper().parse(metadataFile.get().asFile)
            } catch (error: Exception) {
                throw GradleException("Unable to parse IconPack metadata.json.", error)
            }
        return parsed as? List<*>
            ?: throw GradleException("IconPack metadata.json must contain a JSON array.")
    }

    private fun resolveSource(source: String): File {
        if (!source.endsWith(".xml", ignoreCase = true) ||
            source.contains('/') ||
            source.contains('\\')
        ) {
            throw GradleException("IconPack Source \"$source\" must be an XML basename.")
        }

        val root = xmlDirectory.get().asFile.canonicalFile
        val sourceFile = File(root, source).canonicalFile
        if (sourceFile.parentFile != root || !sourceFile.isFile) {
            throw GradleException("IconPack Source \"$source\" is missing from ${root.path}.")
        }
        return sourceFile
    }

    private fun validateVector(sourceFile: File) {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
        val rootName =
            try {
                factory.newDocumentBuilder().parse(sourceFile).documentElement.localName
            } catch (error: Exception) {
                throw GradleException("IconPack Source \"${sourceFile.name}\" is not valid XML.", error)
            }
        if (rootName != "vector") {
            throw GradleException("IconPack Source \"${sourceFile.name}\" must have a <vector> root.")
        }
    }

    private fun buildManifest(catalog: List<Map<String, String>>): String {
        val packageName = applicationId.get()
        val aliases =
            catalog.joinToString(separator = System.lineSeparator()) { entry ->
                val aliasClassName = entry.getValue("aliasClassName")
                val drawableName = entry.getValue("drawableResourceName")
                """
        <activity-alias
            android:name="$aliasClassName"
            android:enabled="false"
            android:exported="true"
            android:icon="@drawable/$drawableName"
            android:label="@string/app_name"
            android:roundIcon="@drawable/$drawableName"
            android:targetActivity="${targetActivityClassName.get()}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.APP_MUSIC" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity-alias>
                """.trimEnd()
            }

        return """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity-alias
            android:name="$packageName.launcher.DefaultIconAlias"
            android:enabled="true"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/ic_launcher_round"
            android:targetActivity="${targetActivityClassName.get()}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.APP_MUSIC" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity-alias>
${aliases.prependIndent("        ")}
    </application>
</manifest>
        """.trimIndent() + System.lineSeparator()
    }

    private fun Map<*, *>.requiredString(
        key: String,
        index: Int,
    ): String =
        this[key]
            ?.let { value ->
                when (value) {
                    is String,
                    is Number,
                    -> value.toString()

                    else -> null
                }
            }?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw GradleException("IconPack metadata entry $index requires a non-empty $key.")

    private fun Map<*, *>.optionalString(key: String): String =
        (this[key] as? String)?.trim().orEmpty()

    private fun String.sha256Prefix(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val DefaultIconId = "default"
    }
}
