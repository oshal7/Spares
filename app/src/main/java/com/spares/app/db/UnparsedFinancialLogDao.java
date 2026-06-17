package com.spares.app.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface UnparsedFinancialLogDao {

    @Insert
    void insertLog(UnparsedFinancialLog log);

    @Query("SELECT * FROM unparsed_financial_logs ORDER BY timestamp DESC")
    LiveData<List<UnparsedFinancialLog>> getAllLogs();

    @Query("DELETE FROM unparsed_financial_logs WHERE timestamp < :cutoff")
    void purgeBefore(long cutoff);

    @Query("SELECT COUNT(*) FROM unparsed_financial_logs")
    int count();
}
