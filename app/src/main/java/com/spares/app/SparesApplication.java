package com.spares.app;

import android.app.Application;
import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.spares.app.worker.PdnNotificationWorker;
import com.spares.app.worker.WeeklySweepWorker;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class SparesApplication extends Application {

    public static final String WORK_PDN_NOTIFICATION = "pdn_notification";
    public static final String WORK_WEEKLY_SWEEP = "weekly_sweep";

    @Override
    public void onCreate() {
        super.onCreate();
        scheduleWork(getApplicationContext());
    }

    public static void scheduleWork(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);

        long pdnDelayMs = delayUntilNext(Calendar.SATURDAY, 22, 0);
        PeriodicWorkRequest pdnRequest = new PeriodicWorkRequest.Builder(
                PdnNotificationWorker.class, 7, TimeUnit.DAYS)
                .setInitialDelay(pdnDelayMs, TimeUnit.MILLISECONDS)
                .build();
        workManager.enqueueUniquePeriodicWork(
                WORK_PDN_NOTIFICATION, ExistingPeriodicWorkPolicy.KEEP, pdnRequest);

        long sweepDelayMs = delayUntilNext(Calendar.SUNDAY, 22, 0);
        PeriodicWorkRequest sweepRequest = new PeriodicWorkRequest.Builder(
                WeeklySweepWorker.class, 7, TimeUnit.DAYS)
                .setInitialDelay(sweepDelayMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, PeriodicWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();
        workManager.enqueueUniquePeriodicWork(
                WORK_WEEKLY_SWEEP, ExistingPeriodicWorkPolicy.KEEP, sweepRequest);
    }

    private static long delayUntilNext(int targetDayOfWeek, int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar target = (Calendar) now.clone();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        while (target.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek || target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        return target.getTimeInMillis() - now.getTimeInMillis();
    }
}
