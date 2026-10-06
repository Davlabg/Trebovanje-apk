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
import java.util.List;

/** Cuva listu artikala i uneta stanja u internu memoriju telefona. */
public final class Storage {

    private static final String FILE = "trebovanje.json";

    public static class Data {
        public String source = "";
        public List<Item> items = new ArrayList<>();
    }

    private Storage() {}

    public static Data load(Context ctx) {
        Data data = new Data();
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
        } catch (Exception e) {
            // Ostecen fajl: pocinjemo od prazne liste.
        }
        return data;
    }

    public static void save(Context ctx, Data data) {
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
