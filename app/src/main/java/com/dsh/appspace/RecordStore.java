/*
 * 应用空间助手
 * Copyright (C) 2026 AYUAN
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dsh.appspace;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RecordStore {
    static final String RESULT_TURNED_OFF = "turned_off";
    static final String RESULT_ALREADY_OFF = "already_off";
    static final String RESULT_NOT_FOUND = "not_found";
    static final String RESULT_FAILED = "failed";

    private static final String PREFS = "appspace_records";
    private static final String KEY_RECORDS = "records";
    private static final String KEY_DONE = "done_packages";
    private static final String KEY_SUM_TOTAL = "sum_total";
    private static final String KEY_SUM_CHECKED = "sum_checked";
    private static final String KEY_SUM_OFF = "sum_off";
    private static final String KEY_SUM_ALREADY = "sum_already";
    private static final String KEY_SUM_FAILED = "sum_failed";
    private static final String KEY_SUM_TIME = "sum_time";
    private static final String KEY_SUM_STOPPED = "sum_stopped";
    private static final String KEY_SUM_INTERRUPTED = "sum_interrupted";
    private static final String KEY_RUN_ACTIVE = "run_active";
    private static final int MAX_RECORDS = 500;

    private RecordStore() {
    }

    static final class Item {
        final String packageName;
        final String label;
        final String result;
        final String reason;
        final long time;
        final String before;
        final String after;

        Item(String packageName, String label, String result, String reason, long time,
             String before, String after) {
            this.packageName = packageName;
            this.label = label;
            this.result = result;
            this.reason = reason;
            this.time = time;
            this.before = before;
            this.after = after;
        }
    }

    static final class Summary {
        final int total;
        final int checked;
        final int turnedOff;
        final int alreadyOff;
        final int failed;
        final long time;
        final boolean stopped;
        final boolean interrupted;

        Summary(int total, int checked, int turnedOff, int alreadyOff, int failed, long time,
                boolean stopped, boolean interrupted) {
            this.total = total;
            this.checked = checked;
            this.turnedOff = turnedOff;
            this.alreadyOff = alreadyOff;
            this.failed = failed;
            this.time = time;
            this.stopped = stopped;
            this.interrupted = interrupted;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized void add(Context context, String packageName, String label, String result,
                                 String reason, boolean beforeChecked, boolean afterChecked) {
        JSONArray array = readArray(prefs(context));
        JSONObject object = new JSONObject();
        try {
            object.put("pkg", packageName);
            object.put("label", label);
            object.put("result", result);
            object.put("reason", reason == null ? "" : reason);
            object.put("time", System.currentTimeMillis());
            object.put("before", beforeChecked ? "on" : "off");
            object.put("after", afterChecked ? "on" : "off");
        } catch (JSONException ignored) {
            return;
        }
        array.put(object);
        while (array.length() > MAX_RECORDS) {
            array.remove(0);
        }
        prefs(context).edit().putString(KEY_RECORDS, array.toString()).apply();
    }

    static synchronized boolean isDone(Context context, String packageName) {
        return prefs(context).getStringSet(KEY_DONE, new HashSet<String>())
                .contains(packageName);
    }

    static synchronized void markDone(Context context, String packageName) {
        Set<String> current = prefs(context).getStringSet(KEY_DONE, new HashSet<String>());
        Set<String> copy = new HashSet<>(current);
        if (copy.add(packageName)) {
            prefs(context).edit().putStringSet(KEY_DONE, copy).apply();
        }
    }

    static synchronized int doneCount(Context context) {
        return prefs(context).getStringSet(KEY_DONE, new HashSet<String>()).size();
    }

    static synchronized List<Item> recent(Context context, int limit) {
        JSONArray array = readArray(prefs(context));
        List<Item> items = new ArrayList<>();
        for (int i = array.length() - 1; i >= 0 && items.size() < limit; i--) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String pkg = object.optString("pkg", "");
            if (pkg.isEmpty()) {
                continue;
            }
            items.add(new Item(
                    pkg,
                    object.optString("label", pkg),
                    object.optString("result", RESULT_FAILED),
                    object.optString("reason", ""),
                    object.optLong("time", 0L),
                    object.optString("before", "off"),
                    object.optString("after", "off")));
        }
        return items;
    }

    static synchronized void saveSummary(Context context, int total, int checked, int turnedOff,
                                         int alreadyOff, int failed, boolean stopped) {
        prefs(context).edit()
                .putInt(KEY_SUM_TOTAL, total)
                .putInt(KEY_SUM_CHECKED, checked)
                .putInt(KEY_SUM_OFF, turnedOff)
                .putInt(KEY_SUM_ALREADY, alreadyOff)
                .putInt(KEY_SUM_FAILED, failed)
                .putLong(KEY_SUM_TIME, System.currentTimeMillis())
                .putBoolean(KEY_SUM_STOPPED, stopped)
                .putBoolean(KEY_SUM_INTERRUPTED, false)
                .commit();
    }

    static synchronized void markInterrupted(Context context) {
        prefs(context).edit().putBoolean(KEY_SUM_INTERRUPTED, true).commit();
    }

    static synchronized void setRunActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_RUN_ACTIVE, active).commit();
    }

    static synchronized boolean isRunActive(Context context) {
        return prefs(context).getBoolean(KEY_RUN_ACTIVE, false);
    }

    static synchronized Summary lastSummary(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains(KEY_SUM_TIME)) {
            return new Summary(0, 0, 0, 0, 0, 0L, false,
                    p.getBoolean(KEY_SUM_INTERRUPTED, false));
        }
        return new Summary(
                p.getInt(KEY_SUM_TOTAL, 0),
                p.getInt(KEY_SUM_CHECKED, 0),
                p.getInt(KEY_SUM_OFF, 0),
                p.getInt(KEY_SUM_ALREADY, 0),
                p.getInt(KEY_SUM_FAILED, 0),
                p.getLong(KEY_SUM_TIME, 0L),
                p.getBoolean(KEY_SUM_STOPPED, false),
                p.getBoolean(KEY_SUM_INTERRUPTED, false));
    }

    static synchronized void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static JSONArray readArray(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_RECORDS, "[]");
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
