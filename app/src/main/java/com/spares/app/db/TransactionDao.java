package com.spares.app.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    long insertTransaction(Transaction transaction);

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    LiveData<List<Transaction>> getAllTransactions();

    @Query("SELECT * FROM transactions WHERE goal_id = :goalId ORDER BY timestamp DESC")
    LiveData<List<Transaction>> getTransactionsForGoal(int goalId);

    @Query("SELECT COALESCE(SUM(round_up_amount), 0.0) FROM transactions WHERE is_settled = 0")
    LiveData<Double> getUnsettledTotal();

    @Query("SELECT COALESCE(SUM(round_up_amount), 0.0) FROM transactions WHERE is_settled = 0")
    double getUnsettledTotalSync();

    @Query("UPDATE transactions SET is_settled = 1 WHERE is_settled = 0")
    void settleAllPending();

    // Duplicate detection: same amount within 5-second window
    @Query("SELECT COUNT(*) FROM transactions WHERE original_amount = :amount AND timestamp > :windowStart")
    int countDuplicatesInWindow(double amount, long windowStart);

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 50")
    LiveData<List<Transaction>> getRecentTransactions();
}
