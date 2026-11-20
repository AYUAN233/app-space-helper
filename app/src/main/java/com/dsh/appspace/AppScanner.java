/*
 * 应用空间助手
 * Copyright (C) 2026 AYUAN
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dsh.appspace;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AppScanner {
    private static final Set<String> CORE_BLOCKLIST = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.securitycenter",
            "com.miui.securityadd",
            "com.android.webview",
            "com.google.android.webview",
            "com.miui.home",
            "com.google.android.apps.nexuslauncher"
    ));

    private AppScanner() {
    }

    static List<AppEntry> findTargets(Context context, boolean includeSystem, boolean incremental) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> installed;
        try {
            installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (RuntimeException e) {
            installed = Collections.emptyList();
        }

        List<AppEntry> result = new ArrayList<>();
        String self = context.getPackageName();
        for (ApplicationInfo info : installed) {
            if (info == null || info.packageName == null) {
                continue;
            }
            String pkg = info.packageName;
            if (pkg.equals(self) || CORE_BLOCKLIST.contains(pkg)) {
                continue;
            }
            if (!info.enabled) {
                continue;
            }
            boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (system && !includeSystem) {
                continue;
            }
            if (system) {
                boolean launchable;
                try {
                    launchable = pm.getLaunchIntentForPackage(pkg) != null;
                } catch (RuntimeException e) {
                    launchable = false;
                }
                if (!launchable) {
                    continue;
                }
            }
            if (incremental && RecordStore.isDone(context, pkg)) {
                continue;
            }
            CharSequence loaded = info.loadLabel(pm);
            String label = loaded == null ? "" : loaded.toString().trim();
            if (label.isEmpty()) {
                label = pkg;
            }
            result.add(new AppEntry(pkg, label, system));
        }

        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(result, (a, b) -> {
            int byLabel = collator.compare(a.label, b.label);
            return byLabel != 0 ? byLabel : a.packageName.compareTo(b.packageName);
        });
        return result;
    }
}
