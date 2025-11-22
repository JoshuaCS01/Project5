package edu.uga.cs.project5;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

public class ItemsListFragment extends Fragment {

    private static final String ARG_CAT_ID = "catId";
    private static final String ARG_CAT_NAME = "catName";

    private String categoryId;
    private String categoryName;

    private RecyclerView rv;
    private ItemAdapter adapter;
    private DatabaseReference mappingRef;
    private ValueEventListener mappingListener;

    public static ItemsListFragment create(String catId, String catName) {
        ItemsListFragment f = new ItemsListFragment();
        Bundle b = new Bundle();
        b.putString(ARG_CAT_ID, catId);
        b.putString(ARG_CAT_NAME, catName);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_items_list, container, false);
        rv = root.findViewById(R.id.rvItems);
        adapter = new ItemAdapter();
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        if (getArguments() != null) {
            categoryId = getArguments().getString(ARG_CAT_ID);
            categoryName = getArguments().getString(ARG_CAT_NAME);
        }

        adapter.setOnItemBuyListener(item -> {
            // confirm with user first (show price or "FREE - accept?"), then start transaction
            String priceText = (item.isFree != null && item.isFree) ? "FREE" :
                    (item.priceCents != null ? String.format("$%.2f", item.priceCents / 100.0) : "Unknown");
            new AlertDialog.Builder(requireContext())
                    .setTitle("Confirm purchase")
                    .setMessage("Buy \"" + (item.title != null ? item.title : "item") + "\" for " + priceText + "?")
                    .setPositiveButton("Yes", (d, w) -> startTransaction(item))
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        FloatingActionButton fab = root.findViewById(R.id.fabAddItem);

        // <-- UPDATED: pass the current category to AddItemFragment so it will be preselected
        fab.setOnClickListener(v -> {
            AddItemFragment f;
            if (categoryId != null) {
                f = AddItemFragment.newInstance(categoryId, categoryName);
            } else {
                // fallback to default behavior if no category context
                f = new AddItemFragment();
            }

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, f)
                    .addToBackStack(null)
                    .commit();
        });


        TextView tvTitle = root.findViewById(R.id.tvItemsTitle);
        tvTitle.setText(categoryName != null ? categoryName : "Items");

        if (categoryId != null) {
            mappingRef = FirebaseDatabase.getInstance().getReference("category-items").child(categoryId);
            attachMappingListener();
        } else {
            // fallback: don't crash if categoryId is null — show nothing or handle differently
            adapter.setItems(new ArrayList<>());
        }

        return root;
    }

    private void startTransaction(Item item) {
        final String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(requireContext(), "Sign in to buy items", Toast.LENGTH_SHORT).show();
            return;
        }
        if (item == null || item.id == null) {
            Toast.makeText(requireContext(), "Invalid item", Toast.LENGTH_SHORT).show();
            return;
        }
        // can't buy your own item
        if (uid.equals(item.authorId)) {
            Toast.makeText(requireContext(), "You cannot buy your own item", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference itemsRef = FirebaseDatabase.getInstance().getReference("items").child(item.id);
        itemsRef.get().addOnCompleteListener(itemCheck -> {
            if (!itemCheck.isSuccessful()) {
                Exception ex = itemCheck.getException();
                Log.e("ItemsListFragment", "Failed reading item: " + (ex != null ? ex.getMessage() : "unknown"), ex);
                Toast.makeText(requireContext(), "Failed to check item", Toast.LENGTH_SHORT).show();
                return;
            }
            DataSnapshot s = itemCheck.getResult();
            if (s == null || !s.exists()) {
                Toast.makeText(requireContext(), "Item no longer exists", Toast.LENGTH_SHORT).show();
                return;
            }

            Boolean available = s.child("available").getValue(Boolean.class);
            String authorId = s.child("authorId").getValue(String.class);
            String categoryId = s.child("categoryId").getValue(String.class);

            if (authorId != null && uid.equals(authorId)) {
                Toast.makeText(requireContext(), "You cannot buy your own item", Toast.LENGTH_SHORT).show();
                return;
            }
            if (available != null && !available) {
                Toast.makeText(requireContext(), "Item is no longer available", Toast.LENGTH_SHORT).show();
                return;
            }

            // check for existing pending transactions for this item (if any)
            DatabaseReference txRef = FirebaseDatabase.getInstance().getReference("transactions");
            txRef.orderByChild("itemId").equalTo(item.id).get().addOnCompleteListener(txCheck -> {
                if (!txCheck.isSuccessful()) {
                    // Log and fallback to reading all transactions (works fine when /transactions is absent or rules block orderBy)
                    Exception ex = txCheck.getException();
                    Log.w("ItemsListFragment", "orderBy query failed: " + (ex != null ? ex.getMessage() : "unknown"), ex);
                    // fallback read
                    txRef.get().addOnCompleteListener(fb -> {
                        if (!fb.isSuccessful()) {
                            Exception e2 = fb.getException();
                            Log.e("ItemsListFragment", "Fallback tx read failed: " + (e2 != null ? e2.getMessage() : "unknown"), e2);
                            Toast.makeText(requireContext(), "Failed to check transactions", Toast.LENGTH_LONG).show();
                            return;
                        }
                        DataSnapshot snap = fb.getResult();
                        boolean hasPending = false;
                        if (snap != null) {
                            for (DataSnapshot t : snap.getChildren()) {
                                String itemIdOnTx = t.child("itemId").getValue(String.class);
                                String status = t.child("status").getValue(String.class);
                                if (itemIdOnTx != null && itemIdOnTx.equals(item.id)
                                        && status != null && status.equalsIgnoreCase("pending")) {
                                    hasPending = true;
                                    break;
                                }
                            }
                        }
                        if (hasPending) {
                            Toast.makeText(requireContext(), "Item already has a pending transaction", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // No pending found -> create transaction
                        createTransactionAtomic(item, authorId, uid, categoryId);
                    });
                    return;
                }

                // query succeeded — check results
                DataSnapshot txSnap = txCheck.getResult();
                boolean hasPending = false;
                if (txSnap != null) {
                    for (DataSnapshot t : txSnap.getChildren()) {
                        String status = t.child("status").getValue(String.class);
                        if (status != null && status.equalsIgnoreCase("pending")) {
                            hasPending = true;
                            break;
                        }
                    }
                }
                if (hasPending) {
                    Toast.makeText(requireContext(), "Item already has a pending transaction", Toast.LENGTH_SHORT).show();
                    return;
                }
                // create transaction
                createTransactionAtomic(item, authorId, uid, categoryId);
            });
        });
    }

    private void createTransactionAtomic(Item item, String sellerId, String buyerId, String categoryId) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        String txId = root.child("transactions").push().getKey();
        if (txId == null) {
            Toast.makeText(requireContext(), "Failed to create transaction id", Toast.LENGTH_SHORT).show();
            return;
        }

        // amount (cents) — may be null for free items
        Long amountCents = null;
        if (item.priceCents != null) amountCents = item.priceCents.longValue();

        Map<String, Object> tx = new HashMap<>();
        tx.put("itemId", item.id);
        tx.put("buyerId", buyerId);
        tx.put("sellerId", sellerId);
        tx.put("status", "pending");
        if (amountCents != null) tx.put("amountCents", amountCents);
        tx.put("createdAt", ServerValue.TIMESTAMP);

        Map<String, Object> updates = new HashMap<>();
        updates.put("/transactions/" + txId, tx);
        updates.put("/user-transactions/" + buyerId + "/" + txId, true);
        if (sellerId != null) updates.put("/user-transactions/" + sellerId + "/" + txId, true);
        // mark item unavailable and attach transaction id
        updates.put("/items/" + item.id + "/available", false);
        updates.put("/items/" + item.id + "/transactionId", txId);
        // remove from category listing so buyers browsing don't see it
        if (categoryId != null) updates.put("/category-items/" + categoryId + "/" + item.id, null);

        root.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(requireContext(), "Transaction started", Toast.LENGTH_SHORT).show();
                // Optionally: navigate to PendingTransactionsFragment or refresh UI
            } else {
                Exception ex = task.getException();
                String msg = ex != null ? ex.getMessage() : "";
                Log.e("ItemsListFragment", "Failed to create transaction: " + msg, ex);
                Toast.makeText(requireContext(), "Failed to start transaction: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void completeTransaction(String txId, String actorUid) {
        if (txId == null) return;

        DatabaseReference txRef = FirebaseDatabase.getInstance().getReference("transactions").child(txId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("completedAt", ServerValue.TIMESTAMP);
        // optionally record who completed it
        if (actorUid != null) updates.put("completedBy", actorUid);

        txRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(requireContext(), "Transaction completed", Toast.LENGTH_SHORT).show();
            } else {
                Exception ex = task.getException();
                String msg = ex != null ? ex.getMessage() : "";
                Log.e("ItemsListFragment", "Failed to complete transaction: " + msg, ex);
                Toast.makeText(requireContext(), "Failed to complete transaction: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }




    private void attachMappingListener() {
        mappingListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                // snapshot children are itemIds => true
                List<String> itemIds = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    itemIds.add(s.getKey());
                }
                if (itemIds.isEmpty()) {
                    adapter.setItems(new ArrayList<>()); // empty list
                    return;
                }
                // fetch items for these ids (batch individually)
                DatabaseReference itemsRef = FirebaseDatabase.getInstance().getReference("items");
                // gather results
                List<Item> items = new ArrayList<>();
                final int total = itemIds.size();
                final int[] fetched = {0};
                for (String itemId : itemIds) {
                    itemsRef.child(itemId).get().addOnCompleteListener(task -> {
                        fetched[0]++;
                        if (task.isSuccessful()) {
                            DataSnapshot ds = task.getResult();
                            if (ds != null && ds.exists()) {
                                Item it = ds.getValue(Item.class);
                                if (it != null) {
                                    it.id = ds.getKey();
                                    items.add(it);
                                }
                            }
                        }
                        if (fetched[0] == total) {
                            // all fetched — you might want to sort by createdAt desc
                            items.sort((a,b) -> Long.compare(b.createdAt, a.createdAt));
                            adapter.setItems(items);
                        }
                    });
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load items: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        mappingRef.addValueEventListener(mappingListener);
    }

    private void detachMappingListener() {
        if (mappingListener != null && mappingRef != null) mappingRef.removeEventListener(mappingListener);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        detachMappingListener();
    }
}
