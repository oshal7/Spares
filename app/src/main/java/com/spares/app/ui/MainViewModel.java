package com.spares.app.ui;

import android.app.Application;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.spares.app.db.Goal;
import com.spares.app.db.SpareDatabase;
import com.spares.app.db.Transaction;
import com.spares.app.util.SmsParser;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "SparesVM";

    private final SpareDatabase db;
    private final ExecutorService executor;

    public final LiveData<Goal> activeGoal;
    public final LiveData<Double> unsettledTotal;

    private final MutableLiveData<String> _selectedCategory = new MutableLiveData<>("ALL");
    public final LiveData<List<Transaction>> filteredTransactions;

    private final MutableLiveData<Boolean> _isRefreshing = new MutableLiveData<>(false);
    public final LiveData<Boolean> isRefreshing = _isRefreshing;

    private final MutableLiveData<String> _lastRefreshStatus = new MutableLiveData<>("");
    public final LiveData<String> lastRefreshStatus = _lastRefreshStatus;

    // Top merchant insight: "Swiggy (₹8.50 saved)"
    private final MutableLiveData<String> _topMerchantInsight = new MutableLiveData<>("");
    public final LiveData<String> topMerchantInsight = _topMerchantInsight;

    public MainViewModel(Application application) {
        super(application);
        db = SpareDatabase.getInstance(application);
        executor = Executors.newSingleThreadExecutor();
        activeGoal        = db.goalDao().getActiveGoal();
        unsettledTotal    = db.transactionDao().getUnsettledTotal();

        filteredTransactions = Transformations.switchMap(_selectedCategory, cat -> {
            if ("ALL".equals(cat)) return db.transactionDao().getRecentTransactions();
            return db.transactionDao().getTransactionsByCategory(cat);
        });
    }

    public void setCategory(String category) {
        _selectedCategory.setValue(category);
    }

    public void createGoal(String title, double targetAmount) {
        executor.execute(() -> {
            db.goalDao().deactivateAllGoals();
            db.goalDao().insertGoal(new Goal(title, targetAmount));
        });
    }

    public void settleAll(Runnable onDone) {
        executor.execute(() -> {
            db.transactionDao().sweepSuccessAll();
            if (onDone != null) onDone.run();
        });
    }

    public void refreshFromSmsInbox() {
        _isRefreshing.postValue(true);
        _lastRefreshStatus.postValue("");

        executor.execute(() -> {
            try {
                Goal goal = db.goalDao().getActiveGoalSync();
                Integer goalId = (goal != null) ? goal.id : null;

                long latestTx = db.transactionDao().getLatestTransactionTimestamp();
                long cutoff;
                if (latestTx == 0) {
                    cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
                } else {
                    cutoff = latestTx - (24L * 60 * 60 * 1000);
                }

                Uri inboxUri = Uri.parse("content://sms/inbox");
                String[] projection = {"address", "body", "date"};
                Cursor cursor = getApplication().getContentResolver().query(
                    inboxUri, projection, "date > ?",
                    new String[]{String.valueOf(cutoff)}, "date ASC"
                );

                int newCount = 0, totalScanned = 0, rejectedDupe = 0;

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
                            Log.d(TAG, "Skip [" + sender + "]: " + r.reason);
                            continue;
                        }

                        long winStart = date - 5000L;
                        int dupes = db.transactionDao()
                            .countDuplicatesInWindow(r.originalAmount, winStart);
                        if (dupes > 0) {
                            rejectedDupe++;
                            continue;
                        }

                        Transaction tx = new Transaction(
                            goalId,
                            sender != null ? sender : "UNKNOWN",
                            body,
                            r.originalAmount,
                            r.roundUpAmount,
                            date,
                            r.category,
                            r.merchant,
                            -1
                        );
                        db.transactionDao().insertTransaction(tx);

                        if (goal != null && !"CREDIT".equals(r.category) && r.roundUpAmount > 0) {
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
                        }
                        newCount++;
                    }
                    cursor.close();
                }

                String status;
                if (totalScanned == 0) {
                    status = "No SMS found in scan window";
                } else if (newCount == 0) {
                    status = rejectedDupe > 0
                        ? "Already up to date"
                        : "Scanned " + totalScanned + " messages — nothing new";
                } else {
                    status = "Found " + newCount + " new transaction"
                        + (newCount > 1 ? "s" : "") + " in " + totalScanned + " messages";
                }

                _lastRefreshStatus.postValue(status);
                computeMerchantInsight();

            } catch (Exception e) {
                Log.e(TAG, "Refresh error", e);
                _lastRefreshStatus.postValue("Error: " + e.getMessage());
            } finally {
                _isRefreshing.postValue(false);
            }
        });
    }

    public void computeMerchantInsight() {
        executor.execute(() -> {
            try {
                long since = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
                List<Transaction> txs = db.transactionDao().getDebitsSince(since);
                if (txs == null || txs.isEmpty()) {
                    _topMerchantInsight.postValue("");
                    return;
                }
                Map<String, Double> totals = new HashMap<>();
                for (Transaction tx : txs) {
                    String key = tx.merchant.isEmpty() ? tx.senderId : tx.merchant;
                    totals.put(key, totals.getOrDefault(key, 0.0) + tx.roundUpAmount);
                }
                String topMerchant = Collections.max(totals.entrySet(),
                    Map.Entry.comparingByValue()).getKey();
                double topAmount = totals.get(topMerchant);
                _topMerchantInsight.postValue(
                    topMerchant + " contributed ₹" + String.format("%.2f", topAmount) + " this week");
            } catch (Exception e) {
                _topMerchantInsight.postValue("");
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
