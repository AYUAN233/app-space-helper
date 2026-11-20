/*
 * 应用空间助手
 * Copyright (C) 2026 AYUAN
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dsh.appspace;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

final class OverlayController {
    interface Listener {
        void onTogglePause();

        void onStopConfirmed();
    }

    private static final int COLOR_TEXT = 0xFFF5F7FB;
    private static final int COLOR_SECONDARY = 0xFF9BA9BC;
    private static final int COLOR_TERTIARY = 0xFF65758A;
    private static final int COLOR_ACCENT = 0xFF4D8DFF;
    private static final int COLOR_GREEN = 0xFF4ADE80;
    private static final int COLOR_AMBER = 0xFFFBBF24;
    private static final int COLOR_RED = 0xFFFF6B6B;
    private static final int COLOR_RED_FILL = 0xFFD13B43;
    private static final int COLOR_PANEL = 0xF2111720;

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private FrameLayout root;
    private boolean attached;
    private boolean paused;
    private boolean confirmingStop;

    private View stateDot;
    private TextView stateLabel;
    private TextView progressLabel;
    private TextView progressPercent;
    private ProgressBar progressBar;
    private TextView appLabel;
    private TextView hintLabel;
    private TextView statsLabel;
    private TextView pauseButton;
    private TextView stopButton;

    private int lastIndex;
    private int lastTotal;
    private int lastOff;
    private int lastAlready;
    private int lastFailed;
    private String lastApp = "";

    private final Runnable resetStopConfirmation = () -> {
        if (!attached) {
            return;
        }
        confirmingStop = false;
        render();
    };

    OverlayController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    boolean show() {
        if (attached) {
            return true;
        }
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return false;
        }
        root = build();
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        root.setFocusable(false);
        root.setFocusableInTouchMode(false);
        root.setAlpha(0f);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lp.setFitInsetsTypes(0);
        }
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        try {
            windowManager.addView(root, lp);
        } catch (RuntimeException e) {
            root = null;
            windowManager = null;
            return false;
        }
        attached = true;
        render();
        root.animate().alpha(1f).setDuration(170).start();
        return true;
    }

    void hide() {
        handler.removeCallbacks(resetStopConfirmation);
        if (!attached) {
            return;
        }
        attached = false;
        try {
            if (windowManager != null && root != null) {
                windowManager.removeView(root);
            }
        } catch (RuntimeException ignored) {
            // The accessibility service may already be tearing down its windows.
        }
        root = null;
        windowManager = null;
    }

    void setPaused(boolean value) {
        paused = value;
        cancellingStopConfirmation();
        render();
    }

    void update(int index, int total, int off, int already, int failed, String app) {
        lastIndex = index;
        lastTotal = total;
        lastOff = off;
        lastAlready = already;
        lastFailed = failed;
        lastApp = app == null ? "" : app;
        render();
    }

    private void cancellingStopConfirmation() {
        if (confirmingStop) {
            confirmingStop = false;
            handler.removeCallbacks(resetStopConfirmation);
        }
    }

    private void render() {
        if (!attached) {
            return;
        }
        int percent = lastTotal <= 0 ? 0 : Math.min(100, Math.round(lastIndex * 100f / lastTotal));
        stateLabel.setText(paused ? "已暂停" : "自动关闭中");
        setDot(paused ? COLOR_AMBER : COLOR_ACCENT);
        progressLabel.setText(lastTotal <= 0 ? "正在准备应用列表" :
                String.format(Locale.CHINA, "正在检查  %d / %d", lastIndex, lastTotal));
        progressPercent.setText(String.format(Locale.CHINA, "%d%%", percent));
        progressBar.setProgress(percent);
        appLabel.setText(lastApp.isEmpty() ? "读取应用列表…" : lastApp);
        hintLabel.setText(paused
                ? "已暂停。点「继续」接着处理，随时可以停止。"
                : "请保持屏幕解锁，正在自动操作当前应用的设置页。");
        statsLabel.setText(String.format(Locale.CHINA, "已关闭 %d  ·  原本已关 %d  ·  失败 %d",
                lastOff, lastAlready, lastFailed));

        if (paused) {
            pauseButton.setText("继续");
            pauseButton.setTextColor(0xFFFFFFFF);
            pauseButton.setBackground(Ui.ripple(
                    Ui.gradient(context, 0xFF3D7BFF, 0xFF5B8CFF, 14), 0x66FFFFFF));
        } else {
            pauseButton.setText("暂停");
            pauseButton.setTextColor(COLOR_TEXT);
            pauseButton.setBackground(Ui.ripple(
                    Ui.roundedStroke(context, 0x1FFFFFFF, 14, 0x33FFFFFF, 1), 0x33FFFFFF));
        }

        if (confirmingStop) {
            stopButton.setText("确认停止");
            stopButton.setTextColor(0xFFFFFFFF);
            stopButton.setBackground(Ui.ripple(Ui.rounded(COLOR_RED_FILL, 14, context),
                    0x55FFFFFF));
            hintLabel.setText("再点一次「确认停止」：停止任务并回到应用主界面。");
        } else {
            stopButton.setText("停止");
            stopButton.setTextColor(COLOR_RED);
            stopButton.setBackground(Ui.ripple(
                    Ui.roundedStroke(context, 0x2AFF6B6B, 14, 0x55FF6B6B, 1), 0x44FF6B6B));
        }
    }

    private FrameLayout build() {
        FrameLayout root = new FrameLayout(context);
        root.setClickable(true);
        root.setOnClickListener(v -> {
            // Consume touches so the page underneath is not changed accidentally.
        });
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x73050A12, 0xB8050A12}));

        int topInset = Ui.statusBarHeight(context) + Ui.dp(context, 10);

        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.dp(context, 14), Ui.dp(context, 9),
                Ui.dp(context, 14), Ui.dp(context, 9));
        pill.setBackground(Ui.roundedStroke(context, 0xE6111821, 18, 0x26FFFFFF, 1));
        stateDot = new View(context);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                Ui.dp(context, 8), Ui.dp(context, 8));
        dotLp.rightMargin = Ui.dp(context, 8);
        pill.addView(stateDot, dotLp);
        stateLabel = Ui.text(context, "自动关闭中", 12, COLOR_TEXT, true);
        pill.addView(stateLabel);

        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.gravity = Gravity.TOP | Gravity.START;
        pillLp.leftMargin = Ui.dp(context, 18);
        pillLp.topMargin = topInset;
        root.addView(pill, pillLp);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 20), Ui.dp(context, 20),
                Ui.dp(context, 20), Ui.dp(context, 18));
        card.setBackground(Ui.roundedStroke(context, COLOR_PANEL, 24, 0x1FFFFFFF, 1));
        card.setElevation(Ui.dp(context, 16));

        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressLabel = Ui.text(context, "正在准备应用列表", 16, COLOR_TEXT, true);
        progressRow.addView(progressLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        progressPercent = Ui.text(context, "0%", 12, COLOR_ACCENT, true);
        progressRow.addView(progressPercent);
        card.addView(progressRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF253044));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 6));
        barLp.topMargin = Ui.dp(context, 12);
        card.addView(progressBar, barLp);

        TextView appCaption = Ui.text(context, "正在处理", 11, COLOR_TERTIARY, false);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionLp.topMargin = Ui.dp(context, 18);
        card.addView(appCaption, captionLp);

        appLabel = Ui.text(context, "读取应用列表…", 22, COLOR_TEXT, true);
        appLabel.setSingleLine(true);
        appLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams appLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        appLp.topMargin = Ui.dp(context, 4);
        card.addView(appLabel, appLp);

        hintLabel = Ui.text(context, "请保持屏幕解锁，正在自动操作当前应用的设置页。",
                12, COLOR_SECONDARY, false);
        hintLabel.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = Ui.dp(context, 8);
        card.addView(hintLabel, hintLp);

        statsLabel = Ui.text(context, "已关闭 0  ·  原本已关 0  ·  失败 0",
                12, 0xFFC7D2E5, false);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.topMargin = Ui.dp(context, 12);
        card.addView(statsLabel, statsLp);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        pauseButton = actionButton("暂停");
        stopButton = actionButton("停止");
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, Ui.dp(context, 50), 1f);
        leftLp.rightMargin = Ui.dp(context, 10);
        actions.addView(pauseButton, leftLp);
        actions.addView(stopButton, new LinearLayout.LayoutParams(0, Ui.dp(context, 50), 1f));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = Ui.dp(context, 18);
        card.addView(actions, actionsLp);

        pauseButton.setOnClickListener(v -> {
            if (confirmingStop) {
                confirmingStop = false;
                handler.removeCallbacks(resetStopConfirmation);
            }
            listener.onTogglePause();
        });
        stopButton.setOnClickListener(v -> {
            if (!confirmingStop) {
                confirmingStop = true;
                render();
                handler.postDelayed(resetStopConfirmation, 4000L);
                return;
            }
            confirmingStop = false;
            handler.removeCallbacks(resetStopConfirmation);
            stopButton.setEnabled(false);
            stopButton.setText("正在停止…");
            listener.onStopConfirmed();
        });

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.TOP;
        cardLp.leftMargin = Ui.dp(context, 14);
        cardLp.rightMargin = Ui.dp(context, 14);
        cardLp.topMargin = topInset + Ui.dp(context, 46);
        root.addView(card, cardLp);

        return root;
    }

    private TextView actionButton(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setTextColor(COLOR_TEXT);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void setDot(int color) {
        if (stateDot == null) {
            return;
        }
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        stateDot.setBackground(dot);
    }
}
