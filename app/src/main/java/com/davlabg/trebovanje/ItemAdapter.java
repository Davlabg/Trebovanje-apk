package com.davlabg.trebovanje;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ItemAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final Context ctx;
    private List<Item> all = new ArrayList<>();
    private final List<Item> shown = new ArrayList<>();
    private String query = "";
    private boolean onlyToOrder = false;

    public ItemAdapter(Context ctx) {
        this.ctx = ctx;
        this.inflater = LayoutInflater.from(ctx);
    }

    public void setItems(List<Item> items) {
        this.all = items;
        refresh();
    }

    public void setQuery(String q) {
        this.query = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
        refresh();
    }

    public void setOnlyToOrder(boolean only) {
        this.onlyToOrder = only;
        refresh();
    }

    public void refresh() {
        shown.clear();
        for (Item it : all) {
            if (!query.isEmpty() && !it.name.toLowerCase(Locale.getDefault()).contains(query)
                    && !it.code.contains(query)) continue;
            if (onlyToOrder && it.toOrder() <= 0) continue;
            shown.add(it);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return shown.size();
    }

    @Override
    public Item getItem(int position) {
        return shown.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView : inflater.inflate(R.layout.item_row, parent, false);
        Item it = getItem(position);
        TextView name = v.findViewById(R.id.name);
        TextView details = v.findViewById(R.id.details);
        TextView order = v.findViewById(R.id.order);

        name.setText(it.name);
        String unit = it.unit.isEmpty() ? "" : " " + it.unit;
        String text = ctx.getString(R.string.row_details,
                Item.format(it.usage) + unit,
                it.hasStock() ? Item.format(it.stock) + unit : "—");
        if (!it.code.isEmpty()) text = it.code + "   •   " + text;
        details.setText(text);
        if (!it.hasStock()) {
            order.setText("?");
            order.setTextColor(ctx.getColor(R.color.muted));
        } else {
            double o = it.toOrder();
            order.setText(Item.format(o));
            order.setTextColor(ctx.getColor(o > 0 ? R.color.order : R.color.ok));
        }
        return v;
    }
}
