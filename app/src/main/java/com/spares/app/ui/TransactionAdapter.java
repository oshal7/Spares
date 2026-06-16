package com.spares.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
                return a.isSettled == b.isSettled && a.roundUpAmount == b.roundUpAmount;
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
        private final TextView tvSender, tvAmounts, tvTimestamp, tvRoundup, tvSettled;

        TxViewHolder(@NonNull View v) {
            super(v);
            tvSender    = v.findViewById(R.id.tv_sender);
            tvAmounts   = v.findViewById(R.id.tv_amounts);
            tvTimestamp = v.findViewById(R.id.tv_timestamp);
            tvRoundup   = v.findViewById(R.id.tv_roundup);
            tvSettled   = v.findViewById(R.id.tv_settled);
        }

        void bind(Transaction tx) {
            tvSender.setText(tx.senderId);
            tvAmounts.setText(String.format(Locale.getDefault(), "₹%.2f spent", tx.originalAmount));
            tvTimestamp.setText(DATE_FMT.format(new Date(tx.timestamp)));
            tvRoundup.setText(String.format(Locale.getDefault(), "+₹%.2f", tx.roundUpAmount));
            tvSettled.setText(tx.isSettled == 1 ? "transferred" : "pending");
        }
    }
}
