/*
 * Copyright (C) 2023 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

import com.xiaomi.settings.display.ColorModeService;
import com.xiaomi.settings.display.DcDimmingService;
import com.xiaomi.settings.doze.AodBrightnessService;
import com.xiaomi.settings.touch.AlwaysOnFingerprintService;
import com.xiaomi.settings.touch.TouchOrientationService;
import com.xiaomi.settings.touch.TouchPollingRateService;

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "XiaomiParts";
    private static final boolean DEBUG = true;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            return;
        }
        if (DEBUG) Log.d(TAG, "Received boot completed intent");

        // AOD
        context.startServiceAsUser(new Intent(context, AodBrightnessService.class),
                UserHandle.CURRENT);

        // Display
        context.startServiceAsUser(new Intent(context, ColorModeService.class),
                UserHandle.CURRENT);
        context.startServiceAsUser(new Intent(context, DcDimmingService.class),
                UserHandle.CURRENT);

        // Touchscreen
        context.startServiceAsUser(new Intent(context, AlwaysOnFingerprintService.class),
                UserHandle.CURRENT);
        context.startServiceAsUser(new Intent(context, TouchOrientationService.class),
                UserHandle.CURRENT);
        context.startServiceAsUser(new Intent(context, TouchPollingRateService.class),
                UserHandle.CURRENT);
    }
}
