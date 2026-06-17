package com.spares.app.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {Goal.class, Transaction.class, UnparsedFinancialLog.class},
    version = 3,
    exportSchema = false
)
public abstract class SpareDatabase extends RoomDatabase {

    private static volatile SpareDatabase INSTANCE;

    public abstract GoalDao goalDao();
    public abstract TransactionDao transactionDao();
    public abstract UnparsedFinancialLogDao unparsedLogDao();

    // Migration v2 → v3:
    //  - transactions: rename is_settled → sync_status (TEXT), add sim_slot_index, make goal_id nullable
    //  - new table: unparsed_financial_logs
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. Rebuild transactions table with new schema
            db.execSQL(
                "ALTER TABLE transactions RENAME TO transactions_v2_backup"
            );
            db.execSQL(
                "CREATE TABLE transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "goal_id INTEGER, " +
                "sender_id TEXT, " +
                "raw_body TEXT, " +
                "original_amount REAL NOT NULL DEFAULT 0, " +
                "round_up_amount REAL NOT NULL DEFAULT 0, " +
                "timestamp INTEGER NOT NULL DEFAULT 0, " +
                "sync_status TEXT NOT NULL DEFAULT 'PENDING', " +
                "category TEXT NOT NULL DEFAULT 'DEBIT', " +
                "merchant TEXT NOT NULL DEFAULT '', " +
                "sim_slot_index INTEGER NOT NULL DEFAULT -1, " +
                "FOREIGN KEY(goal_id) REFERENCES goals(id) ON DELETE SET NULL" +
                ")"
            );
            db.execSQL(
                "CREATE INDEX index_transactions_goal_id ON transactions (goal_id)"
            );
            db.execSQL(
                "INSERT INTO transactions " +
                "(id, goal_id, sender_id, raw_body, original_amount, round_up_amount, " +
                " timestamp, sync_status, category, merchant, sim_slot_index) " +
                "SELECT id, goal_id, sender_id, raw_body, original_amount, round_up_amount, " +
                "       timestamp, " +
                "       CASE WHEN is_settled = 1 THEN 'SWEPT_SUCCESS' ELSE 'PENDING' END, " +
                "       category, merchant, -1 " +
                "FROM transactions_v2_backup"
            );
            db.execSQL("DROP TABLE transactions_v2_backup");

            // 2. Create new unparsed_financial_logs table
            db.execSQL(
                "CREATE TABLE unparsed_financial_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "sender_header TEXT NOT NULL DEFAULT '', " +
                "sim_slot_index INTEGER NOT NULL DEFAULT -1, " +
                "raw_body TEXT NOT NULL DEFAULT '', " +
                "timestamp INTEGER NOT NULL DEFAULT 0, " +
                "failure_reason TEXT" +
                ")"
            );
        }
    };

    public static SpareDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SpareDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SpareDatabase.class,
                            "spares_db"
                    )
                    .addMigrations(MIGRATION_2_3)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
