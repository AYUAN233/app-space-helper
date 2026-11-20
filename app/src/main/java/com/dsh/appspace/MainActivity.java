/*
 * 应用空间助手
 * Copyright (C) 2026 AYUAN
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dsh.appspace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int COLOR_BG = 0xFF080B11;
    private static final int COLOR_SURFACE = 0xFF111720;
    private static final int COLOR_STROKE = 0xFF222D3B;
    private static final int COLOR_TEXT = 0xFFF5F7FB;
    private static final int COLOR_SECONDARY = 0xFF9BA9BC;
    private static final int COLOR_TERTIARY = 0xFF65758A;
    private static final int COLOR_ACCENT = 0xFF4D8DFF;
    private static final int COLOR_GREEN = 0xFF4ADE80;
    private static final int COLOR_AMBER = 0xFFFBBF24;
    private static final int COLOR_RED = 0xFFFF6B6B;
    private static final String UI_PREFS = "appspace_ui";
    private static final String KEY_INCLUDE_SYSTEM = "include_system";

    private View permissionDot;
    private TextView permissionStatus;
    private TextView permissionHint;
    private TextView permissionAction;
    private TextView interruptBanner;
    private TextView lastRunLabel;
    private TextView metricChecked;
    private TextView metricOff;
    private TextView metricAlready;
    private TextView metricFailed;
    private TextView recordMeta;
    private TextView clearButton;
    private LinearLayout recordsContainer;
    private CheckBox includeSystem;
    private boolean receiverRegistered;
    private boolean startingAutomation;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildUi());
        SharedPreferences ui = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        includeSystem.setChecked(ui.getBoolean(KEY_INCLUDE_SYSTEM, false));
        includeSystem.setOnCheckedChangeListener((buttonView, isChecked) ->
                getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                        .edit().putBoolean(KEY_INCLUDE_SYSTEM, isChecked).apply());
        requestNotificationPermissionIfNeeded();
        refresh(false);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(AutoAccessibilityService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(false);
        if (!startingAutomation) {
            AutoAccessibilityService service = AutoAccessibilityService.getInstance();
            if (service != null && service.isRunning()) {
                if (!AutomationForegroundService.isActive()) {
                    AutomationForegroundService.start(this);
                }
            } else if (!RecordStore.isRunActive(this)) {
                AutomationForegroundService.stop(this);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refresh(false);
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0C1220, COLOR_BG}));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 26),
                Ui.dp(this, 20), Ui.dp(this, 34));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addHeader(content);
        addAccessibilityCard(content);
        addColorOsTipCard(content);
        addSectionTitle(content, "选择执行方式");
        content.addView(buildModeCard(true, "增量关闭",
                "跳过已经记录过的应用，只处理新安装或上次没做完的应用。适合日常补漏。",
                "快速", AutoAccessibilityService.MODE_INCREMENT));
        content.addView(buildModeCard(false, "全量关闭",
                "忽略历史记录，从头检查全部用户应用。首次使用或想重新核对时选它。",
                "完整", AutoAccessibilityService.MODE_FULL));
        content.addView(buildScopeCard());
        addLastRunSection(content);
        addRecordsSection(content);
        addFooter(content);
        return scroll;
    }

    private void addHeader(LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.setBackground(Ui.gradient(this, 0xFF1B49C8, 0xFF4D8DFF, 16));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_mark);
        icon.setPadding(Ui.dp(this, 13), Ui.dp(this, 13),
                Ui.dp(this, 13), Ui.dp(this, 13));
        iconWrap.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        header.addView(iconWrap, new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 54)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.leftMargin = Ui.dp(this, 14);
        TextView title = Ui.text(this, "应用空间助手", 24, COLOR_TEXT, true);
        titles.addView(title);
        TextView subtitle = Ui.text(this, "逐台关闭 · 自动记录 · 随时可停",
                12, COLOR_SECONDARY, false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = Ui.dp(this, 6);
        titles.addView(subtitle, subtitleLp);
        header.addView(titles, titlesLp);

        TextView localPill = Ui.text(this, "本地运行", 10, COLOR_GREEN, true);
        localPill.setGravity(Gravity.CENTER);
        localPill.setPadding(Ui.dp(this, 9), Ui.dp(this, 5),
                Ui.dp(this, 9), Ui.dp(this, 5));
        localPill.setBackground(Ui.roundedStroke(this, 0x1233D17A, 10, 0x3333D17A, 1));
        header.addView(localPill);

        parent.addView(header);
    }

    private void addAccessibilityCard(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 17),
                Ui.dp(this, 18), Ui.dp(this, 16));
        card.setBackground(Ui.roundedStroke(this, COLOR_SURFACE, 20, COLOR_STROKE, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(this, 24);

        interruptBanner = Ui.text(this, "", 11, COLOR_AMBER, true);
        interruptBanner.setLineSpacing(0f, 1.25f);
        interruptBanner.setPadding(Ui.dp(this, 11), Ui.dp(this, 9),
                Ui.dp(this, 11), Ui.dp(this, 9));
        interruptBanner.setBackground(Ui.roundedStroke(this, 0x1AFBBF24, 11, 0x3DFBBF24, 1));
        interruptBanner.setClickable(true);
        interruptBanner.setFocusable(true);
        interruptBanner.setVisibility(View.GONE);
        interruptBanner.setOnClickListener(v -> showKeepAliveHelp());
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerLp.bottomMargin = Ui.dp(this, 10);
        card.addView(interruptBanner, bannerLp);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        permissionDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 8), Ui.dp(this, 8));
        dotLp.rightMargin = Ui.dp(this, 8);
        statusRow.addView(permissionDot, dotLp);
        permissionStatus = Ui.text(this, "无障碍服务", 15, COLOR_TEXT, true);
        statusRow.addView(permissionStatus, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        permissionAction = Ui.text(this, "去开启", 12, 0xFFFFFFFF, true);
        permissionAction.setGravity(Gravity.CENTER);
        permissionAction.setPadding(Ui.dp(this, 13), Ui.dp(this, 8),
                Ui.dp(this, 13), Ui.dp(this, 8));
        permissionAction.setClickable(true);
        permissionAction.setFocusable(true);
        permissionAction.setOnClickListener(v -> onPermissionAction());
        statusRow.addView(permissionAction);
        card.addView(statusRow);

        permissionHint = Ui.text(this, "", 12, COLOR_SECONDARY, false);
        permissionHint.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = Ui.dp(this, 10);
        card.addView(permissionHint, hintLp);

        TextView restricted = Ui.text(this,
                "如果无障碍开关是灰色：应用信息 → 右上角 ⋮ → 允许受限设置。",
                11, COLOR_TERTIARY, false);
        restricted.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams restrictedLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        restrictedLp.topMargin = Ui.dp(this, 8);
        card.addView(restricted, restrictedLp);

        TextView keepAlive = Ui.text(this, "系统经常中断运行？查看防中断设置  ›",
                11, COLOR_AMBER, true);
        keepAlive.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        keepAlive.setClickable(true);
        keepAlive.setFocusable(true);
        keepAlive.setOnClickListener(v -> showKeepAliveHelp());
        LinearLayout.LayoutParams keepAliveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        keepAliveLp.topMargin = Ui.dp(this, 7);
        card.addView(keepAlive, keepAliveLp);
        parent.addView(card, cardLp);
    }

    private void addColorOsTipCard(LinearLayout parent) {
        if (!isColorOs()) {
            return;
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14),
                Ui.dp(this, 16), Ui.dp(this, 13));
        card.setBackground(Ui.roundedStroke(this, 0xFF17140B, 18, 0x55FBBF24, 1));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "ColorOS 防拦截", 14, COLOR_AMBER, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = Ui.text(this, "重要", 9, COLOR_AMBER, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(this, 7), Ui.dp(this, 3),
                Ui.dp(this, 7), Ui.dp(this, 3));
        badge.setBackground(Ui.roundedStroke(this, 0x24FBBF24, 7, 0x44FBBF24, 1));
        titleRow.addView(badge);
        card.addView(titleRow);

        TextView body = Ui.text(this,
                "手机管家 → 风险行为拦截 → 应用空间助手 → 加入白名单。"
                        + "被拦截时会出现「异常操控设备」记录，表现为只处理一个应用就停止、无障碍被关闭。",
                11, 0xFFE8D7A8, false);
        body.setLineSpacing(0f, 1.28f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = Ui.dp(this, 7);
        card.addView(body, bodyLp);

        TextView action = Ui.text(this, "查看设置步骤和手机管家入口  ›", 11, COLOR_AMBER, true);
        action.setPadding(0, Ui.dp(this, 6), 0, 0);
        action.setClickable(true);
        action.setFocusable(true);
        action.setOnClickListener(v -> showKeepAliveHelp());
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.topMargin = Ui.dp(this, 4);
        card.addView(action, actionLp);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(this, 10);
        parent.addView(card, cardLp);
    }

    private boolean isColorOs() {
        String info = String.valueOf(Build.MANUFACTURER) + " "
                + String.valueOf(Build.BRAND) + " "
                + String.valueOf(Build.PRODUCT);
        String lower = info.toLowerCase(Locale.ROOT);
        return lower.contains("oppo") || lower.contains("oneplus")
                || lower.contains("realme") || lower.contains("oplus")
                || lower.contains("coloros");
    }

    private View buildModeCard(boolean primary, String titleText, String description,
                               String tagText, int mode) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 17),
                Ui.dp(this, 18), Ui.dp(this, 16));
        card.setClickable(true);
        card.setFocusable(true);

        int titleColor;
        int descColor;
        int tagColor;
        if (primary) {
            card.setBackground(Ui.ripple(Ui.gradient(this, 0xFF1E55E8, 0xFF3D7BFF, 20),
                    0x44FFFFFF));
            titleColor = 0xFFFFFFFF;
            descColor = 0xFFD9E5FF;
            tagColor = 0xFFFFFFFF;
        } else {
            card.setBackground(Ui.ripple(
                    Ui.roundedStroke(this, 0xFF141B26, 20, 0xFF2B394C, 1), 0x22FFFFFF));
            titleColor = COLOR_TEXT;
            descColor = COLOR_SECONDARY;
            tagColor = COLOR_ACCENT;
        }

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, titleText, 18, titleColor, true);
        top.addView(title);
        TextView tag = Ui.text(this, tagText, 10, tagColor, true);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(Ui.dp(this, 8), Ui.dp(this, 4),
                Ui.dp(this, 8), Ui.dp(this, 4));
        tag.setBackground(Ui.rounded(primary ? 0x2EFFFFFF : 0x1F4D8DFF, 8, this));
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagLp.leftMargin = Ui.dp(this, 8);
        top.addView(tag, tagLp);
        top.addView(new View(this), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = Ui.text(this, "›", 26,
                primary ? 0xCCFFFFFF : COLOR_SECONDARY, false);
        top.addView(arrow);
        card.addView(top);

        TextView descriptionView = Ui.text(this, description, 12, descColor, false);
        descriptionView.setLineSpacing(0f, 1.28f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = Ui.dp(this, 8);
        descLp.rightMargin = Ui.dp(this, 22);
        card.addView(descriptionView, descLp);

        card.setOnClickListener(v -> startMode(mode));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        card.setLayoutParams(lp);
        return card;
    }

    private View buildScopeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 13),
                Ui.dp(this, 16), Ui.dp(this, 12));
        card.setBackground(Ui.roundedStroke(this, 0xFF0D131C, 16, 0xFF1B2532, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(this, 8);

        includeSystem = new CheckBox(this);
        includeSystem.setText("包含预装的系统应用");
        includeSystem.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        includeSystem.setTextColor(0xFFD5DFED);
        includeSystem.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        includeSystem.setButtonTintList(android.content.res.ColorStateList.valueOf(COLOR_ACCENT));
        includeSystem.setPadding(0, 0, 0, 0);
        includeSystem.setMinHeight(0);
        includeSystem.setMinimumHeight(0);
        card.addView(includeSystem);

        TextView note = Ui.text(this,
                "默认只处理用户安装的应用。系统应用只包含有桌面入口的，数量更多、速度更慢。",
                11, COLOR_TERTIARY, false);
        note.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = Ui.dp(this, 3);
        noteLp.leftMargin = Ui.dp(this, 2);
        card.addView(note, noteLp);
        card.setLayoutParams(cardLp);
        return card;
    }

    private void addLastRunSection(LinearLayout parent) {
        addSectionTitle(parent, "上次运行");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 15),
                Ui.dp(this, 16), Ui.dp(this, 14));
        card.setBackground(Ui.roundedStroke(this, COLOR_SURFACE, 20, COLOR_STROKE, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        lastRunLabel = Ui.text(this, "尚未运行", 11, COLOR_TERTIARY, false);
        card.addView(lastRunLabel);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metricsLp.topMargin = Ui.dp(this, 12);
        metricChecked = addMetric(metrics, "检查", COLOR_TEXT, false);
        metricOff = addMetric(metrics, "已关闭", COLOR_GREEN, true);
        metricAlready = addMetric(metrics, "原本已关", COLOR_ACCENT, true);
        metricFailed = addMetric(metrics, "失败", COLOR_SECONDARY, true);
        card.addView(metrics, metricsLp);
        parent.addView(card, cardLp);
    }

    private TextView addMetric(LinearLayout row, String label, int valueColor, boolean divider) {
        if (divider) {
            View line = new View(this);
            line.setBackgroundColor(0xFF25303E);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    Ui.dp(this, 1), Ui.dp(this, 32));
            lineLp.leftMargin = Ui.dp(this, 7);
            lineLp.rightMargin = Ui.dp(this, 7);
            row.addView(line, lineLp);
        }
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView value = Ui.text(this, "0", 21, valueColor, true);
        value.setGravity(Gravity.CENTER);
        cell.addView(value);
        TextView name = Ui.text(this, label, 10, COLOR_TERTIARY, false);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = Ui.dp(this, 5);
        cell.addView(name, nameLp);
        row.addView(cell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return value;
    }

    private void addRecordsSection(LinearLayout parent) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingLp.topMargin = Ui.dp(this, 26);
        TextView title = Ui.text(this, "最近记录", 15, COLOR_TEXT, true);
        heading.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clearButton = Ui.text(this, "清空", 12, COLOR_SECONDARY, true);
        clearButton.setPadding(Ui.dp(this, 10), Ui.dp(this, 5),
                Ui.dp(this, 10), Ui.dp(this, 5));
        clearButton.setBackground(Ui.ripple(Ui.rounded(0x14FFFFFF, 9, this), 0x22FFFFFF));
        clearButton.setClickable(true);
        clearButton.setFocusable(true);
        clearButton.setOnClickListener(v -> confirmClear());
        heading.addView(clearButton);
        parent.addView(heading, headingLp);

        recordMeta = Ui.text(this, "已记录 0 个应用", 11, COLOR_TERTIARY, false);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = Ui.dp(this, 6);
        parent.addView(recordMeta, metaLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 15), Ui.dp(this, 4),
                Ui.dp(this, 15), Ui.dp(this, 4));
        card.setBackground(Ui.roundedStroke(this, COLOR_SURFACE, 20, COLOR_STROKE, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(this, 10);
        recordsContainer = new LinearLayout(this);
        recordsContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(recordsContainer);
        parent.addView(card, cardLp);
    }

    private void addFooter(LinearLayout parent) {
        TextView footer = Ui.text(this,
                "只会修改「自动释放应用空间」这一个开关，不会卸载应用或清除数据。\n停止前需再次确认，运行中可随时暂停。",
                11, COLOR_TERTIARY, false);
        footer.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 22);
        parent.addView(footer, lp);

        TextView author = Ui.text(this,
                "作者：" + getString(R.string.author_name) + "  ·  GitHub  ›",
                11, 0xFF80B6FF, true);
        author.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        author.setClickable(true);
        author.setFocusable(true);
        author.setOnClickListener(v -> openExternal(getString(R.string.author_url)));
        LinearLayout.LayoutParams authorLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        authorLp.topMargin = Ui.dp(this, 4);
        parent.addView(author, authorLp);

        TextView license = Ui.text(this,
                "开源许可：GPL-3.0-only  ·  查看完整协议", 11, 0xFF80B6FF, true);
        license.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 4));
        license.setClickable(true);
        license.setFocusable(true);
        license.setOnClickListener(v -> showLicenseDialog());
        LinearLayout.LayoutParams licenseLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        licenseLp.topMargin = Ui.dp(this, 2);
        parent.addView(license, licenseLp);
    }

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, "无法打开链接：" + url, Toast.LENGTH_LONG).show();
        }
    }

    private void showLicenseDialog() {
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // Keep the license viewer available even if version lookup fails.
        }
        String author = getString(R.string.author_name);
        String authorUrl = getString(R.string.author_url);
        String preamble = "应用空间助手"
                + (version == null || version.isEmpty() ? "" : "  v" + version)
                + "\nCopyright (C) 2026 " + author
                + "\n" + authorUrl
                + "\nSPDX-License-Identifier: GPL-3.0-only\n\n"
                + "本程序为自由软件：你可以依据 GNU 通用公共许可证第 3 版"
                + "（GPL-3.0-only）重新分发和/或修改它。\n"
                + "本程序按“现状”提供，不附任何担保。"
                + "完整源代码和许可证随发布仓库提供。\n\n"
                + "────────────────────────\n\n";
        String text;
        try (InputStream in = getResources().openRawResource(R.raw.license)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            text = "GNU GPL v3.0 全文：https://www.gnu.org/licenses/gpl-3.0.txt";
        }

        TextView content = Ui.text(this, preamble + text, 10, 0xFFC7D2E5, false);
        content.setTextIsSelectable(true);
        content.setLineSpacing(0f, 1.18f);
        int padding = Ui.dp(this, 18);
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(padding, Ui.dp(this, 6), padding, 0);
        scroll.setFillViewport(true);
        scroll.addView(content);
        int dialogHeight = Math.max(Ui.dp(this, 300),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.60f));
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dialogHeight));
        new AlertDialog.Builder(this)
                .setTitle("开源许可 · GPL-3.0-only")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView title = Ui.text(this, text, 14, 0xFFD9E2EF, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 26);
        lp.bottomMargin = Ui.dp(this, 10);
        parent.addView(title, lp);
    }

    private void refresh(boolean announce) {
        AutoAccessibilityService service = AutoAccessibilityService.getInstance();
        boolean enabled = isAccessibilityServiceEnabled();
        boolean connected = service != null;
        boolean running = connected && service.isRunning();

        if (!running && RecordStore.isRunActive(this)) {
            // The process was killed before the accessibility service could finish
            // (common when ColorOS trims background apps), or the system disabled it.
            RecordStore.setRunActive(this, false);
            RecordStore.markInterrupted(this);
            AutomationForegroundService.stop(this);
        }

        RecordStore.Summary summary = RecordStore.lastSummary(this);
        int done = RecordStore.doneCount(this);
        boolean interrupted = summary.interrupted || (summary.time <= 0 && done > 0);

        if (connected) {
            permissionStatus.setText("无障碍服务已就绪");
            permissionHint.setText("已可以读取系统设置页并自动点击开关。任务运行时请保持屏幕解锁。");
            setPermissionDot(COLOR_GREEN);
            permissionAction.setText("设置");
            setPermissionActionStyle(0xFF1B2431, 0xFFD7E3F5, 0xFF2A384B);
        } else if (enabled) {
            permissionStatus.setText("服务正在连接");
            permissionHint.setText(interrupted
                    ? (isColorOs()
                    ? "上次运行被系统中断。请先检查手机管家「风险行为拦截」白名单，再点「刷新」。"
                    : "上次运行被系统中断。请先点「防中断设置」处理，再点「刷新」。")
                    : "系统已记录开启，服务还没连上。稍等片刻后点「刷新」。");
            setPermissionDot(COLOR_AMBER);
            permissionAction.setText("刷新");
            setPermissionActionStyle(0xFF3A2B0B, 0xFFFFD66B, 0xFF5B4510);
        } else {
            permissionStatus.setText("无障碍服务未开启");
            permissionHint.setText(interrupted
                    ? (isColorOs()
                    ? "上次运行被系统中断。请到手机管家 → 风险行为拦截 将本应用加入白名单，再重新开启无障碍。"
                    : "上次运行被系统中断，无障碍被关闭。请先点「防中断设置」，再重新开启无障碍。")
                    : "开启后才能读取应用详情页并点击「自动释放应用空间」开关。");
            setPermissionDot(interrupted ? COLOR_AMBER : COLOR_RED);
            permissionAction.setText("去开启");
            setPermissionActionStyle(0xFF1E55E8, 0xFFFFFFFF, 0xFF1E55E8);
        }

        if (interruptBanner != null) {
            interruptBanner.setVisibility(interrupted ? View.VISIBLE : View.GONE);
            if (isColorOs()) {
                interruptBanner.setText(summary.time <= 0
                        ? "上次运行被系统中断，只留下了部分记录。请检查手机管家「风险行为拦截」中的「异常操控设备」记录，并把本应用加入白名单。点这里看步骤。"
                        : "上次运行被系统中断。请检查手机管家「风险行为拦截」白名单，然后重新开启无障碍。点这里看步骤。");
            } else {
                interruptBanner.setText(summary.time <= 0
                        ? "上次运行被系统中断，只留下了部分记录。点这里做防中断设置。"
                        : "上次运行被系统中断（无障碍被关闭）。点这里做防中断设置。");
            }
        }

        metricChecked.setText(String.valueOf(summary.checked));
        metricOff.setText(String.valueOf(summary.turnedOff));
        metricAlready.setText(String.valueOf(summary.alreadyOff));
        metricFailed.setText(String.valueOf(summary.failed));
        metricFailed.setTextColor(summary.failed > 0 ? COLOR_RED : COLOR_SECONDARY);
        if (summary.time <= 0) {
            if (done > 0) {
                lastRunLabel.setText(String.format(Locale.CHINA,
                        "上次运行被系统中断 · 已记录 %d 个应用", done));
            } else {
                lastRunLabel.setText("尚未运行");
            }
        } else if (summary.interrupted) {
            lastRunLabel.setText(String.format(Locale.CHINA,
                    "上次运行被系统中断 · %s · 已检查 %d/%d 个",
                    formatTime(summary.time), summary.checked, summary.total));
        } else {
            String when = formatTime(summary.time);
            String prefix = summary.stopped ? "上次已手动停止" : "上次运行";
            lastRunLabel.setText(String.format(Locale.CHINA, "%s · %s · 共检查 %d/%d 个",
                    prefix, when, summary.checked, summary.total));
        }
        lastRunLabel.setTextColor(interrupted ? COLOR_AMBER : COLOR_TERTIARY);

        recordMeta.setText(String.format(Locale.CHINA, "已记录 %d 个应用", done));
        clearButton.setEnabled(done > 0);
        clearButton.setAlpha(done > 0 ? 1f : 0.35f);
        renderRecords();

        if (running) {
            permissionHint.setText("任务正在运行。请通过屏幕上的控制层暂停或停止。");
        }
        if (announce) {
            Toast.makeText(this, connected ? "服务已连接" : "服务尚未连接，请稍候", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void renderRecords() {
        if (recordsContainer == null) {
            return;
        }
        recordsContainer.removeAllViews();
        List<RecordStore.Item> items = RecordStore.recent(this, 10);
        if (items.isEmpty()) {
            TextView empty = Ui.text(this, "还没有记录。选一种执行方式开始。",
                    12, COLOR_TERTIARY, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(this, 8), Ui.dp(this, 24),
                    Ui.dp(this, 8), Ui.dp(this, 24));
            recordsContainer.addView(empty);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            RecordStore.Item item = items.get(i);
            addRecordRow(item);
            if (i < items.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(0xFF1D2734);
                LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
                dividerLp.leftMargin = Ui.dp(this, 20);
                recordsContainer.addView(divider, dividerLp);
            }
        }
    }

    private void addRecordRow(RecordStore.Item item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 11), 0, Ui.dp(this, 11));

        int color = resultColor(item.result);
        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(color);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 7), Ui.dp(this, 7));
        dotLp.rightMargin = Ui.dp(this, 10);
        row.addView(dot, dotLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView label = Ui.text(this, item.label, 13, 0xFFEAF0FA, true);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(label);
        String metaText = item.packageName + " · " + formatTime(item.time);
        if (!item.reason.isEmpty()
                && !RecordStore.RESULT_TURNED_OFF.equals(item.result)
                && !RecordStore.RESULT_ALREADY_OFF.equals(item.result)) {
            metaText = item.reason + " · " + formatTime(item.time);
        }
        TextView meta = Ui.text(this, metaText, 10, COLOR_TERTIARY, false);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = Ui.dp(this, 3);
        texts.addView(meta, metaLp);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tag = Ui.text(this, resultLabel(item.result), 10, color, true);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(Ui.dp(this, 8), Ui.dp(this, 4),
                Ui.dp(this, 8), Ui.dp(this, 4));
        tag.setBackground(Ui.rounded(resultTint(item.result), 8, this));
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagLp.leftMargin = Ui.dp(this, 8);
        row.addView(tag, tagLp);
        recordsContainer.addView(row);
    }

    private void confirmClear() {
        if (AutoAccessibilityService.getInstance() != null
                && AutoAccessibilityService.getInstance().isRunning()) {
            Toast.makeText(this, "运行中不能清空记录", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空全部记录？")
                .setMessage("会同时清除「增量关闭」的已处理进度，下次增量会重新检查所有应用。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    RecordStore.clear(this);
                    refresh(false);
                })
                .show();
    }

    private void startMode(int mode) {
        AutoAccessibilityService service = AutoAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
            openAccessibilitySettings();
            return;
        }
        if (service.isRunning()) {
            Toast.makeText(this, "任务已经在运行", Toast.LENGTH_SHORT).show();
            return;
        }
        if (startingAutomation) {
            return;
        }
        startingAutomation = true;
        boolean keeperRequested = AutomationForegroundService.start(this);
        final boolean keeperAccepted = keeperRequested;
        uiHandler.postDelayed(() -> {
            startingAutomation = false;
            if (isFinishing() || isDestroyed()) {
                AutomationForegroundService.stop(this);
                return;
            }
            AutoAccessibilityService current = AutoAccessibilityService.getInstance();
            if (current == null) {
                AutomationForegroundService.stop(this);
                Toast.makeText(this, "无障碍服务已断开，请重新开启后再试",
                        Toast.LENGTH_LONG).show();
                refresh(false);
                return;
            }
            if (current.isRunning()) {
                Toast.makeText(this, "任务已经在运行", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean include = includeSystem != null && includeSystem.isChecked();
            if (current.startAutomation(mode, include)) {
                String text = mode == AutoAccessibilityService.MODE_INCREMENT
                        ? "开始增量关闭" : "开始全量关闭";
                if (!keeperAccepted || !AutomationForegroundService.isActive()) {
                    text += "；后台保活启动失败，系统可能中断运行";
                }
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            } else {
                AutomationForegroundService.stop(this);
                Toast.makeText(this, "启动失败，请关闭无障碍后重新开启",
                        Toast.LENGTH_LONG).show();
            }
        }, 400);
    }

    private void showKeepAliveHelp() {
        StringBuilder message = new StringBuilder();
        if (isColorOs()) {
            message.append("ColorOS 用户先做这一项：\n")
                    .append("• 手机管家 → 风险行为拦截 → 查看「异常操控设备」记录，")
                    .append("把「应用空间助手」加入白名单。\n")
                    .append("被拦截的典型表现：只处理一个应用就停止、无障碍服务被关闭。\n\n")
                    .append("再建议完成：\n");
        } else {
            message.append("建议做好以下设置：\n");
        }
        message.append("• 应用信息 → 耗电管理 → 允许后台运行。\n")
                .append("• 允许自启动，并在最近任务里锁定本应用。\n")
                .append("• 关闭省电模式 / 睡眠待机优化。\n")
                .append("• 允许通知，以显示运行中的保活提醒。\n")
                .append("• 回到无障碍重新开启。\n\n")
                .append("处理完后重新运行。");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("防止被系统中断")
                .setMessage(message.toString())
                .setNegativeButton("知道了", null)
                .setPositiveButton("应用信息", (dialog, which) -> openAppInfo());
        if (isColorOs()) {
            builder.setNeutralButton("手机管家", (dialog, which) -> openPhoneManager());
        }
        builder.show();
    }

    private void openPhoneManager() {
        String[] candidates = {
                "com.coloros.phonemanager",
                "com.oplus.phonemanager",
                "com.coloros.safecenter",
                "com.oplus.safecenter",
                "com.coloros.securitypermission"
        };
        PackageManager pm = getPackageManager();
        for (String pkg : candidates) {
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent == null) {
                continue;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
                Toast.makeText(this, "请在手机管家里找到「风险行为拦截」",
                        Toast.LENGTH_LONG).show();
                return;
            } catch (RuntimeException ignored) {
                // Try the next OEM package name.
            }
        }
        Toast.makeText(this, "没有找到手机管家，请手动打开手机管家 → 风险行为拦截",
                Toast.LENGTH_LONG).show();
    }

    private void openAppInfo() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, "无法打开应用信息，请手动到系统设置中操作",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onPermissionAction() {
        AutoAccessibilityService service = AutoAccessibilityService.getInstance();
        if (service != null) {
            openAccessibilitySettings();
        } else if (isAccessibilityServiceEnabled()) {
            refresh(true);
        } else {
            openAccessibilitySettings();
        }
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this,
                    "在「已下载的应用」里找到「应用空间助手」并开启",
                    Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, "无法打开无障碍设置，请手动到系统设置中开启",
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName component = new ComponentName(this, AutoAccessibilityService.class);
        String expectedLong = component.flattenToString();
        String expectedShort = component.flattenToShortString();
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            String entry = splitter.next();
            if (entry.equalsIgnoreCase(expectedLong) || entry.equalsIgnoreCase(expectedShort)) {
                return true;
            }
        }
        return false;
    }

    private void setPermissionDot(int color) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        permissionDot.setBackground(dot);
    }

    private void setPermissionActionStyle(int fill, int textColor, int stroke) {
        GradientDrawable bg = Ui.roundedStroke(this, fill, 11, stroke, 1);
        permissionAction.setBackground(Ui.ripple(bg, 0x33FFFFFF));
        permissionAction.setTextColor(textColor);
    }

    private String formatTime(long time) {
        if (time <= 0) {
            return "";
        }
        Date date = new Date(time);
        String pattern = isToday(date) ? "HH:mm" : "MM-dd HH:mm";
        return new SimpleDateFormat(pattern, Locale.CHINA).format(date);
    }

    private boolean isToday(Date date) {
        Date now = new Date();
        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);
        return day.format(now).equals(day.format(date));
    }

    private int resultColor(String result) {
        if (RecordStore.RESULT_TURNED_OFF.equals(result)) {
            return COLOR_GREEN;
        }
        if (RecordStore.RESULT_ALREADY_OFF.equals(result)) {
            return COLOR_ACCENT;
        }
        if (RecordStore.RESULT_NOT_FOUND.equals(result)) {
            return COLOR_AMBER;
        }
        return COLOR_RED;
    }

    private int resultTint(String result) {
        if (RecordStore.RESULT_TURNED_OFF.equals(result)) {
            return 0x184ADE80;
        }
        if (RecordStore.RESULT_ALREADY_OFF.equals(result)) {
            return 0x184D8DFF;
        }
        if (RecordStore.RESULT_NOT_FOUND.equals(result)) {
            return 0x18FBBF24;
        }
        return 0x18FF6B6B;
    }

    private String resultLabel(String result) {
        if (RecordStore.RESULT_TURNED_OFF.equals(result)) {
            return "已关闭";
        }
        if (RecordStore.RESULT_ALREADY_OFF.equals(result)) {
            return "原本已关";
        }
        if (RecordStore.RESULT_NOT_FOUND.equals(result)) {
            return "无此开关";
        }
        return "未完成";
    }
}
