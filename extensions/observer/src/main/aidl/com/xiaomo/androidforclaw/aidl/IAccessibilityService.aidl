package com.xiaomo.androidforclaw.aidl;

import com.xiaomo.androidforclaw.aidl.ViewNodeParcelable;

interface IAccessibilityService {
    // Health check
    boolean isServiceReady();
    String getServiceVersion();

    // UI tree operations
    List<ViewNodeParcelable> dumpViewTree();

    // Gesture operations
    boolean performTap(int x, int y);
    boolean performLongPress(int x, int y);
    boolean performSwipe(int startX, int startY, int endX, int endY, long durationMs);

    // Navigation operations
    boolean pressHome();
    boolean pressBack();

    // Text input operations
    boolean inputText(String text);

    // Current app info
    String getCurrentPackageName();

    // Screenshot operations
    boolean isMediaProjectionGranted();
    String captureScreen();  // 返回截图文件路径 (URI)
    boolean requestMediaProjection();  // 请求录屏权限
    String getMediaProjectionStatus();  // 获取权限状态
}
