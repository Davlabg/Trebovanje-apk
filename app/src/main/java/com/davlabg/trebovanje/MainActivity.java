package com.davlabg.trebovanje;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_IMPORT = 1;
    private static final int REQ_EXPORT = 2;

    /** Gmail nalog sa koga aplikacija sama šalje porudžbinu (lozinka za aplikaciju se upisuje na telefonu). */
    public static final String DEFAULT_SENDER = "utrosak.cw.restoran@gmail.com";
    private static final String MAIL_PREFS = "mail";

    private OrderData data;
    private ItemAdapter adapter;
    private View summary;
    private TpTable tpTable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        data = Storage.load(this);
        tpTable = new TpTable(data.tpCustom != null ? data.tpCustom : TpTable.loadBuiltIn(this));
        // Artikli iz ranije uvezenih izveštaja odmah dobijaju količinu u TP.
        tpTable.apply(data.items);
        Storage.save(this, data);
        adapter = new ItemAdapter(this);
        summary = findViewById(R.id.summary);

        ListView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.empty));
        list.setOnItemClickListener((parent, view, position, id) -> showStockDialog(position));

        Button importButton = findViewById(R.id.import_button);
        importButton.setOnClickListener(v -> pickExcel());
        findViewById(R.id.btn_import).setOnClickListener(v -> pickExcel());
        findViewById(R.id.btn_share).setOnClickListener(v -> shareText());
        findViewById(R.id.btn_export).setOnClickListener(v -> exportExcel());
        findViewById(R.id.btn_mail).setOnClickListener(v -> sendMail());

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.setQuery(s.toString());
            }
        });

        CheckBox onlyOrder = findViewById(R.id.only_order);
        onlyOrder.setOnCheckedChangeListener((b, checked) -> adapter.setOnlyToOrder(checked));

        adapter.setData(data);
        updateSummary();
    }

    // ---------- Meni ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_import || id == R.id.action_import_warehouse || id == R.id.action_import_tp) {
            pickExcel();
        } else if (id == R.id.action_export) {
            exportExcel();
        } else if (id == R.id.action_share) {
            shareText();
        } else if (id == R.id.action_mail_fetch) {
            checkMail(true);
        } else if (id == R.id.action_mail_send) {
            sendMail();
        } else if (id == R.id.action_mail_settings) {
            showMailSettings();
        } else if (id == R.id.action_tp_builtin) {
            restoreBuiltInTp();
        } else if (id == R.id.action_clear) {
            confirmClear();
        } else if (id == R.id.action_clear_percent) {
            confirmClearPercent();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // ---------- Uvoz Excel-a ----------

    private void pickExcel() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast(getString(R.string.no_file_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        super.onActivityResult(requestCode, resultCode, result);
        if (resultCode != RESULT_OK || result == null || result.getData() == null) return;
        Uri uri = result.getData();
        if (requestCode == REQ_IMPORT) importFrom(uri);
        else if (requestCode == REQ_EXPORT) writeExport(uri);
    }

    private void importFrom(Uri uri) {
        String fileName = displayName(uri);
        toast(getString(R.string.reading));
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception(getString(R.string.cannot_open));
                List<List<Map<Integer, String>>> sheets = XlsxReader.readSheets(in);
                for (List<Map<Integer, String>> rows : sheets) {
                    List<Warehouse.Article> articles = Warehouse.parse(rows);
                    if (articles != null) {
                        runOnUiThread(() -> applyWarehouse(articles, fileName));
                        return;
                    }
                }
                for (List<Map<Integer, String>> rows : sheets) {
                    List<TpTable.Entry> entries = TpTable.parse(rows);
                    if (entries != null && !entries.isEmpty()) {
                        runOnUiThread(() -> applyTpTable(entries, fileName));
                        return;
                    }
                }
                for (List<Map<Integer, String>> rows : sheets) {
                    List<Item> items = XlsxReader.parseUsage(rows);
                    if (!items.isEmpty()) {
                        runOnUiThread(() -> applyImport(items, fileName));
                        return;
                    }
                }
                throw new Exception(getString(R.string.unknown_file));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle(R.string.import_failed)
                        .setMessage(msg)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            }
        }).start();
    }

    private void applyImport(List<Item> items, String fileName) {
        toast(applyImportCore(items, fileName));
    }

    /** Ubacuje novi Usage izveštaj (stanja, korekcije, safety i ručni TP ostaju) i vraća poruku o uvozu. */
    private String applyImportCore(List<Item> items, String fileName) {
        // Zadrzi vec uneta stanja za iste artikle (po sifri, a ako je nema, po nazivu).
        Map<String, Double> old = new HashMap<>();
        Map<String, Double> oldPercent = new HashMap<>();
        Map<String, Double> oldSafety = new HashMap<>();
        Map<String, Double> oldTp = new HashMap<>();
        for (Item it : data.items) {
            if (it.tpManual && it.tp != null) oldTp.put(it.key(), it.tp);
            if (it.stock != null) old.put(it.key(), it.stock);
            if (it.hasPercent()) oldPercent.put(it.key(), it.percent);
            if (it.hasSafety()) oldSafety.put(it.key(), it.safety);
        }
        int kept = 0;
        for (Item it : items) {
            Double p = oldPercent.get(it.key());
            if (p != null) it.percent = p;
            Double ss = oldSafety.get(it.key());
            if (ss != null) it.safety = ss;
            Double tp = oldTp.get(it.key());
            if (tp != null) {
                it.tp = tp;
                it.tpManual = true;
            }
            Double s = old.get(it.key());
            if (s != null) {
                it.stock = s;
                kept++;
            }
        }
        data.items = items;
        data.source = fileName;
        int withTp = tpTable.apply(data.items);
        if (data.hasWarehouse()) Warehouse.autoLink(data.items, data.warehouse, data.links);
        Storage.save(this, data);
        adapter.setData(data);
        updateSummary();
        String msg = getString(R.string.imported, items.size());
        if (kept > 0) msg += "\n" + getString(R.string.kept_stock, kept);
        msg += "\n" + getString(R.string.summary_tp, withTp, items.size());
        return msg;
    }

    private void applyTpTable(List<TpTable.Entry> entries, String fileName) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tp_title)
                .setMessage(applyTpTableCore(entries, fileName))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String applyTpTableCore(List<TpTable.Entry> entries, String fileName) {
        data.tpCustom = entries;
        data.tpSource = fileName;
        tpTable = new TpTable(entries);
        int matched = tpTable.apply(data.items);
        saveAndRefresh();
        return getString(R.string.tp_imported, entries.size(), matched, data.items.size());
    }

    private void restoreBuiltInTp() {
        data.tpCustom = null;
        data.tpSource = "";
        tpTable = new TpTable(TpTable.loadBuiltIn(this));
        int matched = tpTable.apply(data.items);
        saveAndRefresh();
        new AlertDialog.Builder(this)
                .setTitle(R.string.tp_title)
                .setMessage(getString(R.string.tp_builtin_done, tpTable.size(), matched, data.items.size()))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void applyWarehouse(List<Warehouse.Article> articles, String fileName) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.warehouse_title)
                .setMessage(applyWarehouseCore(articles, fileName))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String applyWarehouseCore(List<Warehouse.Article> articles, String fileName) {
        data.setWarehouse(articles);
        data.warehouseSource = fileName;
        int added = Warehouse.autoLink(data.items, data.warehouse, data.links);
        Storage.save(this, data);
        adapter.refresh();
        updateSummary();
        String msg = getString(R.string.warehouse_imported, articles.size());
        if (!data.items.isEmpty()) {
            msg += "\n" + getString(R.string.warehouse_linked, added, data.linkedCount(), data.items.size());
        }
        return msg;
    }

    // ---------- Preuzimanje Excel fajlova iz mejla ----------

    private boolean mailChecking;
    private long lastMailCheck;

    @Override
    protected void onResume() {
        super.onResume();
        // Pri otvaranju aplikacije: sama proveri mejl (najviše jednom u 2 minuta).
        if (mailPrefs().getBoolean("autoFetch", true) && !mailPrefs().getString("password", "").isEmpty()
                && System.currentTimeMillis() - lastMailCheck > 120000) {
            checkMail(false);
        }
    }

    /**
     * Preuzima nove Excel priloge iz Gmail sandučeta. Za svaku vrstu (Usage, stanje magacina, tabela TP)
     * uzima najnoviji fajl i odmah ga ubacuje u kalkulaciju. manual = korisnik je pritisnuo dugme.
     */
    private void checkMail(boolean manual) {
        if (mailChecking) return;
        SharedPreferences sp = mailPrefs();
        String user = sp.getString("email", DEFAULT_SENDER);
        String pass = sp.getString("password", "");
        if (pass.isEmpty()) {
            if (manual) {
                toast(getString(R.string.mail_need_password));
                showMailSettings();
            }
            return;
        }
        mailChecking = true;
        lastMailCheck = System.currentTimeMillis();
        if (manual) toast(getString(R.string.fetch_checking, user));
        long validity = sp.getLong("imapValidity", 0);
        long lastUid = sp.getLong("imapLastUid", 0);
        new Thread(() -> {
            String error = null;
            ImapFetcher.Result result = null;
            List<Item> usage = null;
            List<Warehouse.Article> stock = null;
            List<TpTable.Entry> tp = null;
            String usageName = null, stockName = null, tpName = null;
            try {
                result = ImapFetcher.fetch(user, pass, validity, lastUid, 30);
                // Prilozi su od najnovijeg mejla ka starijem: prvi pronađen fajl svake vrste je najnoviji.
                for (ImapFetcher.Attachment a : result.attachments) {
                    String lower = a.fileName.toLowerCase(Locale.ROOT);
                    // Porudžbine iz Nedeljka i fajlovi iz aplikacije Utrošak (isti Gmail nalog) se preskaču.
                    if (lower.startsWith("porudzbina") || lower.startsWith("utrosak") || lower.startsWith("utrošak")) continue;
                    List<List<Map<Integer, String>>> sheets;
                    try {
                        sheets = XlsxReader.readSheets(new java.io.ByteArrayInputStream(a.data));
                    } catch (Exception e) {
                        continue;
                    }
                    String label = a.fileName + (a.date.isEmpty() ? "" : "  (" + shortDate(a.date) + ")");
                    List<Warehouse.Article> w = firstWarehouse(sheets);
                    if (w != null) {
                        if (stock == null) { stock = w; stockName = label; }
                        continue;
                    }
                    List<TpTable.Entry> t = firstTp(sheets);
                    if (t != null) {
                        if (tp == null) { tp = t; tpName = label; }
                        continue;
                    }
                    if (usage == null) {
                        for (List<Map<Integer, String>> rows : sheets) {
                            List<Item> u = XlsxReader.parseUsage(rows);
                            if (!u.isEmpty()) { usage = u; usageName = label; break; }
                        }
                    }
                }
            } catch (SmtpSender.MailException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = getString(R.string.fetch_failed_generic);
            }
            ImapFetcher.Result res = result;
            String err = error;
            List<Item> fUsage = usage;
            List<Warehouse.Article> fStock = stock;
            List<TpTable.Entry> fTp = tp;
            String fUsageName = usageName, fStockName = stockName, fTpName = tpName;
            runOnUiThread(() -> {
                mailChecking = false;
                if (isFinishing()) return;
                if (err != null) {
                    if (manual) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.fetch_failed_title)
                                .setMessage(err)
                                .setPositiveButton(android.R.string.ok, null)
                                .setNeutralButton(R.string.mail_settings_short, (d, w) -> showMailSettings())
                                .show();
                    }
                    return;
                }
                mailPrefs().edit().putLong("imapValidity", res.uidValidity).putLong("imapLastUid", res.maxUid).apply();
                StringBuilder msg = new StringBuilder();
                // Redosled: tabela TP, pa Usage (dobija TP), pa magacin (povezuje se sa novim Usage-om).
                if (fTp != null) {
                    msg.append("📦 ").append(fTpName).append("\n").append(applyTpTableCore(fTp, fTpName)).append("\n\n");
                }
                if (fUsage != null) {
                    msg.append("📊 ").append(fUsageName).append("\n").append(applyImportCore(fUsage, fUsageName)).append("\n\n");
                }
                if (fStock != null) {
                    msg.append("🏬 ").append(fStockName).append("\n").append(applyWarehouseCore(fStock, fStockName)).append("\n\n");
                }
                if (msg.length() == 0) {
                    if (manual) toast(getString(R.string.fetch_nothing));
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle(R.string.fetch_done_title)
                        .setMessage(msg.toString().trim())
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }).start();
    }

    private static List<Warehouse.Article> firstWarehouse(List<List<Map<Integer, String>>> sheets) {
        for (List<Map<Integer, String>> rows : sheets) {
            List<Warehouse.Article> w = Warehouse.parse(rows);
            if (w != null) return w;
        }
        return null;
    }

    private static List<TpTable.Entry> firstTp(List<List<Map<Integer, String>>> sheets) {
        for (List<Map<Integer, String>> rows : sheets) {
            List<TpTable.Entry> t = TpTable.parse(rows);
            if (t != null && !t.isEmpty()) return t;
        }
        return null;
    }

    /** "Tue, 11 Nov 2026 08:15:00 +0100" -> "11.11. 08:15" */
    private static String shortDate(String rfc) {
        try {
            Date d = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US).parse(rfc.replaceAll("\\s*\\(.*\\)$", ""));
            return new SimpleDateFormat("dd.MM. HH:mm", Locale.ROOT).format(d);
        } catch (Exception e) {
            return rfc;
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "";
    }

    // ---------- Unos stanja ----------

    /** Polja prozora za unos stanja; stanje se uvek čuva u jedinici iz izveštaja, a može se popisivati u TP. */
    private class StockForm {
        final Item it;
        final EditText input, pct, safety, tpQty;
        final Button modeTp, modeUnit;
        /** Tekst koji je aplikacija sama upisala u polje stanja i tačna vrednost (u jedinici) koju predstavlja. */
        String autoText;
        Double autoUnits;
        boolean inTp;

        StockForm(Item it, View box) {
            this.it = it;
            input = box.findViewById(R.id.stock);
            pct = box.findViewById(R.id.pct);
            safety = box.findViewById(R.id.safety);
            tpQty = box.findViewById(R.id.tp_qty);
            modeTp = box.findViewById(R.id.mode_tp);
            modeUnit = box.findViewById(R.id.mode_unit);
        }

        /** TP iz polja; prazno polje = vrednost iz tabele. */
        Double tp() {
            Double v = Item.parse(tpQty.getText().toString());
            if (tpQty.getText().toString().trim().isEmpty()) v = tpTable.find(it);
            return v != null && v > 1e-9 ? v : null;
        }

        /** Stanje u jedinici iz izveštaja; null za prazno ili neispravno polje. */
        Double stockUnits() {
            String text = input.getText().toString();
            if (autoText != null && text.equals(autoText)) return autoUnits;
            Double v = Calc.eval(text);
            if (v == null) return null;
            Double tp = tp();
            return inTp && tp != null ? v * tp : v;
        }

        boolean stockInvalid() {
            String text = input.getText().toString();
            return !text.trim().isEmpty() && !(autoText != null && text.equals(autoText)) && Calc.eval(text) == null;
        }

        /** Upisuje stanje u polje u trenutnom režimu (TP ili jedinica). */
        void showStock(Double units) {
            Double tp = tp();
            String text = units == null ? "" : Item.format(inTp && tp != null ? units / tp : units);
            autoText = text;
            autoUnits = units;
            input.setText(text);
            input.setSelection(text.length());
        }

        void setMode(boolean tpMode) {
            Double units = stockUnits();
            boolean wasInvalid = stockInvalid();
            inTp = tpMode && tp() != null;
            if (!wasInvalid) showStock(units);
            styleModes();
        }

        void styleModes() {
            boolean hasTp = tp() != null;
            modeTp.setEnabled(hasTp);
            modeTp.setAlpha(hasTp ? 1f : 0.4f);
            int on = getColor(R.color.yellow);
            int off = getColor(R.color.mode_off);
            modeTp.setBackgroundTintList(ColorStateList.valueOf(inTp ? on : off));
            modeUnit.setBackgroundTintList(ColorStateList.valueOf(inTp ? off : on));
        }

        /** Proverava i čuva unos; vraća false ako neko polje nije ispravno. */
        boolean save() {
            if (stockInvalid()) {
                input.setError(getString(R.string.calc_invalid));
                return false;
            }
            Double s = stockUnits();
            if (s != null && s < -1e-9) {
                input.setError(getString(R.string.invalid_number));
                return false;
            }
            String pctText = pct.getText().toString();
            Double p = Item.parse(pctText);
            if ((p == null && !pctText.trim().isEmpty()) || (p != null && p < -100)) {
                pct.setError(getString(R.string.invalid_percent));
                return false;
            }
            String safetyText = safety.getText().toString();
            Double ss = Calc.eval(safetyText);
            if ((ss == null && !safetyText.trim().isEmpty()) || (ss != null && ss < 0)) {
                safety.setError(getString(R.string.invalid_number));
                return false;
            }
            String tpText = tpQty.getText().toString();
            Double tpv = Item.parse(tpText);
            if ((tpv == null && !tpText.trim().isEmpty()) || (tpv != null && tpv <= 0)) {
                tpQty.setError(getString(R.string.invalid_number));
                return false;
            }
            Double auto = tpTable.find(it);
            if (tpv == null) {
                it.tp = auto;
                it.tpManual = false;
            } else {
                it.tp = tpv;
                it.tpManual = auto == null || Math.abs(auto - tpv) > 1e-9;
            }
            it.stock = s == null ? null : Math.max(0, s);
            it.percent = p == null ? 0 : p;
            it.safety = ss == null ? 0 : ss;
            if (it.hasTp()) data.countInTp = inTp;
            Storage.save(MainActivity.this, data);
            adapter.refresh();
            updateSummary();
            return true;
        }
    }

    private void showStockDialog(int position) {
        if (position < 0 || position >= adapter.getCount()) return;
        Item it = adapter.getItem(position);
        String unit = it.unit.isEmpty() ? "" : " " + it.unit;
        String unitName = it.unit.isEmpty() ? "-" : it.unit;

        View box = getLayoutInflater().inflate(R.layout.dialog_item, null);
        StockForm f = new StockForm(it, box);
        TextView info = box.findViewById(R.id.info);
        TextView neededView = box.findViewById(R.id.needed);
        TextView neededTpView = box.findViewById(R.id.needed_tp);
        TextView orderView = box.findViewById(R.id.to_order);
        TextView orderSub = box.findViewById(R.id.to_order_sub);
        TextView neededDetail = box.findViewById(R.id.needed_detail);
        TextView stockLabel = box.findViewById(R.id.stock_label);
        TextView stockCalc = box.findViewById(R.id.stock_calc);
        TextView safetyLabel = box.findViewById(R.id.safety_label);
        TextView tpLabel = box.findViewById(R.id.tp_label);
        TextView tpHint = box.findViewById(R.id.tp_hint);
        EditText input = f.input;

        String infoText = getString(R.string.dialog_usage, Item.format(it.usage) + unit);
        if (!it.code.isEmpty()) infoText = getString(R.string.dialog_code, it.code) + "   •   " + infoText;
        info.setText(infoText);
        safetyLabel.setText(getString(R.string.label_safety, unitName));
        tpLabel.setText(getString(R.string.label_tp, unitName));
        f.modeUnit.setText(unitName);
        if (it.hasTp()) f.tpQty.setText(Item.format(it.tp));
        if (it.hasPercent()) f.pct.setText(Item.format(it.percent).replace(',', '.'));
        if (it.hasSafety()) f.safety.setText(Item.format(it.safety));
        f.inTp = data.countInTp && it.hasTp();
        f.showStock(it.stock);
        f.styleModes();

        // Korekcija: dugmad -10 / +10
        Button minus = box.findViewById(R.id.pct_minus);
        Button plus = box.findViewById(R.id.pct_plus);
        View.OnClickListener step = v -> {
            Double cur = Item.parse(f.pct.getText().toString());
            double next = (cur == null ? 0 : cur) + (v == plus ? 10 : -10);
            if (next < -100) next = -100;
            f.pct.setText(Math.abs(next) < 1e-9 ? "" : Item.format(next).replace(',', '.'));
        };
        minus.setOnClickListener(step);
        plus.setOnClickListener(step);

        // Rezultat na vrhu se racuna dok se kuca.
        Runnable updatePreview = () -> {
            Double tp = f.tp();
            if (f.inTp && tp == null) f.inTp = false;
            f.styleModes();
            stockLabel.setText(f.inTp ? getString(R.string.label_stock_tp) : getString(R.string.label_stock_unit, unitName));
            tpHint.setText(!f.tpQty.getText().toString().trim().isEmpty()
                    ? (tpTable.find(it) != null && tp != null && Math.abs(tpTable.find(it) - tp) < 1e-9
                        ? getString(R.string.tp_hint_auto) : getString(R.string.tp_hint_manual))
                    : getString(R.string.tp_hint_none, unitName));

            Double s = f.stockUnits();
            Double p = Item.parse(f.pct.getText().toString());
            Double ss = Calc.eval(f.safety.getText().toString());
            Item tmp = new Item(it.code, it.name, it.unit, it.usage, s);
            tmp.percent = p == null ? 0 : Math.max(-100, p);
            tmp.safety = ss == null ? 0 : Math.max(0, ss);
            tmp.tp = tp;

            // Prikaz pretvaranja i rezultata računa ispod polja stanja.
            String text = input.getText().toString();
            if (f.stockInvalid()) {
                stockCalc.setText(getString(R.string.calc_invalid));
                stockCalc.setTextColor(getColor(R.color.shortage));
                stockCalc.setVisibility(View.VISIBLE);
            } else if (s != null && (f.inTp || Calc.isExpression(text) || tp != null)) {
                StringBuilder sb = new StringBuilder();
                if (Calc.isExpression(text)) {
                    sb.append(getString(R.string.calc_equals, Item.format(f.inTp ? s / tp : s)
                            + (f.inTp ? " TP" : unit)));
                }
                if (f.inTp) sb.append(sb.length() > 0 ? "  →  " : "= ").append(Item.format(s)).append(unit);
                else if (tp != null) sb.append(sb.length() > 0 ? "  →  " : "= ")
                        .append(getString(R.string.tp_amount, Item.format(s / tp)));
                stockCalc.setText(sb);
                stockCalc.setTextColor(getColor(R.color.primary));
                stockCalc.setVisibility(View.VISIBLE);
            } else {
                stockCalc.setVisibility(View.GONE);
            }

            neededView.setText(Item.format(tmp.needed()) + unit);
            if (tmp.hasTp()) {
                neededTpView.setText(getString(R.string.needed_tp, Item.format(tmp.toTp(tmp.needed()))));
                neededTpView.setVisibility(View.VISIBLE);
            } else {
                neededTpView.setVisibility(View.GONE);
            }
            if (s == null) {
                orderView.setText("—");
                orderSub.setVisibility(View.GONE);
            } else if (tmp.hasTp()) {
                orderView.setText(getString(R.string.tp_amount, Item.format(tmp.toOrderTp())));
                orderSub.setText(getString(R.string.order_tp_sub, Item.format(tmp.toOrderTpQty()) + unit,
                        Item.format(tmp.toOrder()) + unit));
                orderSub.setVisibility(View.VISIBLE);
            } else {
                orderView.setText(Item.format(tmp.toOrder()) + unit);
                orderSub.setVisibility(View.GONE);
            }
            StringBuilder why = new StringBuilder(getString(R.string.needed_detail, Item.format(it.usage)));
            if (tmp.hasPercent()) why.append(' ').append(Item.formatPercent(tmp.percent));
            if (tmp.hasSafety()) why.append("  + ").append(getString(R.string.row_safety, Item.format(tmp.safety)));
            neededDetail.setText(tmp.hasPercent() || tmp.hasSafety() ? why : "");
            neededDetail.setVisibility(tmp.hasPercent() || tmp.hasSafety() ? View.VISIBLE : View.GONE);
        };
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updatePreview.run(); }
        };
        input.addTextChangedListener(watcher);
        f.pct.addTextChangedListener(watcher);
        f.safety.addTextChangedListener(watcher);
        f.tpQty.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                // Nova količina u TP: broj TP iz polja stanja se ponovo preračunava.
                f.autoText = null;
                updatePreview.run();
            }
        });

        f.modeTp.setOnClickListener(v -> { f.setMode(true); updatePreview.run(); input.requestFocus(); });
        f.modeUnit.setOnClickListener(v -> { f.setMode(false); updatePreview.run(); input.requestFocus(); });

        // Mini kalkulator: operacije se upisuju u polje stanja, "=" upisuje rezultat.
        int[] opIds = {R.id.calc_plus, R.id.calc_minus, R.id.calc_times, R.id.calc_div, R.id.calc_open, R.id.calc_close};
        String[] ops = {"+", "-", "*", "/", "(", ")"};
        for (int i = 0; i < opIds.length; i++) {
            String op = ops[i];
            box.findViewById(opIds[i]).setOnClickListener(v -> insertAtCursor(input, op));
        }
        box.findViewById(R.id.calc_eq).setOnClickListener(v -> {
            Double r = Calc.eval(input.getText().toString());
            if (r == null) {
                if (!input.getText().toString().trim().isEmpty()) input.setError(getString(R.string.calc_invalid));
                return;
            }
            String t = Item.format(r);
            input.setText(t);
            input.setSelection(t.length());
        });
        box.findViewById(R.id.calc_back).setOnClickListener(v -> {
            Editable e = input.getText();
            int a = Math.min(input.getSelectionStart(), input.getSelectionEnd());
            int b = Math.max(input.getSelectionStart(), input.getSelectionEnd());
            if (a < 0) a = b = e.length();
            if (a != b) e.delete(a, b);
            else if (a > 0) e.delete(a - 1, a);
        });
        updatePreview.run();

        AlertDialog[] holder = new AlertDialog[1];
        View warehouseBox = box.findViewById(R.id.warehouse_box);
        if (data.hasWarehouse()) {
            TextView wh = box.findViewById(R.id.warehouse);
            wh.setText(ItemAdapter.warehouseLine(this, data, it));
            wh.setTextColor(getColor(data.shortage(it) ? R.color.shortage : R.color.navy));
            Button link = box.findViewById(R.id.link);
            link.setText(data.isLinked(it) ? R.string.link_short : R.string.link_warehouse_short);
            link.setOnClickListener(v -> {
                if (!f.save()) return;
                holder[0].dismiss();
                showLinkPicker(it, () -> {
                    int idx = indexOf(it);
                    if (idx >= 0) showStockDialog(idx);
                });
            });
        } else {
            warehouseBox.setVisibility(View.GONE);
        }

        boolean hasNext = position + 1 < adapter.getCount();
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(it.name.trim())
                .setView(box)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (hasNext) b.setNeutralButton(R.string.save_next, null);
        AlertDialog dialog = b.create();
        holder[0] = dialog;

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (f.save()) dialog.dismiss();
            });
            if (hasNext) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    if (f.save()) {
                        dialog.dismiss();
                        // Ako je artikal nestao iz filtera "samo za poručivanje", sledeći je na istoj poziciji.
                        int idx = indexOf(it);
                        int next = idx >= 0 ? idx + 1 : position;
                        if (next < adapter.getCount()) showStockDialog(next);
                    }
                });
            }
            input.requestFocus();
            input.selectAll();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                (hasNext ? dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                        : dialog.getButton(AlertDialog.BUTTON_POSITIVE)).performClick();
                return true;
            }
            return false;
        });
        if (dialog.getWindow() != null) {
            // Prozor se smanjuje i skroluje kad se otvori tastatura, pa se sve vidi.
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
    }

    /** Upisuje znak računske operacije na mesto kursora (ako je ceo broj označen, dodaje ga na kraj). */
    private static void insertAtCursor(EditText input, String op) {
        Editable e = input.getText();
        int a = Math.min(input.getSelectionStart(), input.getSelectionEnd());
        int b = Math.max(input.getSelectionStart(), input.getSelectionEnd());
        if (a < 0 || (a == 0 && b == e.length() && b > 0)) a = b = e.length();
        e.replace(a, b, op);
        input.setSelection(a + op.length());
    }

    // ---------- Povezivanje sa magacinom ----------

    /** Izbor jedne ili vise sifara iz magacina; stanja izabranih sifara se sabiraju. */
    private void showLinkPicker(Item it, Runnable done) {
        List<Warehouse.Article> sorted = Warehouse.suggestions(it.name, data.warehouse);
        List<Warehouse.Article> shown = new ArrayList<>(sorted);
        // Vec povezane sifre idu na vrh liste.
        Set<Warehouse.Article> selected = new LinkedHashSet<>(data.articlesFor(it));
        sorted.removeAll(selected);
        sorted.addAll(0, selected);
        boolean wasLinked = !selected.isEmpty();

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.link_hint, it.name.trim(), it.unit));
        box.addView(hint);

        TextView total = new TextView(this);
        total.setTextSize(16);
        total.setTypeface(null, Typeface.BOLD);
        total.setPadding(0, pad / 2, 0, 0);
        box.addView(total);
        Runnable updateTotal = () -> {
            double sum = 0;
            for (Warehouse.Article a : selected) sum += a.available;
            String unit = selected.isEmpty() ? "" : selected.iterator().next().unitLabel();
            total.setText(getString(R.string.link_selected, selected.size(), Item.format(sum), unit));
        };
        updateTotal.run();

        EditText search = new EditText(this);
        search.setHint(R.string.search_hint);
        search.setSingleLine(true);
        box.addView(search);

        ListView list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ArrayAdapter<String> labels = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice);
        list.setAdapter(labels);
        Runnable fill = () -> {
            String q = Warehouse.normalize(search.getText().toString());
            shown.clear();
            labels.clear();
            for (Warehouse.Article a : sorted) {
                if (!q.isEmpty() && !Warehouse.normalize(a.name + " " + a.code + " " + a.extCode).contains(q)) continue;
                shown.add(a);
                labels.add(a.name + "\n" + a.code + "   •   "
                        + getString(R.string.wh_amount, Item.format(a.available), a.unitLabel()));
            }
            labels.notifyDataSetChanged();
            list.clearChoices();
            for (int i = 0; i < shown.size(); i++) list.setItemChecked(i, selected.contains(shown.get(i)));
        };
        box.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.45)));
        fill.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { fill.run(); }
        });
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Warehouse.Article a = shown.get(pos);
            if (list.isItemChecked(pos)) selected.add(a);
            else selected.remove(a);
            updateTotal.run();
        });

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(R.string.link_title)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    if (!selected.isEmpty() || wasLinked) {
                        data.link(it, new ArrayList<>(selected));
                        saveAndRefresh();
                    }
                    done.run();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> done.run());
        if (wasLinked) {
            b.setNeutralButton(R.string.unlink, (d, w) -> {
                data.link(it, new ArrayList<>());
                saveAndRefresh();
                done.run();
            });
        }
        b.show();
    }

    private void saveAndRefresh() {
        Storage.save(this, data);
        adapter.refresh();
        updateSummary();
    }

    private int indexOf(Item it) {
        for (int i = 0; i < adapter.getCount(); i++) if (adapter.getItem(i) == it) return i;
        return -1;
    }

    // ---------- Pregled ----------

    private void updateSummary() {
        if (data.items.isEmpty()) {
            summary.setVisibility(View.GONE);
            return;
        }
        int entered = 0;
        int toOrder = 0;
        for (Item it : data.items) {
            if (it.hasStock()) entered++;
            if (it.toOrder() > 0) toOrder++;
        }
        summary.setVisibility(View.VISIBLE);
        setStat(R.id.stat_entered, getString(R.string.stat_entered, entered, data.items.size()));
        setStat(R.id.stat_order, getString(R.string.stat_order, toOrder));
        if (data.hasWarehouse()) {
            int shortage = 0;
            for (Item it : data.items) if (data.shortage(it)) shortage++;
            setStat(R.id.stat_shortage, getString(R.string.stat_shortage, shortage));
        } else {
            setStat(R.id.stat_shortage, getString(R.string.stat_items, data.items.size()));
        }
        StringBuilder src = new StringBuilder();
        if (data.source != null && !data.source.isEmpty()) src.append(data.source);
        if (src.length() > 0) src.append("  •  ");
        src.append(getString(R.string.summary_tp, data.tpCount(), data.items.size()));
        if (data.hasWarehouse()) {
            if (src.length() > 0) src.append("\n");
            src.append(data.warehouseSource).append("  •  ")
                    .append(getString(R.string.summary_linked, data.linkedCount(), data.items.size()));
        }
        TextView source = findViewById(R.id.summary_source);
        source.setText(src);
        source.setVisibility(src.length() > 0 ? View.VISIBLE : View.GONE);
    }

    /** Tekst "Naziv\nBroj": broj ispod naziva, veci i podebljan. */
    private void setStat(int id, String text) {
        TextView tv = findViewById(id);
        SpannableString span = new SpannableString(text);
        int nl = text.indexOf('\n');
        if (nl >= 0) {
            span.setSpan(new RelativeSizeSpan(1.5f), nl + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(Typeface.BOLD), nl + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tv.setText(span);
    }

    // ---------- Izvoz ----------

    private boolean hasOrder() {
        for (Item it : data.items) if (it.toOrder() > 0) return true;
        toast(getString(R.string.nothing_to_order));
        return false;
    }

    private void exportExcel() {
        if (!hasOrder()) return;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        i.putExtra(Intent.EXTRA_TITLE, "Porudzbina_" + date + ".xlsx");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            toast(getString(R.string.no_file_picker));
        }
    }

    private void writeExport(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new Exception(getString(R.string.cannot_open));
            XlsxWriter.writeOrder(out, data, true);
            toast(getString(R.string.saved));
        } catch (Exception e) {
            toast(getString(R.string.save_failed) + ": " + e.getMessage());
        }
    }

    /** Tekst porudžbine (za deljenje i za telo mejla). */
    private String orderText(String date) {
        StringBuilder sb = new StringBuilder(getString(R.string.share_title, date)).append("\n\n");
        for (Item it : data.items) {
            double o = it.toOrder();
            if (o <= 0) continue;
            if (!it.code.isEmpty()) sb.append(it.code).append("  ");
            sb.append(it.name.trim()).append(" – ");
            if (it.hasTp()) {
                sb.append(getString(R.string.tp_amount, Item.format(it.toOrderTp()))).append(" (")
                        .append(Item.format(it.toOrderTpQty()));
                if (!it.unit.isEmpty()) sb.append(' ').append(it.unit);
                sb.append(')');
            } else {
                sb.append(Item.format(o));
                if (!it.unit.isEmpty()) sb.append(' ').append(it.unit);
            }
            if (it.hasPercent()) sb.append(" (").append(Item.formatPercent(it.percent)).append(')');
            if (data.shortage(it)) {
                sb.append("  ⚠ ").append(getString(R.string.share_shortage,
                        Item.format(data.availableFor(it)), data.unitFor(it)));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private void shareText() {
        if (!hasOrder()) return;
        String date = new SimpleDateFormat("dd.MM.yyyy.", Locale.ROOT).format(new Date());
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_title, date));
        send.putExtra(Intent.EXTRA_TEXT, orderText(date));
        startActivity(Intent.createChooser(send, getString(R.string.action_share)));
    }

    // ---------- Slanje mejlom ----------

    private SharedPreferences mailPrefs() {
        return getSharedPreferences(MAIL_PREFS, MODE_PRIVATE);
    }

    /** Adrese primalaca iz podešavanja (razdvojene zarezom, tačka-zarezom, razmakom ili novim redom). */
    private List<String> recipients() {
        List<String> out = new ArrayList<>();
        for (String r : mailPrefs().getString("to", "").split("[,;\\s]+")) {
            if (!r.trim().isEmpty()) out.add(r.trim());
        }
        return out;
    }

    private static boolean validEmail(String e) {
        return e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }

    /** Jedno dugme: Excel porudžbina ide odmah na zadate adrese preko Gmail naloga. */
    private void sendMail() {
        if (!hasOrder()) return;
        if (recipients().isEmpty()) {
            toast(getString(R.string.mail_no_recipients));
            showMailSettings();
            return;
        }
        String date = new SimpleDateFormat("dd.MM.yyyy.", Locale.ROOT).format(new Date());
        String fileName = "Porudzbina_" + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".xlsx";
        String subject = getString(R.string.share_title, date);
        String body = orderText(date);
        byte[] xlsx;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            XlsxWriter.writeOrder(bos, data, true);
            xlsx = bos.toByteArray();
        } catch (Exception e) {
            toast(getString(R.string.save_failed) + ": " + e.getMessage());
            return;
        }
        String pass = mailPrefs().getString("password", "");
        if (pass.isEmpty()) {
            // Bez lozinke za aplikaciju: otvara se Gmail sa svim upisanim, ostaje samo "Pošalji".
            openMailApp(recipients(), subject, body, fileName, xlsx);
            return;
        }
        sendViaGmail(recipients(), subject, body, fileName, xlsx, null);
    }

    /** Šalje u pozadini preko smtp.gmail.com; posle uspeha poziva done (ako nije null). */
    private void sendViaGmail(List<String> to, String subject, String body, String fileName, byte[] xlsx, Runnable done) {
        SharedPreferences sp = mailPrefs();
        String user = sp.getString("email", DEFAULT_SENDER);
        String pass = sp.getString("password", "");
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle(R.string.mail_sending_title)
                .setMessage(getString(R.string.mail_sending, String.join(", ", to)))
                .setCancelable(false)
                .show();
        new Thread(() -> {
            String error = null;
            try {
                SmtpSender.send(user, pass, to, subject, body, fileName, xlsx);
            } catch (SmtpSender.MailException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = getString(R.string.mail_failed_generic);
            }
            String err = error;
            runOnUiThread(() -> {
                progress.dismiss();
                if (err == null) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.mail_sent_title)
                            .setMessage(getString(R.string.mail_sent, String.join("\n", to)))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    if (done != null) done.run();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.mail_failed_title)
                            .setMessage(err)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(R.string.mail_settings_short, (d, w) -> showMailSettings())
                            .show();
                }
            });
        }).start();
    }

    /** Otvara mejl aplikaciju sa primaocima, naslovom, tekstom i Excel prilogom. */
    private void openMailApp(List<String> to, String subject, String body, String fileName, byte[] xlsx) {
        Uri uri;
        try {
            File dir = AttachmentProvider.dir(this);
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            File f = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(xlsx);
            }
            uri = Uri.parse("content://" + AttachmentProvider.AUTHORITY + "/" + Uri.encode(fileName));
        } catch (Exception e) {
            toast(getString(R.string.save_failed) + ": " + e.getMessage());
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(AttachmentProvider.MIME_XLSX);
        send.putExtra(Intent.EXTRA_EMAIL, to.toArray(new String[0]));
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, body);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri(fileName, uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setSelector(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")));
        try {
            startActivity(send);
            toast(getString(R.string.mail_opened_app));
        } catch (Exception e) {
            send.setSelector(null);
            try {
                startActivity(Intent.createChooser(send, subject));
            } catch (Exception e2) {
                toast(getString(R.string.mail_no_app));
            }
        }
    }

    /** Podešavanja: nalog sa koga se šalje, lozinka za aplikaciju i adrese primalaca. */
    private void showMailSettings() {
        SharedPreferences sp = mailPrefs();
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView l1 = label(R.string.mail_label_to);
        box.addView(l1);
        EditText to = new EditText(this);
        to.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        to.setMinLines(2);
        to.setHint(R.string.mail_hint_to);
        to.setText(String.join("\n", recipients()));
        box.addView(to);

        box.addView(label(R.string.mail_label_from));
        EditText from = new EditText(this);
        from.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        from.setSingleLine(true);
        from.setText(sp.getString("email", DEFAULT_SENDER));
        box.addView(from);

        box.addView(label(R.string.mail_label_password));
        EditText pass = new EditText(this);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setSingleLine(true);
        boolean hasPass = !sp.getString("password", "").isEmpty();
        pass.setHint(hasPass ? R.string.mail_hint_password_saved : R.string.mail_hint_password);
        box.addView(pass);

        CheckBox auto = new CheckBox(this);
        auto.setText(R.string.mail_auto_fetch);
        auto.setChecked(sp.getBoolean("autoFetch", true));
        box.addView(auto);

        TextView help = new TextView(this);
        help.setText(R.string.mail_help);
        help.setTextColor(getColor(R.color.text_secondary));
        help.setTextSize(12);
        help.setPadding(0, pad / 3, 0, pad / 3);
        box.addView(help);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(box);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.mail_settings_title)
                .setView(scroll)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.mail_test, null)
                .create();
        dialog.setOnShowListener(d -> {
            // Vraća true ako je sve ispravno i sačuvano.
            java.util.function.BooleanSupplier save = () -> {
                List<String> list = new ArrayList<>();
                for (String r : to.getText().toString().split("[,;\\s]+")) {
                    String t = r.trim();
                    if (t.isEmpty()) continue;
                    if (!validEmail(t)) {
                        to.setError(getString(R.string.mail_invalid, t));
                        return false;
                    }
                    if (!list.contains(t)) list.add(t);
                }
                String f = from.getText().toString().trim();
                if (!f.isEmpty() && !validEmail(f)) {
                    from.setError(getString(R.string.mail_invalid, f));
                    return false;
                }
                SharedPreferences.Editor e = sp.edit();
                e.putString("to", String.join(",", list));
                e.putBoolean("autoFetch", auto.isChecked());
                e.putString("email", f.isEmpty() ? DEFAULT_SENDER : f);
                String p = pass.getText().toString().replaceAll("\\s+", "");
                if (!p.isEmpty()) e.putString("password", p);
                e.apply();
                return true;
            };
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (save.getAsBoolean()) {
                    dialog.dismiss();
                    toast(getString(R.string.mail_saved));
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (!save.getAsBoolean()) return;
                if (mailPrefs().getString("password", "").isEmpty()) {
                    pass.setError(getString(R.string.mail_need_password));
                    return;
                }
                List<String> rcpt = recipients();
                if (rcpt.isEmpty()) rcpt.add(mailPrefs().getString("email", DEFAULT_SENDER));
                dialog.dismiss();
                sendViaGmail(rcpt, getString(R.string.mail_test_subject), getString(R.string.mail_test_body),
                        null, null, null);
            });
        });
        dialog.show();
    }

    private TextView label(int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.navy));
        t.setTextSize(14);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_clear)
                .setMessage(R.string.clear_confirm)
                .setPositiveButton(R.string.clear_yes, (d, w) -> {
                    for (Item it : data.items) it.stock = null;
                    Storage.save(this, data);
                    adapter.refresh();
                    updateSummary();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClearPercent() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_clear_percent)
                .setMessage(R.string.clear_percent_confirm)
                .setPositiveButton(R.string.clear_yes, (d, w) -> {
                    for (Item it : data.items) it.percent = 0;
                    saveAndRefresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
