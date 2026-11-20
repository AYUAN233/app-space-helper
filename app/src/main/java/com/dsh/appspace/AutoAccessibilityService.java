/*
 * 应用空间助手
 * Copyright (C) 2026 AYUAN
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dsh.appspace;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AutoAccessibilityService extends AccessibilityService {
    static final int MODE_INCREMENT = 0;
    static final int MODE_FULL = 1;
    static final String ACTION_STATE = "com.dsh.appspace.action.STATE";

    private static final String TAG = "AppSpace";
    private static final String TARGET_LABEL = "自动释放应用空间";
    private static final String[] DETAIL_MARKERS = {
            "应用详情", "应用信息", "App info", "应用设置"
    };
    private static final Set<String> KNOWN_SETTINGS_HOSTS = new HashSet<>(Arrays.asList(
            "com.android.settings",
            "com.google.android.settings",
            "com.oplus.settings",
            "com.coloros.settings",
            "com.miui.securitycenter",
            "com.miui.securityadd"
    ));

    private static volatile AutoAccessibilityService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> settingsHosts = new HashSet<>(KNOWN_SETTINGS_HOSTS);

    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean stopRequested;
    private volatile boolean finishing;
    private volatile int total;
    private volatile int index;
    private volatile int checked;
    private volatile int turnedOff;
    private volatile int alreadyOff;
    private volatile int failed;
    private volatile String currentApp = "";
    private volatile String expectedHost;
    private volatile String abortMessage = "";
    private volatile Thread worker;

    private OverlayController overlay;

    static AutoAccessibilityService getInstance() {
        return instance;
    }

    boolean isRunning() {
        return running;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            setServiceInfo(info);
        }
        refreshSettingsHosts();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This service drives Settings directly instead of reacting to every UI event.
    }

    @Override
    public void onInterrupt() {
        // No spoken feedback is produced by this service.
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        boolean wasRunning = running;
        finishing = true;
        running = false;
        stopRequested = true;
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
        if (overlay != null) {
            overlay.hide();
            overlay = null;
        }
        if (wasRunning) {
            RecordStore.saveSummary(this, total, checked, turnedOff, alreadyOff, failed, true);
            RecordStore.markInterrupted(this);
            RecordStore.setRunActive(this, false);
        }
        AutomationForegroundService.stop(this);
        instance = null;
        super.onDestroy();
    }

    public boolean startAutomation(int mode, boolean includeSystem) {
        if (running) {
            return false;
        }
        Thread oldWorker = worker;
        if (oldWorker != null && oldWorker.isAlive()) {
            return false;
        }
        finishing = false;
        stopRequested = false;
        paused = false;
        running = true;
        total = 0;
        index = 0;
        checked = 0;
        turnedOff = 0;
        alreadyOff = 0;
        failed = 0;
        currentApp = "";
        expectedHost = null;
        abortMessage = "";
        refreshSettingsHosts();

        overlay = new OverlayController(this, new OverlayController.Listener() {
            @Override
            public void onTogglePause() {
                togglePause();
            }

            @Override
            public void onStopConfirmed() {
                requestStopAndReturn();
            }
        });
        if (!overlay.show()) {
            overlay = null;
            running = false;
            return false;
        }
        overlay.update(0, 0, 0, 0, 0, "正在准备应用列表…");
        publishState();
        RecordStore.setRunActive(this, true);

        Thread thread = new Thread(() -> runAutomation(mode, includeSystem), "appspace-worker");
        worker = thread;
        thread.start();
        return true;
    }

    public void togglePause() {
        if (!running) {
            return;
        }
        paused = !paused;
        if (overlay != null) {
            overlay.setPaused(paused);
        }
        publishState();
    }

    public void requestStopAndReturn() {
        if (!running && !finishing) {
            return;
        }
        mainHandler.post(() -> performFinish(true, "已停止"));
    }

    private void runAutomation(int mode, boolean includeSystem) {
        try {
            List<AppEntry> apps = AppScanner.findTargets(this, includeSystem,
                    mode == MODE_INCREMENT);
            total = apps.size();
            publishState();
            if (stopRequested) {
                return;
            }
            if (apps.isEmpty()) {
                abortMessage = mode == MODE_INCREMENT
                        ? "增量记录已是最新，没有待处理的应用"
                        : "没有找到可处理的应用";
                return;
            }

            openAppManagement();
            settle(650);
            int pageFailureStreak = 0;

            for (int i = 0; i < apps.size() && !stopRequested; i++) {
                waitIfPaused();
                if (stopRequested) {
                    break;
                }
                AppEntry app = apps.get(i);
                index = i + 1;
                currentApp = app.label;
                publishState();

                AppResult result = processApp(app);
                if (result != null) {
                    checked++;
                    if (RecordStore.RESULT_TURNED_OFF.equals(result.result)) {
                        turnedOff++;
                    } else if (RecordStore.RESULT_ALREADY_OFF.equals(result.result)) {
                        alreadyOff++;
                    } else if (RecordStore.RESULT_FAILED.equals(result.result)) {
                        failed++;
                    }
                    RecordStore.add(this, app.packageName, app.label, result.result,
                            result.reason, result.beforeChecked, result.afterChecked);
                    if (!RecordStore.RESULT_FAILED.equals(result.result)) {
                        RecordStore.markDone(this, app.packageName);
                    }
                    publishState();
                    if (result.pageLoadFailure) {
                        pageFailureStreak++;
                    } else {
                        pageFailureStreak = 0;
                    }
                }

                if (stopRequested) {
                    break;
                }
                performGlobalAction(GLOBAL_ACTION_BACK);
                settle(280);

                if (pageFailureStreak >= 5) {
                    abortMessage = "连续 5 个应用无法读取设置页面，已自动结束";
                    break;
                }
                settle(120);
            }

            if (!stopRequested) {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        } catch (Throwable t) {
            Log.w(TAG, "automation stopped unexpectedly", t);
            abortMessage = "运行中断：" + safeMessage(t);
        } finally {
            if (!finishing) {
                String message = abortMessage;
                mainHandler.post(() -> performFinish(false, message));
            }
            worker = null;
        }
    }

    private AppResult processApp(AppEntry app) {
        if (stopRequested) {
            return null;
        }
        waitIfPaused();
        if (stopRequested) {
            return null;
        }

        if (!openAppDetails(app.packageName)) {
            return AppResult.failure("无法打开应用详情页", true);
        }
        settle(720);
        Page page = waitForDetailPage();
        if (stopRequested) {
            return null;
        }
        if (page == null || !page.loaded) {
            return AppResult.failure("无法读取应用详情页", true);
        }

        TargetScan scan = findTargetWithScroll();
        waitIfPaused();
        if (stopRequested) {
            return null;
        }
        if (scan.target == null) {
            return AppResult.missing("未找到「" + TARGET_LABEL + "」设置项");
        }

        Target target = scan.target;
        if (!target.checked) {
            return AppResult.alreadyOff();
        }

        boolean clicked = clickToggle(target.node);
        settle(720);
        if (stopRequested) {
            return null;
        }
        waitIfPaused();

        AccessibilityNodeInfo root = getTargetRoot();
        Target after = findTarget(root);
        if (after != null && !after.checked) {
            return AppResult.turnedOff();
        }
        if (after != null) {
            return AppResult.failure(clicked ? "点击后开关仍为开启状态" : "无法点击该开关",
                    false);
        }
        return AppResult.failure("点击后未能确认开关状态", false);
    }

    private boolean clickToggle(AccessibilityNodeInfo toggle) {
        try {
            if (toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "toggle click failed", e);
        }
        AccessibilityNodeInfo parent = toggle.getParent();
        for (int depth = 0; depth < 3 && parent != null; depth++) {
            try {
                if (parent.isClickable() && parent.isEnabled()
                        && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "toggle parent click failed", e);
            }
            parent = parent.getParent();
        }
        return false;
    }

    private Page waitForDetailPage() {
        long deadline = SystemClock.uptimeMillis() + 5200L;
        AccessibilityNodeInfo fallback = null;
        while (!stopRequested) {
            if (paused) {
                long pausedAt = SystemClock.uptimeMillis();
                waitIfPaused();
                deadline += SystemClock.uptimeMillis() - pausedAt;
            }
            long now = SystemClock.uptimeMillis();
            if (now >= deadline) {
                break;
            }
            AccessibilityNodeInfo root = getTargetRoot();
            if (root != null) {
                fallback = root;
                if (findTarget(root) != null || containsAnyText(root, DETAIL_MARKERS)) {
                    settle(220);
                    AccessibilityNodeInfo refreshed = getTargetRoot();
                    return new Page(refreshed != null ? refreshed : root, true);
                }
            }
            settle(180);
        }
        return new Page(fallback, false);
    }

    private boolean openAppDetails(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        String host = resolveSettingsHost(intent);
        expectedHost = host;
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "open app details failed for " + packageName, e);
            return false;
        }
    }

    private void openAppManagement() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (RuntimeException ignored) {
            // Some ROMs expose only the top-level Apps settings page.
        }
        try {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(fallback);
        } catch (RuntimeException e) {
            Log.w(TAG, "unable to open app management", e);
        }
    }

    private void refreshSettingsHosts() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        String host = resolveSettingsHost(intent);
        if (host != null) {
            settingsHosts.add(host);
        }
    }

    private String resolveSettingsHost(Intent intent) {
        try {
            ResolveInfo info = getPackageManager().resolveActivity(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null && info.activityInfo != null
                    && !TextUtilsCompat.isEmpty(info.activityInfo.packageName)
                    && !"android".equals(info.activityInfo.packageName)) {
                return info.activityInfo.packageName;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "resolve settings host failed", e);
        }
        return null;
    }

    private AccessibilityNodeInfo getTargetRoot() {
        AccessibilityNodeInfo bestRoot = null;
        int bestLayer = Integer.MIN_VALUE;
        AccessibilityNodeInfo markerFallback = null;
        int fallbackLayer = Integer.MIN_VALUE;
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) {
                    continue;
                }
                String pkg = stringValue(root.getPackageName());
                if (pkg == null || pkg.equals(getPackageName())) {
                    continue;
                }
                int layer = window.getLayer();
                boolean wanted = pkg.equals(expectedHost) || settingsHosts.contains(pkg);
                if (wanted) {
                    if (bestRoot == null || layer > bestLayer) {
                        bestRoot = root;
                        bestLayer = layer;
                    }
                } else if (markerFallback == null || layer > fallbackLayer) {
                    // Covers custom ROM Settings packages that we do not know by name.
                    if (containsAnyText(root, DETAIL_MARKERS)) {
                        markerFallback = root;
                        fallbackLayer = layer;
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "enumerating windows failed", e);
        }
        if (bestRoot != null) {
            return bestRoot;
        }
        if (markerFallback != null) {
            return markerFallback;
        }
        try {
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null) {
                String pkg = stringValue(active.getPackageName());
                if (pkg != null && !pkg.equals(getPackageName())
                        && (pkg.equals(expectedHost) || settingsHosts.contains(pkg)
                        || containsAnyText(active, DETAIL_MARKERS))) {
                    return active;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to null.
        }
        return null;
    }

    private TargetScan findTargetWithScroll() {
        int noScrollAttempts = 0;
        for (int attempt = 0; attempt < 8 && !stopRequested; attempt++) {
            waitIfPaused();
            if (stopRequested) {
                break;
            }
            AccessibilityNodeInfo root = getTargetRoot();
            if (root == null) {
                noScrollAttempts++;
                settle(300);
                if (noScrollAttempts >= 3) {
                    break;
                }
                continue;
            }
            Target target = findTarget(root);
            if (target != null) {
                return new TargetScan(target, true);
            }

            AccessibilityNodeInfo label = findLabel(root);
            if (label != null) {
                settle(260);
                AccessibilityNodeInfo retryRoot = getTargetRoot();
                if (retryRoot != null) {
                    Target retry = findTarget(retryRoot);
                    if (retry != null) {
                        return new TargetScan(retry, true);
                    }
                }
            }

            if (!scrollForward(root)) {
                noScrollAttempts++;
                if (noScrollAttempts >= 2) {
                    break;
                }
            } else {
                noScrollAttempts = 0;
            }
            settle(430);
        }
        return new TargetScan(null, false);
    }

    private Target findTarget(AccessibilityNodeInfo root) {
        if (root == null) {
            return null;
        }
        AccessibilityNodeInfo label = findLabel(root);
        if (label == null) {
            return null;
        }

        List<AccessibilityNodeInfo> allToggles = collectToggles(root);
        if (allToggles.isEmpty()) {
            return null;
        }
        Rect labelBounds = new Rect();
        label.getBoundsInScreen(labelBounds);
        if (labelBounds.isEmpty()) {
            return null;
        }

        AccessibilityNodeInfo current = label;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            List<AccessibilityNodeInfo> local = collectToggles(current);
            AccessibilityNodeInfo best = nearestByY(local, labelBounds);
            if (best != null) {
                return targetFor(best);
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }

        AccessibilityNodeInfo best = nearestByY(allToggles, labelBounds);
        return best == null ? null : targetFor(best);
    }

    private AccessibilityNodeInfo nearestByY(List<AccessibilityNodeInfo> nodes, Rect labelBounds) {
        AccessibilityNodeInfo best = null;
        int bestDistance = Integer.MAX_VALUE;
        int maxDistance = Ui.dp(this, 125);
        for (AccessibilityNodeInfo node : nodes) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.isEmpty()) {
                continue;
            }
            int distance = Math.abs(bounds.centerY() - labelBounds.centerY());
            if (distance <= maxDistance && distance < bestDistance) {
                best = node;
                bestDistance = distance;
            }
        }
        return best;
    }

    private AccessibilityNodeInfo findLabel(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int count = 0;
        while (!queue.isEmpty() && count++ < 3500) {
            AccessibilityNodeInfo node = queue.poll();
            if (node == null) {
                continue;
            }
            String text = stringValue(node.getText());
            String description = stringValue(node.getContentDescription());
            if ((text != null && text.contains(TARGET_LABEL))
                    || (description != null && description.contains(TARGET_LABEL))) {
                return node;
            }
            addChildren(queue, node);
        }
        return null;
    }

    private List<AccessibilityNodeInfo> collectToggles(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int count = 0;
        while (!queue.isEmpty() && count++ < 3500) {
            AccessibilityNodeInfo node = queue.poll();
            if (node == null) {
                continue;
            }
            if (isToggle(node)) {
                result.add(node);
            }
            addChildren(queue, node);
        }
        return result;
    }

    private boolean isToggle(AccessibilityNodeInfo node) {
        try {
            if (node.isCheckable()) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // Continue with class-based detection.
        }
        String cls = stringValue(node.getClassName());
        if (cls == null) {
            return false;
        }
        String lower = cls.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("switch") || lower.contains("checkbox")
                || lower.contains("togglebutton");
    }

    private Target targetFor(AccessibilityNodeInfo node) {
        boolean checked = false;
        try {
            checked = node.isChecked();
        } catch (RuntimeException ignored) {
            // Default to considering the switch off if state is unavailable.
        }
        return new Target(node, checked);
    }

    private boolean scrollForward(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> scrollables = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int count = 0;
        while (!queue.isEmpty() && count++ < 3500) {
            AccessibilityNodeInfo node = queue.poll();
            if (node == null) {
                continue;
            }
            try {
                if (node.isScrollable()) {
                    scrollables.add(node);
                }
            } catch (RuntimeException ignored) {
                // Skip malformed nodes.
            }
            addChildren(queue, node);
        }
        if (scrollables.isEmpty()) {
            return false;
        }
        scrollables.sort(Comparator.comparingInt((AccessibilityNodeInfo node) -> {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            return bounds.width() * bounds.height();
        }).reversed());
        for (AccessibilityNodeInfo node : scrollables) {
            try {
                if (node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Try the next scrollable container.
            }
        }
        return false;
    }

    private boolean containsAnyText(AccessibilityNodeInfo root, String... needles) {
        if (root == null) {
            return false;
        }
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int count = 0;
        while (!queue.isEmpty() && count++ < 4000) {
            AccessibilityNodeInfo node = queue.poll();
            if (node == null) {
                continue;
            }
            String text = stringValue(node.getText());
            String description = stringValue(node.getContentDescription());
            for (String needle : needles) {
                if ((text != null && text.contains(needle))
                        || (description != null && description.contains(needle))) {
                    return true;
                }
            }
            addChildren(queue, node);
        }
        return false;
    }

    private void addChildren(ArrayDeque<AccessibilityNodeInfo> queue, AccessibilityNodeInfo node) {
        int children;
        try {
            children = node.getChildCount();
        } catch (RuntimeException e) {
            return;
        }
        for (int i = 0; i < children; i++) {
            try {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.add(child);
                }
            } catch (RuntimeException ignored) {
                // Ignore a stale branch of the tree.
            }
        }
    }

    private void waitIfPaused() {
        while (paused && !stopRequested) {
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void settle(long milliseconds) {
        long deadline = SystemClock.uptimeMillis() + milliseconds;
        while (!stopRequested) {
            long remaining = deadline - SystemClock.uptimeMillis();
            if (remaining <= 0) {
                return;
            }
            try {
                Thread.sleep(Math.min(remaining, 100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void publishState() {
        Intent intent = new Intent(ACTION_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra("running", running);
        intent.putExtra("paused", paused);
        intent.putExtra("index", index);
        intent.putExtra("total", total);
        intent.putExtra("checked", checked);
        intent.putExtra("turned_off", turnedOff);
        intent.putExtra("already_off", alreadyOff);
        intent.putExtra("failed", failed);
        sendBroadcast(intent);

        final int overlayIndex = index;
        final int overlayTotal = total;
        final int overlayOff = turnedOff;
        final int overlayAlready = alreadyOff;
        final int overlayFailed = failed;
        final String app = currentApp;
        mainHandler.post(() -> {
            if (overlay != null) {
                overlay.update(overlayIndex, overlayTotal, overlayOff, overlayAlready,
                        overlayFailed, app);
            }
        });
    }

    private void performFinish(boolean stoppedByUser, String message) {
        if (finishing) {
            return;
        }
        finishing = true;
        stopRequested = true;
        running = false;
        paused = false;

        final int finishTotal = total;
        final int finishChecked = checked;
        final int finishOff = turnedOff;
        final int finishAlready = alreadyOff;
        final int finishFailed = failed;
        RecordStore.saveSummary(this, finishTotal, finishChecked, finishOff, finishAlready,
                finishFailed, stoppedByUser);
        RecordStore.setRunActive(this, false);

        OverlayController oldOverlay = overlay;
        overlay = null;

        String toast;
        if (stoppedByUser) {
            toast = String.format(java.util.Locale.CHINA,
                    "已停止：关闭 %d 个，原本已关 %d 个", finishOff, finishAlready);
        } else if (message != null && !message.isEmpty()) {
            toast = String.format(java.util.Locale.CHINA,
                    "%s。已检查 %d/%d 个，关闭 %d 个", message, finishChecked, finishTotal,
                    finishOff);
        } else {
            toast = String.format(java.util.Locale.CHINA,
                    "处理完成：关闭 %d 个，原本已关 %d 个，失败 %d 个",
                    finishOff, finishAlready, finishFailed);
        }

        boolean returned = false;
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("from_automation", true);
            startActivity(intent);
            returned = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "unable to return to app", e);
        }

        if (oldOverlay != null) {
            if (returned) {
                mainHandler.postDelayed(oldOverlay::hide, 320);
            } else {
                oldOverlay.hide();
            }
        }
        mainHandler.postDelayed(() -> AutomationForegroundService.stop(this), 900);
        Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
        publishState();
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return message.length() > 80 ? message.substring(0, 80) : message;
    }

    private String stringValue(CharSequence value) {
        return value == null ? null : value.toString();
    }

    private static final class Target {
        final AccessibilityNodeInfo node;
        final boolean checked;

        Target(AccessibilityNodeInfo node, boolean checked) {
            this.node = node;
            this.checked = checked;
        }
    }

    private static final class TargetScan {
        final Target target;
        final boolean pagePresent;

        TargetScan(Target target, boolean pagePresent) {
            this.target = target;
            this.pagePresent = pagePresent;
        }
    }

    private static final class Page {
        final AccessibilityNodeInfo root;
        final boolean loaded;

        Page(AccessibilityNodeInfo root, boolean loaded) {
            this.root = root;
            this.loaded = loaded;
        }
    }

    private static final class AppResult {
        final String result;
        final String reason;
        final boolean beforeChecked;
        final boolean afterChecked;
        final boolean pageLoadFailure;

        AppResult(String result, String reason, boolean beforeChecked, boolean afterChecked,
                  boolean pageLoadFailure) {
            this.result = result;
            this.reason = reason;
            this.beforeChecked = beforeChecked;
            this.afterChecked = afterChecked;
            this.pageLoadFailure = pageLoadFailure;
        }

        static AppResult turnedOff() {
            return new AppResult(RecordStore.RESULT_TURNED_OFF, "开关已关闭",
                    true, false, false);
        }

        static AppResult alreadyOff() {
            return new AppResult(RecordStore.RESULT_ALREADY_OFF, "开关原本已关闭",
                    false, false, false);
        }

        static AppResult missing(String reason) {
            return new AppResult(RecordStore.RESULT_NOT_FOUND, reason,
                    false, false, false);
        }

        static AppResult failure(String reason, boolean pageLoadFailure) {
            return new AppResult(RecordStore.RESULT_FAILED, reason,
                    false, false, pageLoadFailure);
        }
    }

    private static final class TextUtilsCompat {
        static boolean isEmpty(String value) {
            return value == null || value.isEmpty();
        }
    }
}
