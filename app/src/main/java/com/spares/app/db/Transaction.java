package com.spares.app.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "transactions",
    foreignKeys = @ForeignKey(
        entity = Goal.class,
        parentColumns = "id",
        childColumns = "goal_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index(value = "goal_id")}
)
public class Transaction {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "goal_id")
    public int goalId;

    @ColumnInfo(name = "sender_id")
    public String senderId;

    @ColumnInfo(name = "raw_body")
    public String rawBody;

    @ColumnInfo(name = "original_amount")
    public double originalAmount;

    @ColumnInfo(name = "round_up_amount")
    public double roundUpAmount;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "is_settled")
    public int isSettled = 0; // 0 = unsettled, 1 = settled

    public Transaction() {}

    public Transaction(int goalId, String senderId, String rawBody,
                       double originalAmount, double roundUpAmount, long timestamp) {
        this.goalId = goalId;
        this.senderId = senderId;
        this.rawBody = rawBody;
        this.originalAmount = originalAmount;
        this.roundUpAmount = roundUpAmount;
        this.timestamp = timestamp;
        this.isSettled = 0;
    }
}
