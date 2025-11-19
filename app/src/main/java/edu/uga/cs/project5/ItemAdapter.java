package edu.uga.cs.project5;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.BreakIterator;
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


        //Sets the title
        Item item = items.get(position);
        holder.title.setText(item.title != null ? item.title : "—");

        //Sets the price
        String price = null;
        if (item.isFree != null && item.isFree) {
            price = "FREE";
        }
        if (item.priceCents != null) {
            price = String.format("$%.2f", item.priceCents / 100.0);
        }

        //Sets the date and time
        long tempTime = item.createdAt;
        String date = null;
        date =  DateFormat.getDateTimeInstance().format(new Date(tempTime));
        date = price + " • " + date;
        holder.meta.setText(date);

        //Sets the seller username
        String seller = item.createdByName;
        seller = "Sold by: " + seller;
        holder.seller.setText(seller);

    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView title, meta, seller;
        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvItemTitle);
            meta = itemView.findViewById(R.id.tvItemMeta);
            seller = itemView.findViewById(R.id.seller);
        }
    }
}
