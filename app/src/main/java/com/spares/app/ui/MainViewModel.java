package com.spares.app.ui;

import android.app.Application;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.spares.app.db.Goal;
import com.spares.app.db.SpareDatabase;
import com.spares.app.db.Transaction;
import com.spares.app.util.SmsParser;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "SparesVM";

    private final SpareDatabase db;
    private final ExecutorService executor;

    public final LiveData<Goal> activeGoal;
    public final LiveData<List<Transaction>> recentTransactions;
    public final LiveData<Double> unsettledTotal;

    private final MutableLiveData<Boolean> _isRefreshing = new MutableLiveData<>(false);
    public final LiveData<Boolean> isRefreshing = _isRefreshing;

    private final MutableLiveData<String> _lastRefreshStatus = new MutableLiveData<>("");
    public final LiveData<String> lastRefreshStatus = _lastRefreshStatus;

    public MainViewModel(Application application) {
        super(application);
        db = SpareDatabase.getInstance(application);
        executor = Executors.newSingleThreadExecutor();
        activeGoal        = db.goalDao().getActiveGoal();
        recentTransactions= db.transactionDao().getRecentTransactions();
        unsettledTotal    = db.transactionDao().getUnsettledTotal();
    }

    public void createGoal(String title, double targetAmount) {
        executor.execute(() -> {
            db.goalDao().deactivateAllGoals();
            db.goalDao().insertGoal(new Goal(title, targetAmount));
        });
    }

    public void settleAll(Runnable onDone) {
        executor.execute(() -> {
            db.transactionDao().settleAllPending();
            if (onDone != null) onDone.run();
        });
    }

    /**
     * Scans the device SMS inbox for missed bank transactions.
     *
     * Scan window:
     *   - If we have existing transactions → scan from 24h before the latest one
     *     (catches any gap if the foreground listener missed messages)
     *   - If no transactions yet (first scan ever) → scan last 7 days
     *     so existing payments are picked up immediately
     */
    public void refreshFromSmsInbox() {
        _isRefreshing.postValue(true);
        _lastRefreshStatus.postValue("");

        executor.execute(() -> {
            try {
                Goal goal = db.goalDao().getActiveGoalSync();
                if (goal == null) {
                    _lastRefreshStatus.postValue("Set a goal first");
                    _isRefreshing.postValue(false);
                    return;
                }

                long latestTx = db.transactionDao().getLatestTransactionTimestamp();
                long cutoff;
                boolean isFirstScan = (latestTx == 0);

                if (isFirstScan) {
                    // First time: go back 7 days to pick up recent payments
                    cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
                    Log.d(TAG, "First scan — going back 7 days");
                } else {
                    // Subsequent: go back 24h before last known transaction
                    cutoff = latestTx - (24L * 60 * 60 * 1000);
                    Log.d(TAG, "Incremental scan from " + cutoff);
                }

                Uri inboxUri = Uri.parse("content://sms/inbox");
                String[] projection = {"address", "body", "date"};
                Cursor cursor = getApplication().getContentResolver().query(
                    inboxUri,
                    projection,
                    "date > ?",
                    new String[]{String.valueOf(cutoff)},
                    "date ASC"
                );

                int newCount      = 0;
                int rejectedOtp   = 0;
                int rejectedNoKw  = 0;
                int rejectedDupe  = 0;
                int totalScanned  = 0;

                if (cursor != null) {
                    int ci = cursor.getColumnIndex("address");
                    int bi = cursor.getColumnIndex("body");
                    int di = cursor.getColumnIndex("date");

                    while (cursor.moveToNext()) {
                        totalScanned++;
                        String sender = cursor.getString(ci);
                        String body   = cursor.getString(bi);
                        long   date   = cursor.getLong(di);

                        SmsParser.ParseResult r = SmsParser.parse(body);

                        if (!r.valid) {
                            // Categorise rejections for the status message
                            String reason = r.reason.toLowerCase();
                            if (reason.contains("otp") || reason.contains("blocked"))
                                rejectedOtp++;
                            else
                                rejectedNoKw++;
                            Log.d(TAG, "Skip [" + sender + "]: " + r.reason);
                            continue;
                        }

                        // Duplicate guard: same amount within 5 seconds
                        long winStart = date - 5000L;
                        int dupes = db.transactionDao()
                            .countDuplicatesInWindow(r.originalAmount, winStart);
                        if (dupes > 0) {
                            rejectedDupe++;
                            Log.d(TAG, "Dupe skip ₹" + r.originalAmount);
                            continue;
                        }

                        // Save transaction
                        Transaction tx = new Transaction(
                            goal.id,
                            sender != null ? sender : "UNKNOWN",
                            body,
                            r.originalAmount,
                            r.roundUpAmount,
                            date
                        );
                        db.transactionDao().insertTransaction(tx);

                        // Update goal accumulated (with ceiling clamp)
                        double newTotal = goal.currentAccumulated + r.roundUpAmount;
                        if (newTotal <= goal.targetAmount) {
                            db.goalDao().addToAccumulated(r.roundUpAmount);
                            goal.currentAccumulated += r.roundUpAmount;
                        } else {
                            double rem = goal.targetAmount - goal.currentAccumulated;
                            if (rem > 0.001) {
                                db.goalDao().addToAccumulated(rem);
                                goal.currentAccumulated += rem;
                            }
                        }
                        newCount++;
                        Log.i(TAG, "✓ Saved ₹" + r.roundUpAmount + " from ₹" + r.originalAmount + " [" + sender + "]");
                    }
                    cursor.close();
                }

                // Build a useful status message
                String status;
                if (totalScanned == 0) {
                    status = "No SMS found in scan window";
                } else if (newCount == 0) {
                    if (rejectedDupe > 0) {
                        status = "Already up to date (" + rejectedDupe + " already saved)";
                    } else {
                        status = "Scanned " + totalScanned + " messages — no bank transactions found";
                    }
                } else {
                    status = "✓ Found " + newCount + " new transaction"
                        + (newCount > 1 ? "s" : "") + " in " + totalScanned + " messages";
                }

                Log.d(TAG, "Refresh done: scanned=" + totalScanned + " new=" + newCount
                    + " otpSkip=" + rejectedOtp + " noKw=" + rejectedNoKw + " dupe=" + rejectedDupe);

                _lastRefreshStatus.postValue(status);

            } catch (Exception e) {
                Log.e(TAG, "Refresh error", e);
                _lastRefreshStatus.postValue("Error: " + e.getMessage());
            } finally {
                _isRefreshing.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
