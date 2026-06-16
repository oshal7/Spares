package com.spares.app.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.spares.app.R;
import com.spares.app.db.Transaction;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionAdapter extends ListAdapter<Transaction, TransactionAdapter.TxViewHolder> {

    private static final DiffUtil.ItemCallback<Transaction> DIFF_CB =
        new DiffUtil.ItemCallback<Transaction>() {
            @Override public boolean areItemsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                return a.id == b.id;
            }
            @Override public boolean areContentsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                return a.isSettled == b.isSettled
                    && a.roundUpAmount == b.roundUpAmount
                    && a.category.equals(b.category);
            }
        };

    private static final SimpleDateFormat DATE_FMT =
        new SimpleDateFormat("dd MMM · hh:mm a", Locale.getDefault());

    public TransactionAdapter() { super(DIFF_CB); }

    @NonNull
    @Override
    public TxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_transaction, parent, false);
        return new TxViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TxViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class TxViewHolder extends RecyclerView.ViewHolder {
        private final View categoryBar;
        private final TextView tvMerchant, tvAmountLabel, tvSender, tvTimestamp;
        private final TextView tvRoundup, tvSettled;

        TxViewHolder(@NonNull View v) {
            super(v);
            categoryBar   = v.findViewById(R.id.view_category_bar);
            tvMerchant    = v.findViewById(R.id.tv_merchant);
            tvAmountLabel = v.findViewById(R.id.tv_amount_label);
            tvSender      = v.findViewById(R.id.tv_sender);
            tvTimestamp   = v.findViewById(R.id.tv_timestamp);
            tvRoundup     = v.findViewById(R.id.tv_roundup);
            tvSettled     = v.findViewById(R.id.tv_settled);
        }

        void bind(Transaction tx) {
            String category = tx.category != null ? tx.category : "DEBIT";

            // Category bar color
            int barColor = categoryColor(category, itemView);
            GradientDrawable bar = new GradientDrawable();
            bar.setColor(barColor);
            bar.setCornerRadius(4f);
            categoryBar.setBackground(bar);

            // Merchant / primary name
            boolean hasMerchant = tx.merchant != null && !tx.merchant.isEmpty();
            tvMerchant.setText(hasMerchant ? tx.merchant : tx.senderId);

            // Amount + category label
            String amtStr = String.format(Locale.getDefault(), "₹%.2f", tx.originalAmount);
            String label;
            switch (category) {
                case "CREDIT":   label = amtStr + " received";    break;
                case "TRANSFER": label = amtStr + " transferred"; break;
                default:         label = amtStr + " spent";       break;
            }
            tvAmountLabel.setText(label);

            // Sender (bank/service ID)
            tvSender.setText(tx.senderId);
            tvTimestamp.setText(DATE_FMT.format(new Date(tx.timestamp)));

            // Round-up chip (only for DEBIT/TRANSFER with non-zero round-up)
            if (!"CREDIT".equals(category) && tx.roundUpAmount > 0) {
                tvRoundup.setVisibility(View.VISIBLE);
                tvRoundup.setText(String.format(Locale.getDefault(), "+₹%.2f", tx.roundUpAmount));
                tvRoundup.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bg_dark));
                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setColor(barColor);
                chipBg.setCornerRadius(32f);
                tvRoundup.setBackground(chipBg);
            } else {
                tvRoundup.setVisibility(View.GONE);
            }

            // Settlement badge
            if ("CREDIT".equals(category)) {
                tvSettled.setText("received");
                tvSettled.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.cat_credit));
            } else if (tx.isSettled == 1) {
                tvSettled.setText("transferred");
                tvSettled.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            } else {
                tvSettled.setText("pending");
                tvSettled.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.accent_yellow));
            }
        }

        private static int categoryColor(String category, View view) {
            switch (category) {
                case "CREDIT":   return ContextCompat.getColor(view.getContext(), R.color.cat_credit);
                case "TRANSFER": return ContextCompat.getColor(view.getContext(), R.color.cat_transfer);
                default:         return ContextCompat.getColor(view.getContext(), R.color.cat_debit);
            }
        }
    }
}
