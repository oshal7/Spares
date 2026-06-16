package com.spares.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.spares.app.service.SmsParsingService;

/**
 * Static BroadcastReceiver declared in AndroidManifest.
 * Wakes on SMS_RECEIVED and hands the payload to the foreground parsing service.
 */
public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        // Reassemble multipart SMS body
        StringBuilder bodyBuilder = new StringBuilder();
        String sender = messages[0].getDisplayOriginatingAddress();

        for (SmsMessage msg : messages) {
            if (msg.getMessageBody() != null) {
                bodyBuilder.append(msg.getMessageBody());
            }
        }

        String fullBody = bodyBuilder.toString().trim();
        if (fullBody.isEmpty()) return;

        Log.d(TAG, "SMS from " + sender + ": " + fullBody);

        // Hand off to the foreground service for parsing + DB write
        Intent serviceIntent = new Intent(context, SmsParsingService.class);
        serviceIntent.putExtra(SmsParsingService.EXTRA_SENDER, sender);
        serviceIntent.putExtra(SmsParsingService.EXTRA_BODY, fullBody);
        serviceIntent.putExtra(SmsParsingService.EXTRA_TIMESTAMP, System.currentTimeMillis());
        context.startForegroundService(serviceIntent);
    }
}
