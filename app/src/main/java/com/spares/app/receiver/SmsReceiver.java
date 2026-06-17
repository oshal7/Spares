package com.spares.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.spares.app.service.SmsParsingService;

import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    // Only process SMS from 6-character alphanumeric financial sender IDs (e.g., BP-HDFCBK)
    private static final Pattern FINANCIAL_HEADER = Pattern.compile(
        "^[A-Z]{2}-[A-Z0-9]{6}$", Pattern.CASE_INSENSITIVE
    );

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        String sender = messages[0].getDisplayOriginatingAddress();

        // Drop non-financial SMS instantly before waking any service
        if (!isFinancialSender(sender)) {
            Log.d(TAG, "Dropped non-financial sender: " + sender);
            return;
        }

        // Extract SIM slot index
        int simSlotIndex = extractSimSlot(context, intent);

        // Reassemble multipart SMS body
        StringBuilder bodyBuilder = new StringBuilder();
        for (SmsMessage msg : messages) {
            if (msg.getMessageBody() != null) {
                bodyBuilder.append(msg.getMessageBody());
            }
        }

        String fullBody = bodyBuilder.toString().trim();
        if (fullBody.isEmpty()) return;

        Log.d(TAG, "Financial SMS from " + sender + " [slot " + simSlotIndex + "]: " + fullBody);

        Intent serviceIntent = new Intent(context, SmsParsingService.class);
        serviceIntent.putExtra(SmsParsingService.EXTRA_SENDER, sender);
        serviceIntent.putExtra(SmsParsingService.EXTRA_BODY, fullBody);
        serviceIntent.putExtra(SmsParsingService.EXTRA_TIMESTAMP, System.currentTimeMillis());
        serviceIntent.putExtra(SmsParsingService.EXTRA_SIM_SLOT, simSlotIndex);
        context.startForegroundService(serviceIntent);
    }

    private static boolean isFinancialSender(String sender) {
        if (sender == null || sender.isEmpty()) return false;
        return FINANCIAL_HEADER.matcher(sender.trim()).matches();
    }

    private static int extractSimSlot(Context context, Intent intent) {
        // API 29+: use SUBSCRIPTION_INDEX extra → map to slot via SubscriptionManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
            if (subId >= 0) {
                try {
                    SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
                    if (sm != null) {
                        android.telephony.SubscriptionInfo info = sm.getActiveSubscriptionInfo(subId);
                        if (info != null) return info.getSimSlotIndex();
                    }
                } catch (SecurityException ignored) {}
            }
        }
        // Fallback: OEM-specific extras (Qualcomm / MTK devices)
        int slot = intent.getIntExtra("slot", -1);
        if (slot < 0) slot = intent.getIntExtra("simSlot", -1);
        if (slot < 0) slot = intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1);
        return slot;
    }
}
