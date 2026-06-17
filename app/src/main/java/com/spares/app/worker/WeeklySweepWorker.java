package com.spares.app.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.spares.app.db.SpareDatabase;
import com.spares.app.db.Transaction;
import com.spares.app.network.RetrofitClient;
import com.spares.app.network.model.SafeGoldBuyRequest;
import com.spares.app.network.model.SafeGoldBuyResponse;

import java.util.List;

import retrofit2.Response;

/**
 * Fires weekly to convert accumulated round-ups into gold. The PhonePe
 * mandate debit is stubbed until Phase 3 supplies merchant credentials and
 * the SDK — debitMandateStub() is the single seam Phase 3 replaces with
 * PhonePeManager.debitMandate().
 */
public class WeeklySweepWorker extends Worker {

    private static final String TAG = "WeeklySweepWorker";
    private static final double MIN_SWEEP_AMOUNT = 1.0;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    public WeeklySweepWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SpareDatabase db = SpareDatabase.getInstance(getApplicationContext());

        List<Transaction> pending = db.transactionDao().getPendingTransactionsSync();
        double total = 0.0;
        for (Transaction tx : pending) total += tx.roundUpAmount;

        if (total < MIN_SWEEP_AMOUNT) {
            Log.d(TAG, "Sweep skipped — pending total below minimum");
            return Result.success();
        }

        switch (debitMandateStub(total)) {
            case SUCCESS:
                return buyGoldAndSettle(db, total);
            case INSUFFICIENT_FUNDS:
                Log.w(TAG, "Mandate debit declined — insufficient funds. Rolling over to next week.");
                db.transactionDao().markAllSkipped();
                return Result.success();
            default:
                return retryOrGiveUp("Mandate debit failed");
        }
    }

    private Result buyGoldAndSettle(SpareDatabase db, double total) {
        try {
            SafeGoldBuyRequest request = new SafeGoldBuyRequest(total, "sweep-" + System.currentTimeMillis());
            Response<SafeGoldBuyResponse> response = RetrofitClient.safeGold().buyGold(request).execute();

            if (response.isSuccessful() && response.body() != null && response.body().success) {
                db.transactionDao().sweepSuccessAll();
                Log.i(TAG, "Sweep complete — ₹" + total + " converted to gold");
                return Result.success();
            }
            return retryOrGiveUp("SafeGold purchase rejected");

        } catch (Exception e) {
            return retryOrGiveUp("SafeGold purchase error: " + e.getMessage());
        }
    }

    private Result retryOrGiveUp(String reason) {
        if (getRunAttemptCount() >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, reason + " — giving up after " + MAX_RETRY_ATTEMPTS + " attempts this week");
            return Result.failure();
        }
        Log.w(TAG, reason + " — retrying (attempt " + (getRunAttemptCount() + 1) + ")");
        return Result.retry();
    }

    private MandateDebitOutcome debitMandateStub(double amount) {
        return MandateDebitOutcome.SUCCESS;
    }

    private enum MandateDebitOutcome { SUCCESS, INSUFFICIENT_FUNDS, FAILURE }
}
