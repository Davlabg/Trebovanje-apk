package com.davlabg.trebovanje;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Pravi jednostavan .xlsx fajl sa porudzbinom (bez spoljnih biblioteka). */
public final class XlsxWriter {

    private XlsxWriter() {}

    public static void writeOrder(OutputStream out, OrderData data, boolean onlyToOrder) throws IOException {
        boolean wh = data.hasWarehouse();
        StringBuilder rows = new StringBuilder();
        int r = 1;
        if (wh) {
            rows.append(row(r++, true, "Šifra", "Artikal", "Jed. mere", "Nedeljna prodaja", "Stanje", "Poručiti",
                    "Magacin (raspoloživo)", "Jed. mere magacin", "Šifre magacina", "Napomena"));
        } else {
            rows.append(row(r++, true, "Šifra", "Artikal", "Jed. mere", "Nedeljna prodaja", "Stanje", "Poručiti"));
        }
        for (Item it : data.items) {
            double order = it.toOrder();
            if (onlyToOrder && order <= 0) continue;
            rows.append("<row r=\"").append(r).append("\">")
                    .append(strCell("A" + r, it.code, 0))
                    .append(strCell("B" + r, it.name, 0))
                    .append(strCell("C" + r, it.unit, 0))
                    .append(numCell("D" + r, it.usage))
                    .append(it.stock == null ? "" : numCell("E" + r, it.stock))
                    .append(it.stock == null ? "" : numCell("F" + r, order));
            if (wh) {
                List<Warehouse.Article> linked = data.articlesFor(it);
                if (linked.isEmpty()) {
                    rows.append(strCell("J" + r, "Nije povezano sa magacinom", 0));
                } else {
                    StringBuilder codes = new StringBuilder();
                    for (Warehouse.Article a : linked) {
                        if (codes.length() > 0) codes.append(", ");
                        codes.append(a.code);
                    }
                    rows.append(numCell("G" + r, data.availableFor(it)))
                            .append(strCell("H" + r, data.unitFor(it), 0))
                            .append(strCell("I" + r, codes.toString(), 0));
                    if (data.shortage(it)) rows.append(strCell("J" + r, "Nema dovoljno u magacinu", 1));
                }
            }
            rows.append("</row>");
            r++;
        }

        String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>"
                + "<cols><col min=\"1\" max=\"1\" width=\"13\" customWidth=\"1\"/>"
                + "<col min=\"2\" max=\"2\" width=\"34\" customWidth=\"1\"/>"
                + "<col min=\"3\" max=\"3\" width=\"10\" customWidth=\"1\"/>"
                + "<col min=\"4\" max=\"8\" width=\"17\" customWidth=\"1\"/>"
                + "<col min=\"9\" max=\"9\" width=\"20\" customWidth=\"1\"/>"
                + "<col min=\"10\" max=\"10\" width=\"30\" customWidth=\"1\"/></cols>"
                + "<sheetData>" + rows + "</sheetData></worksheet>";

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "</Types>");
            put(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"Porudzbina\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            put(zip, "xl/styles.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                    + "</styleSheet>");
            put(zip, "xl/worksheets/sheet1.xml", sheet);
        }
    }

    private static String row(int r, boolean bold, String... values) {
        StringBuilder sb = new StringBuilder("<row r=\"" + r + "\">");
        for (int i = 0; i < values.length; i++) {
            sb.append(strCell((char) ('A' + i) + "" + r, values[i], bold ? 1 : 0));
        }
        return sb.append("</row>").toString();
    }

    private static String strCell(String ref, String value, int style) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\"" + (style > 0 ? " s=\"" + style + "\"" : "")
                + "><is><t xml:space=\"preserve\">" + escape(value) + "</t></is></c>";
    }

    private static String numCell(String ref, double value) {
        return "<c r=\"" + ref + "\"><v>" + num(value) + "</v></c>";
    }

    private static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.valueOf(v);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default:
                    if (ch < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') continue;
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
