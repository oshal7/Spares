package com.spares.app.ui;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.spares.app.db.Goal;
import com.spares.app.db.SpareDatabase;
import com.spares.app.db.Transaction;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {

    private final SpareDatabase db;
    private final ExecutorService executor;

    public final LiveData<Goal> activeGoal;
    public final LiveData<List<Transaction>> recentTransactions;
    public final LiveData<Double> unsettledTotal;

    public MainViewModel(Application application) {
        super(application);
        db = SpareDatabase.getInstance(application);
        executor = Executors.newSingleThreadExecutor();

        activeGoal = db.goalDao().getActiveGoal();
        recentTransactions = db.transactionDao().getRecentTransactions();
        unsettledTotal = db.transactionDao().getUnsettledTotal();
    }

    public void createGoal(String title, double targetAmount) {
        executor.execute(() -> {
            db.goalDao().deactivateAllGoals();
            Goal goal = new Goal(title, targetAmount);
            db.goalDao().insertGoal(goal);
        });
    }

    public void settleAll(Runnable onDone) {
        executor.execute(() -> {
            db.transactionDao().settleAllPending();
            if (onDone != null) onDone.run();
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
