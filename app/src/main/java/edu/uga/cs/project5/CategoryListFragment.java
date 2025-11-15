package edu.uga.cs.project5;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class CategoryListFragment extends Fragment {

    private RecyclerView rv;
    private CategoryAdapter adapter;
    private DatabaseReference categoriesRef;
    private ValueEventListener valueListener;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_category_list, container, false);
        rv = root.findViewById(R.id.rvCategories);
        adapter = new CategoryAdapter();
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        categoriesRef = FirebaseDatabase.getInstance().getReference("categories");

        root.findViewById(R.id.fabAddCategory).setOnClickListener(v -> showAddDialog());

        // click behavior for category rows (placeholder)
        adapter.setOnItemClickListener(category -> {
            Toast.makeText(requireContext(), "Clicked: " + category.name, Toast.LENGTH_SHORT).show();
            // TODO: navigate to items in this category
        });

        attachListener();
        return root;
    }

    private void attachListener() {
        // single ValueEventListener to load snapshot and sort alphabetically
        valueListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Category> list = new ArrayList<>();
                for (DataSnapshot cSnap : snapshot.getChildren()) {
                    Category c = cSnap.getValue(Category.class);
                    if (c == null) continue;
                    c.id = cSnap.getKey();
                    // createdAt may come as Long or Double depending on server; try to coerce
                    Object t = cSnap.child("createdAt").getValue();
                    if (t instanceof Long) c.createdAt = (Long) t;
                    else if (t instanceof Double) c.createdAt = ((Double) t).longValue();
                    list.add(c);
                }
                // sort alphabetically by name (case-insensitive)
                Collections.sort(list, Comparator.comparing(cat -> cat.name == null ? "" : cat.name.toLowerCase(Locale.ROOT)));
                adapter.setItems(list);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load categories: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        categoriesRef.addValueEventListener(valueListener);
    }

    private void detachListener() {
        if (valueListener != null) categoriesRef.removeEventListener(valueListener);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        detachListener();
    }

    private void showAddDialog() {
        final EditText input = new EditText(requireContext());
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(60) });
        input.setHint("Category name");
        new AlertDialog.Builder(requireContext())
                .setTitle("Add Category")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String raw = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(raw)) {
                        Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addCategoryIfNotExists(raw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addCategoryIfNotExists(String name) {
        // simple uniqueness check (case-insensitive). We load current snapshot once and check.
        categoriesRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(requireContext(), "Error checking existing categories", Toast.LENGTH_SHORT).show();
                return;
            }
            DataSnapshot snap = task.getResult();
            boolean exists = false;
            for (DataSnapshot cSnap : snap.getChildren()) {
                String existing = cSnap.child("name").getValue(String.class);
                if (existing != null && existing.equalsIgnoreCase(name)) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                Toast.makeText(requireContext(), "Category already exists", Toast.LENGTH_SHORT).show();
                return;
            }
            // create new
            String catId = categoriesRef.push().getKey();
            Map<String, Object> cat = new HashMap<>();
            cat.put("name", name);
            cat.put("createdBy", FirebaseAuth.getInstance().getUid());
            cat.put("createdAt", ServerValue.TIMESTAMP);
            categoriesRef.child(catId).setValue(cat).addOnCompleteListener(setTask -> {
                if (setTask.isSuccessful()) {
                    Toast.makeText(requireContext(), "Category added", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Failed to add category", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
