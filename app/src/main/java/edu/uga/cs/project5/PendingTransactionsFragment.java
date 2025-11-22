package edu.uga.cs.project5;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PendingTransactionsFragment extends Fragment {

    private RecyclerView rv;
    private TransactionAdapter adapter;
    private DatabaseReference txRef;
    private ValueEventListener valueListener;

    public PendingTransactionsFragment() { }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_transactions_list, container, false);
        rv = root.findViewById(R.id.rvTransactions);
        adapter = new TransactionAdapter();
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // Wire adapter action listener (handles both row click and complete button)
        adapter.setOnTransactionActionListener(new TransactionAdapter.OnTransactionActionListener() {
            @Override
            public void onTransactionClicked(Transaction tx) {
                // optional: show details or nothing
                Toast.makeText(requireContext(), "Clicked: " + tx.id, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCompleteClicked(Transaction tx) {
                if (tx == null || tx.id == null) {
                    Toast.makeText(requireContext(), "Invalid transaction", Toast.LENGTH_SHORT).show();
                    return;
                }
                String currentUid = FirebaseAuth.getInstance().getUid();
                if (currentUid == null) {
                    Toast.makeText(requireContext(), "Sign in to manage transactions", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Only the buyer may mark complete in your flow — change this if you want seller to be able too
                if (!currentUid.equals(tx.sellerId)) {
                    Toast.makeText(requireContext(), "Only the buyer can mark this transaction complete", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Confirm with buyer (adapter also shows a confirmation dialog — this double-checks if needed)
                new AlertDialog.Builder(requireContext())
                        .setTitle("Complete Transaction")
                        .setMessage("Have you picked up the item and completed payment? Mark transaction as completed?")
                        .setPositiveButton("Yes", (d, w) -> completeTransaction(tx.id, currentUid, tx.itemId))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        txRef = FirebaseDatabase.getInstance().getReference("transactions");
        attachListener("pending");
        return root;
    }

    private void attachListener(String status) {
        if (valueListener != null && txRef != null) txRef.removeEventListener(valueListener);
        valueListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Transaction> list = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Transaction t = s.getValue(Transaction.class);
                    if (t == null) continue;
                    t.id = s.getKey();
                    if (t.status != null && t.status.equalsIgnoreCase(status)) {
                        list.add(t);
                    }
                }
                adapter.setItems(list);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load transactions: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        txRef.addValueEventListener(valueListener);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (valueListener != null && txRef != null) txRef.removeEventListener(valueListener);
    }

    /**
     * Marks a transaction as completed (updates status and completedAt).
     * Optionally clears the item's transactionId (uncomment if desired).
     */
    private void completeTransaction(String txId, String actorUid, String itemId) {
        if (txId == null || actorUid == null) {
            Toast.makeText(requireContext(), "Missing transaction data", Toast.LENGTH_SHORT).show();
            return;
        }
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();

        Map<String, Object> updates = new HashMap<>();
        updates.put("/transactions/" + txId + "/status", "completed");
        updates.put("/transactions/" + txId + "/completedAt", ServerValue.TIMESTAMP);
        updates.put("/transactions/" + txId + "/completedBy", actorUid);

        // OPTIONAL: clear the transactionId on the item so it no longer points to transaction
        // if you want to keep a permanent link, leave this commented.
        if (itemId != null) {
            // updates.put("/items/" + itemId + "/transactionId", null);
            // OR: keep it but leave available = false so item remains marked sold
        }

        root.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(requireContext(), "Transaction marked completed", Toast.LENGTH_SHORT).show();
            } else {
                String msg = task.getException() != null ? task.getException().getMessage() : "";
                Log.e("PendingTxFragment", "Failed to complete tx: " + msg, task.getException());
                Toast.makeText(requireContext(), "Failed to complete transaction: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
