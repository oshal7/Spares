package com.spares.app.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Phase 1 stub. Phase 2 wires in: PhonePe mandate debit + SafeGold purchase +
 * sweepSuccessAll()/markAllSkipped() based on outcome.
 */
public class WeeklySweepWorker extends Worker {

    private static final String TAG = "WeeklySweepWorker";

    public WeeklySweepWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Weekly sweep triggered (stub — Phase 2 adds real sweep logic)");
        return Result.success();
    }
}
