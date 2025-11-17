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

    private EditText etTitle, etDescription, etPrice;
    private CheckBox cbFree;
    private Button btnSelectCats, btnAddCategory, btnPost;
    private ProgressBar progress;
    private TextView tvSelectedCats;

    // category lists for selection
    private List<String> catIds = new ArrayList<>();
    private List<String> catNames = new ArrayList<>();
    private boolean[] checkedCats;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_item, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
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

        btnSelectCats.setOnClickListener(x -> showCategoryMultiSelect());
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
                checkedCats = new boolean[0];
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
            checkedCats = new boolean[catIds.size()];
            updateSelectedCatsText();
        });
    }

    private void showCategoryMultiSelect() {
        if (catIds.isEmpty()) {
            Toast.makeText(requireContext(), "No categories available", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = catNames.toArray(new String[0]);
        boolean[] checked = checkedCats.clone();
        new AlertDialog.Builder(requireContext())
                .setTitle("Select categories (multiple)")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("OK", (d,w) -> {
                    checkedCats = checked;
                    updateSelectedCatsText();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSelectedCatsText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < catNames.size(); i++) {
            if (i < checkedCats.length && checkedCats[i]) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(catNames.get(i));
            }
        }
        tvSelectedCats.setText(sb.length() > 0 ? sb.toString() : "No categories selected");
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
                    // create category and reload categories, mark it selected
                    DatabaseReference categoriesRef = FirebaseDatabase.getInstance().getReference("categories");
                    String newId = categoriesRef.push().getKey();
                    if (newId == null) {
                        Toast.makeText(requireContext(), "Failed to create category id", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String,Object> cat = new HashMap<>();
                    cat.put("name", raw);
                    cat.put("createdBy", FirebaseAuth.getInstance().getUid());
                    cat.put("createdAt", ServerValue.TIMESTAMP);
                    categoriesRef.child(newId).setValue(cat).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(requireContext(), "Category created", Toast.LENGTH_SHORT).show();
                            // reload categories and automatically select the new one
                            loadCategories();
                            // mark newly created as selected after small delay (we'll find it by name)
                            categoriesRef.child(newId).get().addOnCompleteListener(g -> {
                                int idx = catIds.indexOf(newId);
                                if (idx >= 0) {
                                    if (checkedCats == null || checkedCats.length < catIds.size()) {
                                        boolean[] n = new boolean[catIds.size()];
                                        System.arraycopy(checkedCats, 0, n, 0, Math.min(checkedCats.length, n.length));
                                        checkedCats = n;
                                    }
                                    checkedCats[idx] = true;
                                    updateSelectedCatsText();
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

        if (TextUtils.isEmpty(title)) { Toast.makeText(requireContext(), "Title required", Toast.LENGTH_SHORT).show(); return; }
        if (!isFree && TextUtils.isEmpty(priceText)) { Toast.makeText(requireContext(), "Price required or mark free", Toast.LENGTH_SHORT).show(); return; }

        // collect selected category ids
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < catIds.size(); i++) {
            if (i < checkedCats.length && checkedCats[i]) selected.add(catIds.get(i));
        }
        if (selected.isEmpty()) { Toast.makeText(requireContext(), "Select at least one category", Toast.LENGTH_SHORT).show(); return; }

        long priceCents = 0;
        if (!isFree) {
            try { double p = Double.parseDouble(priceText); priceCents = Math.round(p * 100.0); }
            catch (Exception e) { Toast.makeText(requireContext(), "Invalid price", Toast.LENGTH_SHORT).show(); return; }
        }

        setLoading(true);

        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        String itemId = root.child("items").push().getKey();
        if (itemId == null) { setLoading(false); Toast.makeText(requireContext(), "Failed to generate id", Toast.LENGTH_SHORT).show(); return; }

        Map<String, Object> item = new HashMap<>();
        item.put("title", title);
        if (!TextUtils.isEmpty(description)) item.put("description", description);
        item.put("isFree", isFree);
        if (!isFree) item.put("priceCents", priceCents);
        item.put("authorId", FirebaseAuth.getInstance().getUid());
        item.put("createdAt", ServerValue.TIMESTAMP);
        item.put("available", true);
        // For backwards-compat, keep single categoryId set to first selection
        item.put("categoryId", selected.get(0));

        Map<String, Object> updates = new HashMap<>();
        updates.put("/items/" + itemId, item);
        for (String catId : selected) {
            updates.put("/category-items/" + catId + "/" + itemId, true);
        }

        root.updateChildren(updates).addOnCompleteListener(task -> {
            setLoading(false);
            if (task.isSuccessful()) {
                Toast.makeText(requireContext(), "Item posted", Toast.LENGTH_SHORT).show();
                // go back
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                Toast.makeText(requireContext(), "Failed to post item: " + (task.getException()!=null ? task.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
            }
        });
    }
}
