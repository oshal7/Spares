package com.spares.app.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "goals")
public class Goal {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "target_amount")
    public double targetAmount;

    @ColumnInfo(name = "current_accumulated")
    public double currentAccumulated = 0.0;

    @ColumnInfo(name = "is_active")
    public int isActive = 0; // 1 = active, 0 = inactive

    public Goal() {}

    public Goal(String title, double targetAmount) {
        this.title = title;
        this.targetAmount = targetAmount;
        this.currentAccumulated = 0.0;
        this.isActive = 1;
    }
}
