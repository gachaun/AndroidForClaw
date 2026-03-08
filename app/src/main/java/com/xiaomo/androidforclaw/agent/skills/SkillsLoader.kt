package com.xiaomo.androidforclaw.agent.skills

import android.content.Context
import android.os.FileObserver
import android.util.Log
import com.tencent.mmkv.MMKV
import java.io.File

/**
 * Skills 加载器
 * 学习自 OpenClaw 的三层加载机制
 *
 * 加载优先级（高优先级覆盖低优先级）:
 * 1. Bundled Skills (最低) - assets/skills/
 * 2. Managed Skills (中等) - /sdcard/AndroidForClaw/.skills/
 * 3. Workspace Skills (最高) - /sdcard/androidforclaw-workspace/skills/
 *
 * Workspace 对齐 OpenClaw 架构：
 * - OpenClaw: ~/.openclaw/workspace/ (Git repo)
 * - AndroidForClaw: /sdcard/.androidforclaw/workspace/ (可 Git)
 * - 用户可通过文件管理器直接访问和编辑
 */
class SkillsLoader(private val context: Context) {
    companion object {
        private const val TAG = "SkillsLoader"

        // 三层 Skills 目录 (对齐 OpenClaw 架构)
        private const val BUNDLED_SKILLS_PATH = "skills"  // assets 路径
        private const val MANAGED_SKILLS_DIR = "/sdcard/.androidforclaw/skills"  // 对齐 ~/.openclaw/skills/
        private const val WORKSPACE_SKILLS_DIR = "/sdcard/.androidforclaw/workspace/skills"  // 对齐 ~/.openclaw/workspace/

        // Skill 文件名
        private const val SKILL_FILE_NAME = "SKILL.md"
    }

    // Skills 缓存
    private val skillsCache = mutableMapOf<String, SkillDocument>()
    private var cacheValid = false

    // 文件监控 (Block 6 - 热重载)
    private var fileObserver: FileObserver? = null
    private var hotReloadEnabled = false

    /**
     * 加载所有 Skills
     * 按优先级覆盖: Workspace > Managed > Bundled
     *
     * @return Map<name, SkillDocument>
     */
    fun loadSkills(): Map<String, SkillDocument> {
        // 如果缓存有效，直接返回
        if (cacheValid && skillsCache.isNotEmpty()) {
            Log.d(TAG, "返回缓存的 Skills (${skillsCache.size} 个)")
            return skillsCache.toMap()
        }

        Log.d(TAG, "开始加载 Skills...")
        skillsCache.clear()

        // 按优先级加载，高优先级覆盖低优先级
        val bundledCount = loadBundledSkills(skillsCache)
        val managedCount = loadManagedSkills(skillsCache)
        val workspaceCount = loadWorkspaceSkills(skillsCache)

        cacheValid = true

        Log.i(TAG, "Skills 加载完成: 总计 ${skillsCache.size} 个")
        Log.i(TAG, "  - Bundled: $bundledCount")
        Log.i(TAG, "  - Managed: $managedCount (覆盖)")
        Log.i(TAG, "  - Workspace: $workspaceCount (覆盖)")

        return skillsCache.toMap()
    }

    /**
     * 重新加载 Skills (清除缓存)
     */
    fun reload() {
        Log.i(TAG, "重新加载 Skills...")
        cacheValid = false
        loadSkills()
    }

    /**
     * 启用热重载 (Block 6)
     * 监控 Workspace 和 Managed 目录，文件变化时自动 reload
     */
    fun enableHotReload() {
        if (hotReloadEnabled) {
            Log.d(TAG, "热重载已启用")
            return
        }

        try {
            // 监控 Workspace Skills 目录
            val workspaceDir = File(WORKSPACE_SKILLS_DIR)
            if (workspaceDir.exists()) {
                fileObserver = object : FileObserver(workspaceDir, CREATE or MODIFY or DELETE) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != null && path.endsWith(SKILL_FILE_NAME)) {
                            Log.i(TAG, "检测到 Skill 文件变化: $path")
                            Log.i(TAG, "自动重新加载 Skills...")
                            reload()
                        }
                    }
                }
                fileObserver?.startWatching()
                hotReloadEnabled = true
                Log.i(TAG, "✅ 热重载已启用 - 监控: $WORKSPACE_SKILLS_DIR")
            } else {
                Log.w(TAG, "Workspace 目录不存在，跳过热重载")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启用热重载失败", e)
        }
    }

    /**
     * 禁用热重载 (Block 6)
     */
    fun disableHotReload() {
        fileObserver?.stopWatching()
        fileObserver = null
        hotReloadEnabled = false
        Log.i(TAG, "热重载已禁用")
    }

    /**
     * 热重载是否启用
     */
    fun isHotReloadEnabled(): Boolean = hotReloadEnabled

    /**
     * 获取 Always Skills (始终加载的技能)
     * 这些技能会在启动时加载到系统提示词
     */
    fun getAlwaysSkills(): List<SkillDocument> {
        val allSkills = loadSkills()
        val alwaysSkills = allSkills.values.filter { it.metadata.always }
        Log.d(TAG, "Always Skills: ${alwaysSkills.size} 个")
        return alwaysSkills
    }

    /**
     * 根据用户目标选择相关 Skills (Block 5 改进)
     *
     * @param userGoal 用户目标/指令
     * @param excludeAlways 是否排除 always skills（避免重复）
     * @return 相关的 Skills 列表
     */
    fun selectRelevantSkills(
        userGoal: String,
        excludeAlways: Boolean = true
    ): List<SkillDocument> {
        val allSkills = loadSkills()
        val keywords = userGoal.lowercase()

        // 1. 使用任务类型识别
        val recommendedSkillNames = identifyTaskType(userGoal)

        // 2. 关键词匹配
        val relevant = allSkills.values.filter { skill ->
            // 排除 always skills（避免重复注入）
            if (excludeAlways && skill.metadata.always) {
                return@filter false
            }

            // 优先匹配任务类型推荐
            if (recommendedSkillNames.contains(skill.name)) {
                return@filter true
            }

            // 然后尝试关键词匹配
            keywords.contains(skill.name.lowercase()) ||
                    keywords.contains(skill.description.lowercase()) ||
                    matchesKeywords(skill, keywords)
        }

        Log.d(TAG, "选择相关 Skills: ${relevant.size} 个")
        for (skill in relevant) {
            Log.d(TAG, "  - ${skill.name} (${skill.description})")
        }

        return relevant
    }

    /**
     * 检查 Skill 的依赖要求是否满足
     */
    fun checkRequirements(skill: SkillDocument): RequirementsCheckResult {
        val requires = skill.metadata.requires
            ?: return RequirementsCheckResult.Satisfied

        if (!requires.hasRequirements()) {
            return RequirementsCheckResult.Satisfied
        }

        val missingBins = requires.bins.filter { !isBinaryAvailable(it) }
        val missingEnv = requires.env.filter { System.getenv(it) == null }
        val missingConfig = requires.config.filter { !isConfigAvailable(it) }

        if (missingBins.isEmpty() && missingEnv.isEmpty() && missingConfig.isEmpty()) {
            return RequirementsCheckResult.Satisfied
        }

        return RequirementsCheckResult.Unsatisfied(
            missingBins = missingBins,
            missingEnv = missingEnv,
            missingConfig = missingConfig
        )
    }

    /**
     * 获取 Skill 统计信息
     */
    fun getStatistics(): SkillsStatistics {
        val skills = loadSkills()
        val alwaysSkills = skills.values.count { it.metadata.always }
        val onDemandSkills = skills.size - alwaysSkills
        val totalTokens = skills.values.sumOf { it.estimateTokens() }
        val alwaysTokens = skills.values.filter { it.metadata.always }.sumOf { it.estimateTokens() }

        return SkillsStatistics(
            totalSkills = skills.size,
            alwaysSkills = alwaysSkills,
            onDemandSkills = onDemandSkills,
            totalTokens = totalTokens,
            alwaysTokens = alwaysTokens
        )
    }

    // ==================== 私有方法 ====================

    /**
     * 从 assets/skills/ 加载内置 Skills (Bundled)
     * 优先级: 最低
     */
    private fun loadBundledSkills(skills: MutableMap<String, SkillDocument>): Int {
        var count = 0

        try {
            val skillDirs = context.assets.list(BUNDLED_SKILLS_PATH) ?: emptyArray()
            Log.d(TAG, "扫描 Bundled Skills: ${skillDirs.size} 个目录")

            for (dir in skillDirs) {
                val skillPath = "$BUNDLED_SKILLS_PATH/$dir/$SKILL_FILE_NAME"
                try {
                    val content = context.assets.open(skillPath)
                        .bufferedReader().use { it.readText() }

                    val skill = SkillParser.parse(content).copy(source = SkillSource.BUNDLED)
                    skills[skill.name] = skill
                    count++

                    Log.d(TAG, "✅ Bundled: ${skill.name} (${skill.estimateTokens()} tokens)")
                } catch (e: Exception) {
                    Log.w(TAG, "❌ 加载 Bundled Skill 失败: $dir - ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描 Bundled Skills 失败", e)
        }

        return count
    }

    /**
     * 从 /sdcard/.androidforclaw/skills/ 加载托管 Skills (Managed)
     * 优先级: 中等（覆盖 Bundled）
     *
     * 对齐 OpenClaw: ~/.openclaw/skills/
     */
    private fun loadManagedSkills(skills: MutableMap<String, SkillDocument>): Int {
        var count = 0
        val managedDir = File(MANAGED_SKILLS_DIR)

        if (!managedDir.exists()) {
            Log.d(TAG, "Managed Skills 目录不存在: $MANAGED_SKILLS_DIR")
            return 0
        }

        try {
            val skillDirs = managedDir.listFiles { file -> file.isDirectory } ?: emptyArray()
            Log.d(TAG, "扫描 Managed Skills: ${skillDirs.size} 个目录")

            for (dir in skillDirs) {
                val skillFile = File(dir, SKILL_FILE_NAME)
                if (!skillFile.exists()) {
                    Log.w(TAG, "Managed Skill 文件不存在: ${dir.name}/$SKILL_FILE_NAME")
                    continue
                }

                try {
                    val content = skillFile.readText()
                    val skill = SkillParser.parse(content).copy(source = SkillSource.MANAGED)

                    val isOverride = skills.containsKey(skill.name)
                    skills[skill.name] = skill
                    count++

                    val action = if (isOverride) "覆盖" else "新增"
                    Log.d(TAG, "✅ Managed ($action): ${skill.name} (${skill.estimateTokens()} tokens)")
                } catch (e: Exception) {
                    Log.w(TAG, "❌ 加载 Managed Skill 失败: ${dir.name} - ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描 Managed Skills 失败", e)
        }

        return count
    }

    /**
     * 从 /sdcard/.androidforclaw/workspace/skills/ 加载工作区 Skills (Workspace)
     * 优先级: 最高（覆盖 Bundled 和 Managed）
     *
     * 对齐 OpenClaw 架构：
     * - OpenClaw: ~/.openclaw/workspace/ (Git repo)
     * - AndroidForClaw: /sdcard/.androidforclaw/workspace/ (可 Git)
     * - workspace/ 是用户的主工作区，支持版本控制
     */
    private fun loadWorkspaceSkills(skills: MutableMap<String, SkillDocument>): Int {
        var count = 0
        val workspaceDir = File(WORKSPACE_SKILLS_DIR)

        if (!workspaceDir.exists()) {
            Log.d(TAG, "Workspace Skills 目录不存在: $WORKSPACE_SKILLS_DIR")
            return 0
        }

        try {
            val skillDirs = workspaceDir.listFiles { file -> file.isDirectory } ?: emptyArray()
            Log.d(TAG, "扫描 Workspace Skills: ${skillDirs.size} 个目录")

            for (dir in skillDirs) {
                val skillFile = File(dir, SKILL_FILE_NAME)
                if (!skillFile.exists()) {
                    Log.w(TAG, "Workspace Skill 文件不存在: ${dir.name}/$SKILL_FILE_NAME")
                    continue
                }

                try {
                    val content = skillFile.readText()
                    val skill = SkillParser.parse(content).copy(source = SkillSource.WORKSPACE)

                    val isOverride = skills.containsKey(skill.name)
                    skills[skill.name] = skill
                    count++

                    val action = if (isOverride) "覆盖" else "新增"
                    Log.d(TAG, "✅ Workspace ($action): ${skill.name} (${skill.estimateTokens()} tokens)")
                } catch (e: Exception) {
                    Log.w(TAG, "❌ 加载 Workspace Skill 失败: ${dir.name} - ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描 Workspace Skills 失败", e)
        }

        return count
    }

    /**
     * 关键词匹配 (Block 5 改进)
     * 用于判断 Skill 是否与用户目标相关
     */
    private fun matchesKeywords(skill: SkillDocument, keywords: String): Boolean {
        // 预定义的关键词映射
        return when (skill.name) {
            "app-testing" -> {
                keywords.contains("测试") || keywords.contains("test") ||
                keywords.contains("检查") || keywords.contains("验证") ||
                keywords.contains("功能") || keywords.contains("用例")
            }
            "debugging" -> {
                keywords.contains("调试") || keywords.contains("debug") ||
                keywords.contains("bug") || keywords.contains("错误") ||
                keywords.contains("问题") || keywords.contains("异常") ||
                keywords.contains("崩溃")
            }
            "accessibility" -> {
                keywords.contains("无障碍") || keywords.contains("accessibility") ||
                keywords.contains("wcag") || keywords.contains("适配") ||
                keywords.contains("可读性") || keywords.contains("对比度")
            }
            "performance" -> {
                keywords.contains("性能") || keywords.contains("performance") ||
                keywords.contains("优化") || keywords.contains("卡顿") ||
                keywords.contains("流畅") || keywords.contains("启动") ||
                keywords.contains("加载") || keywords.contains("慢")
            }
            "ui-validation" -> {
                keywords.contains("ui") || keywords.contains("界面") ||
                keywords.contains("布局") || keywords.contains("显示") ||
                keywords.contains("页面") || keywords.contains("视觉")
            }
            "network-testing" -> {
                keywords.contains("网络") || keywords.contains("network") ||
                keywords.contains("联网") || keywords.contains("在线") ||
                keywords.contains("离线") || keywords.contains("断网") ||
                keywords.contains("api") || keywords.contains("请求")
            }
            else -> false
        }
    }

    /**
     * 任务类型识别 (Block 5 新增)
     * 根据用户目标识别任务类型，返回建议的 Skills
     */
    private fun identifyTaskType(userGoal: String): List<String> {
        val keywords = userGoal.lowercase()
        val recommendedSkills = mutableListOf<String>()

        // 测试类任务
        if (keywords.contains("测试") || keywords.contains("test") ||
            keywords.contains("验证") || keywords.contains("检查")) {
            recommendedSkills.add("app-testing")
        }

        // 调试类任务
        if (keywords.contains("调试") || keywords.contains("debug") ||
            keywords.contains("bug") || keywords.contains("问题") ||
            keywords.contains("错误") || keywords.contains("崩溃")) {
            recommendedSkills.add("debugging")
        }

        // UI 验证任务
        if (keywords.contains("界面") || keywords.contains("ui") ||
            keywords.contains("布局") || keywords.contains("显示") ||
            keywords.contains("页面")) {
            recommendedSkills.add("ui-validation")
        }

        // 性能测试任务
        if (keywords.contains("性能") || keywords.contains("卡顿") ||
            keywords.contains("慢") || keywords.contains("优化") ||
            keywords.contains("启动") || keywords.contains("流畅")) {
            recommendedSkills.add("performance")
        }

        // 无障碍测试任务
        if (keywords.contains("无障碍") || keywords.contains("accessibility") ||
            keywords.contains("适配") || keywords.contains("可读性")) {
            recommendedSkills.add("accessibility")
        }

        // 网络测试任务
        if (keywords.contains("网络") || keywords.contains("联网") ||
            keywords.contains("离线") || keywords.contains("断网") ||
            keywords.contains("api")) {
            recommendedSkills.add("network-testing")
        }

        Log.d(TAG, "识别任务类型: ${recommendedSkills.joinToString(", ")}")
        return recommendedSkills
    }

    /**
     * 检查二进制工具是否可用
     */
    private fun isBinaryAvailable(bin: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which $bin")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "检查二进制工具失败: $bin", e)
            false
        }
    }

    /**
     * 检查配置项是否可用
     */
    private fun isConfigAvailable(configKey: String): Boolean {
        return try {
            MMKV.defaultMMKV()?.containsKey(configKey) ?: false
        } catch (e: Exception) {
            Log.w(TAG, "检查配置项失败: $configKey", e)
            false
        }
    }
}

/**
 * 依赖检查结果
 */
sealed class RequirementsCheckResult {
    /**
     * 依赖已满足
     */
    object Satisfied : RequirementsCheckResult()

    /**
     * 依赖未满足
     */
    data class Unsatisfied(
        val missingBins: List<String>,
        val missingEnv: List<String>,
        val missingConfig: List<String>
    ) : RequirementsCheckResult() {
        fun getErrorMessage(): String {
            val parts = mutableListOf<String>()
            if (missingBins.isNotEmpty()) {
                parts.add("缺少二进制工具: ${missingBins.joinToString()}")
            }
            if (missingEnv.isNotEmpty()) {
                parts.add("缺少环境变量: ${missingEnv.joinToString()}")
            }
            if (missingConfig.isNotEmpty()) {
                parts.add("缺少配置项: ${missingConfig.joinToString()}")
            }
            return parts.joinToString("; ")
        }
    }
}

/**
 * Skills 统计信息
 */
data class SkillsStatistics(
    val totalSkills: Int,
    val alwaysSkills: Int,
    val onDemandSkills: Int,
    val totalTokens: Int,
    val alwaysTokens: Int
) {
    fun getReport(): String {
        return """
Skills 统计:
  - 总计: $totalSkills 个
  - Always: $alwaysSkills 个
  - On-Demand: $onDemandSkills 个
  - Token 总量: $totalTokens tokens
  - Always Token: $alwaysTokens tokens
        """.trimIndent()
    }
}
