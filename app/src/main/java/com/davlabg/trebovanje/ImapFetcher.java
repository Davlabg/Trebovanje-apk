package com.davlabg.trebovanje;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Preuzima Excel (.xlsx) priloge iz Gmail sandučeta (imap.gmail.com:993, SSL), prijavom sa Gmail adresom
 * i "lozinkom za aplikaciju" (ista kao za slanje). Bez spoljnih biblioteka.
 * Pamti se poslednji pregledani UID, pa se svaki mejl obrađuje samo jednom.
 */
public final class ImapFetcher {

    public static final String HOST = "imap.gmail.com";
    public static final int PORT = 993;
    /** Mejlovi veći od ovoga se preskaču. */
    private static final int MAX_MESSAGE = 20 * 1024 * 1024;
    /** Koliko najnovijih mejlova sa prilogom se najviše pregleda u jednoj proveri. */
    private static final int MAX_MESSAGES = 25;

    public static class Attachment {
        public final String fileName;
        public final byte[] data;
        public final long uid;
        public final String subject;
        public final String date;

        Attachment(String fileName, byte[] data, long uid, String subject, String date) {
            this.fileName = fileName;
            this.data = data;
            this.uid = uid;
            this.subject = subject;
            this.date = date;
        }
    }

    public static class Result {
        /** Prilozi od najnovijeg mejla ka starijem. */
        public final List<Attachment> attachments = new ArrayList<>();
        public long uidValidity;
        public long maxUid;
    }

    private InputStream in;
    private OutputStream out;
    private int tag;

    private ImapFetcher() {}

    /**
     * @param knownValidity UIDVALIDITY iz prethodne provere (0 = prva provera)
     * @param lastUid       najveći UID iz prethodne provere; stariji mejlovi se preskaču
     * @param days          koliko dana unazad se gleda
     */
    public static Result fetch(String user, String password, long knownValidity, long lastUid, int days)
            throws SmtpSender.MailException {
        return fetch(SSLSocketFactory.getDefault(), HOST, PORT, user, password, knownValidity, lastUid, days);
    }

    static Result fetch(SocketFactory factory, String host, int port, String user, String password,
                        long knownValidity, long lastUid, int days) throws SmtpSender.MailException {
        Socket socket;
        try {
            socket = factory.createSocket(host, port);
            socket.setSoTimeout(45000);
            if (socket instanceof SSLSocket) ((SSLSocket) socket).startHandshake();
        } catch (IOException e) {
            throw new SmtpSender.MailException("Nema veze sa Gmail-om. Proverite internet i pokušajte ponovo.");
        }
        try {
            ImapFetcher f = new ImapFetcher();
            f.in = new BufferedInputStream(socket.getInputStream());
            f.out = socket.getOutputStream();
            return f.run(user, password, knownValidity, lastUid, days);
        } catch (IOException e) {
            throw new SmtpSender.MailException("Veza sa Gmail-om je prekinuta. Pokušajte ponovo.");
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ---------- IMAP ----------

    /** Jedan odgovor servera: tekst (literali zamenjeni sa {n}) i literali redom. */
    private static class Response {
        final StringBuilder text = new StringBuilder();
        final List<byte[]> literals = new ArrayList<>();
    }

    private static class Reply {
        final List<Response> untagged = new ArrayList<>();
        String status = "";
    }

    private Result run(String user, String password, long knownValidity, long lastUid, int days)
            throws IOException, SmtpSender.MailException {
        String greeting = readLine();
        if (!greeting.startsWith("* OK")) throw new SmtpSender.MailException("Gmail server nije odgovorio.");

        Reply login = command("LOGIN " + quote(user) + " " + quote(password));
        if (!login.status.startsWith("OK")) {
            throw new SmtpSender.MailException("Gmail je odbio prijavu za preuzimanje mejla. Proverite adresu naloga i "
                    + "lozinku za aplikaciju (16 slova sa myaccount.google.com/apppasswords).");
        }
        Reply select = command("SELECT INBOX");
        if (!select.status.startsWith("OK")) throw new SmtpSender.MailException("Gmail nije otvorio sanduče (INBOX).");

        Result result = new Result();
        for (Response r : select.untagged) {
            Matcher m = Pattern.compile("UIDVALIDITY (\\d+)").matcher(r.text);
            if (m.find()) result.uidValidity = Long.parseLong(m.group(1));
        }
        if (result.uidValidity != knownValidity) lastUid = 0;
        result.maxUid = lastUid;

        // Gmail pretraga (X-GM-RAW); ako nije podržana, običan IMAP SEARCH po datumu.
        Reply search = command("UID SEARCH X-GM-RAW " + quote("has:attachment newer_than:" + days + "d"));
        if (!search.status.startsWith("OK")) {
            String since = new SimpleDateFormat("dd-MMM-yyyy", Locale.US)
                    .format(new Date(System.currentTimeMillis() - days * 86400000L));
            search = command("UID SEARCH SINCE " + since);
        }
        List<Long> uids = new ArrayList<>();
        for (Response r : search.untagged) {
            String t = r.text.toString();
            if (!t.startsWith("* SEARCH")) continue;
            for (String p : t.substring(8).trim().split("\\s+")) {
                if (p.matches("\\d+")) uids.add(Long.parseLong(p));
            }
        }
        List<Long> fresh = new ArrayList<>();
        for (long u : uids) {
            if (u > result.maxUid) result.maxUid = u;
            if (u > lastUid) fresh.add(u);
        }
        Collections.sort(fresh, Collections.reverseOrder());
        if (fresh.size() > MAX_MESSAGES) fresh = new ArrayList<>(fresh.subList(0, MAX_MESSAGES));

        if (!fresh.isEmpty()) {
            Map<Long, Long> sizes = new HashMap<>();
            StringBuilder set = new StringBuilder();
            for (long u : fresh) {
                if (set.length() > 0) set.append(',');
                set.append(u);
            }
            Reply sz = command("UID FETCH " + set + " (UID RFC822.SIZE)");
            for (Response r : sz.untagged) {
                Matcher mu = Pattern.compile("UID (\\d+)").matcher(r.text);
                Matcher ms = Pattern.compile("RFC822\\.SIZE (\\d+)").matcher(r.text);
                if (mu.find() && ms.find()) sizes.put(Long.parseLong(mu.group(1)), Long.parseLong(ms.group(1)));
            }
            for (long u : fresh) {
                Long size = sizes.get(u);
                if (size != null && size > MAX_MESSAGE) continue;
                Reply msg = command("UID FETCH " + u + " (BODY.PEEK[])");
                for (Response r : msg.untagged) {
                    if (r.literals.isEmpty()) continue;
                    Mime.collect(r.literals.get(0), u, result.attachments);
                }
            }
        }
        try {
            command("LOGOUT");
        } catch (IOException ignored) {
        }
        return result;
    }

    private Reply command(String cmd) throws IOException {
        String t = "n" + (++tag);
        out.write((t + " " + cmd + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        Reply reply = new Reply();
        while (true) {
            Response r = readResponse();
            String text = r.text.toString();
            if (text.startsWith(t + " ")) {
                reply.status = text.substring(t.length() + 1);
                return reply;
            }
            if (text.startsWith("*")) reply.untagged.add(r);
        }
    }

    /** Čita jedan logički odgovor, zajedno sa literalima {n}. */
    private Response readResponse() throws IOException {
        Response r = new Response();
        while (true) {
            String line = readLine();
            r.text.append(line);
            Matcher m = Pattern.compile("\\{(\\d+)\\}$").matcher(line);
            if (!m.find()) return r;
            int n = Integer.parseInt(m.group(1));
            byte[] lit = new byte[n];
            int off = 0;
            while (off < n) {
                int k = in.read(lit, off, n - off);
                if (k < 0) throw new IOException("closed");
                off += k;
            }
            r.literals.add(lit);
        }
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            b.write(c);
        }
        if (c < 0 && b.size() == 0) throw new IOException("closed");
        byte[] bytes = b.toByteArray();
        int len = bytes.length;
        if (len > 0 && bytes[len - 1] == '\r') len--;
        return new String(bytes, 0, len, StandardCharsets.ISO_8859_1);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------- MIME ----------

    /** Izdvaja .xlsx priloge iz sirovog mejla (i iz prosleđenih mejlova unutar njega). */
    static final class Mime {
        private Mime() {}

        static void collect(byte[] raw, long uid, List<Attachment> out) {
            String all = new String(raw, StandardCharsets.ISO_8859_1);
            Map<String, String> h = headers(all);
            String subject = decodeWords(h.getOrDefault("subject", ""));
            String date = h.getOrDefault("date", "");
            walk(all, uid, subject, date, out, 0);
        }

        private static void walk(String part, long uid, String subject, String date, List<Attachment> out, int depth) {
            if (depth > 8) return;
            int split = headerEnd(part);
            String headText = split < 0 ? part : part.substring(0, split);
            String body = split < 0 ? "" : part.substring(split).replaceFirst("^\\r?\\n\\r?\\n", "");
            Map<String, String> h = headers(headText);
            String ctype = h.getOrDefault("content-type", "text/plain");
            String type = ctype.split(";")[0].trim().toLowerCase(Locale.ROOT);

            if (type.startsWith("multipart/")) {
                String boundary = param(ctype, "boundary");
                if (boundary == null) return;
                String delim = "--" + boundary;
                String[] pieces = body.split("(?m)^" + Pattern.quote(delim));
                for (int i = 1; i < pieces.length; i++) {
                    String p = pieces[i];
                    if (p.startsWith("--")) break; // kraj
                    walk(p.replaceFirst("^[ \\t]*\\r?\\n", ""), uid, subject, date, out, depth + 1);
                }
                return;
            }
            if (type.equals("message/rfc822")) {
                walk(body, uid, subject, date, out, depth + 1);
                return;
            }
            String disp = h.getOrDefault("content-disposition", "");
            String name = filename(disp, ctype);
            boolean xlsx = (name != null && name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
                    || type.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            if (!xlsx) return;
            String enc = h.getOrDefault("content-transfer-encoding", "").trim().toLowerCase(Locale.ROOT);
            byte[] data;
            try {
                if (enc.equals("base64")) data = Base64.getMimeDecoder().decode(body.trim());
                else if (enc.equals("quoted-printable")) data = quotedPrintable(body);
                else data = body.getBytes(StandardCharsets.ISO_8859_1);
            } catch (IllegalArgumentException e) {
                return;
            }
            out.add(new Attachment(name != null ? name : "prilog.xlsx", data, uid, subject, date));
        }

        private static int headerEnd(String s) {
            int a = s.indexOf("\r\n\r\n");
            int b = s.indexOf("\n\n");
            if (a < 0) return b;
            if (b < 0) return a;
            return Math.min(a, b);
        }

        /** Zaglavlja malim slovima, sa spojenim nastavcima redova. */
        static Map<String, String> headers(String text) {
            int end = headerEnd(text);
            String head = end < 0 ? text : text.substring(0, end);
            head = head.replaceAll("\\r?\\n[ \\t]+", " ");
            Map<String, String> h = new HashMap<>();
            for (String line : head.split("\\r?\\n")) {
                int c = line.indexOf(':');
                if (c <= 0) continue;
                String k = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
                if (!h.containsKey(k)) h.put(k, line.substring(c + 1).trim());
            }
            return h;
        }

        static String param(String header, String name) {
            Matcher m = Pattern.compile("(?i)(?:^|;)\\s*" + Pattern.quote(name) + "\\s*=\\s*(\"([^\"]*)\"|[^;\\s]+)")
                    .matcher(header);
            if (!m.find()) return null;
            return m.group(2) != null ? m.group(2) : m.group(1);
        }

        static String filename(String disposition, String ctype) {
            // RFC 2231: filename*=UTF-8''Usage%20report.xlsx (može biti i u delovima filename*0*=...)
            for (String h : new String[]{disposition, ctype}) {
                String key = h == disposition ? "filename" : "name";
                StringBuilder ext = new StringBuilder();
                String charset = "UTF-8";
                Matcher m = Pattern.compile("(?i)" + key + "\\*(\\d+)?\\*?\\s*=\\s*(\"([^\"]*)\"|[^;\\s]+)").matcher(h);
                while (m.find()) {
                    String v = m.group(3) != null ? m.group(3) : m.group(2);
                    int q = v.indexOf("''");
                    if (q >= 0 && ext.length() == 0) {
                        if (q > 0) charset = v.substring(0, q);
                        v = v.substring(q + 2);
                    }
                    ext.append(v);
                }
                if (ext.length() > 0) return percentDecode(ext.toString(), charset);
                String plain = param(h, key);
                if (plain != null) return decodeWords(plain);
            }
            return null;
        }

        private static String percentDecode(String s, String charset) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '%' && i + 2 < s.length()) {
                    try {
                        b.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                        i += 2;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
                b.write(c);
            }
            try {
                return new String(b.toByteArray(), Charset.forName(charset));
            } catch (Exception e) {
                return new String(b.toByteArray(), StandardCharsets.UTF_8);
            }
        }

        /** RFC 2047: =?UTF-8?B?...?= i =?UTF-8?Q?...?= */
        static String decodeWords(String s) {
            Matcher m = Pattern.compile("=\\?([^?]+)\\?([bBqQ])\\?([^?]*)\\?=").matcher(s);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (m.find()) {
                String between = s.substring(last, m.start());
                if (!(last > 0 && between.trim().isEmpty())) sb.append(between);
                byte[] bytes;
                try {
                    if (m.group(2).equalsIgnoreCase("B")) bytes = Base64.getMimeDecoder().decode(m.group(3));
                    else bytes = quotedPrintable(m.group(3).replace('_', ' '));
                    sb.append(new String(bytes, Charset.forName(m.group(1))));
                } catch (Exception e) {
                    sb.append(m.group());
                }
                last = m.end();
            }
            sb.append(s.substring(last));
            return sb.toString();
        }

        static byte[] quotedPrintable(String s) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            String t = s.replaceAll("=\\r?\\n", "");
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == '=' && i + 2 < t.length()) {
                    try {
                        b.write(Integer.parseInt(t.substring(i + 1, i + 3), 16));
                        i += 2;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
                b.write(c);
            }
            return b.toByteArray();
        }
    }
}
