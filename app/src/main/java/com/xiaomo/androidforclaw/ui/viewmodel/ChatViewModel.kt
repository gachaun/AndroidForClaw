package com.xiaomo.androidforclaw.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomo.androidforclaw.channel.ChannelManager
import com.xiaomo.androidforclaw.ui.compose.ChatMessage
import com.xiaomo.androidforclaw.ui.compose.MessageStatus
import com.xiaomo.androidforclaw.ui.session.SessionManager
import com.xiaomo.androidforclaw.util.ReasoningTagFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 聊天界面的 ViewModel - 单一数据源架构
 *
 * 架构原则:
 * 1. SessionManager 是唯一的消息来源 (Single Source of Truth)
 * 2. 定时同步后端消息,仅在有新消息时更新 UI
 * 3. 避免重复消息和复杂的merge逻辑
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val SYNC_INTERVAL = 3000L // 3秒
    }

    // 单一数据源: SessionManager
    private val uiSessionManager = SessionManager()
    private val channelManager = ChannelManager(application)

    // 暴露 session 相关的 flows
    val sessions: StateFlow<List<SessionManager.Session>> = uiSessionManager.sessions
    val currentSession: StateFlow<SessionManager.Session> = uiSessionManager.currentSession

    // 消息列表 - 直接从 currentSession 获取
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 追踪每个会话的同步状态
    private val sessionSyncState = mutableMapOf<String, Int>() // sessionId -> lastMessageCount

    init {
        // 监听 currentSession 变化,直接更新消息列表
        viewModelScope.launch {
            currentSession.collect { session ->
                _messages.value = session.messages
            }
        }

        // 启动时初始化
        viewModelScope.launch {
            initialize()
        }

        // 定时同步后端消息 (智能同步,仅在有新消息时更新)
        viewModelScope.launch {
            while (true) {
                delay(SYNC_INTERVAL)
                syncFromBackend()
            }
        }
    }

    /**
     * 初始化 - 启动时加载历史
     */
    private suspend fun initialize() {
        try {
            Log.d(TAG, "🚀 [初始化] 开始...")

            // 确保 MainEntryNew 已初始化
            com.xiaomo.androidforclaw.core.MainEntryNew.initialize(getApplication())

            // 加载所有后端 sessions (飞书/Discord/WebSocket)
            uiSessionManager.loadSessionsFromBackend()

            // 加载当前会话的消息
            syncFromBackend()

            Log.d(TAG, "✅ [初始化] 完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [初始化] 失败", e)
        }
    }

    /**
     * 从后端同步消息 - 智能同步,仅在有新消息时更新
     */
    private suspend fun syncFromBackend() {
        try {
            val sessionId = currentSession.value.id
            Log.d(TAG, "🔍 [同步检查] 会话: $sessionId")

            // 如果是后端会话,消息已在 loadSessionsFromBackend 中同步
            if (isBackendSession(sessionId)) {
                Log.d(TAG, "⏭️ [同步跳过] 后端会话")
                return
            }

            // UI 本地会话 - 使用当前会话ID作为后端session key
            val agentSessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
            if (agentSessionManager == null) {
                Log.w(TAG, "⚠️ [同步] SessionManager 未初始化")
                return
            }

            // 使用当前会话ID获取对应的agent session
            val agentSession = agentSessionManager.get(sessionId)
            if (agentSession == null) {
                Log.d(TAG, "ℹ️ [同步] 会话 $sessionId 无后端数据")
                return
            }

            val newMessageCount = agentSession.messageCount()
            val lastSyncedCount = sessionSyncState[sessionId] ?: 0
            Log.d(TAG, "📊 [同步] last=$lastSyncedCount, new=$newMessageCount")

            // 检查是否有新消息
            if (newMessageCount <= lastSyncedCount) {
                Log.d(TAG, "⏭️ [同步跳过] 无新消息")
                return
            }

            Log.d(TAG, "🔄 [同步] 新消息: $lastSyncedCount -> $newMessageCount")

            // 转换所有消息
            val chatMessages = convertMessages(agentSession.messages)

            // 更新 SessionManager (单一数据源)
            uiSessionManager.replaceCurrentSessionMessages(chatMessages)

            sessionSyncState[sessionId] = newMessageCount
            Log.d(TAG, "✅ [同步] 完成: ${chatMessages.size} 条")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [同步] 失败", e)
        }
    }

    /**
     * 转换后端消息为 UI 消息
     */
    private fun convertMessages(messages: List<com.xiaomo.androidforclaw.providers.LegacyMessage>): List<ChatMessage> {
        return messages.mapNotNull { msg ->
            val contentStr: String? = when (val content = msg.content) {
                is String -> content
                null -> null
                else -> content.toString()
            }

            if (contentStr.isNullOrEmpty()) {
                return@mapNotNull null
            }

            when (msg.role) {
                "user" -> ChatMessage(
                    content = contentStr,
                    isUser = true,
                    status = MessageStatus.SENT
                )
                "assistant" -> {
                    val cleanContent = ReasoningTagFilter.stripReasoningTags(contentStr)
                    if (cleanContent.isNotEmpty()) {
                        ChatMessage(
                            content = cleanContent,
                            isUser = false,
                            status = MessageStatus.SENT
                        )
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }

    /**
     * 判断是否是后端会话
     */
    private fun isBackendSession(sessionId: String): Boolean {
        return sessionId.startsWith("discord_") ||
               sessionId.contains("_p2p") ||
               sessionId.contains("_group") ||
               sessionId.startsWith("session_")
    }

    /**
     * 发送用户消息
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        Log.d(TAG, "💬 [发送] $content")

        // 记录 inbound
        channelManager.recordInbound()

        // 添加用户消息到 UI
        val userMessage = ChatMessage(
            content = content,
            isUser = true,
            status = MessageStatus.SENT
        )
        uiSessionManager.addMessageToCurrentSession(userMessage)

        // 添加思考中提示
        val thinkingMessage = ChatMessage(
            content = "正在思考...",
            isUser = false,
            status = MessageStatus.SENDING
        )
        uiSessionManager.addMessageToCurrentSession(thinkingMessage)

        // 调用 MainEntryNew 执行
        viewModelScope.launch {
            val sessionId = currentSession.value.id
            Log.d(TAG, "🚀 [MainEntryNew] 执行 (会话: $sessionId)...")

            com.xiaomo.androidforclaw.core.MainEntryNew.runWithSession(
                userInput = content,
                sessionId = if (isBackendSession(sessionId)) sessionId else "default",
                application = getApplication()
            )

            // 等待一小段时间后移除思考中消息
            delay(500)
            uiSessionManager.removeMessageFromCurrentSession(thinkingMessage.id)
        }

        // 自动生成会话标题
        if (currentSession.value.title == "新对话") {
            uiSessionManager.autoGenerateCurrentSessionTitle()
        }
    }

    // === Session Management ===

    fun createNewSession() {
        uiSessionManager.createSession()
        // 新会话自动初始化同步状态为0
    }

    fun switchSession(sessionId: String) {
        Log.d(TAG, "🔀 [切换会话] $sessionId")
        uiSessionManager.switchSession(sessionId)
        // 切换会话时立即同步
        viewModelScope.launch {
            syncFromBackend()
        }
    }

    fun deleteSession(sessionId: String) {
        uiSessionManager.deleteSession(sessionId)
        // 清理同步状态
        sessionSyncState.remove(sessionId)

        // 清理后端 session
        val agentSessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
        agentSessionManager?.clear(sessionId)
    }

    fun clearCurrentSession() {
        val sessionId = currentSession.value.id
        Log.d(TAG, "🗑️ [清空会话] $sessionId")

        uiSessionManager.clearCurrentSession()

        // 同时清空 Agent Session
        val agentSessionManager = com.xiaomo.androidforclaw.core.MainEntryNew.getSessionManager()
        agentSessionManager?.clear(if (isBackendSession(sessionId)) sessionId else sessionId)

        // 重置同步状态
        sessionSyncState[sessionId] = 0
    }
}
