package com.davlabg.trebovanje;

import android.content.Context;
import android.graphics.Typeface;
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
    private OrderData data = new OrderData();
    private final List<Item> shown = new ArrayList<>();
    private String query = "";
    private boolean onlyToOrder = false;

    public ItemAdapter(Context ctx) {
        this.ctx = ctx;
        this.inflater = LayoutInflater.from(ctx);
    }

    public void setData(OrderData data) {
        this.data = data;
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
        for (Item it : data.items) {
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
        TextView warehouse = v.findViewById(R.id.warehouse);

        name.setText(it.name);
        String unit = it.unit.isEmpty() ? "" : " " + it.unit;
        String text = ctx.getString(R.string.row_details,
                Item.format(it.usage) + unit,
                it.hasStock() ? Item.format(it.stock) + unit : "—");
        if (!it.code.isEmpty()) text = it.code + "   •   " + text;
        details.setText(text);
        if (data.hasWarehouse()) {
            warehouse.setVisibility(View.VISIBLE);
            warehouse.setText(warehouseLine(ctx, data, it));
            boolean shortage = data.shortage(it);
            warehouse.setTextColor(ctx.getColor(shortage ? R.color.order : R.color.text_secondary));
            warehouse.setTypeface(null, shortage ? Typeface.BOLD : Typeface.NORMAL);
        } else {
            warehouse.setVisibility(View.GONE);
        }

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

    /** Npr. "Magacin: 380 kom (2 šifre)  ⚠ nema dovoljno" (samo status Regularno, u jedinici iz magacina). */
    public static String warehouseLine(Context ctx, OrderData data, Item it) {
        int count = data.articlesFor(it).size();
        if (count == 0) return ctx.getString(R.string.wh_not_linked);
        StringBuilder sb = new StringBuilder(ctx.getString(R.string.wh_line,
                ctx.getString(R.string.wh_amount, Item.format(data.availableFor(it)), data.unitFor(it))));
        if (count > 1) sb.append(ctx.getString(R.string.wh_codes, count));
        if (data.shortage(it)) sb.append(ctx.getString(R.string.wh_shortage));
        return sb.toString();
    }
}
