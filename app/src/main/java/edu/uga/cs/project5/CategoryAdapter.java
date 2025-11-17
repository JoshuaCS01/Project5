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
    private OnItemClickListener clickListener;
    private OnItemSettingsClickListener settingsListener;

    public interface OnItemClickListener {
        void onItemClick(Category category);
    }

    public interface OnItemSettingsClickListener {
        void onItemSettingsClick(Category category, View anchorView);
    }

    public void setOnItemClickListener(OnItemClickListener l) { this.clickListener = l; }
    public void setOnItemSettingsClickListener(OnItemSettingsClickListener l) { this.settingsListener = l; }

    public void setItems(List<Category> categories) {
        items.clear();
        if (categories != null) items.addAll(categories);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
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
            if (clickListener != null) clickListener.onItemClick(c);
        });

        holder.btnSettings.setOnClickListener(v -> {
            if (settingsListener != null) settingsListener.onItemSettingsClick(c, v);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView name, meta;
        View btnSettings;
        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvCategoryName);
            meta = itemView.findViewById(R.id.tvCategoryMeta);
            btnSettings = itemView.findViewById(R.id.btnSettings);
        }
    }

    private String shortUid(String uid) {
        if (uid == null) return "";
        return uid.length() > 8 ? uid.substring(0,8) : uid;
    }
}
