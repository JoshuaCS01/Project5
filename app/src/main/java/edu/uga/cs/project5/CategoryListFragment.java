package edu.uga.cs.project5;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
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
import com.google.firebase.auth.FirebaseUser;
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

        adapter.setOnItemSettingsClickListener((category, anchor) -> {
            android.widget.PopupMenu pm = new android.widget.PopupMenu(requireContext(), anchor);
            pm.getMenu().add("Edit name");
            pm.getMenu().add("Delete category");
            pm.setOnMenuItemClickListener(item -> {
                String title = item.getTitle().toString();
                if (title.equals("Edit name")) {
                    showEditDialog(category);
                    return true;
                } else if (title.equals("Delete category")) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Delete category")
                            .setMessage("Delete category '" + category.name + "'? Category must be empty.")
                            .setPositiveButton("Delete", (d, w) -> deleteCategoryIfEmpty(category.id))
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                }
                return false;
            });
            pm.show();
        });


        categoriesRef = FirebaseDatabase.getInstance().getReference("categories");

        root.findViewById(R.id.fabAddCategory).setOnClickListener(v -> showAddDialog());

        adapter.setOnItemClickListener(category -> {
            ItemsListFragment f = ItemsListFragment.create(category.id, category.name);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, f)
                    .addToBackStack(null)
                    .commit();
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
                list.sort(Comparator.comparing(cat -> cat.name == null ? "" : cat.name.toLowerCase(Locale.ROOT)));
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

    // show a dialog to edit the category name; calls updateCategoryIfEmpty(...)
    private void showEditDialog(final Category category) {
        final EditText input = new EditText(requireContext());
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(60) });
        input.setText(category.name != null ? category.name : "");
        new AlertDialog.Builder(requireContext())
                .setTitle("Edit Category")
                .setView(input)
                .setPositiveButton("Update", (dialog, which) -> {
                    String raw = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(raw)) {
                        Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Calls your existing method that checks emptiness and updates
                    updateCategoryIfEmpty(category.id, raw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    // --- Empty check interface ---
    private interface EmptyCheckCallback {
        void onResult(boolean isEmpty);
    }

    // --- Check if category is empty ---
    private void isCategoryEmpty(String catId, EmptyCheckCallback cb) {
        DatabaseReference mappingRef = FirebaseDatabase.getInstance()
                .getReference("category-items")
                .child(catId);

        mappingRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.d("CategoryCheck", "Failed to get category-items mapping");
                // conservative: treat as not empty
                cb.onResult(false);
                return;
            }

            DataSnapshot snap = task.getResult();
            boolean empty = snap == null || !snap.exists() || !snap.hasChildren();
            Log.d("CategoryCheck", "Category " + catId + " empty: " + empty);
            cb.onResult(empty);
        });
    }


    private void updateCategoryIfEmpty(String catId, String newName) {
        DatabaseReference catRef = FirebaseDatabase.getInstance()
                .getReference("categories").child(catId);

        catRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            DataSnapshot snap = task.getResult();
            if (snap == null || !snap.exists()) return;

            String owner = snap.child("createdBy").getValue(String.class);
            if (!FirebaseAuth.getInstance().getUid().equals(owner)) return;

            isCategoryEmpty(catId, isEmpty -> {
                if (!isEmpty) {
                    Toast.makeText(requireContext(), "Category not empty — cannot rename.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("name", newName);
                updates.put("createdAt", ServerValue.TIMESTAMP);

                catRef.updateChildren(updates).addOnCompleteListener(uTask -> {
                    if (uTask.isSuccessful()) {
                        Toast.makeText(requireContext(), "Category updated", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Failed to update category", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    private void deleteCategoryIfEmpty(String catId) {
        DatabaseReference catRef = FirebaseDatabase.getInstance()
                .getReference("categories").child(catId);

        catRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            DataSnapshot snap = task.getResult();
            if (snap == null || !snap.exists()) return;

            String owner = snap.child("createdBy").getValue(String.class);
            if (!FirebaseAuth.getInstance().getUid().equals(owner)) return;

            isCategoryEmpty(catId, isEmpty -> {
                if (!isEmpty) {
                    Toast.makeText(requireContext(), "Category must be empty to delete", Toast.LENGTH_SHORT).show();
                    Log.d("CategoryDelete", "Current user UID: " + FirebaseAuth.getInstance().getUid());

                    return;
                }

                // Remove category
                catRef.removeValue().addOnCompleteListener(rTask -> {
                    if (rTask.isSuccessful()) {
                        Toast.makeText(requireContext(), "Category deleted", Toast.LENGTH_SHORT).show();

                        // Optionally, remove empty mapping in /category-items
                        DatabaseReference mappingRef = FirebaseDatabase.getInstance()
                                .getReference("category-items").child(catId);
                        mappingRef.removeValue();
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete category", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }


    private void addCategoryIfNotExists(String name) {
        if (name == null) return;
        final String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (categoriesRef == null) {
            categoriesRef = FirebaseDatabase.getInstance().getReference("categories");
        }

        // 1) Check duplicates (case-insensitive)
        categoriesRef.get().addOnCompleteListener(checkTask -> {
            if (!checkTask.isSuccessful()) {
                Toast.makeText(requireContext(), "Error checking existing categories", Toast.LENGTH_SHORT).show();
                return;
            }

            DataSnapshot snap = checkTask.getResult();
            boolean exists = false;
            if (snap != null) {
                for (DataSnapshot cSnap : snap.getChildren()) {
                    String existing = cSnap.child("name").getValue(String.class);
                    if (existing != null && existing.equalsIgnoreCase(trimmed)) {
                        exists = true;
                        break;
                    }
                }
            }

            if (exists) {
                Toast.makeText(requireContext(), "Category already exists", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2) Resolve current user info (uid + friendly name)
            final String uid = FirebaseAuth.getInstance().getUid();
            if (uid == null) {
                Toast.makeText(requireContext(), "You must be signed in to create a category", Toast.LENGTH_SHORT).show();
                return;
            }

            // Try FirebaseAuth profile first (fast, no extra DB read)
            String profileName = null;
            if (FirebaseAuth.getInstance().getCurrentUser() != null
                    && FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null) {
                profileName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName().trim();
            }

            if (profileName != null && !profileName.isEmpty()) {
                // we have display name already — write category now
                writeNewCategory(trimmed, uid, profileName);
            } else {
                // fallback: read /users/{uid} for displayName or username
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
                userRef.get().addOnCompleteListener(userTask -> {
                    String displayName = null;
                    if (userTask.isSuccessful()) {
                        DataSnapshot userSnap = userTask.getResult();
                        if (userSnap != null && userSnap.exists()) {
                            displayName = userSnap.child("displayName").getValue(String.class);
                            if (displayName == null) {
                                displayName = userSnap.child("username").getValue(String.class);
                            }
                            if (displayName != null) displayName = displayName.trim();
                        }
                    }
                    // final fallback to short uid if still null
                    if (displayName == null || displayName.isEmpty()) {
                        displayName = uid.length() > 8 ? uid.substring(0, 8) : uid;
                    }
                    writeNewCategory(trimmed, uid, displayName);
                });
            }
        });
    }

    private void writeNewCategory(String name, String uid, String displayName) {
        String catId = categoriesRef.push().getKey();
        if (catId == null) {
            Toast.makeText(requireContext(), "Failed to create category id", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> cat = new HashMap<>();
        cat.put("name", name);
        cat.put("createdBy", uid);               // keep uid for ownership checks
        cat.put("createdByName", displayName);   // human friendly
        cat.put("createdAt", ServerValue.TIMESTAMP);

        categoriesRef.child(catId).setValue(cat).addOnCompleteListener(setTask -> {
            if (setTask.isSuccessful()) {
                Toast.makeText(requireContext(), "Category added", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Failed to add category", Toast.LENGTH_SHORT).show();
            }
        });
    }

}
