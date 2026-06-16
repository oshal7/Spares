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
import com.spares.app.ui.MainActivity;
import com.spares.app.util.SmsParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that keeps the parsing thread alive even when
 * Android enforces Doze Mode / memory eviction policies (PRD §2.2).
 */
public class SmsParsingService extends Service {

    private static final String TAG = "SmsParsingService";
    public static final String CHANNEL_ID = "spares_service_channel";
    public static final int NOTIF_ID = 1001;

    public static final String EXTRA_SENDER    = "extra_sender";
    public static final String EXTRA_BODY      = "extra_body";
    public static final String EXTRA_TIMESTAMP = "extra_timestamp";

    // Duplicate detection window: 5 seconds (PRD §6, Edge Case #1)
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

        // Process on background thread
        executor.execute(() -> processSms(sender, body, timestamp));

        return START_NOT_STICKY;
    }

    private void processSms(String sender, String body, long timestamp) {
        try {
            // ── Parse the SMS ────────────────────────────────────────────────
            SmsParser.ParseResult result = SmsParser.parse(body);

            if (!result.valid) {
                Log.d(TAG, "SMS rejected: " + result.reason);
                return;
            }

            // ── Duplicate detection (PRD §6, Risk #1) ───────────────────────
            long windowStart = timestamp - DUPLICATE_WINDOW_MS;
            int dupeCount = db.transactionDao().countDuplicatesInWindow(result.originalAmount, windowStart);
            if (dupeCount > 0) {
                Log.w(TAG, "Duplicate transaction detected within 5s window — discarding.");
                return;
            }

            // ── Get active goal ──────────────────────────────────────────────
            Goal activeGoal = db.goalDao().getActiveGoalSync();
            if (activeGoal == null) {
                Log.w(TAG, "No active goal set — ignoring transaction.");
                return;
            }

            // ── Insert transaction ───────────────────────────────────────────
            Transaction tx = new Transaction(
                activeGoal.id,
                sender != null ? sender : "UNKNOWN",
                body,
                result.originalAmount,
                result.roundUpAmount,
                timestamp,
                result.category,
                result.merchant
            );
            db.transactionDao().insertTransaction(tx);

            // ── Update goal accumulated total (DEBIT/TRANSFER only) ──────────
            if (!"CREDIT".equals(result.category) && result.roundUpAmount > 0) {
                double newTotal = activeGoal.currentAccumulated + result.roundUpAmount;
                if (newTotal <= activeGoal.targetAmount) {
                    db.goalDao().addToAccumulated(result.roundUpAmount);
                } else {
                    double remaining = activeGoal.targetAmount - activeGoal.currentAccumulated;
                    if (remaining > 0) {
                        db.goalDao().addToAccumulated(remaining);
                    }
                }
            }

            Log.i(TAG, String.format("✅ Saved ₹%.2f round-up from ₹%.2f transaction [%s]",
                result.roundUpAmount, result.originalAmount, sender));

        } catch (Exception e) {
            // PRD §6, Risk #3: safe discard on any crash — never throw global exceptions
            Log.e(TAG, "Parsing pipeline error (safe discard): " + e.getMessage());
        }
    }

    // ── Notification boilerplate ─────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "Spares Active Listener",
            NotificationManager.IMPORTANCE_LOW
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
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
