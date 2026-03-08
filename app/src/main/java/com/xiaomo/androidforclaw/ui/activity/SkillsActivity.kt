package com.xiaomo.androidforclaw.ui.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.draco.ladb.R
import com.draco.ladb.databinding.ActivitySkillsBinding
import com.draco.ladb.databinding.ItemSkillBinding
import com.xiaomo.androidforclaw.agent.skills.SkillsLoader
import com.xiaomo.androidforclaw.agent.skills.SkillDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Skill 显示模型（带来源信息）
 */
data class SkillDisplayModel(
    val document: SkillDocument,
    val source: String, // "bundled", "workspace", "managed", "unknown"
    val path: String
)

/**
 * Skills 管理界面
 * 对标 OpenClaw CLI: openclaw skills list
 *
 * 功能：
 * - 查看所有 Skills
 * - 查看 Skill 详情
 * - 删除用户 Skills
 */
class SkillsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SkillsActivity"
    }

    private lateinit var binding: ActivitySkillsBinding
    private lateinit var skillsLoader: SkillsLoader
    private lateinit var adapter: SkillsAdapter
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySkillsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Skills 管理"
        }

        skillsLoader = SkillsLoader(this)
        setupRecyclerView()
        loadSkills()
    }

    private fun setupRecyclerView() {
        adapter = SkillsAdapter(
            onItemClick = { skill -> showSkillDetail(skill) },
            onDeleteClick = { skill -> confirmDeleteSkill(skill) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SkillsActivity)
            adapter = this@SkillsActivity.adapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadSkills()
        }
    }

    private fun loadSkills() {
        binding.swipeRefresh.isRefreshing = true

        scope.launch {
            try {
                val skills = withContext(Dispatchers.IO) {
                    val allSkills = skillsLoader.loadSkills()
                    val result = mutableListOf<SkillDisplayModel>()

                    // 将 Map 转换为 List 并推断来源
                    allSkills.forEach { (name, doc) ->
                        val (source, path) = detectSkillSource(name)
                        result.add(SkillDisplayModel(doc, source, path))
                    }

                    result
                }

                adapter.submitList(skills)
                updateStats(skills)

                Log.d(TAG, "加载了 ${skills.size} 个 Skills")

            } catch (e: Exception) {
                Log.e(TAG, "加载 Skills 失败", e)
                Toast.makeText(this@SkillsActivity, "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    /**
     * 检测 Skill 来源
     */
    private fun detectSkillSource(skillName: String): Pair<String, String> {
        // 检查 workspace
        val workspacePath = "/sdcard/.androidforclaw/workspace/skills/$skillName"
        if (File(workspacePath).exists()) {
            return "workspace" to workspacePath
        }

        // 检查 managed
        val managedPath = "/sdcard/.androidforclaw/.skills/$skillName"
        if (File(managedPath).exists()) {
            return "managed" to managedPath
        }

        // 默认为 bundled
        return "bundled" to "assets/skills/$skillName"
    }

    private fun updateStats(skills: List<SkillDisplayModel>) {
        val bundled = skills.count { it.source == "bundled" }
        val workspace = skills.count { it.source == "workspace" }
        val managed = skills.count { it.source == "managed" }

        binding.tvStats.text = "总计: ${skills.size} | 内置: $bundled | 用户: $workspace | 管理: $managed"
    }

    private fun showSkillDetail(skill: SkillDisplayModel) {
        val doc = skill.document
        val meta = doc.metadata

        val message = buildString {
            appendLine("${meta.emoji ?: "📄"} ${doc.name}")
            appendLine()
            appendLine("📝 描述:")
            appendLine(doc.description)
            appendLine()
            appendLine("📂 来源: ${getSourceLabel(skill.source)}")
            appendLine("📍 路径: ${skill.path}")
            appendLine()
            appendLine("🔄 自动加载: ${if (meta.always) "是" else "否"}")

            if (meta.requires != null && meta.requires.hasRequirements()) {
                appendLine()
                appendLine("⚙️ 依赖:")
                if (meta.requires.bins.isNotEmpty()) {
                    appendLine("  • bins: ${meta.requires.bins.joinToString(", ")}")
                }
                if (meta.requires.env.isNotEmpty()) {
                    appendLine("  • env: ${meta.requires.env.joinToString(", ")}")
                }
                if (meta.requires.config.isNotEmpty()) {
                    appendLine("  • config: ${meta.requires.config.joinToString(", ")}")
                }
            }

            appendLine()
            appendLine("📊 预估 Tokens: ~${doc.estimateTokens()}")
        }

        AlertDialog.Builder(this)
            .setTitle("Skill 详情")
            .setMessage(message)
            .setPositiveButton("查看内容") { _, _ ->
                showSkillContent(doc)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showSkillContent(doc: SkillDocument) {
        val content = doc.getFormattedContent()
            .take(1000) + if (doc.content.length > 1000) "\n\n... (内容过长，仅显示前 1000 字符)" else ""

        AlertDialog.Builder(this)
            .setTitle("${doc.metadata.emoji ?: ""} ${doc.name}")
            .setMessage(content)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun getSourceLabel(source: String): String {
        return when (source) {
            "bundled" -> "📦 内置"
            "workspace" -> "👤 用户"
            "managed" -> "🔧 管理"
            else -> "❓ 未知"
        }
    }

    private fun confirmDeleteSkill(skill: SkillDisplayModel) {
        if (skill.source == "bundled") {
            Toast.makeText(this, "内置 Skill 无法删除", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("删除 Skill")
            .setMessage("确定要删除 \"${skill.document.name}\" 吗？\n\n此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                deleteSkill(skill)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteSkill(skill: SkillDisplayModel) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 删除 Skill 目录
                    val skillDir = File(skill.path)
                    if (skillDir.exists()) {
                        skillDir.deleteRecursively()
                        Log.d(TAG, "已删除: ${skill.path}")
                    }
                }

                Toast.makeText(this@SkillsActivity, "已删除: ${skill.document.name}", Toast.LENGTH_SHORT).show()

                // 重新加载（SkillsLoader 会自动重新扫描文件系统）
                loadSkills()

            } catch (e: Exception) {
                Log.e(TAG, "删除 Skill 失败", e)
                Toast.makeText(this@SkillsActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        (scope.coroutineContext[Job] as? Job)?.cancel()
    }
}

/**
 * Skills 列表适配器
 */
class SkillsAdapter(
    private val onItemClick: (SkillDisplayModel) -> Unit,
    private val onDeleteClick: (SkillDisplayModel) -> Unit
) : RecyclerView.Adapter<SkillsAdapter.ViewHolder>() {

    private var skills = listOf<SkillDisplayModel>()

    fun submitList(newSkills: List<SkillDisplayModel>) {
        skills = newSkills
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSkillBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(skills[position])
    }

    override fun getItemCount() = skills.size

    inner class ViewHolder(private val binding: ItemSkillBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(skill: SkillDisplayModel) {
            val doc = skill.document
            val meta = doc.metadata

            binding.apply {
                // Emoji + 名称
                tvName.text = "${meta.emoji ?: "📄"} ${doc.name}"

                // 描述
                tvDescription.text = doc.description

                // 来源标签
                tvSource.text = when (skill.source) {
                    "bundled" -> "📦 内置"
                    "workspace" -> "👤 用户"
                    "managed" -> "🔧 管理"
                    else -> "❓ 未知"
                }

                // 分类标签（从名称推断）
                val category = when {
                    doc.name.contains("mobile") || doc.name.contains("app") -> "automation"
                    doc.name.contains("test") -> "testing"
                    doc.name.contains("data") -> "data"
                    doc.name.contains("javascript") -> "scripting"
                    doc.name.contains("browser") -> "web"
                    else -> null
                }

                if (category != null) {
                    tvCategory.text = "🏷️ $category"
                    tvCategory.visibility = View.VISIBLE
                } else {
                    tvCategory.visibility = View.GONE
                }

                // 自动加载标记
                if (meta.always) {
                    tvAutoLoad.visibility = View.VISIBLE
                } else {
                    tvAutoLoad.visibility = View.GONE
                }

                // 点击事件
                root.setOnClickListener { onItemClick(skill) }

                // 删除按钮（仅用户 Skill 可见）
                btnDelete.visibility = if (skill.source != "bundled") View.VISIBLE else View.GONE
                btnDelete.setOnClickListener { onDeleteClick(skill) }
            }
        }
    }
}
