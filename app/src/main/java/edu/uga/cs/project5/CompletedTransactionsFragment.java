package edu.uga.cs.project5;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class CompletedTransactionsFragment extends Fragment {

    private RecyclerView rv;
    private TransactionAdapter adapter;
    private DatabaseReference txRef;
    private ValueEventListener valueListener;

    public CompletedTransactionsFragment() { }

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

        txRef = FirebaseDatabase.getInstance().getReference("transactions");
        attachListener("completed");

        return root;
    }

    /**
     * Show ONLY this user's completed transactions,
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
                        Toast.makeText(getContext(), "Sign in to view your completed transactions", Toast.LENGTH_SHORT).show();
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
}
