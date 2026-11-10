package com.davlabg.trebovanje;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Količina u TP (transportnom pakovanju): koliko jedinica iz Usage izveštaja (kg, kom…) ima u jednom TP.
 * Ugrađena tabela je u assets/kolicina_u_tp.tsv; korisnik može uvesti novu Excel tabelu
 * (kolone "Ime proizvoda", "Kolicina u TP", "Sifra artikla") i ona tada zamenjuje ugrađenu.
 */
public final class TpTable {

    public static class Entry {
        /** Jedna ili više šifara (u tabeli zapisane kao "100009172 / 100029263"). */
        public final List<String> codes;
        public final String name;
        public final double qty;

        public Entry(List<String> codes, String name, double qty) {
            this.codes = codes;
            this.name = name;
            this.qty = qty;
        }

        public String codeText() {
            return String.join(" / ", codes);
        }
    }

    private static final String ASSET = "kolicina_u_tp.tsv";

    private final List<Entry> entries;
    private final Map<String, Entry> byCode = new HashMap<>();
    /** Normalizovan naziv -> količina; null vrednost znači da isti naziv ima različite količine. */
    private final Map<String, Double> byName = new HashMap<>();

    public TpTable(List<Entry> entries) {
        this.entries = entries;
        for (Entry e : entries) {
            for (String c : e.codes) byCode.put(c, e);
            String n = Warehouse.normalize(e.name);
            if (n.isEmpty()) continue;
            if (!byName.containsKey(n)) byName.put(n, e.qty);
            else {
                Double old = byName.get(n);
                if (old != null && Math.abs(old - e.qty) > 1e-9) byName.put(n, null);
            }
        }
    }

    public List<Entry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    /** Ugrađena tabela iz aplikacije. */
    public static List<Entry> loadBuiltIn(Context ctx) {
        List<Entry> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                ctx.getAssets().open(ASSET), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length < 3) continue;
                Double q = Item.parse(p[2]);
                if (q == null || q <= 0) continue;
                out.add(new Entry(splitCodes(p[0]), p[1].trim(), q));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static List<String> splitCodes(String text) {
        List<String> codes = new ArrayList<>();
        if (text == null) return codes;
        for (String c : text.split("[/,;]")) {
            String t = cleanCode(c);
            if (!t.isEmpty()) codes.add(t);
        }
        return codes;
    }

    /** "100008167.0" -> "100008167". */
    static String cleanCode(String c) {
        String t = c == null ? "" : c.trim();
        if (t.matches("\\d+\\.0+")) t = t.substring(0, t.indexOf('.'));
        return t;
    }

    /**
     * Količina u TP za artikal iz Usage izveštaja, ili null ako nije pronađena.
     * Redosled: šifra artikla, isti naziv, pa naziv koji je početak drugog naziva (samo ako je jednoznačno).
     */
    public Double find(Item it) {
        if (!it.code.isEmpty()) {
            Entry e = byCode.get(cleanCode(it.code));
            if (e != null) return e.qty;
        }
        String n = Warehouse.normalize(it.name);
        if (n.isEmpty()) return null;
        if (byName.containsKey(n)) return byName.get(n);
        if (n.length() < 6) return null;
        Double found = null;
        boolean any = false;
        for (Map.Entry<String, Double> e : byName.entrySet()) {
            String k = e.getKey();
            if (k.length() < 6) continue;
            if (k.startsWith(n) || n.startsWith(k)) {
                if (e.getValue() == null) return null;
                if (any && Math.abs(found - e.getValue()) > 1e-9) return null;
                found = e.getValue();
                any = true;
            }
        }
        return found;
    }

    /** Postavlja TP za sve artikle koje korisnik nije ručno menjao. Vraća broj artikala sa TP. */
    public int apply(List<Item> items) {
        int n = 0;
        for (Item it : items) {
            if (!it.tpManual) it.tp = find(it);
            if (it.hasTp()) n++;
        }
        return n;
    }

    /** Vraća null ako list nema zaglavlje tabele TP (kolona sa "TP" i kolona sa nazivom). */
    public static List<Entry> parse(List<Map<Integer, String>> rows) {
        int nameCol = -1, qtyCol = -1, codeCol = -1;
        boolean headerFound = false;
        List<Entry> out = new ArrayList<>();
        for (Map<Integer, String> cells : rows) {
            if (!headerFound) {
                int n = -1, q = -1, c = -1;
                for (Map.Entry<Integer, String> e : cells.entrySet()) {
                    String h = XlsxReader.header(e.getValue()).replace(" ", "");
                    if (h.contains("tp") && (h.contains("kolicin") || h.contains("količin") || h.equals("tp")) && q < 0) q = e.getKey();
                    else if ((h.startsWith("ime") || h.startsWith("naziv") || h.equals("item") || h.equals("artikal")) && n < 0) n = e.getKey();
                    else if ((h.startsWith("sifr") || h.startsWith("šifr") || h.equals("itemcode") || h.equals("code")) && c < 0) c = e.getKey();
                }
                if (n >= 0 && q >= 0) {
                    nameCol = n;
                    qtyCol = q;
                    codeCol = c;
                    headerFound = true;
                }
                continue;
            }
            String name = XlsxReader.trim(cells.get(nameCol));
            Double qty = Item.parse(cells.get(qtyCol));
            if (name.isEmpty() || qty == null || qty <= 0) continue;
            List<String> codes = codeCol >= 0 ? splitCodes(cells.get(codeCol)) : new ArrayList<>();
            out.add(new Entry(codes, name, qty));
        }
        return headerFound ? out : null;
    }
}
