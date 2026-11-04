package com.cuter.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

class ProjectRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val root: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(projectId: String): File = File(root, projectId).apply { mkdirs() }
    fun mediaDir(projectId: String): File = File(projectDir(projectId), "media").apply { mkdirs() }
    fun thumbsDir(projectId: String): File = File(projectDir(projectId), "thumbs").apply { mkdirs() }

    fun fileFor(projectId: String, asset: MediaAsset): File = File(projectDir(projectId), asset.relPath)
    fun thumbFile(projectId: String, asset: MediaAsset): File? =
        asset.thumbName?.let { File(thumbsDir(projectId), it) }

    fun create(name: String, ratio: CanvasRatio): Project {
        val now = System.currentTimeMillis()
        val project = Project(
            id = newId(),
            name = name.ifBlank { "Новый проект" },
            createdAt = now,
            updatedAt = now,
            ratio = ratio,
        )
        save(project)
        return project
    }

    fun load(projectId: String): Project? = runCatching {
        val file = File(projectDir(projectId), "project.json")
        if (!file.exists()) null else json.decodeFromString<Project>(file.readText())
    }.getOrNull()

    fun save(project: Project) {
        val dir = projectDir(project.id)
        val target = File(dir, "project.json")
        val tmp = File(dir, "project.json.tmp")
        tmp.writeText(json.encodeToString(Project.serializer(), project.copy(updatedAt = System.currentTimeMillis())))
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    fun delete(projectId: String) {
        projectDir(projectId).deleteRecursively()
    }

    fun duplicate(project: Project): Project {
        val now = System.currentTimeMillis()
        val copy = project.copy(
            id = newId(),
            name = "${project.name} копия",
            createdAt = now,
            updatedAt = now,
        )
        projectDir(copy.id)
        projectDir(project.id).walkTopDown().forEach { src ->
            if (src.isDirectory) return@forEach
            val rel = src.relativeTo(projectDir(project.id)).path
            if (rel == "project.json") return@forEach
            val dst = File(projectDir(copy.id), rel)
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
        }
        save(copy)
        return copy
    }

    fun list(): List<ProjectSummary> {
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val project = runCatching {
                val f = File(dir, "project.json")
                if (!f.exists()) null else json.decodeFromString<Project>(f.readText())
            }.getOrNull() ?: return@mapNotNull null
            val thumb = project.assets.firstNotNullOfOrNull { a ->
                a.thumbName?.let { File(File(dir, "thumbs"), it).takeIf { t -> t.exists() }?.absolutePath }
            }
            ProjectSummary(
                id = project.id,
                name = project.name,
                updatedAt = project.updatedAt,
                durationMs = project.durationMs,
                ratio = project.ratio,
                thumbPath = thumb,
                clipCount = project.tracks.sumOf { it.clips.size },
            )
        }.sortedByDescending { it.updatedAt }
    }

    fun rename(projectId: String, name: String) {
        load(projectId)?.let { save(it.copy(name = name)) }
    }
}
