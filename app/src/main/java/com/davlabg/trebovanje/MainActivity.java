package com.davlabg.trebovanje;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_IMPORT = 1;
    private static final int REQ_EXPORT = 2;

    private Storage.Data data;
    private ItemAdapter adapter;
    private TextView summary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        data = Storage.load(this);
        adapter = new ItemAdapter(this);
        summary = findViewById(R.id.summary);

        ListView list = findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.empty));
        list.setOnItemClickListener((parent, view, position, id) -> showStockDialog(position));

        Button importButton = findViewById(R.id.import_button);
        importButton.setOnClickListener(v -> pickExcel());

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

        adapter.setItems(data.items);
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
        if (id == R.id.action_import) {
            pickExcel();
        } else if (id == R.id.action_export) {
            exportExcel();
        } else if (id == R.id.action_share) {
            shareText();
        } else if (id == R.id.action_clear) {
            confirmClear();
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
                List<Item> items = XlsxReader.read(in);
                runOnUiThread(() -> applyImport(items, fileName));
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
        // Zadrzi vec uneta stanja za iste artikle (po sifri, a ako je nema, po nazivu).
        Map<String, Double> old = new HashMap<>();
        for (Item it : data.items) if (it.stock != null) old.put(it.key(), it.stock);
        int kept = 0;
        for (Item it : items) {
            Double s = old.get(it.key());
            if (s != null) {
                it.stock = s;
                kept++;
            }
        }
        data.items = items;
        data.source = fileName;
        Storage.save(this, data);
        adapter.setItems(data.items);
        updateSummary();
        String msg = getString(R.string.imported, items.size());
        if (kept > 0) msg += "\n" + getString(R.string.kept_stock, kept);
        toast(msg);
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

    private void showStockDialog(int position) {
        if (position < 0 || position >= adapter.getCount()) return;
        Item it = adapter.getItem(position);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView info = new TextView(this);
        String unit = it.unit.isEmpty() ? "" : " " + it.unit;
        String infoText = getString(R.string.dialog_usage, Item.format(it.usage) + unit);
        if (!it.code.isEmpty()) infoText = getString(R.string.dialog_code, it.code) + "\n" + infoText;
        info.setText(infoText);
        info.setTextSize(16);
        box.addView(info);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(R.string.dialog_hint);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(24);
        input.setSelectAllOnFocus(true);
        input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (it.hasStock()) input.setText(Item.format(it.stock));
        box.addView(input);

        TextView preview = new TextView(this);
        preview.setTextSize(18);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, pad / 2, 0, 0);
        box.addView(preview);

        Runnable updatePreview = () -> {
            Double s = Item.parse(input.getText().toString());
            if (s == null) {
                preview.setText("");
            } else {
                Item tmp = new Item(it.code, it.name, it.unit, it.usage, s);
                preview.setText(getString(R.string.dialog_order, Item.format(tmp.toOrder()) + unit));
            }
        };
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updatePreview.run(); }
        });
        updatePreview.run();

        boolean hasNext = position + 1 < adapter.getCount();
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(it.name)
                .setView(box)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (hasNext) b.setNeutralButton(R.string.save_next, null);
        AlertDialog dialog = b.create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (saveStock(it, input)) dialog.dismiss();
            });
            if (hasNext) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    if (saveStock(it, input)) {
                        dialog.dismiss();
                        // Ako je artikal nestao iz filtera "samo za poručivanje", sledeći je na istoj poziciji.
                        int idx = indexOf(it);
                        int next = idx >= 0 ? idx + 1 : position;
                        if (next < adapter.getCount()) showStockDialog(next);
                    }
                });
            }
            input.requestFocus();
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
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
    }

    private int indexOf(Item it) {
        for (int i = 0; i < adapter.getCount(); i++) if (adapter.getItem(i) == it) return i;
        return -1;
    }

    private boolean saveStock(Item it, EditText input) {
        String text = input.getText().toString();
        Double s = Item.parse(text);
        if (s == null && !text.trim().isEmpty()) {
            input.setError(getString(R.string.invalid_number));
            return false;
        }
        if (s != null && s < 0) {
            input.setError(getString(R.string.invalid_number));
            return false;
        }
        it.stock = s;
        Storage.save(this, data);
        adapter.refresh();
        updateSummary();
        return true;
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
        String text = getString(R.string.summary, entered, data.items.size(), toOrder);
        if (data.source != null && !data.source.isEmpty()) text = data.source + "\n" + text;
        summary.setText(text);
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
            XlsxWriter.writeOrder(out, data.items, true);
            toast(getString(R.string.saved));
        } catch (Exception e) {
            toast(getString(R.string.save_failed) + ": " + e.getMessage());
        }
    }

    private void shareText() {
        if (!hasOrder()) return;
        String date = new SimpleDateFormat("dd.MM.yyyy.", Locale.ROOT).format(new Date());
        StringBuilder sb = new StringBuilder(getString(R.string.share_title, date)).append("\n\n");
        for (Item it : data.items) {
            double o = it.toOrder();
            if (o <= 0) continue;
            if (!it.code.isEmpty()) sb.append(it.code).append("  ");
            sb.append(it.name.trim()).append(" – ").append(Item.format(o));
            if (!it.unit.isEmpty()) sb.append(' ').append(it.unit);
            sb.append('\n');
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_title, date));
        send.putExtra(Intent.EXTRA_TEXT, sb.toString().trim());
        startActivity(Intent.createChooser(send, getString(R.string.action_share)));
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
