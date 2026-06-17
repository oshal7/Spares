package com.spares.app.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.spares.app.db.SpareDatabase;
import com.spares.app.db.UnparsedFinancialLog;
import com.spares.app.network.RetrofitClient;
import com.spares.app.network.model.PdnRequest;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Fires ~24h before the Sunday sweep so the NPCI-mandated Pre-Debit
 * Notification reaches the user in time. Failures must never block or
 * surface to the UI — the sweep itself is the source of truth.
 */
public class PdnNotificationWorker extends Worker {

    private static final String TAG = "PdnNotificationWorker";
    private static final double MIN_NOTIFY_AMOUNT = 1.0;
    private static final String PREFS_NAME = "spares_prefs";
    private static final String PREF_MANDATE_ID = "mandate_id";

    public PdnNotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SpareDatabase db = SpareDatabase.getInstance(getApplicationContext());
        double total = db.transactionDao().getUnsettledTotalSync();

        if (total < MIN_NOTIFY_AMOUNT) {
            Log.d(TAG, "PDN skipped — unsettled total below minimum");
            return Result.success();
        }

        String mandateId = getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_MANDATE_ID, "");

        try {
            PdnRequest request = new PdnRequest(mandateId, total, nextSundayIso());
            RetrofitClient.pdn().notifyDebit(request).execute();
        } catch (Exception e) {
            Log.e(TAG, "PDN call failed (failing silently): " + e.getMessage());
            db.unparsedLogDao().insertLog(new UnparsedFinancialLog(
                "PDN_BACKEND", -1, "PDN notify failed for sweep amount " + total,
                System.currentTimeMillis(), e.getMessage()
            ));
        }
        return Result.success();
    }

    private static String nextSundayIso() {
        Calendar cal = Calendar.getInstance();
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }
}
