package com.xiaomo.androidforclaw.accessibility.service;

import android.graphics.Bitmap;
import android.util.Log;
import com.xiaomo.androidforclaw.aidl.ViewNodeParcelable;
import com.xiaomo.androidforclaw.aidl.IAccessibilityService;
import com.xiaomo.androidforclaw.accessibility.MediaProjectionHelper;
import kotlin.Pair;

import java.util.ArrayList;
import java.util.List;

public class AccessibilityBinder extends IAccessibilityService.Stub {
    private static final String TAG = "AccessibilityBinder";
    private static final String VERSION = "1.0.0";

    private final PhoneAccessibilityService service;

    public AccessibilityBinder(PhoneAccessibilityService service) {
        this.service = service;
    }

    @Override
    public boolean isServiceReady() {
        return service.getRootInActiveWindow() != null;
    }

    @Override
    public String getServiceVersion() {
        return VERSION;
    }

    @Override
    public List<ViewNodeParcelable> dumpViewTree() {
        try {
            List<ViewNode> nodes = service.dumpView();
            Log.d(TAG, "dumpViewTree: returning " + nodes.size() + " nodes");

            List<ViewNodeParcelable> result = new ArrayList<>();
            for (ViewNode node : nodes) {
                result.add(new ViewNodeParcelable(
                    node.getIndex(),
                    node.getText(),
                    node.getResourceId(),
                    node.getClassName(),
                    node.getPackageName(),
                    node.getContentDesc(),
                    node.getClickable(),
                    node.getEnabled(),
                    node.getFocusable(),
                    node.getFocused(),
                    node.getScrollable(),
                    node.getPoint().getX(),
                    node.getPoint().getY(),
                    node.getLeft(),
                    node.getRight(),
                    node.getTop(),
                    node.getBottom()
                ));
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to dump view tree", e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean performTap(int x, int y) {
        try {
            return service.performClickAtSync(x, y, false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to perform tap at (" + x + ", " + y + ")", e);
            return false;
        }
    }

    @Override
    public boolean performLongPress(int x, int y) {
        try {
            return service.performClickAtSync(x, y, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to perform long press at (" + x + ", " + y + ")", e);
            return false;
        }
    }

    @Override
    public boolean performSwipe(int startX, int startY, int endX, int endY, long durationMs) {
        try {
            service.performSwipe((float) startX, (float) startY, (float) endX, (float) endY);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to perform swipe", e);
            return false;
        }
    }

    @Override
    public boolean pressHome() {
        try {
            service.pressHomeButton();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to press home", e);
            return false;
        }
    }

    @Override
    public boolean pressBack() {
        try {
            service.pressBackButton();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to press back", e);
            return false;
        }
    }

    @Override
    public boolean inputText(String text) {
        try {
            return service.inputText(text);
        } catch (Exception e) {
            Log.e(TAG, "Failed to input text: " + text, e);
            return false;
        }
    }

    @Override
    public String getCurrentPackageName() {
        return service.currentPackageName;
    }

    @Override
    public boolean isMediaProjectionGranted() {
        return MediaProjectionHelper.INSTANCE.isMediaProjectionGranted();
    }

    @Override
    public String captureScreen() {
        try {
            Pair<Bitmap, String> result = MediaProjectionHelper.INSTANCE.captureScreen();
            if (result != null) {
                String path = result.getSecond();
                Log.d(TAG, "Screenshot saved to: " + path);
                return path;
            } else {
                Log.w(TAG, "Failed to capture screen");
                return "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to capture screen", e);
            return "";
        }
    }

    @Override
    public boolean requestMediaProjection() {
        // 无法直接从 Service 请求权限，需要返回 false
        // 调用方需要通过 Activity 请求
        Log.w(TAG, "Cannot request MediaProjection from service, needs Activity");
        return false;
    }

    @Override
    public String getMediaProjectionStatus() {
        return MediaProjectionHelper.INSTANCE.getPermissionStatus();
    }
}
