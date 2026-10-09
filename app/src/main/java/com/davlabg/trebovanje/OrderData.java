package com.davlabg.trebovanje;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sve sto aplikacija pamti: artikli iz izvestaja, stanje u eksternom magacinu i veze izmedju njih. */
public class OrderData {
    public String source = "";
    public List<Item> items = new ArrayList<>();

    public String warehouseSource = "";
    public List<Warehouse.Article> warehouse = new ArrayList<>();
    /** Kljuc artikla iz izvestaja -> sifra artikla u magacinu. */
    public Map<String, String> links = new LinkedHashMap<>();
    /** Kljuc artikla -> koliko jedinica iz izvestaja (npr. kg) ima u jednoj jedinici iz magacina (npr. komad). */
    public Map<String, Double> factors = new LinkedHashMap<>();

    private Map<String, Warehouse.Article> index;

    public void setWarehouse(List<Warehouse.Article> articles) {
        warehouse = articles;
        index = null;
    }

    public boolean hasWarehouse() {
        return !warehouse.isEmpty();
    }

    public Warehouse.Article articleFor(Item it) {
        String code = links.get(it.key());
        if (code == null) return null;
        if (index == null) {
            index = new HashMap<>();
            for (Warehouse.Article a : warehouse) index.put(a.code, a);
        }
        return index.get(code);
    }

    /** Faktor pretvaranja jedinica magacina u jedinice izvestaja; null ako nije poznat. */
    public Double factorFor(Item it) {
        Double f = factors.get(it.key());
        if (f != null) return f;
        Warehouse.Article a = articleFor(it);
        if (a != null && Warehouse.sameUnit(it.unit, a.unit)) return 1.0;
        return null;
    }

    /** Raspolozivo u magacinu (status "Regularno"), u jedinicama izvestaja; null ako ne moze da se izracuna. */
    public Double availableFor(Item it) {
        Warehouse.Article a = articleFor(it);
        Double f = factorFor(it);
        if (a == null || f == null) return null;
        return a.available * f;
    }

    /** Magacin nema dovoljno za porudzbinu. */
    public boolean shortage(Item it) {
        double order = it.toOrder();
        if (order <= 0) return false;
        Warehouse.Article a = articleFor(it);
        if (a != null && a.available <= 0) return true; // nema nista, bez obzira na jedinice
        Double avail = availableFor(it);
        return avail != null && avail + 1e-9 < order;
    }

    public int linkedCount() {
        int n = 0;
        for (Item it : items) if (articleFor(it) != null) n++;
        return n;
    }

    public void link(Item it, Warehouse.Article a, Double factor) {
        if (a == null) {
            links.remove(it.key());
            factors.remove(it.key());
            return;
        }
        links.put(it.key(), a.code);
        if (factor == null) factors.remove(it.key());
        else factors.put(it.key(), factor);
    }
}
