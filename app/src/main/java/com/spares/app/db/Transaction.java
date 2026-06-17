package com.spares.app.db;

import androidx.annotation.Nullable;
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
        onDelete = ForeignKey.SET_NULL
    ),
    indices = {@Index(value = "goal_id")}
)
public class Transaction {

    public static final String STATUS_PENDING       = "PENDING";
    public static final String STATUS_SWEPT_SUCCESS = "SWEPT_SUCCESS";
    public static final String STATUS_SKIPPED       = "SKIPPED";

    @PrimaryKey(autoGenerate = true)
    public int id;

    @Nullable
    @ColumnInfo(name = "goal_id")
    public Integer goalId;

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

    @ColumnInfo(name = "sync_status")
    public String syncStatus = STATUS_PENDING;

    @ColumnInfo(name = "category")
    public String category = "DEBIT";

    @ColumnInfo(name = "merchant")
    public String merchant = "";

    @ColumnInfo(name = "sim_slot_index")
    public int simSlotIndex = -1;

    public Transaction() {}

    public Transaction(@Nullable Integer goalId, String senderId, String rawBody,
                       double originalAmount, double roundUpAmount, long timestamp,
                       String category, String merchant, int simSlotIndex) {
        this.goalId = goalId;
        this.senderId = senderId;
        this.rawBody = rawBody;
        this.originalAmount = originalAmount;
        this.roundUpAmount = roundUpAmount;
        this.timestamp = timestamp;
        this.syncStatus = STATUS_PENDING;
        this.category = category != null ? category : "DEBIT";
        this.merchant = merchant != null ? merchant : "";
        this.simSlotIndex = simSlotIndex;
    }
}
