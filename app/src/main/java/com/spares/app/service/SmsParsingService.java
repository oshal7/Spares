package com.spares.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.spares.app.R;
import com.spares.app.db.Goal;
import com.spares.app.db.SpareDatabase;
import com.spares.app.db.Transaction;
import com.spares.app.db.UnparsedFinancialLog;
import com.spares.app.ui.MainActivity;
import com.spares.app.util.SmsParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsParsingService extends Service {

    private static final String TAG = "SmsParsingService";
    public static final String CHANNEL_ID = "spares_service_channel";
    public static final int NOTIF_ID = 1001;

    public static final String EXTRA_SENDER    = "extra_sender";
    public static final String EXTRA_BODY      = "extra_body";
    public static final String EXTRA_TIMESTAMP = "extra_timestamp";
    public static final String EXTRA_SIM_SLOT  = "extra_sim_slot";

    private static final long DUPLICATE_WINDOW_MS = 5_000L;

    private ExecutorService executor;
    private SpareDatabase db;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        db = SpareDatabase.getInstance(getApplicationContext());
        createNotificationChannel();
        startForeground(NOTIF_ID, buildPersistentNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String sender    = intent.getStringExtra(EXTRA_SENDER);
        String body      = intent.getStringExtra(EXTRA_BODY);
        long   timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        int    simSlot   = intent.getIntExtra(EXTRA_SIM_SLOT, -1);

        executor.execute(() -> processSms(sender, body, timestamp, simSlot));

        return START_NOT_STICKY;
    }

    private void processSms(String sender, String body, long timestamp, int simSlot) {
        try {
            SmsParser.ParseResult result = SmsParser.parse(body);

            if (!result.valid) {
                Log.d(TAG, "SMS rejected [" + sender + "]: " + result.reason);
                // All SMS reaching this service passed the 6-char financial header filter,
                // so any parse failure is worth logging for pattern debugging.
                db.unparsedLogDao().insertLog(new UnparsedFinancialLog(
                    sender != null ? sender : "UNKNOWN",
                    simSlot,
                    body != null ? body : "",
                    timestamp,
                    result.reason
                ));
                return;
            }

            // Duplicate detection
            long windowStart = timestamp - DUPLICATE_WINDOW_MS;
            int dupeCount = db.transactionDao().countDuplicatesInWindow(result.originalAmount, windowStart);
            if (dupeCount > 0) {
                Log.w(TAG, "Duplicate transaction detected — discarding.");
                return;
            }

            // Active goal (may be null — transactions are still stored without a goal)
            Goal activeGoal = db.goalDao().getActiveGoalSync();
            Integer goalId = (activeGoal != null) ? activeGoal.id : null;

            Transaction tx = new Transaction(
                goalId,
                sender != null ? sender : "UNKNOWN",
                body,
                result.originalAmount,
                result.roundUpAmount,
                timestamp,
                result.category,
                result.merchant,
                simSlot
            );
            db.transactionDao().insertTransaction(tx);

            // Update goal only when a goal exists and this is a spending transaction
            if (activeGoal != null && !"CREDIT".equals(result.category) && result.roundUpAmount > 0) {
                double newTotal = activeGoal.currentAccumulated + result.roundUpAmount;
                if (newTotal <= activeGoal.targetAmount) {
                    db.goalDao().addToAccumulated(result.roundUpAmount);
                } else {
                    double remaining = activeGoal.targetAmount - activeGoal.currentAccumulated;
                    if (remaining > 0) db.goalDao().addToAccumulated(remaining);
                }
            }

            Log.i(TAG, String.format("Saved ₹%.2f round-up [%s | slot %d | %s]",
                result.roundUpAmount, sender, simSlot, result.category));

        } catch (Exception e) {
            Log.e(TAG, "Parsing error (safe discard): " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Spares Active Listener", NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Monitors incoming SMS for transactions");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildPersistentNotification() {
        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spares is running")
            .setContentText("Watching for transactions to round up…")
            .setSmallIcon(R.drawable.ic_coin)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
