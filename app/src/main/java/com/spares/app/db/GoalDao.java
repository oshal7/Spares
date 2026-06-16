package com.spares.app.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface GoalDao {

    @Insert
    long insertGoal(Goal goal);

    @Update
    void updateGoal(Goal goal);

    @Query("SELECT * FROM goals WHERE is_active = 1 LIMIT 1")
    LiveData<Goal> getActiveGoal();

    @Query("SELECT * FROM goals WHERE is_active = 1 LIMIT 1")
    Goal getActiveGoalSync();

    @Query("UPDATE goals SET is_active = 0 WHERE is_active = 1")
    void deactivateAllGoals();

    @Query("UPDATE goals SET current_accumulated = current_accumulated + :amount WHERE is_active = 1")
    void addToAccumulated(double amount);

    @Query("SELECT * FROM goals ORDER BY id DESC")
    LiveData<List<Goal>> getAllGoals();
}
