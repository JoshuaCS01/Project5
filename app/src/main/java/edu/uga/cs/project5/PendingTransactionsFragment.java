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
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_transactions_list, container, false);

        rv = root.findViewById(R.id.rvTransactions);
        adapter = new TransactionAdapter();
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // Wire adapter action listener (row click + complete button)
        adapter.setOnTransactionActionListener(new TransactionAdapter.OnTransactionActionListener() {
            @Override
            public void onTransactionClicked(Transaction tx) {
                // Optional: show more details, for now just a toast
                if (tx != null && tx.id != null && getContext() != null) {
                    Toast.makeText(getContext(), "Transaction: " + tx.id, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCompleteClicked(Transaction tx) {
                if (tx == null || tx.id == null) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Invalid transaction", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                String currentUid = FirebaseAuth.getInstance().getUid();
                if (currentUid == null) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Sign in to manage transactions", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                // Story 14: only the SELLER can confirm completion
                if (tx.sellerId == null || !currentUid.equals(tx.sellerId)) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Only the seller can mark this transaction complete", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle("Complete Transaction")
                        .setMessage("Confirm that the buyer has picked up the item and payment is complete?")
                        .setPositiveButton("Yes", (d, w) ->
                                completeTransaction(tx.id, currentUid, tx.itemId))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        txRef = FirebaseDatabase.getInstance().getReference("transactions");
        attachListener("pending");
        return root;
    }

    /**
     * Attach a listener to /transactions and build a list of
     * ONLY this user's transactions with the given status,
     * sorted from most recent to oldest.
     */
    private void attachListener(String status) {
        if (valueListener != null && txRef != null) {
            txRef.removeEventListener(valueListener);
        }

        valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String currentUid = FirebaseAuth.getInstance().getUid();
                if (currentUid == null) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Sign in to view your pending transactions", Toast.LENGTH_SHORT).show();
                    }
                    adapter.setItems(new ArrayList<>());
                    return;
                }

                List<Transaction> mine = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Transaction t = s.getValue(Transaction.class);
                    if (t == null) continue;
                    t.id = s.getKey();

                    boolean statusMatch =
                            t.status != null && t.status.equalsIgnoreCase(status);

                    boolean isMine =
                            (t.buyerId != null && t.buyerId.equals(currentUid)) ||
                                    (t.sellerId != null && t.sellerId.equals(currentUid));

                    if (statusMatch && isMine) {
                        mine.add(t);
                    }
                }

                // Sort by createdAt desc (most recent first)
                mine.sort((a, b) -> {
                    long ca = (a.createdAt != null) ? a.createdAt : 0L;
                    long cb = (b.createdAt != null) ? b.createdAt : 0L;
                    return Long.compare(cb, ca);
                });

                adapter.setCurrentUserId(currentUid);
                adapter.setItems(mine);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(),
                            "Failed to load transactions: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
        txRef.addValueEventListener(valueListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (valueListener != null && txRef != null) {
            txRef.removeEventListener(valueListener);
        }
    }

    private void completeTransaction(String txId, String actorUid, String itemId) {
        if (txId == null || actorUid == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Missing transaction data", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        DatabaseReference root = FirebaseDatabase.getInstance().getReference();

        Map<String, Object> updates = new HashMap<>();
        updates.put("/transactions/" + txId + "/status", "completed");
        updates.put("/transactions/" + txId + "/completedAt", ServerValue.TIMESTAMP);
        updates.put("/transactions/" + txId + "/completedBy", actorUid);

        root.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Transaction marked completed", Toast.LENGTH_SHORT).show();
                }
            } else {
                String msg = task.getException() != null
                        ? task.getException().getMessage() : "";
                Log.e("PendingTxFragment", "Failed to complete tx: " + msg, task.getException());
                if (getContext() != null) {
                    Toast.makeText(getContext(),
                            "Failed to complete transaction: " + msg,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
