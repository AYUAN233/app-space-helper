/*
 * 应用空间助手
 * Copyright (C) 2026 AYUAN
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dsh.appspace;

final class AppEntry {
    final String packageName;
    final String label;
    final boolean system;

    AppEntry(String packageName, String label, boolean system) {
        this.packageName = packageName;
        this.label = label;
        this.system = system;
    }
}
