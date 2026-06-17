package com.spares.app.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Phase 1 stub. Phase 2 wires in: read unsettledTotal, call PdnApiService.notifyDebit()
 * 24h ahead of the Sunday sweep per NPCI mandate rules.
 */
public class PdnNotificationWorker extends Worker {

    private static final String TAG = "PdnNotificationWorker";

    public PdnNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "PDN notification triggered (stub — Phase 2 adds real PDN API call)");
        return Result.success();
    }
}
