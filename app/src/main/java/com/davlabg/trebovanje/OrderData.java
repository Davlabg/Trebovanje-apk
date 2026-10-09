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

    /**
     * Magacin nema dovoljno za porudzbinu. Kolicine se ne pretvaraju izmedju jedinica (kg u izvestaju,
     * komad u magacinu), pa se porede samo kad su jedinice iste; prazan magacin je uvek "nema dovoljno".
     */
    public boolean shortage(Item it) {
        double order = it.toOrder();
        if (order <= 0) return false;
        Warehouse.Article a = articleFor(it);
        if (a == null) return false;
        if (a.available <= 0) return true;
        return Warehouse.sameUnit(it.unit, a.unit) && a.available + 1e-9 < order;
    }

    public int linkedCount() {
        int n = 0;
        for (Item it : items) if (articleFor(it) != null) n++;
        return n;
    }

    /** Povezuje artikal sa magacinom; null uklanja vezu i sprecava da je automatsko povezivanje vrati. */
    public void link(Item it, Warehouse.Article a) {
        links.put(it.key(), a == null ? "" : a.code);
    }
}
