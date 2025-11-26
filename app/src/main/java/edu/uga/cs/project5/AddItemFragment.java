package edu.uga.cs.project5;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddItemFragment extends Fragment {

    private static final String ARG_CATEGORY_ID = "arg_category_id";
    private static final String ARG_CATEGORY_NAME = "arg_category_name";

    private EditText etTitle, etDescription, etPrice;
    private CheckBox cbFree;
    private Button btnSelectCats, btnAddCategory, btnPost;
    private ProgressBar progress;
    private TextView tvSelectedCats;

    private List<String> catIds = new ArrayList<>();
    private List<String> catNames = new ArrayList<>();
    private int selectedCatIndex = -1; // -1 = none selected

    // If fragment opened with a preselected category
    private String initialCategoryId = null;
    private String initialCategoryName = null;

    public AddItemFragment() { /* required empty ctor */ }

    /**
     * Call this to open AddItemFragment preselected to a category.
     * Pass null for either parameter if you don't have it.
     */
    public static AddItemFragment newInstance(@Nullable String categoryId, @Nullable String categoryName) {
        AddItemFragment f = new AddItemFragment();
        Bundle args = new Bundle();
        if (categoryId != null) args.putString(ARG_CATEGORY_ID, categoryId);
        if (categoryName != null) args.putString(ARG_CATEGORY_NAME, categoryName);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // read args here so they exist early
        Bundle a = getArguments();
        if (a != null) {
            initialCategoryId = a.getString(ARG_CATEGORY_ID, null);
            initialCategoryName = a.getString(ARG_CATEGORY_NAME, null);
        }
        return inflater.inflate(R.layout.fragment_add_item, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        etTitle = v.findViewById(R.id.etItemTitle);
        etDescription = v.findViewById(R.id.etItemDescription);
        etPrice = v.findViewById(R.id.etItemPrice);
        cbFree = v.findViewById(R.id.cbFree);
        btnSelectCats = v.findViewById(R.id.btnSelectCats);
        btnAddCategory = v.findViewById(R.id.btnAddCategory);
        btnPost = v.findViewById(R.id.btnPostItem);
        tvSelectedCats = v.findViewById(R.id.tvSelectedCats);
        progress = v.findViewById(R.id.progress);

        cbFree.setOnCheckedChangeListener((b, isChecked) -> {
            etPrice.setEnabled(!isChecked);
            if (isChecked) etPrice.setText("");
        });

        btnSelectCats.setOnClickListener(x -> showCategorySingleSelect());
        btnAddCategory.setOnClickListener(x -> showAddCategoryDialog());
        btnPost.setOnClickListener(x -> submitItem());
        loadCategories();
    }

    private void setLoading(boolean l) {
        progress.setVisibility(l ? View.VISIBLE : View.GONE);
        btnPost.setEnabled(!l);
    }

    private void loadCategories() {
        DatabaseReference categoriesRef = FirebaseDatabase.getInstance().getReference("categories");
        categoriesRef.get().addOnCompleteListener(task -> {
            catIds.clear();
            catNames.clear();
            if (!task.isSuccessful()) {
                Toast.makeText(requireContext(), "Failed to load categories", Toast.LENGTH_SHORT).show();
                selectedCatIndex = -1;
                updateSelectedCatsText();
                return;
            }
            DataSnapshot snap = task.getResult();
            for (DataSnapshot c : snap.getChildren()) {
                String id = c.getKey();
                String name = c.child("name").getValue(String.class);
                if (id != null && name != null) {
                    catIds.add(id);
                    catNames.add(name);
                }
            }

            // If the fragment was opened with an initialCategoryId, select it automatically (if found)
            if (initialCategoryId != null) {
                int idx = catIds.indexOf(initialCategoryId);
                if (idx >= 0) {
                    selectedCatIndex = idx;
                } else {
                    // fallback: if category name was provided, try to match by name
                    if (initialCategoryName != null) {
                        int idxByName = catNames.indexOf(initialCategoryName);
                        selectedCatIndex = idxByName >= 0 ? idxByName : -1;
                    } else {
                        selectedCatIndex = -1;
                    }
                }
            } else {
                // default behavior: no preselection
                selectedCatIndex = -1;
            }

            // Update visible UI
            updateSelectedCatsText();

            // If we had an initial category, hide the select button so user isn't prompted again.
            // If you'd prefer the user be allowed to change it, comment out the next lines.
            if (initialCategoryId != null) {
                btnSelectCats.setVisibility(View.GONE);
                // optionally disable add-category if you don't want them to create new categories here:
                // btnAddCategory.setVisibility(View.GONE);
            } else {
                btnSelectCats.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showCategorySingleSelect() {
        if (catIds.isEmpty()) {
            Toast.makeText(requireContext(), "No categories available", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = catNames.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle("Select a category")
                .setSingleChoiceItems(names, selectedCatIndex, (dialog, which) -> {
                    selectedCatIndex = which;
                })
                .setPositiveButton("OK", (d, w) -> updateSelectedCatsText())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSelectedCatsText() {
        if (selectedCatIndex >= 0 && selectedCatIndex < catNames.size()) {
            tvSelectedCats.setText(catNames.get(selectedCatIndex));
        } else if (initialCategoryName != null && (selectedCatIndex < 0 || catNames.isEmpty())) {
            tvSelectedCats.setText(initialCategoryName);
        } else {
            tvSelectedCats.setText("No categories selected");
        }
    }

    private void showAddCategoryDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint("Category name");
        new AlertDialog.Builder(requireContext())
                .setTitle("Add new category")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String raw = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(raw)) {
                        Toast.makeText(requireContext(), "Name required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DatabaseReference categoriesRef = FirebaseDatabase.getInstance().getReference("categories");
                    String newId = categoriesRef.push().getKey();
                    if (newId == null) {
                        Toast.makeText(requireContext(), "Failed to create category id", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> cat = new HashMap<>();
                    cat.put("name", raw);
                    cat.put("createdBy", FirebaseAuth.getInstance().getUid());
                    cat.put("createdAt", ServerValue.TIMESTAMP);
                    categoriesRef.child(newId).setValue(cat).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(requireContext(), "Category created", Toast.LENGTH_SHORT).show();
                            // reload categories and automatically select the new one
                            categoriesRef.get().addOnCompleteListener(g -> {
                                if (g.isSuccessful()) {
                                    catIds.clear();
                                    catNames.clear();
                                    DataSnapshot snap = g.getResult();
                                    for (DataSnapshot c : snap.getChildren()) {
                                        String id = c.getKey();
                                        String name = c.child("name").getValue(String.class);
                                        if (id != null && name != null) {
                                            catIds.add(id);
                                            catNames.add(name);
                                        }
                                    }
                                    int idx = catIds.indexOf(newId);
                                    selectedCatIndex = idx >= 0 ? idx : -1;
                                    // since user just added a category, clear any initialCategoryId
                                    initialCategoryId = null;
                                    initialCategoryName = null;
                                    btnSelectCats.setVisibility(View.VISIBLE);
                                    updateSelectedCatsText();
                                } else {
                                    loadCategories();
                                }
                            });

                        } else {
                            Toast.makeText(requireContext(), "Failed to create category", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitItem() {
        final String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        final String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        final boolean isFree = cbFree.isChecked();
        final String priceText = etPrice.getText() != null ? etPrice.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(requireContext(), "Title required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isFree && TextUtils.isEmpty(priceText)) {
            Toast.makeText(requireContext(), "Price required or mark free", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCatIndex < 0 || selectedCatIndex >= catIds.size()) {
            Toast.makeText(requireContext(), "Select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        String selectedCatId = catIds.get(selectedCatIndex);

        long priceCents = 0;
        if (!isFree) {
            try {
                double p = Double.parseDouble(priceText);
                if (p < 0) {
                    Toast.makeText(requireContext(), "Price must be non-negative", Toast.LENGTH_SHORT).show();
                    return;
                }
                priceCents = Math.round(p * 100.0);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Invalid price", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        final String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(requireContext(), "You must be signed in to post items", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
        long finalPriceCents = priceCents;
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

            DatabaseReference root = FirebaseDatabase.getInstance().getReference();
            String itemId = root.child("items").push().getKey();
            if (itemId == null) {
                setLoading(false);
                Toast.makeText(requireContext(), "Failed to generate id", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> item = new HashMap<>();
            if (displayName != null) item.put("createdByName", displayName);
            item.put("title", title);
            if (!TextUtils.isEmpty(description)) item.put("description", description);
            item.put("isFree", isFree);
            if (!isFree) item.put("priceCents", finalPriceCents);
            item.put("authorId", uid);
            item.put("createdAt", ServerValue.TIMESTAMP);
            item.put("available", true);
            item.put("categoryId", selectedCatId);

            Map<String, Object> updates = new HashMap<>();
            updates.put("/items/" + itemId, item);
            updates.put("/category-items/" + selectedCatId + "/" + itemId, true);

            root.updateChildren(updates).addOnCompleteListener(task -> {
                setLoading(false);
                if (task.isSuccessful()) {
                    Toast.makeText(requireContext(), "Item posted", Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager().popBackStack();
                } else {
                    String msg = task.getException() != null ? task.getException().getMessage() : "";
                    Toast.makeText(requireContext(), "Failed to post item: " + msg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
