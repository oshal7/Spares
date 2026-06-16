package com.spares.app.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
    entities = {Goal.class, Transaction.class},
    version = 1,
    exportSchema = false
)
public abstract class SpareDatabase extends RoomDatabase {

    private static volatile SpareDatabase INSTANCE;

    public abstract GoalDao goalDao();
    public abstract TransactionDao transactionDao();

    public static SpareDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SpareDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SpareDatabase.class,
                            "spares_db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
