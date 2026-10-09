package com.davlabg.trebovanje;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Cita .xlsx izvestaj bez spoljnih biblioteka.
 * Naziv artikla se uzima iz kolone B ("item"), nedeljna prodaja iz kolone S ("usage").
 * Ako red zaglavlja sadrzi "item" i "usage", koriste se te kolone cak i ako su pomerene;
 * iz istog zaglavlja se prepoznaju i "item code" i "manage unit".
 * Izvestaj potrosnju belezi kao odliv (negativno), pa se znak okrece.
 */
public final class XlsxReader {

    public static final int DEFAULT_NAME_COL = 1;   // B
    public static final int DEFAULT_USAGE_COL = 18; // S

    private static final String REL_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private XlsxReader() {}

    public static class FormatException extends IOException {
        public FormatException(String message) {
            super(message);
        }
    }

    /** Cita izvestaj o potrosnji (Usage). */
    public static List<Item> read(InputStream in) throws Exception {
        for (List<Map<Integer, String>> rows : readSheets(in)) {
            List<Item> items = parseUsage(rows);
            if (!items.isEmpty()) return items;
        }
        throw new FormatException("Nisu pronadjeni artikli (kolona B) sa nedeljnom prodajom (kolona S).");
    }

    /** Svi listovi iz fajla, svaki kao lista redova (indeks kolone od 0 -> tekst celije). */
    public static List<List<Map<Integer, String>>> readSheets(InputStream in) throws Exception {
        byte[] head = new byte[4];
        InputStream buffered = new java.io.BufferedInputStream(in);
        buffered.mark(8);
        int n = buffered.read(head);
        buffered.reset();
        if (n == 4 && (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF) {
            throw new FormatException("Stari .xls format nije podrzan. Otvorite fajl u Excel-u i sacuvajte ga kao .xlsx.");
        }
        if (n < 2 || head[0] != 'P' || head[1] != 'K') {
            throw new FormatException("Fajl nije Excel (.xlsx) dokument.");
        }

        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(buffered)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (name.startsWith("/")) name = name.substring(1);
                if (!e.isDirectory() && name.startsWith("xl/") && name.endsWith(".xml")
                        || name.endsWith(".rels")) {
                    files.put(name, readAll(zip));
                }
            }
        }

        List<String> shared = readSharedStrings(files.get("xl/sharedStrings.xml"));
        List<String> sheetPaths = findSheets(files);
        if (sheetPaths.isEmpty()) throw new FormatException("U fajlu nije pronadjen nijedan list.");

        List<List<Map<Integer, String>>> sheets = new ArrayList<>();
        for (String path : sheetPaths) {
            byte[] data = files.get(path);
            if (data != null) sheets.add(readRows(data, shared));
        }
        return sheets;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int r;
        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        return out.toByteArray();
    }

    private static Document parse(byte[] data) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(data));
    }

    private static List<Element> children(Node parent, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node c = nl.item(i);
            if (c instanceof Element && localName.equals(localName(c))) out.add((Element) c);
        }
        return out;
    }

    private static String localName(Node n) {
        String ln = n.getLocalName();
        if (ln != null) return ln;
        String name = n.getNodeName();
        int idx = name.indexOf(':');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    private static Element firstChild(Node parent, String localName) {
        List<Element> l = children(parent, localName);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Sav tekst iz <t> elemenata (bez fonetskih <rPh> delova). */
    private static String collectText(Node node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        return sb.toString();
    }

    private static void collectText(Node node, StringBuilder sb) {
        NodeList nl = node.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node c = nl.item(i);
            if (!(c instanceof Element)) continue;
            String ln = localName(c);
            if ("t".equals(ln)) sb.append(c.getTextContent());
            else if (!"rPh".equals(ln)) collectText(c, sb);
        }
    }

    private static List<String> readSharedStrings(byte[] data) throws Exception {
        List<String> out = new ArrayList<>();
        if (data == null) return out;
        Element root = parse(data).getDocumentElement();
        for (Element si : children(root, "si")) out.add(collectText(si));
        return out;
    }

    private static List<String> findSheets(Map<String, byte[]> files) throws Exception {
        List<String> out = new ArrayList<>();
        byte[] wb = files.get("xl/workbook.xml");
        byte[] rels = files.get("xl/_rels/workbook.xml.rels");
        if (wb != null && rels != null) {
            Map<String, String> targets = new HashMap<>();
            for (Element rel : children(parse(rels).getDocumentElement(), "Relationship")) {
                targets.put(rel.getAttribute("Id"), rel.getAttribute("Target"));
            }
            Element sheets = firstChild(parse(wb).getDocumentElement(), "sheets");
            if (sheets != null) {
                for (Element s : children(sheets, "sheet")) {
                    String id = s.getAttributeNS(REL_NS, "id");
                    if (id == null || id.isEmpty()) id = s.getAttribute("r:id");
                    String target = targets.get(id);
                    if (target == null) continue;
                    if (target.startsWith("/")) target = target.substring(1);
                    else if (!target.startsWith("xl/")) target = "xl/" + target;
                    out.add(target);
                }
            }
        }
        if (out.isEmpty()) {
            TreeMap<String, Boolean> sorted = new TreeMap<>();
            for (String k : files.keySet()) {
                if (k.startsWith("xl/worksheets/") && k.endsWith(".xml")) sorted.put(k, true);
            }
            out.addAll(sorted.keySet());
        }
        return out;
    }

    /** "S12" -> 18 (indeks kolone od 0), ili -1 ako ref nije validan. */
    static int columnIndex(String ref) {
        if (ref == null) return -1;
        int col = 0;
        int i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) {
            col = col * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1);
            i++;
        }
        return i == 0 ? -1 : col - 1;
    }

    private static String cellValue(Element c, List<String> shared) {
        String t = c.getAttribute("t");
        if ("inlineStr".equals(t)) {
            Element is = firstChild(c, "is");
            return is == null ? "" : collectText(is);
        }
        Element v = firstChild(c, "v");
        if (v == null) return "";
        String raw = v.getTextContent();
        if ("s".equals(t)) {
            try {
                int idx = Integer.parseInt(raw.trim());
                return idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if ("e".equals(t)) return "";
        return raw;
    }

    static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static List<Map<Integer, String>> readRows(byte[] data, List<String> shared) throws Exception {
        Element root = parse(data).getDocumentElement();
        Element sheetData = firstChild(root, "sheetData");
        List<Map<Integer, String>> rows = new ArrayList<>();
        if (sheetData == null) return rows;
        for (Element row : children(sheetData, "row")) {
            Map<Integer, String> cells = new TreeMap<>();
            int pos = 0;
            for (Element c : children(row, "c")) {
                int col = columnIndex(c.getAttribute("r"));
                if (col < 0) col = pos;
                pos = col + 1;
                cells.put(col, cellValue(c, shared));
            }
            rows.add(cells);
        }
        return rows;
    }

    /** Naslov kolone sveden na mala slova i jedan razmak. */
    static String header(String h) {
        return h == null ? "" : h.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static List<Item> parseUsage(List<Map<Integer, String>> rows) {
        List<Item> items = new ArrayList<>();
        int nameCol = DEFAULT_NAME_COL;
        int usageCol = DEFAULT_USAGE_COL;
        int codeCol = -1;
        int unitCol = -1;
        boolean headerFound = false;

        for (Map<Integer, String> cells : rows) {
            if (!headerFound) {
                int hName = -1, hUsage = -1, hCode = -1, hUnit = -1;
                for (Map.Entry<Integer, String> e : cells.entrySet()) {
                    String h = header(e.getValue());
                    if (h.equals("item") && hName < 0) hName = e.getKey();
                    if (h.equals("usage") && hUsage < 0) hUsage = e.getKey();
                    if (h.equals("item code") && hCode < 0) hCode = e.getKey();
                    if ((h.equals("manage unit") || h.equals("unit")) && hUnit < 0) hUnit = e.getKey();
                }
                if (hName >= 0 && hUsage >= 0) {
                    nameCol = hName;
                    usageCol = hUsage;
                    codeCol = hCode;
                    unitCol = hUnit;
                    headerFound = true;
                    continue;
                }
            }

            String name = cells.get(nameCol);
            if (name == null) continue;
            name = name.trim();
            if (name.isEmpty()) continue;
            Double usage = Item.parse(cells.get(usageCol));
            if (usage == null) continue; // zaglavlje, prazni ili tekstualni redovi
            String code = codeCol >= 0 ? trim(cells.get(codeCol)) : "";
            String unit = unitCol >= 0 ? trim(cells.get(unitCol)) : "";
            items.add(new Item(code, name, unit, usage, null));
        }

        List<Item> normalized = Item.normalizeUsage(items);
        return normalized != null ? normalized : items;
    }
}
