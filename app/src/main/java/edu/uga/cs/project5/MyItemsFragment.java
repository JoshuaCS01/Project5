package edu.uga.cs.project5;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists the current user's items and allows edit/delete (with the rules you specified).
 */
public class MyItemsFragment extends Fragment {

    private RecyclerView rv;
    private ItemAdapter adapter;
    private ProgressBar progress;

    private DatabaseReference itemsRef;
    private ValueEventListener itemsListener;

    public MyItemsFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_items_list, container, false);
        rv = root.findViewById(R.id.rvItems);
        TextView title = root.findViewById(R.id.tvItemsTitle);
        title.setText("My Items");
        progress = root.findViewById(R.id.progress); // optional: reuse layout's progress view
        adapter = new ItemAdapter();
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // enable adapter click callbacks
        adapter.setOnItemActionListener(item -> showItemActionsDialog(item));

        // load current user's items
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(requireContext(), "Sign in to view your items", Toast.LENGTH_SHORT).show();
            adapter.setItems(new ArrayList<>());
            return root;
        }

        itemsRef = FirebaseDatabase.getInstance().getReference("items");
        attachItemsListener(uid);

        return root;
    }

    private void setLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void attachItemsListener(String uid) {
        setLoading(true);
        itemsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Item> mine = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Item it = s.getValue(Item.class);
                    if (it != null && uid.equals(it.authorId)) {
                        it.id = s.getKey();
                        mine.add(it);
                    }
                }
                mine.sort((a, b) -> Long.compare(b.createdAt != null ? b.createdAt : 0L,
                        a.createdAt != null ? a.createdAt : 0L));
                adapter.setItems(mine);
                setLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                Toast.makeText(requireContext(),
                        "Failed to load your items: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };
        itemsRef.addValueEventListener(itemsListener);
    }

    private void detachItemsListener() {
        if (itemsRef != null && itemsListener != null) {
            itemsRef.removeEventListener(itemsListener);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachItemsListener();
    }

    // ---- Actions UI ----

    private void showItemActionsDialog(Item item) {
        new AlertDialog.Builder(requireContext())
                .setTitle(item.title != null ? item.title : "Item")
                .setItems(new CharSequence[]{"Edit", "Delete", "Cancel"}, (dialog, which) -> {
                    if (which == 0) {
                        showEditItemDialog(item);
                    } else if (which == 1) {
                        confirmAndDeleteItem(item);
                    } else {
                        dialog.dismiss();
                    }
                }).show();
    }

    // Edit only allowed fields: title, description, isFree, priceCents
    private void showEditItemDialog(Item item) {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.edit_item, null);
        EditText etTitle = v.findViewById(R.id.etDialogTitle);
        EditText etDescription = v.findViewById(R.id.etDialogDescription);
        EditText etPrice = v.findViewById(R.id.etDialogPrice);
        android.widget.CheckBox cbFree = v.findViewById(R.id.cbDialogFree);

        // prefill
        etTitle.setText(item.title);
        etDescription.setText(item.description);
        cbFree.setChecked(Boolean.TRUE.equals(item.isFree));
        if (item.priceCents != null) {
            double p = item.priceCents / 100.0;
            etPrice.setText(String.format("%.2f", p));
        } else {
            etPrice.setText("");
        }
        etPrice.setEnabled(!cbFree.isChecked());
        cbFree.setOnCheckedChangeListener((b, checked) -> etPrice.setEnabled(!checked));

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit item")
                .setView(v)
                .setPositiveButton("Save", (d, w) -> {
                    String newTitle = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
                    String newDesc = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
                    boolean newIsFree = cbFree.isChecked();
                    String priceText = etPrice.getText() != null ? etPrice.getText().toString().trim() : "";

                    if (TextUtils.isEmpty(newTitle)) {
                        Toast.makeText(requireContext(), "Title required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Long newPriceCents = null;
                    if (!newIsFree) {
                        if (TextUtils.isEmpty(priceText)) {
                            Toast.makeText(requireContext(), "Price required or mark free", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            double p = Double.parseDouble(priceText);
                            if (p < 0) {
                                Toast.makeText(requireContext(), "Price must be non-negative", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            newPriceCents = Math.round(p * 100.0);
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Invalid price", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("/items/" + item.id + "/title", newTitle);
                    if (!TextUtils.isEmpty(newDesc)) {
                        updates.put("/items/" + item.id + "/description", newDesc);
                    } else {
                        updates.put("/items/" + item.id + "/description", null);
                    }
                    updates.put("/items/" + item.id + "/isFree", newIsFree);
                    if (newPriceCents != null) {
                        updates.put("/items/" + item.id + "/priceCents", newPriceCents);
                    } else {
                        updates.put("/items/" + item.id + "/priceCents", null);
                    }

                    // Do not touch categoryId or createdAt here.

                    setLoading(true);
                    FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                            .addOnCompleteListener(task -> {
                                setLoading(false);
                                if (task.isSuccessful()) {
                                    Toast.makeText(requireContext(), "Item updated", Toast.LENGTH_SHORT).show();
                                } else {
                                    String msg = task.getException() != null ? task.getException().getMessage() : "";
                                    Toast.makeText(requireContext(),
                                            "Failed to update: " + msg,
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Check pending transactions, then delete item and mapping
    private void confirmAndDeleteItem(Item item) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || !uid.equals(item.authorId)) {
            Toast.makeText(requireContext(), "You can only delete your own items", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete item")
                .setMessage("Are you sure you want to delete this item? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> checkPendingTransactionsAndDelete(item))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkPendingTransactionsAndDelete(Item item) {
        setLoading(true);
        DatabaseReference txRef = FirebaseDatabase.getInstance().getReference("transactions");

        // Try indexed query first
        txRef.orderByChild("itemId").equalTo(item.id).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                // Fallback: read all transactions, like in ItemsListFragment
                txRef.get().addOnCompleteListener(fb -> {
                    if (!fb.isSuccessful()) {
                        setLoading(false);
                        Toast.makeText(requireContext(),
                                "Failed to check transactions",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DataSnapshot snap = fb.getResult();
                    boolean hasPending = false;
                    if (snap != null) {
                        for (DataSnapshot s : snap.getChildren()) {
                            String itemIdOnTx = s.child("itemId").getValue(String.class);
                            String status = s.child("status").getValue(String.class);
                            if (itemIdOnTx != null && itemIdOnTx.equals(item.id)
                                    && status != null && status.equalsIgnoreCase("pending")) {
                                hasPending = true;
                                break;
                            }
                        }
                    }

                    if (hasPending) {
                        setLoading(false);
                        Toast.makeText(requireContext(),
                                "Cannot delete item — it has a pending transaction",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // No pending transaction; proceed to delete
                    performItemDelete(item);
                });
                return;
            }

            // Query worked: scan results
            DataSnapshot snap = task.getResult();
            boolean hasPending = false;
            if (snap != null) {
                for (DataSnapshot s : snap.getChildren()) {
                    String status = s.child("status").getValue(String.class);
                    if (status != null && status.equalsIgnoreCase("pending")) {
                        hasPending = true;
                        break;
                    }
                }
            }

            if (hasPending) {
                setLoading(false);
                Toast.makeText(requireContext(),
                        "Cannot delete item — it has a pending transaction",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // No pending; delete item
            performItemDelete(item);
        });
    }

    private void performItemDelete(Item item) {
        Map<String, Object> removals = new HashMap<>();
        removals.put("/items/" + item.id, null);
        if (item.categoryId != null) {
            removals.put("/category-items/" + item.categoryId + "/" + item.id, null);
        }

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(removals)
                .addOnCompleteListener(delTask -> {
                    setLoading(false);
                    if (delTask.isSuccessful()) {
                        Toast.makeText(requireContext(), "Item deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        String msg = delTask.getException() != null
                                ? delTask.getException().getMessage()
                                : "";
                        Toast.makeText(requireContext(),
                                "Failed to delete: " + msg,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
