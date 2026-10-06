package com.davlabg.trebovanje;

/** Jedan artikal: sifra (kolona A), naziv (kolona B), jedinica mere (kolona D), nedeljna prodaja (kolona S) i uneto stanje. */
public class Item {
    public final String code;
    public final String name;
    public final String unit;
    public final double usage;
    /** Trenutno stanje; null dok korisnik ne unese vrednost. */
    public Double stock;

    public Item(String code, String name, String unit, double usage, Double stock) {
        this.code = code == null ? "" : code;
        this.name = name;
        this.unit = unit == null ? "" : unit;
        this.usage = usage;
        this.stock = stock;
    }

    /** Kljuc za prepoznavanje artikla pri novom uvozu (isti naziv moze imati vise sifara). */
    public String key() {
        return code.isEmpty() ? "n:" + name : "c:" + code;
    }

    public boolean hasStock() {
        return stock != null;
    }

    /** Kolicina za porucivanje za nedelju dana: prodaja - stanje, zaokruzeno navise, nikad manje od 0. */
    public double toOrder() {
        if (stock == null) return 0;
        double diff = usage - stock;
        if (diff <= 0) return 0;
        return Math.ceil(diff - 1e-9);
    }

    public static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) Math.rint(value));
        }
        String s = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.replace('.', ',');
    }

    /** Prihvata i zarez i tacku kao decimalni separator. Vraca null za prazan unos. */
    public static Double parse(String text) {
        if (text == null) return null;
        String t = text.trim().replace(" ", "");
        if (t.isEmpty()) return null;
        if (t.contains(",") && t.contains(".")) {
            // Poslednji separator je decimalni: 1.234,5 -> 1234.5 i 1,234.5 -> 1234.5
            if (t.lastIndexOf(',') > t.lastIndexOf('.')) {
                t = t.replace(".", "").replace(',', '.');
            } else {
                t = t.replace(",", "");
            }
        } else {
            t = t.replace(',', '.');
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
