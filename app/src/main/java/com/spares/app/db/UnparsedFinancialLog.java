package com.spares.app.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "unparsed_financial_logs")
public class UnparsedFinancialLog {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "sender_header")
    public String senderHeader;

    @ColumnInfo(name = "sim_slot_index")
    public int simSlotIndex = -1;

    @ColumnInfo(name = "raw_body")
    public String rawBody;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "failure_reason")
    public String failureReason;

    public UnparsedFinancialLog() {}

    public UnparsedFinancialLog(String senderHeader, int simSlotIndex,
                                String rawBody, long timestamp, String failureReason) {
        this.senderHeader = senderHeader;
        this.simSlotIndex = simSlotIndex;
        this.rawBody = rawBody;
        this.timestamp = timestamp;
        this.failureReason = failureReason;
    }
}
