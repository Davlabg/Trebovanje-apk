package com.davlabg.trebovanje;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Cuva artikle, uneta stanja, stanje magacina i veze u internu memoriju telefona. */
public final class Storage {

    private static final String FILE = "trebovanje.json";

    private Storage() {}

    public static OrderData load(Context ctx) {
        OrderData data = new OrderData();
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return data;
        try (InputStream in = new FileInputStream(f)) {
            byte[] bytes = new byte[(int) f.length()];
            int off = 0;
            while (off < bytes.length) {
                int r = in.read(bytes, off, bytes.length - off);
                if (r < 0) break;
                off += r;
            }
            JSONObject root = new JSONObject(new String(bytes, 0, off, StandardCharsets.UTF_8));
            data.source = root.optString("source", "");
            JSONArray arr = root.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Double stock = o.has("stock") && !o.isNull("stock") ? o.getDouble("stock") : null;
                    data.items.add(new Item(o.optString("code", ""), o.getString("name"),
                            o.optString("unit", ""), o.getDouble("usage"), stock));
                }
            }
            data.warehouseSource = root.optString("warehouseSource", "");
            JSONArray wh = root.optJSONArray("warehouse");
            if (wh != null) {
                List<Warehouse.Article> articles = new ArrayList<>();
                for (int i = 0; i < wh.length(); i++) {
                    JSONObject o = wh.getJSONObject(i);
                    articles.add(new Warehouse.Article(o.getString("code"), o.optString("ext", ""),
                            o.getString("name"), o.optString("unit", ""), o.optDouble("available", 0)));
                }
                data.setWarehouse(articles);
            }
            JSONObject links = root.optJSONObject("links");
            if (links != null) {
                Iterator<String> keys = links.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    data.links.put(k, links.getString(k));
                }
            }
        } catch (Exception e) {
            // Ostecen fajl: pocinjemo od prazne liste.
        }
        // Verzija 1.0 je cuvala prodaju kao negativan broj, pa je porudzbina uvek bila 0.
        List<Item> normalized = Item.normalizeUsage(data.items);
        if (normalized != null) {
            data.items = normalized;
            save(ctx, data);
        }
        return data;
    }

    public static void save(Context ctx, OrderData data) {
        try {
            JSONObject root = new JSONObject();
            root.put("source", data.source);
            JSONArray arr = new JSONArray();
            for (Item it : data.items) {
                JSONObject o = new JSONObject();
                o.put("code", it.code);
                o.put("name", it.name);
                o.put("unit", it.unit);
                o.put("usage", it.usage);
                if (it.stock != null) o.put("stock", it.stock.doubleValue());
                arr.put(o);
            }
            root.put("items", arr);
            root.put("warehouseSource", data.warehouseSource);
            JSONArray wh = new JSONArray();
            for (Warehouse.Article a : data.warehouse) {
                JSONObject o = new JSONObject();
                o.put("code", a.code);
                o.put("ext", a.extCode);
                o.put("name", a.name);
                o.put("unit", a.unit);
                o.put("available", a.available);
                wh.put(o);
            }
            root.put("warehouse", wh);
            JSONObject links = new JSONObject();
            for (Map.Entry<String, String> e : data.links.entrySet()) links.put(e.getKey(), e.getValue());
            root.put("links", links);
            File tmp = new File(ctx.getFilesDir(), FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            File target = new File(ctx.getFilesDir(), FILE);
            if (!tmp.renameTo(target)) {
                target.delete();
                tmp.renameTo(target);
            }
        } catch (Exception ignored) {
        }
    }
}
