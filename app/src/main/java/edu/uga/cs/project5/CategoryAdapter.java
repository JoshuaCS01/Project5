package edu.uga.cs.project5;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {

    private final List<Category> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Category category);
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    public void setItems(List<Category> categories) {
        items.clear();
        if (categories != null) items.addAll(categories);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.category_item, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Category c = items.get(position);
        holder.name.setText(c.name != null ? c.name : "—");
        String meta = "by " + (c.createdBy != null ? shortUid(c.createdBy) : "unknown");
        if (c.createdAt > 0) {
            String date = DateFormat.getDateTimeInstance().format(new Date(c.createdAt));
            meta += " • " + date;
        }
        holder.meta.setText(meta);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(c);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView name, meta;
        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvCategoryName);
            meta = itemView.findViewById(R.id.tvCategoryMeta);
        }
    }

    private String shortUid(String uid) {
        if (uid == null) return "";
        return uid.length() > 8 ? uid.substring(0,8) : uid;
    }
}
