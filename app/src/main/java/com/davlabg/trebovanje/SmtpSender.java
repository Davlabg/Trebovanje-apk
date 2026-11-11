package com.davlabg.trebovanje;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Šalje mejl sa Excel prilogom direktno preko Gmail SMTP servera (smtp.gmail.com:465, SSL),
 * prijavom sa Gmail adresom i "lozinkom za aplikaciju". Bez spoljnih biblioteka.
 */
public final class SmtpSender {

    public static final String HOST = "smtp.gmail.com";
    public static final int PORT = 465;
    public static final String SENDER_NAME = "Nedeljko – porudžbina";

    /** Greška sa porukom za korisnika (na srpskom). */
    public static class MailException extends Exception {
        public MailException(String msg) {
            super(msg);
        }
    }

    private BufferedReader in;
    private OutputStream out;

    private SmtpSender() {}

    public static void send(String user, String password, List<String> to, String subject, String body,
                            String fileName, byte[] attachment) throws MailException {
        new SmtpSender().doSend(user, password, to, subject, body, fileName, attachment);
    }

    private void doSend(String user, String password, List<String> to, String subject, String body,
                        String fileName, byte[] attachment) throws MailException {
        SSLSocket socket;
        try {
            socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(HOST, PORT);
            socket.setSoTimeout(30000);
            socket.startHandshake();
        } catch (IOException e) {
            throw new MailException("Nema veze sa Gmail-om. Proverite internet i pokušajte ponovo.");
        }
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = socket.getOutputStream();

            expect(220, "Gmail server nije odgovorio.");
            cmd("EHLO nedeljko", 250, "Gmail server nije prihvatio vezu.");

            cmd("AUTH LOGIN", 334, "Gmail nije dozvolio prijavu.");
            cmd(b64(user), 334, "Gmail nije prihvatio adresu naloga.");
            int code = cmdCode(b64(password));
            if (code != 235) {
                throw new MailException("Gmail je odbio prijavu. Proverite adresu naloga i lozinku za aplikaciju "
                        + "(16 slova sa myaccount.google.com/apppasswords, ne obična lozinka).");
            }

            cmd("MAIL FROM:<" + user + ">", 250, "Gmail nije prihvatio pošiljaoca.");
            for (String r : to) {
                int c = cmdCode("RCPT TO:<" + r + ">");
                if (c != 250 && c != 251) throw new MailException("Gmail nije prihvatio adresu primaoca: " + r);
            }
            cmd("DATA", 354, "Gmail nije prihvatio poruku.");
            write(buildMessage(user, to, subject, body, fileName, attachment));
            write(".\r\n");
            expect(250, "Gmail nije poslao poruku (možda je dostignuto dnevno ograničenje).");
            try {
                write("QUIT\r\n");
            } catch (IOException ignored) {
            }
        } catch (IOException e) {
            throw new MailException("Veza sa Gmail-om je prekinuta. Pokušajte ponovo.");
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String buildMessage(String from, List<String> to, String subject, String body,
                                String fileName, byte[] attachment) {
        String boundary = "=_nedeljko_" + UUID.randomUUID().toString().replace("-", "");
        StringBuilder m = new StringBuilder();
        m.append("From: ").append(encodeWord(SENDER_NAME)).append(" <").append(from).append(">\r\n");
        m.append("To: ").append(String.join(", ", to)).append("\r\n");
        m.append("Subject: ").append(encodeWord(subject)).append("\r\n");
        m.append("Date: ").append(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(new Date())).append("\r\n");
        m.append("Message-ID: <").append(UUID.randomUUID()).append("@nedeljko.app>\r\n");
        m.append("MIME-Version: 1.0\r\n");
        if (attachment == null) {
            m.append("Content-Type: text/plain; charset=UTF-8\r\n");
            m.append("Content-Transfer-Encoding: base64\r\n\r\n");
            m.append(wrap(body.getBytes(StandardCharsets.UTF_8)));
            return m.toString();
        }
        m.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");
        m.append("--").append(boundary).append("\r\n");
        m.append("Content-Type: text/plain; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        m.append(wrap(body.getBytes(StandardCharsets.UTF_8)));
        m.append("--").append(boundary).append("\r\n");
        m.append("Content-Type: ").append(AttachmentProvider.MIME_XLSX).append("; name=\"").append(fileName).append("\"\r\n");
        m.append("Content-Disposition: attachment; filename=\"").append(fileName).append("\"\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        m.append(wrap(attachment));
        m.append("--").append(boundary).append("--\r\n");
        return m.toString();
    }

    private static String b64(String s) {
        return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String encodeWord(String s) {
        return "=?UTF-8?B?" + b64(s) + "?=";
    }

    /** Base64 u redovima od 76 znakova, svaki završen sa CRLF. */
    private static String wrap(byte[] data) {
        String all = Base64.encodeToString(data, Base64.NO_WRAP);
        StringBuilder sb = new StringBuilder(all.length() + all.length() / 76 * 2 + 4);
        for (int i = 0; i < all.length(); i += 76) {
            sb.append(all, i, Math.min(all.length(), i + 76)).append("\r\n");
        }
        return sb.toString();
    }

    private void write(String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Čita (višeredni) odgovor servera i vraća njegov kod. */
    private int readCode() throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) throw new IOException("closed");
        } while (line.length() > 3 && line.charAt(3) == '-');
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private void expect(int code, String err) throws IOException, MailException {
        if (readCode() != code) throw new MailException(err);
    }

    private int cmdCode(String c) throws IOException {
        write(c + "\r\n");
        return readCode();
    }

    private void cmd(String c, int code, String err) throws IOException, MailException {
        if (cmdCode(c) != code) throw new MailException(err);
    }
}
