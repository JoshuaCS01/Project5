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

public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.Holder> {

    private final List<Item> items = new ArrayList<>();

    public void setItems(List<Item> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new Holder(v);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Item it = items.get(position);
        holder.title.setText(it.title != null ? it.title : "—");
        String meta = it.isFree != null && it.isFree ? "FREE" : (it.priceCents != null ? String.format("$%.2f", it.priceCents/100.0) : "");
        long ts = it.createdAt != null ? it.createdAt : 0L;
        String date = ts > 0 ? DateFormat.getDateTimeInstance().format(new Date(ts)) : "";
        holder.meta.setText(meta + " • " + date);
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView title, meta;
        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvItemTitle);
            meta = itemView.findViewById(R.id.tvItemMeta);
        }
    }
}
