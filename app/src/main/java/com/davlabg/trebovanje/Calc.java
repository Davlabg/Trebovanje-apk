package com.davlabg.trebovanje;

/**
 * Mini kalkulator za polja sa količinom: + − × ÷ i zagrade, npr. "3*15 + 4,5" ili "(12+8)/2".
 * Decimalni separator može biti zarez ili tačka. Običan broj se čita kao i ranije.
 */
public final class Calc {

    private final String s;
    private int pos;

    private Calc(String s) {
        this.s = s;
    }

    /** Vraća null za prazan ili neispravan izraz. */
    public static Double eval(String text) {
        if (text == null) return null;
        String t = text.trim()
                .replace('×', '*').replace('x', '*').replace('X', '*')
                .replace('÷', '/').replace(':', '/')
                .replace('−', '-').replace('–', '-')
                .replace(" ", "");
        if (t.isEmpty()) return null;
        // Običan broj (i sa separatorom hiljada, npr. 1.234,5) ide preko postojećeg parsiranja.
        if (t.matches("-?[0-9.,]+")) return Item.parse(t);
        try {
            Calc c = new Calc(t);
            double v = c.expr();
            if (c.pos != t.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Da li tekst sadrži računsku operaciju (a ne samo broj). */
    public static boolean isExpression(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.startsWith("-")) t = t.substring(1);
        for (int i = 0; i < t.length(); i++) {
            if ("+-*/×÷:()xX−".indexOf(t.charAt(i)) >= 0) return true;
        }
        return false;
    }

    private double expr() {
        double v = term();
        while (pos < s.length()) {
            char ch = s.charAt(pos);
            if (ch == '+') { pos++; v += term(); }
            else if (ch == '-') { pos++; v -= term(); }
            else break;
        }
        return v;
    }

    private double term() {
        double v = factor();
        while (pos < s.length()) {
            char ch = s.charAt(pos);
            if (ch == '*') { pos++; v *= factor(); }
            else if (ch == '/') {
                pos++;
                double d = factor();
                if (Math.abs(d) < 1e-12) throw new ArithmeticException();
                v /= d;
            } else if (ch == '(') {
                v *= factor(); // 2(3+4) = 14
            } else break;
        }
        return v;
    }

    private double factor() {
        if (pos >= s.length()) throw new IllegalArgumentException();
        char ch = s.charAt(pos);
        if (ch == '+') { pos++; return factor(); }
        if (ch == '-') { pos++; return -factor(); }
        if (ch == '(') {
            pos++;
            double v = expr();
            if (pos < s.length() && s.charAt(pos) == ')') pos++; // zagrada se može izostaviti na kraju
            return v;
        }
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == ',' || s.charAt(pos) == '.')) pos++;
        if (start == pos) throw new IllegalArgumentException();
        String num = s.substring(start, pos).replace(',', '.');
        if (num.indexOf('.') != num.lastIndexOf('.')) throw new IllegalArgumentException();
        return Double.parseDouble(num);
    }
}
