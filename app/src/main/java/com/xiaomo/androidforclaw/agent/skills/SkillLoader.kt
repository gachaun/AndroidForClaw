package com.xiaomo.androidforclaw.agent.skills

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Skill Loader - 从 markdown 文件加载 skills
 *
 * 支持 OpenClaw 兼容的 skill 格式:
 * - YAML frontmatter (name, description, metadata)
 * - Markdown 内容（技能说明、最佳实践等）
 *
 * 加载顺序（优先级从高到低）:
 * 1. Workspace Skills: /sdcard/.androidforclaw/workspace/skills/
 * 2. Managed Skills: /sdcard/.androidforclaw/.skills/
 * 3. Bundled Skills: assets/skills/
 */
class SkillLoader(private val context: Context) {

    companion object {
        private const val TAG = "SkillLoader"

        // Skill 文件路径
        private const val WORKSPACE_SKILLS_DIR = "/sdcard/.androidforclaw/workspace/skills"
        private const val MANAGED_SKILLS_DIR = "/sdcard/.androidforclaw/.skills"
        private const val BUNDLED_SKILLS_PATH = "skills"

        // 文件名
        private const val SKILL_FILE_NAME = "SKILL.md"
    }

    /**
     * Skill Entry - 加载的 skill 条目
     */
    data class SkillEntry(
        val name: String,
        val description: String,
        val content: String,
        val metadata: Map<String, Any?>,
        val filePath: String,
        val source: SkillSource
    )

    enum class SkillSource {
        BUNDLED,    // assets/skills/
        MANAGED,    // /sdcard/.androidforclaw/.skills/
        WORKSPACE   // /sdcard/.androidforclaw/workspace/skills/
    }

    /**
     * 加载所有 skills
     */
    fun loadAllSkills(): List<SkillEntry> {
        val allSkills = mutableMapOf<String, SkillEntry>()

        // 1. 加载 bundled skills (优先级最低)
        loadBundledSkills().forEach { skill ->
            allSkills[skill.name] = skill
        }

        // 2. 加载 managed skills (覆盖 bundled)
        loadManagedSkills().forEach { skill ->
            allSkills[skill.name] = skill
        }

        // 3. 加载 workspace skills (优先级最高)
        loadWorkspaceSkills().forEach { skill ->
            allSkills[skill.name] = skill
        }

        Log.d(TAG, "Loaded ${allSkills.size} skills total")
        return allSkills.values.toList()
    }

    /**
     * 加载 bundled skills (assets)
     */
    private fun loadBundledSkills(): List<SkillEntry> {
        val skills = mutableListOf<SkillEntry>()

        try {
            val assetManager = context.assets
            val skillDirs = assetManager.list(BUNDLED_SKILLS_PATH) ?: emptyArray()

            for (skillDir in skillDirs) {
                val skillPath = "$BUNDLED_SKILLS_PATH/$skillDir"
                try {
                    val skillFile = "$skillPath/$SKILL_FILE_NAME"
                    val content = readAssetFile(assetManager, skillFile)
                    val entry = parseSkillFile(content, skillFile, SkillSource.BUNDLED)
                    if (entry != null) {
                        skills.add(entry)
                        Log.d(TAG, "Loaded bundled skill: ${entry.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load bundled skill from $skillPath: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bundled skills", e)
        }

        return skills
    }

    /**
     * 加载 managed skills (外部存储)
     */
    private fun loadManagedSkills(): List<SkillEntry> {
        return loadSkillsFromDirectory(MANAGED_SKILLS_DIR, SkillSource.MANAGED)
    }

    /**
     * 加载 workspace skills (外部存储)
     */
    private fun loadWorkspaceSkills(): List<SkillEntry> {
        return loadSkillsFromDirectory(WORKSPACE_SKILLS_DIR, SkillSource.WORKSPACE)
    }

    /**
     * 从目录加载 skills
     */
    private fun loadSkillsFromDirectory(dirPath: String, source: SkillSource): List<SkillEntry> {
        val skills = mutableListOf<SkillEntry>()
        val dir = File(dirPath)

        if (!dir.exists() || !dir.isDirectory) {
            Log.d(TAG, "Skill directory not found: $dirPath")
            return skills
        }

        val skillDirs = dir.listFiles { f -> f.isDirectory } ?: emptyArray()
        for (skillDir in skillDirs) {
            try {
                val skillFile = File(skillDir, SKILL_FILE_NAME)
                if (skillFile.exists()) {
                    val content = skillFile.readText()
                    val entry = parseSkillFile(content, skillFile.absolutePath, source)
                    if (entry != null) {
                        skills.add(entry)
                        Log.d(TAG, "Loaded ${source.name.lowercase()} skill: ${entry.name}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load skill from ${skillDir.name}: ${e.message}")
            }
        }

        return skills
    }

    /**
     * 解析 skill 文件
     */
    private fun parseSkillFile(content: String, filePath: String, source: SkillSource): SkillEntry? {
        try {
            val (frontmatter, markdownContent) = parseFrontmatter(content)

            val name = frontmatter["name"] as? String
            val description = frontmatter["description"] as? String

            if (name == null || description == null) {
                Log.w(TAG, "Skill file missing name or description: $filePath")
                return null
            }

            // 解析 metadata
            val metadata = parseFrontmatterMetadata(frontmatter)

            return SkillEntry(
                name = name,
                description = description,
                content = markdownContent,
                metadata = metadata,
                filePath = filePath,
                source = source
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse skill file: $filePath", e)
            return null
        }
    }

    /**
     * 解析 frontmatter（YAML格式）
     *
     * 格式:
     * ---
     * name: skill_name
     * description: Skill description
     * metadata: { "openclaw": { "always": true } }
     * ---
     * # Markdown content
     */
    private fun parseFrontmatter(content: String): Pair<Map<String, Any?>, String> {
        val lines = content.lines()
        val frontmatterLines = mutableListOf<String>()
        var inFrontmatter = false
        var frontmatterEnd = 0

        for ((index, line) in lines.withIndex()) {
            if (line.trim() == "---") {
                if (!inFrontmatter) {
                    inFrontmatter = true
                } else {
                    frontmatterEnd = index
                    break
                }
            } else if (inFrontmatter) {
                frontmatterLines.add(line)
            }
        }

        // 解析 frontmatter 键值对
        val frontmatter = mutableMapOf<String, Any?>()
        for (line in frontmatterLines) {
            if (line.contains(":")) {
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()

                    // 尝试解析 JSON 格式的值
                    frontmatter[key] = parseValue(value)
                }
            }
        }

        // Markdown 内容
        val markdownContent = lines
            .drop(frontmatterEnd + 1)
            .joinToString("\n")
            .trim()

        return Pair(frontmatter, markdownContent)
    }

    /**
     * 解析 frontmatter 的值
     */
    private fun parseValue(value: String): Any? {
        return when {
            // JSON 对象或数组
            value.startsWith("{") || value.startsWith("[") -> {
                try {
                    if (value.startsWith("{")) {
                        jsonObjectToMap(JSONObject(value))
                    } else {
                        // 简单处理 JSON 数组，返回原始字符串
                        value
                    }
                } catch (e: Exception) {
                    value  // 解析失败，返回原始字符串
                }
            }
            // 布尔值
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            // 数字
            value.toIntOrNull() != null -> value.toInt()
            value.toLongOrNull() != null -> value.toLong()
            value.toDoubleOrNull() != null -> value.toDouble()
            // 字符串
            else -> value
        }
    }

    /**
     * 解析 frontmatter metadata
     */
    private fun parseFrontmatterMetadata(frontmatter: Map<String, Any?>): Map<String, Any?> {
        val metadata = frontmatter["metadata"]
        return when (metadata) {
            is Map<*, *> -> metadata as Map<String, Any?>
            is String -> {
                try {
                    jsonObjectToMap(JSONObject(metadata))
                } catch (e: Exception) {
                    emptyMap()
                }
            }
            else -> emptyMap()
        }
    }

    /**
     * JSONObject 转 Map
     */
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                else -> value
            }
        }
        return map
    }

    /**
     * 读取 asset 文件
     */
    private fun readAssetFile(assetManager: AssetManager, path: String): String {
        return assetManager.open(path).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }
}
