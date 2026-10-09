package com.davlabg.trebovanje;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stanje robe u eksternom magacinu (izvestaj "ArticlesOnStock").
 * Jedan artikal moze imati vise redova (lotovi, rokovi); sabiraju se samo redovi sa statusom "Regularno",
 * a "Carina" i "Blokirano" se ne stavljaju na stanje.
 * Sifre magacina se ne poklapaju sa siframa iz izvestaja o potrosnji, pa se artikli povezuju po nazivu.
 */
public final class Warehouse {

    public static class Article {
        public final String code;
        public final String extCode;
        public final String name;
        public final String unit;
        /** Kolicina sa statusom "Regularno", u jedinici iz magacina (komad). Carina i Blokirano se ne racunaju. */
        public double available;

        public Article(String code, String extCode, String name, String unit, double available) {
            this.code = code;
            this.extCode = extCode;
            this.name = name;
            this.unit = unit;
            this.available = available;
        }

        public String unitLabel() {
            String u = unitKind(unit);
            if (u.equals("kom")) return "kom";
            if (u.equals("kg")) return "kg";
            if (u.equals("l")) return "l";
            return unit;
        }
    }

    private Warehouse() {}

    /** Svodi nazive jedinica iz oba izvestaja na "kom", "kg", "l" (ili vraca original malim slovima). */
    public static String unitKind(String unit) {
        String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT).replace(".", "");
        switch (u) {
            case "komad": case "kom": case "kos": case "each": case "ea": case "pcs": case "pc": case "piece":
                return "kom";
            case "kilogram": case "kilo": case "kg": case "kilogrami":
                return "kg";
            case "litar": case "litre": case "liter": case "l": case "lit":
                return "l";
            default:
                return u;
        }
    }

    /** Da li su jedinica iz izvestaja o potrosnji i jedinica iz magacina iste (samo tada se kolicine porede). */
    public static boolean sameUnit(String usageUnit, String warehouseUnit) {
        String a = unitKind(usageUnit);
        String b = unitKind(warehouseUnit);
        return !a.isEmpty() && a.equals(b);
    }

    /** Vraca null ako listovi nemaju zaglavlje magacinskog izvestaja (NazivArtikla, NaStanju). */
    public static List<Article> parse(List<Map<Integer, String>> rows) {
        int nameCol = -1, qtyCol = -1, codeCol = -1, extCol = -1, statusCol = -1, unitCol = -1;
        boolean headerFound = false;
        Map<String, Article> byKey = new LinkedHashMap<>();

        for (Map<Integer, String> cells : rows) {
            if (!headerFound) {
                for (Map.Entry<Integer, String> e : cells.entrySet()) {
                    String h = XlsxReader.header(e.getValue()).replace(" ", "");
                    int c = e.getKey();
                    if (h.equals("nazivartikla")) nameCol = c;
                    else if ((h.equals("nastanju") || h.equals("kolicina") || h.equals("količina")) && qtyCol < 0) qtyCol = c;
                    else if (h.equals("internikodartikla")) codeCol = c;
                    else if (h.equals("eksternikodartikla")) extCol = c;
                    else if (h.equals("status")) statusCol = c;
                    else if (h.equals("jedinicamere")) unitCol = c;
                }
                headerFound = nameCol >= 0 && qtyCol >= 0;
                continue;
            }
            String name = XlsxReader.trim(cells.get(nameCol));
            Double qty = Item.parse(cells.get(qtyCol));
            if (name.isEmpty() || qty == null) continue;
            String code = codeCol >= 0 ? XlsxReader.trim(cells.get(codeCol)) : "";
            String ext = extCol >= 0 ? XlsxReader.trim(cells.get(extCol)) : "";
            String unit = unitCol >= 0 ? XlsxReader.trim(cells.get(unitCol)) : "";
            String status = statusCol >= 0 ? XlsxReader.header(cells.get(statusCol)) : "regularno";

            String key = code.isEmpty() ? "n:" + name : code;
            Article a = byKey.get(key);
            if (a == null) {
                a = new Article(code.isEmpty() ? key : code, ext, name, unit, 0);
                byKey.put(key, a);
            }
            if (status.isEmpty() || status.equals("regularno")) a.available += qty;
        }
        return headerFound ? new ArrayList<>(byKey.values()) : null;
    }

    // ---------- Povezivanje po nazivu ----------

    /** Mala slova, sve osim slova i brojeva postaje razmak. */
    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /**
     * Koliko naziv iz magacina lici na naziv iz izvestaja o potrosnji (koji je skracen na 20 znakova).
     * Vece je bolje; 0 znaci da nema zajednickih reci.
     */
    public static double score(String usageName, String warehouseName) {
        String a = normalize(usageName);
        String b = normalize(warehouseName);
        if (a.isEmpty()) return 0;
        String[] ut = a.split(" ");
        String[] wt = b.split(" ");
        double s = b.startsWith(a) ? 10 : 0;
        for (int i = 0; i < ut.length; i++) {
            String t = ut[i];
            boolean exact = false, prefix = false;
            for (String w : wt) {
                if (w.equals(t)) exact = true;
                else if (t.length() >= 2 && w.startsWith(t)) prefix = true;
            }
            if (exact) s += 2;
            else if (prefix) s += i == ut.length - 1 ? 1.5 : 1;
        }
        return s / ut.length;
    }

    /** Artikli iz magacina poredjani od najslicnijeg nazivu. */
    public static List<Article> suggestions(String usageName, List<Article> articles) {
        List<Article> sorted = new ArrayList<>(articles);
        Map<Article, Double> scores = new HashMap<>();
        for (Article a : sorted) scores.put(a, score(usageName, a.name));
        sorted.sort((x, y) -> {
            int c = Double.compare(scores.get(y), scores.get(x));
            return c != 0 ? c : x.name.compareToIgnoreCase(y.name);
        });
        return sorted;
    }

    /**
     * Automatsko povezivanje samo kad je sigurno: skraceni naziv iz izvestaja je pocetak tacno jednog
     * naziva u magacinu, a taj artikal iz magacina odgovara samo jednom artiklu iz izvestaja.
     * Vec postojece veze se ne menjaju. Vraca broj novih veza.
     */
    public static int autoLink(List<Item> items, List<Article> articles, Map<String, List<String>> links) {
        Map<String, String> candidate = new HashMap<>();
        Map<String, Integer> claims = new HashMap<>();
        for (Item it : items) {
            String a = normalize(it.name);
            if (a.isEmpty()) continue;
            String found = null;
            int count = 0;
            for (Article w : articles) {
                if (normalize(w.name).startsWith(a)) {
                    found = w.code;
                    count++;
                }
            }
            if (count == 1) {
                candidate.put(it.key(), found);
                claims.merge(found, 1, Integer::sum);
            }
        }
        int added = 0;
        for (Item it : items) {
            String code = candidate.get(it.key());
            if (code == null || claims.get(code) != 1 || links.containsKey(it.key())) continue;
            List<String> codes = new ArrayList<>();
            codes.add(code);
            links.put(it.key(), codes);
            added++;
        }
        return added;
    }
}
