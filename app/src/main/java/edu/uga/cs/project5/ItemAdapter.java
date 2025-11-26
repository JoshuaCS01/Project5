package edu.uga.cs.project5;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    public interface OnItemActionListener {
        void onItemClicked(Item item); // single tap -> show actions
    }

    private OnItemActionListener actionListener;

    public void setOnItemActionListener(OnItemActionListener l) {
        this.actionListener = l;
    }

    public interface OnItemBuyListener {
        void onBuyClicked(Item item);
    }

    private OnItemBuyListener buyListener;
    public void setOnItemBuyListener(OnItemBuyListener l) {
        this.buyListener = l;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {

        Item item = items.get(position);

        // Title
        holder.title.setText(item.title != null ? item.title : "—");

        // Description (read-only)
        if (item.description != null && !item.description.trim().isEmpty()) {
            holder.description.setText(item.description.trim());
        } else {
            holder.description.setText(""); // or "No description"
        }

        // Buy button visibility
        String currentUid = FirebaseAuth.getInstance().getUid();
        boolean isOwner = currentUid != null && currentUid.equals(item.authorId);
        boolean isAvailable = item.available == null ? true : item.available;

        // Price
        String price;
        if (item.isFree != null && item.isFree) {
            price = "FREE";
        } else if (item.priceCents != null) {
            price = String.format(Locale.getDefault(), "$%.2f", item.priceCents / 100.0);
        } else {
            price = "—";
        }

        // Date/time
        long tempTime = item.createdAt != null ? item.createdAt : 0L;
        String date = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                .format(new Date(tempTime));
        String meta = price + " • " + date;
        holder.meta.setText(meta);

        // Seller username
        String seller = item.createdByName != null ? item.createdByName : "Unknown";
        holder.seller.setText("Sold by: " + seller);

        // Category
        if (item.category != null) {
            holder.category.setText("Category: " + item.category);
        } else {
            holder.category.setText("Category: —");
        }

        // Item click
        holder.itemView.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onItemClicked(item);
        });

        // Buy / Accept button
        if (holder.btnBuy != null) {
            if (!isOwner && isAvailable) {
                holder.btnBuy.setVisibility(View.VISIBLE);

                if (item.isFree != null && item.isFree)
                    holder.btnBuy.setText("Accept");
                else
                    holder.btnBuy.setText("Buy");

                holder.btnBuy.setOnClickListener(v -> {
                    if (buyListener != null) buyListener.onBuyClicked(item);
                });

            } else {
                holder.btnBuy.setVisibility(View.GONE);
                holder.btnBuy.setOnClickListener(null);
            }
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView title, description, meta, seller, category;
        Button btnBuy;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvItemTitle);
            description = itemView.findViewById(R.id.tvItemDescription);
            meta = itemView.findViewById(R.id.tvItemMeta);
            seller = itemView.findViewById(R.id.seller);
            category = itemView.findViewById(R.id.category_name);
            btnBuy = itemView.findViewById(R.id.btnBuy);
        }
    }
}
