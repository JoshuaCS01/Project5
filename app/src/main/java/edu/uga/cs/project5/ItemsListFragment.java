package edu.uga.cs.project5;

import android.os.Bundle;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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

        FloatingActionButton fab = root.findViewById(R.id.fabAddItem);

        fab.setOnClickListener(v -> {
            AddItemFragment f = new AddItemFragment();

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, f)
                    .addToBackStack(null)
                    .commit();
        });


        TextView tvTitle = root.findViewById(R.id.tvItemsTitle);
        tvTitle.setText(categoryName != null ? categoryName : "Items");

        mappingRef = FirebaseDatabase.getInstance().getReference("category-items").child(categoryId);

        attachMappingListener();

        return root;
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
        if (mappingListener != null) mappingRef.removeEventListener(mappingListener);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        detachMappingListener();
    }
}
