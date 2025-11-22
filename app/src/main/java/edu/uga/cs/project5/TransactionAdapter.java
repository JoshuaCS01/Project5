package edu.uga.cs.project5;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.Holder> {

    // -- Listener with both click types (row + complete button)
    public interface OnTransactionActionListener {
        void onTransactionClicked(Transaction tx);
        void onCompleteClicked(Transaction tx);
    }

    private final List<Transaction> items = new ArrayList<>();
    private OnTransactionActionListener actionListener;

    public void setOnTransactionActionListener(OnTransactionActionListener l) {
        this.actionListener = l;
    }

    public void setItems(List<Transaction> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Transaction t = items.get(position);

        holder.tvId.setText(t.id != null ? "ID: " + t.id : "—");
        holder.tvStatus.setText("Status: " + (t.status != null ? t.status : "—"));

        if (t.createdAt != null) {
            String s = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                    .format(new Date(t.createdAt));
            holder.tvTime.setText("Created: " + s);
        } else {
            holder.tvTime.setText("Created: —");
        }

        // Hide complete button for already completed transactions
        if (t.status != null && t.status.equalsIgnoreCase("completed")) {
            holder.btnComplete.setVisibility(View.GONE);
        } else {
            holder.btnComplete.setVisibility(View.VISIBLE);
        }

        // Row click
        holder.itemView.setOnClickListener(v -> {
            if (actionListener != null)
                actionListener.onTransactionClicked(t);
        });

        // Complete button click
        holder.btnComplete.setOnClickListener(v -> {
            if (actionListener != null)
                actionListener.onCompleteClicked(t);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {

        TextView tvId, tvStatus, tvTime;
        Button btnComplete;

        Holder(@NonNull View itemView) {
            super(itemView);

            tvId = itemView.findViewById(R.id.tvTxId);
            tvStatus = itemView.findViewById(R.id.tvTxStatus);
            tvTime = itemView.findViewById(R.id.tvTxTime);
            btnComplete = itemView.findViewById(R.id.btnComplete);
        }
    }
}
