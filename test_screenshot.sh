#!/bin/bash

# AndroidForClaw 截图测试脚本
# 使用固定 sessionId 保持会话连续性

SESSION_ID="test_adb_session"
MESSAGE="$1"

if [ -z "$MESSAGE" ]; then
    echo "用法: $0 <消息内容>"
    echo "示例: $0 '截图发我'"
    exit 1
fi

echo "📤 发送消息到 AndroidForClaw Agent..."
echo "   Session: $SESSION_ID"
echo "   Message: $MESSAGE"

adb shell am broadcast \
    -a com.xiaomo.androidforclaw.ACTION_EXECUTE_AGENT \
    -n com.xiaomo.androidforclaw/.core.AgentMessageReceiver \
    --es message "$MESSAGE" \
    --es sessionId "$SESSION_ID"

echo "✅ 消息已发送，等待执行..."
echo ""
echo "💡 监听日志: adb logcat | grep -E '(AgentLoop|Screenshot|ScreenshotSkill)'"
