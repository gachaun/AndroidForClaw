# OpenClaw 业务逻辑对齐分析

根据 `~/.openclaw/` 目录结构深度分析各文件背后的业务逻辑，并评估 AndroidForClaw 的对齐状态。

---

## 1. 📁 agents/ - Agent 实例管理

### OpenClaw 业务逻辑
```
~/.openclaw/agents/
├── main/                           # 主 agent 实例
│   ├── agent/                      # Agent 持久化状态
│   └── sessions/                   # 会话存储 (JSONL 格式)
│       ├── <session-id>.jsonl      # 单个会话的消息历史
│       └── sessions.json           # 会话元数据索引
```

**核心逻辑**:
- **多 Agent 架构**: 支持多个独立的 agent 实例（main, custom 等）
- **会话持久化**: 每个会话一个 JSONL 文件，增量追加消息
- **会话索引**: sessions.json 存储所有会话的元数据（创建时间、标题等）
- **状态隔离**: 每个 agent 有独立的工作目录

### AndroidForClaw 对齐状态

**❌ 未对齐** - 需要实现

**当前实现**:
- SessionManager 使用应用内部存储 `/data/data/.../files/sessions/`
- 单一 agent 实例，无多实例支持
- 会话存储格式不同（不是 JSONL）

**需要实现**:
```kotlin
// 1. 对齐会话存储位置
val agentsDir = "/sdcard/.androidforclaw/agents"
val mainAgentDir = "$agentsDir/main"
val sessionsDir = "$mainAgentDir/sessions"

// 2. JSONL 格式存储
// 每条消息一行 JSON
{"role":"user","content":"...","timestamp":"..."}
{"role":"assistant","content":"...","timestamp":"..."}

// 3. sessions.json 索引
{
  "sessions": {
    "<session-id>": {
      "title": "...",
      "createdAt": "...",
      "lastMessageAt": "...",
      "messageCount": 10
    }
  }
}
```

**优先级**: 🔴 高 - 核心存储架构

---

## 2. 📝 openclaw.last-known-good.json - 配置备份

### OpenClaw 业务逻辑

**核心逻辑**:
- **自动备份**: 每次成功应用配置后，备份当前配置
- **回滚机制**: 如果新配置导致启动失败，自动回滚到 last-known-good
- **故障恢复**: 配置损坏时的安全网

**工作流程**:
1. 用户修改 `openclaw.json`
2. 系统尝试加载新配置
3. 如果成功 → 更新 `openclaw.last-known-good.json`
4. 如果失败 → 从 `openclaw.last-known-good.json` 恢复

### AndroidForClaw 对齐状态

**❌ 未对齐** - 需要实现

**需要实现**:
```kotlin
class ConfigBackupManager(private val context: Context) {
    private val configPath = "/sdcard/.androidforclaw/config/openclaw.json"
    private val backupPath = "/sdcard/.androidforclaw/config/openclaw.last-known-good.json"

    /**
     * 备份当前配置
     */
    fun backupCurrentConfig() {
        val configFile = File(configPath)
        val backupFile = File(backupPath)
        if (configFile.exists()) {
            configFile.copyTo(backupFile, overwrite = true)
            Log.i(TAG, "配置已备份到 last-known-good")
        }
    }

    /**
     * 恢复到 last-known-good
     */
    fun restoreFromBackup(): Boolean {
        val backupFile = File(backupPath)
        val configFile = File(configPath)

        return if (backupFile.exists()) {
            backupFile.copyTo(configFile, overwrite = true)
            Log.i(TAG, "已从 last-known-good 恢复配置")
            true
        } else {
            Log.e(TAG, "没有可用的 last-known-good 备份")
            false
        }
    }

    /**
     * 安全加载配置（带自动恢复）
     */
    fun loadConfigSafely(): OpenClawConfig? {
        return try {
            val config = configLoader.loadOpenClawConfig()
            // 加载成功，备份当前配置
            backupCurrentConfig()
            config
        } catch (e: Exception) {
            Log.e(TAG, "配置加载失败，尝试从 last-known-good 恢复", e)
            if (restoreFromBackup()) {
                configLoader.loadOpenClawConfig()
            } else {
                null
            }
        }
    }
}
```

**集成点**:
- ConfigLoader 加载配置时使用 loadConfigSafely()
- MyApplication.onCreate() 初始化时验证配置

**优先级**: 🟡 中 - 提升稳定性

---

## 3. 📦 config-backups/ - 配置历史备份

### OpenClaw 业务逻辑

**核心逻辑**:
- **时间戳备份**: 每次配置修改前，创建带时间戳的备份
- **历史追踪**: 可查看和恢复任意历史版本
- **防误操作**: 用户可以回溯到任何历史状态

**备份格式**:
```
config-backups/
├── openclaw-20260304-214054.json
├── openclaw-20260305-103022.json
└── openclaw-20260306-154312.json
```

### AndroidForClaw 对齐状态

**❌ 未对齐** - 需要实现

**需要实现**:
```kotlin
class ConfigBackupManager {
    private val backupsDir = "/sdcard/.androidforclaw/config-backups"

    /**
     * 创建配置备份
     */
    fun createBackup(): String? {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(Date())
        val backupName = "openclaw-$timestamp.json"
        val backupFile = File(backupsDir, backupName)

        File(backupsDir).mkdirs()

        return try {
            File(configPath).copyTo(backupFile)
            Log.i(TAG, "配置已备份: $backupName")
            backupName
        } catch (e: Exception) {
            Log.e(TAG, "配置备份失败", e)
            null
        }
    }

    /**
     * 列出所有备份
     */
    fun listBackups(): List<BackupInfo> {
        val backupsDir = File(backupsDir)
        if (!backupsDir.exists()) return emptyList()

        return backupsDir.listFiles()
            ?.filter { it.name.startsWith("openclaw-") }
            ?.map { file ->
                BackupInfo(
                    name = file.name,
                    timestamp = extractTimestamp(file.name),
                    size = file.length()
                )
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    /**
     * 恢复指定备份
     */
    fun restoreBackup(backupName: String): Boolean {
        val backupFile = File(backupsDir, backupName)
        return if (backupFile.exists()) {
            // 先备份当前配置
            createBackup()
            // 恢复指定备份
            backupFile.copyTo(File(configPath), overwrite = true)
            Log.i(TAG, "已恢复备份: $backupName")
            true
        } else {
            false
        }
    }
}
```

**UI 集成**:
- 配置界面添加 "备份历史" 按钮
- 显示备份列表，支持预览和恢复

**优先级**: 🟡 中 - 用户体验增强

---

## 4. 🕐 update-check.json - 更新检查

### OpenClaw 业务逻辑

**核心逻辑**:
- **定期更新检查**: 记录上次检查更新时间
- **避免频繁请求**: 控制检查频率（如每天一次）
- **静默检查**: 后台检查，不干扰用户

**文件格式**:
```json
{
  "lastCheckedAt": "2026-03-06T06:47:26.687Z"
}
```

### AndroidForClaw 对齐状态

**❌ 未对齐** - 建议实现

**需要实现**:
```kotlin
class UpdateChecker(private val context: Context) {
    private val updateCheckFile = "/sdcard/.androidforclaw/update-check.json"
    private val checkInterval = 24 * 60 * 60 * 1000L // 24小时

    data class UpdateCheckState(
        val lastCheckedAt: String,
        val latestVersion: String? = null,
        val updateAvailable: Boolean = false
    )

    /**
     * 检查是否需要检查更新
     */
    fun shouldCheckForUpdates(): Boolean {
        val state = loadState() ?: return true

        val lastCheck = Instant.parse(state.lastCheckedAt)
        val now = Instant.now()

        return Duration.between(lastCheck, now).toMillis() > checkInterval
    }

    /**
     * 执行更新检查
     */
    suspend fun checkForUpdates() {
        if (!shouldCheckForUpdates()) return

        try {
            // 检查 GitHub releases 或自定义更新 API
            val latestVersion = fetchLatestVersion()
            val currentVersion = BuildConfig.VERSION_NAME

            val updateAvailable = isNewerVersion(latestVersion, currentVersion)

            saveState(UpdateCheckState(
                lastCheckedAt = Instant.now().toString(),
                latestVersion = latestVersion,
                updateAvailable = updateAvailable
            ))

            if (updateAvailable) {
                notifyUpdateAvailable(latestVersion)
            }

        } catch (e: Exception) {
            Log.e(TAG, "更新检查失败", e)
        }
    }

    private suspend fun fetchLatestVersion(): String {
        // 从 GitHub API 或自定义服务器获取
        val response = httpClient.get(
            "https://api.github.com/repos/yourorg/androidforclaw/releases/latest"
        )
        return response.tag_name
    }
}
```

**集成点**:
- MyApplication.onCreate() 启动时检查
- 后台 WorkManager 定期检查

**优先级**: 🟢 低 - 便利功能

---

## 5. 🎨 canvas/ - 交互式测试页面

### OpenClaw 业务逻辑

**核心逻辑**:
- **浏览器测试环境**: 提供交互式 HTML 页面用于测试
- **实时重载**: 文件变化自动刷新
- **快速原型**: 测试 browser tool 功能

**使用场景**:
- 测试 browser.navigate(), browser.click() 等工具
- 验证页面抓取和交互逻辑
- 开发调试时的沙箱环境

### AndroidForClaw 对齐状态

**✅ 部分对齐** - BClaw (Browser for Claw) 已实现

**当前实现**:
- BClaw: 完整的浏览器集成（WebView + Accessibility）
- 可以打开本地 HTML 文件
- 支持 JavaScript 注入和交互

**对齐建议**:
```kotlin
// 创建测试页面目录
val canvasDir = "/sdcard/.androidforclaw/canvas"

// 初始化时写入默认测试页面
fun initializeCanvasPages() {
    val canvasDir = File("/sdcard/.androidforclaw/canvas")
    canvasDir.mkdirs()

    val testPageContent = """
        <!doctype html>
        <meta charset="utf-8" />
        <title>AndroidClaw Canvas</title>
        <style>
          body { font-family: system-ui; padding: 20px; }
          button { padding: 10px 20px; margin: 10px; font-size: 16px; }
        </style>
        <h1>AndroidClaw Test Canvas</h1>
        <div>
          <button id="btn-hello">Hello</button>
          <button id="btn-time">Show Time</button>
          <button id="btn-test">Run Test</button>
        </div>
        <div id="log" style="margin-top: 20px; padding: 10px; background: #f0f0f0;"></div>
        <script>
          document.getElementById('btn-hello').onclick = () => {
            document.getElementById('log').innerText = 'Hello from AndroidClaw!';
          };
          document.getElementById('btn-time').onclick = () => {
            document.getElementById('log').innerText = new Date().toLocaleString();
          };
        </script>
    """.trimIndent()

    File(canvasDir, "index.html").writeText(testPageContent)
}

// BClaw 快速打开测试页面
fun openCanvasPage() {
    val canvasUrl = "file:///sdcard/.androidforclaw/canvas/index.html"
    // 通过 BClaw 打开
}
```

**优先级**: 🟢 低 - 已有替代方案（BClaw）

---

## 6. 📱 devices/ - 设备配对管理

### OpenClaw 业务逻辑

**核心逻辑**:
- **设备配对**: 管理已配对的移动设备
- **待配对队列**: pending.json 存储等待配对的设备
- **设备元数据**: paired.json 存储已配对设备信息

**使用场景**:
- OpenClaw (桌面) 控制移动设备（如 AndroidForClaw）
- 设备发现和配对流程
- 多设备管理

### AndroidForClaw 对齐状态

**⚠️ 架构反转** - AndroidForClaw 是被控制端

**当前架构**:
- AndroidForClaw 是 Agent Runtime（执行端）
- OpenClaw (桌面) 是控制端
- 通过 Gateway (WebSocket) 通信

**对齐策略**:
AndroidForClaw 不需要 devices/ 目录，但可以实现**反向注册**：

```kotlin
class DeviceRegistrationManager {
    /**
     * 向 OpenClaw (桌面) 注册自己
     */
    suspend fun registerToDesktop(desktopUrl: String) {
        val deviceInfo = DeviceInfo(
            deviceId = getDeviceId(),
            deviceName = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            capabilities = listOf("screenshot", "tap", "swipe", "browser"),
            gatewayUrl = "ws://192.168.x.x:8765"
        )

        // 发送注册请求到桌面 OpenClaw
        httpClient.post("$desktopUrl/api/devices/register") {
            setBody(deviceInfo)
        }
    }
}
```

**优先级**: 🟢 低 - 跨设备协作场景

---

## 7. ⏰ cron/ - 定时任务

### OpenClaw 业务逻辑

**核心逻辑**:
- **Cron 格式任务**: 定义定期执行的任务
- **持久化调度**: jobs.json 存储任务定义
- **后台执行**: 即使 agent 不活跃也执行

**文件格式**:
```json
{
  "version": 1,
  "jobs": [
    {
      "id": "daily-backup",
      "schedule": "0 2 * * *",  // 每天凌晨2点
      "command": "backup-workspace",
      "enabled": true
    }
  ]
}
```

### AndroidForClaw 对齐状态

**❌ 未对齐** - 需要实现

**需要实现**:
```kotlin
class CronJobManager(private val context: Context) {
    private val cronFile = "/sdcard/.androidforclaw/cron/jobs.json"

    data class CronJob(
        val id: String,
        val schedule: String,  // Cron 表达式
        val command: String,
        val enabled: Boolean = true,
        val lastRun: String? = null
    )

    /**
     * 加载 Cron 任务
     */
    fun loadJobs(): List<CronJob> {
        val file = File(cronFile)
        if (!file.exists()) return emptyList()

        val json = file.readText()
        return gson.fromJson(json, CronJobsConfig::class.java).jobs
    }

    /**
     * 调度所有任务
     */
    fun scheduleAllJobs() {
        val jobs = loadJobs()

        jobs.filter { it.enabled }.forEach { job ->
            scheduleJob(job)
        }
    }

    private fun scheduleJob(job: CronJob) {
        // 使用 Android WorkManager 实现 Cron 调度
        val workRequest = PeriodicWorkRequestBuilder<CronWorker>(
            parseCronInterval(job.schedule),
            TimeUnit.MILLISECONDS
        ).setInputData(
            workDataOf("jobId" to job.id, "command" to job.command)
        ).build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

class CronWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val jobId = inputData.getString("jobId") ?: return Result.failure()
        val command = inputData.getString("command") ?: return Result.failure()

        // 执行任务
        when (command) {
            "backup-workspace" -> backupWorkspace()
            "check-updates" -> checkForUpdates()
            "clean-logs" -> cleanOldLogs()
            else -> Log.w(TAG, "Unknown command: $command")
        }

        return Result.success()
    }
}
```

**常见移动端 Cron 任务**:
- 每日备份 workspace
- 检查应用更新
- 清理旧日志
- 定期检查 AccessibilityService 状态
- 电池低时的省电模式切换

**优先级**: 🟡 中 - 自动化增强

---

## 8. 📊 日志文件对齐

### OpenClaw 业务逻辑

```
app.log      - 应用运行日志
gateway.log  - Gateway 服务日志
```

**特点**:
- 结构化日志（时间戳、级别、来源）
- 日志轮转（大小限制、旧日志归档）
- 分类存储（app 和 gateway 分离）

### AndroidForClaw 对齐状态

**🔄 部分对齐** - Android 使用 logcat

**当前实现**:
- 使用 Android logcat 系统
- 可通过 `adb logcat` 查看
- 配置中有 logging 配置但未完全使用

**对齐建议**:
```kotlin
class FileLogger(private val context: Context) {
    private val logsDir = "/sdcard/.androidforclaw/logs"
    private val appLogFile = "$logsDir/app.log"
    private val gatewayLogFile = "$logsDir/gateway.log"
    private val maxFileSize = 10 * 1024 * 1024 // 10MB

    /**
     * 写入应用日志
     */
    fun logApp(level: String, tag: String, message: String) {
        val timestamp = Instant.now().toString()
        val logLine = "[$timestamp] $level $tag: $message\n"

        appendToFile(appLogFile, logLine)

        // 同时输出到 logcat
        when (level) {
            "INFO" -> Log.i(tag, message)
            "WARN" -> Log.w(tag, message)
            "ERROR" -> Log.e(tag, message)
        }
    }

    /**
     * 写入 Gateway 日志
     */
    fun logGateway(level: String, message: String) {
        val timestamp = Instant.now().toString()
        val logLine = "[$timestamp] $level Gateway: $message\n"

        appendToFile(gatewayLogFile, logLine)
    }

    private fun appendToFile(filePath: String, content: String) {
        val file = File(filePath)

        // 检查文件大小，超过限制则轮转
        if (file.exists() && file.length() > maxFileSize) {
            rotateLog(file)
        }

        file.appendText(content)
    }

    private fun rotateLog(file: File) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val archiveName = "${file.nameWithoutExtension}-$timestamp.log"
        val archiveFile = File(file.parent, archiveName)

        file.renameTo(archiveFile)
        Log.i(TAG, "日志已轮转: $archiveName")
    }
}

// 集成到现有日志
object AppLog {
    private val fileLogger = FileLogger(context)

    fun i(tag: String, message: String) {
        fileLogger.logApp("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        fileLogger.logApp("WARN", tag, message)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        val fullMessage = if (error != null) {
            "$message\n${Log.getStackTraceString(error)}"
        } else {
            message
        }
        fileLogger.logApp("ERROR", tag, fullMessage)
    }
}
```

**优先级**: 🟡 中 - 调试和诊断

---

## 实施优先级总结

### 🔴 高优先级（核心架构）
1. **agents/ - Session 存储对齐**
   - 实现 JSONL 格式
   - 迁移到 `/sdcard/.androidforclaw/agents/main/sessions/`
   - sessions.json 索引

2. **openclaw.last-known-good.json - 配置容错**
   - 自动备份机制
   - 启动失败自动恢复

### 🟡 中优先级（稳定性增强）
3. **config-backups/ - 配置历史**
   - 时间戳备份
   - UI 恢复功能

4. **cron/ - 定时任务**
   - WorkManager 实现
   - 常见移动端任务

5. **日志文件对齐**
   - app.log, gateway.log
   - 日志轮转

### 🟢 低优先级（便利功能）
6. **update-check.json - 更新检查**
7. **canvas/ - 测试页面** (已有 BClaw)
8. **devices/ - 设备配对** (架构不同)

---

## 下一步行动

建议按以下顺序实施：

1. **Session 存储重构**（最关键）
   - 创建 SessionStorageManager
   - 实现 JSONL 格式
   - 数据迁移工具

2. **配置容错机制**
   - ConfigBackupManager
   - 集成到启动流程

3. **日志系统完善**
   - FileLogger 实现
   - 替换现有 Log 调用

4. **Cron 任务系统**
   - CronJobManager
   - WorkManager 集成

这样可以确保核心业务逻辑与 OpenClaw 完全对齐！
