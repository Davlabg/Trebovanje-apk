package com.davlabg.trebovanje;

import java.util.ArrayList;
import java.util.Collections;
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
    /**
     * Kljuc artikla iz izvestaja -> sifre artikala u magacinu (isti artikal moze biti pod vise sifara,
     * stanja se sabiraju). Prazna lista znaci da je veza namerno uklonjena.
     */
    public Map<String, List<String>> links = new LinkedHashMap<>();

    private Map<String, Warehouse.Article> index;

    public void setWarehouse(List<Warehouse.Article> articles) {
        warehouse = articles;
        index = null;
    }

    public boolean hasWarehouse() {
        return !warehouse.isEmpty();
    }

    /** Povezani artikli iz magacina koji postoje u poslednjem uvezenom stanju magacina. */
    public List<Warehouse.Article> articlesFor(Item it) {
        List<String> codes = links.get(it.key());
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        if (index == null) {
            index = new HashMap<>();
            for (Warehouse.Article a : warehouse) index.put(a.code, a);
        }
        List<Warehouse.Article> out = new ArrayList<>(codes.size());
        for (String c : codes) {
            Warehouse.Article a = index.get(c);
            if (a != null) out.add(a);
        }
        return out;
    }

    public boolean isLinked(Item it) {
        return !articlesFor(it).isEmpty();
    }

    /** Zbir stanja svih povezanih sifara (status Regularno, u jedinici iz magacina). */
    public double availableFor(Item it) {
        double sum = 0;
        for (Warehouse.Article a : articlesFor(it)) sum += a.available;
        return sum;
    }

    /** Jedinica iz magacina za povezane sifre (npr. "kom"); prazno ako nije povezano. */
    public String unitFor(Item it) {
        List<Warehouse.Article> list = articlesFor(it);
        return list.isEmpty() ? "" : list.get(0).unitLabel();
    }

    /**
     * Magacin nema dovoljno za porudzbinu. Kolicine se ne pretvaraju izmedju jedinica (kg u izvestaju,
     * komad u magacinu), pa se porede samo kad su jedinice iste; prazan magacin je uvek "nema dovoljno".
     */
    public boolean shortage(Item it) {
        double order = it.toOrder();
        if (order <= 0) return false;
        List<Warehouse.Article> list = articlesFor(it);
        if (list.isEmpty()) return false;
        double avail = availableFor(it);
        if (avail <= 0) return true;
        for (Warehouse.Article a : list) {
            if (!Warehouse.sameUnit(it.unit, a.unit)) return false;
        }
        return avail + 1e-9 < order;
    }

    public int linkedCount() {
        int n = 0;
        for (Item it : items) if (isLinked(it)) n++;
        return n;
    }

    /** Povezuje artikal sa jednom ili vise sifara iz magacina; prazna lista uklanja vezu i sprecava automatsko povezivanje. */
    public void link(Item it, List<Warehouse.Article> articles) {
        List<String> codes = new ArrayList<>(articles.size());
        for (Warehouse.Article a : articles) codes.add(a.code);
        links.put(it.key(), codes);
    }
}
