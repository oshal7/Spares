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

    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY timestamp DESC")
    LiveData<List<Transaction>> getTransactionsByCategory(String category);

    // Phase 1: PENDING only. Phase 2 will expand to IN ('PENDING','SKIPPED')
    @Query("SELECT COALESCE(SUM(round_up_amount), 0.0) FROM transactions WHERE sync_status = 'PENDING' AND category != 'CREDIT'")
    LiveData<Double> getUnsettledTotal();

    @Query("SELECT COALESCE(SUM(round_up_amount), 0.0) FROM transactions WHERE sync_status = 'PENDING' AND category != 'CREDIT'")
    double getUnsettledTotalSync();

    @Query("UPDATE transactions SET sync_status = 'SWEPT_SUCCESS' WHERE sync_status = 'PENDING' AND category != 'CREDIT'")
    void sweepSuccessAll();

    @Query("UPDATE transactions SET sync_status = 'SKIPPED' WHERE sync_status = 'PENDING' AND category != 'CREDIT'")
    void markAllSkipped();

    @Query("SELECT * FROM transactions WHERE sync_status = 'PENDING' AND category != 'CREDIT'")
    List<Transaction> getPendingTransactionsSync();

    @Query("SELECT COUNT(*) FROM transactions WHERE original_amount = :amount AND timestamp > :windowStart")
    int countDuplicatesInWindow(double amount, long windowStart);

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 100")
    LiveData<List<Transaction>> getRecentTransactions();

    @Query("SELECT COALESCE(MAX(timestamp), 0) FROM transactions")
    long getLatestTransactionTimestamp();

    @Query("SELECT * FROM transactions WHERE category IN ('DEBIT', 'TRANSFER') AND merchant != '' AND timestamp > :since ORDER BY timestamp DESC")
    List<Transaction> getDebitsSince(long since);
}
