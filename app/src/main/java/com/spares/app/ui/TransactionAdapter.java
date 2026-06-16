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
            @Override
            public boolean areItemsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                return a.id == b.id;
            }
            @Override
            public boolean areContentsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                return a.isSettled == b.isSettled && a.roundUpAmount == b.roundUpAmount;
            }
        };

    private static final SimpleDateFormat DATE_FMT =
        new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

    public TransactionAdapter() {
        super(DIFF_CB);
    }

    @NonNull
    @Override
    public TxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_transaction, parent, false);
        return new TxViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TxViewHolder holder, int position) {
        Transaction tx = getItem(position);
        holder.bind(tx);
    }

    static class TxViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSender;
        private final TextView tvTimestamp;
        private final TextView tvAmounts;
        private final TextView tvSettled;

        TxViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender    = itemView.findViewById(R.id.tv_sender);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvAmounts   = itemView.findViewById(R.id.tv_amounts);
            tvSettled   = itemView.findViewById(R.id.tv_settled);
        }

        void bind(Transaction tx) {
            tvSender.setText(tx.senderId);
            tvTimestamp.setText(DATE_FMT.format(new Date(tx.timestamp)));
            tvAmounts.setText(String.format(Locale.getDefault(),
                "₹%.2f  →  +₹%.2f saved", tx.originalAmount, tx.roundUpAmount));
            tvSettled.setText(tx.isSettled == 1 ? "✓ Settled" : "Pending");
            tvSettled.setAlpha(tx.isSettled == 1 ? 0.5f : 1.0f);
        }
    }
}
