package ai.arena.portscanner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the Scan (📡) page. Holds references to all input widgets and exposes
 * actions through {@link Callbacks} so {@link MainActivity} stays glue-only.
 */
final class ScanPage {

    interface Callbacks {
        void onStart(ScanConfig cfg, List<String> ips);
        void onStop();
        void onResolveDomain(String input);
        void onImportFile();
    }

    private final Context ctx;
    private final Callbacks cb;

    // header / inputs
    EditText targetInput;
    EditText rangeInput;
    Spinner cdnPresetSpinner;
    Spinner engineSpinner;
    EditText sniInput;
    EditText hostInput;
    EditText pathInput;
    EditText expectedStatusInput;
    LinearLayout pairFieldsBox;

    // buttons
    Button pasteBtn, importBtn, resolveBtn, clearBtn, generateBtn, addRangeBtn;
    Button startBtn, stopBtn;

    // live stats
    ProgressBar progressBar;
    TextView progressLine;
    TextView miniTotal, miniOk, miniMaybe, miniFail;

    private final LinearLayout root;

    ScanPage(Context ctx, Callbacks cb) {
        this.ctx = ctx;
        this.cb = cb;
        this.root = UiKit.column(ctx);
        build();
    }

    View view() { return root; }

    private void build() {
        // ----- Hero header -----
        LinearLayout hero = UiKit.card(ctx);
        hero.setBackground(UiKit.gradient(UiKit.PRIMARY2, UiKit.PRIMARY, UiKit.dp(ctx, 18), 0));
        LinearLayout heroRow = UiKit.row(ctx);
        TextView logo = UiKit.text(ctx, "⚡", 28, android.graphics.Color.WHITE, true);
        logo.setPadding(UiKit.dp(ctx, 6), UiKit.dp(ctx, 6), UiKit.dp(ctx, 6), UiKit.dp(ctx, 6));
        logo.setBackground(UiKit.rounded(android.graphics.Color.parseColor("#33000000"), UiKit.dp(ctx, 14)));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(UiKit.dp(ctx, 50), UiKit.dp(ctx, 50));
        heroRow.addView(logo, logoLp);

        LinearLayout titleCol = UiKit.column(ctx);
        titleCol.setPadding(UiKit.dp(ctx, 12), 0, 0, 0);
        TextView title = UiKit.text(ctx, "EdgePulse", 22, android.graphics.Color.WHITE, true);
        TextView subtitle = UiKit.text(ctx, "اسکن چندمرحله‌ای CDN / Edge", 12, android.graphics.Color.parseColor("#E2E8F0"), false);
        titleCol.addView(title);
        titleCol.addView(subtitle);
        heroRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView ver = UiKit.pill(ctx, "v2.0", android.graphics.Color.parseColor("#33000000"));
        heroRow.addView(ver);
        hero.addView(heroRow);
        root.addView(hero);

        // ----- Target input card -----
        LinearLayout targetCard = UiKit.card(ctx);
        targetCard.addView(UiKit.label(ctx, "📂 لیست هدف (IP، CIDR، بازه، یا دامنه)"));
        targetInput = UiKit.editMultilineFixed(ctx, "1.1.1.1\n8.8.8.8\nexample.com\n104.16.0.0/24\n104.16.0.1-104.16.0.20", 200);
        targetCard.addView(targetInput);

        LinearLayout toolbar = UiKit.row(ctx);
        pasteBtn = UiKit.button(ctx, "📋 الصاق", UiKit.ACCENT);
        importBtn = UiKit.button(ctx, "📄 فایل txt", UiKit.ACCENT);
        resolveBtn = UiKit.button(ctx, "🌐 DoH دامنه", UiKit.INFO);
        clearBtn = UiKit.button(ctx, "🧹 پاک", android.graphics.Color.parseColor("#1F2937"));
        toolbar.addView(pasteBtn, UiKit.weight(ctx));
        toolbar.addView(importBtn, UiKit.weight(ctx));
        toolbar.addView(resolveBtn, UiKit.weight(ctx));
        toolbar.addView(clearBtn, UiKit.weight(ctx));
        targetCard.addView(toolbar);

        pasteBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pasteFromClipboard(); }
        });
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cb.onImportFile(); }
        });
        resolveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cb.onResolveDomain(targetInput.getText().toString()); }
        });
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { targetInput.setText(""); }
        });
        root.addView(targetCard);

        // ----- CIDR generator collapsible -----
        LinearLayout genBody = UiKit.collapsible(ctx, root, "🌐 تولید لیست از CIDR / بازه", false);
        rangeInput = UiKit.editMultilineFixed(ctx, "104.16.0.0/24\n172.64.0.1-172.64.0.50", 90);
        genBody.addView(rangeInput);
        LinearLayout genRow = UiKit.row(ctx);
        generateBtn = UiKit.button(ctx, "⚙️ تولید + پیش‌نمایش", UiKit.ACCENT);
        addRangeBtn = UiKit.button(ctx, "➕ افزودن به هدف", UiKit.SUCCESS);
        genRow.addView(generateBtn, UiKit.weight(ctx));
        genRow.addView(addRangeBtn, UiKit.weight(ctx));
        genBody.addView(genRow);
        final TextView genStat = UiKit.caption(ctx, "تولیدشده: 0");
        genBody.addView(genStat);
        generateBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<String> ips = IpListParser.parse(rangeInput.getText().toString());
                genStat.setText("تولیدشده: " + ips.size());
                Toast.makeText(ctx, ips.size() + " IP تولید شد", Toast.LENGTH_SHORT).show();
            }
        });
        addRangeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<String> ips = IpListParser.parse(rangeInput.getText().toString());
                if (ips.isEmpty()) { Toast.makeText(ctx, "ابتدا تولید کن", Toast.LENGTH_SHORT).show(); return; }
                appendToTarget(ips);
                genStat.setText("افزوده شد: " + ips.size());
            }
        });

        // ----- CDN Presets -----
        LinearLayout presetsCard = UiKit.card(ctx);
        presetsCard.addView(UiKit.label(ctx, "🛰️ Presetهای CDN"));
        LinearLayout presetRow = UiKit.row(ctx);
        Button akamai = UiKit.button(ctx, "Akamai", android.graphics.Color.parseColor("#1F2937"));
        Button cloudflare = UiKit.button(ctx, "Cloudflare", android.graphics.Color.parseColor("#1F2937"));
        Button fastly = UiKit.button(ctx, "Fastly", android.graphics.Color.parseColor("#1F2937"));
        Button custom = UiKit.button(ctx, "Custom", android.graphics.Color.parseColor("#1F2937"));
        presetRow.addView(akamai, UiKit.weight(ctx));
        presetRow.addView(cloudflare, UiKit.weight(ctx));
        presetRow.addView(fastly, UiKit.weight(ctx));
        presetRow.addView(custom, UiKit.weight(ctx));
        presetsCard.addView(presetRow);
        akamai.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { rangeInput.setText(CdnPresets.AKAMAI); Toast.makeText(ctx, "Akamai preset", Toast.LENGTH_SHORT).show(); }
        });
        cloudflare.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { rangeInput.setText(CdnPresets.CLOUDFLARE); Toast.makeText(ctx, "Cloudflare preset", Toast.LENGTH_SHORT).show(); }
        });
        fastly.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { rangeInput.setText(CdnPresets.FASTLY); Toast.makeText(ctx, "Fastly preset", Toast.LENGTH_SHORT).show(); }
        });
        custom.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                rangeInput.setText("");
                rangeInput.setHint("CIDR یا range سفارشی");
            }
        });
        root.addView(presetsCard);

        // ----- Engine selector -----
        LinearLayout engineCard = UiKit.card(ctx);
        engineCard.addView(UiKit.label(ctx, "🎯 موتور اسکن"));
        engineSpinner = new Spinner(ctx);
        ArrayAdapter<String> a = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item,
                new String[]{"Quick Scan — سریع (TCP + TLS)", "Pair Test — اعتبارسنجی Host+Path"});
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        engineSpinner.setAdapter(a);
        engineCard.addView(engineSpinner, UiKit.matchWidth(ctx));

        pairFieldsBox = UiKit.column(ctx);
        sniInput = UiKit.editText(ctx, "SNI (مثلا example.com)", false);
        hostInput = UiKit.editText(ctx, "HTTP Host (دامنه)", false);
        pathInput = UiKit.editText(ctx, "Path (پیش‌فرض /)", false);
        expectedStatusInput = UiKit.editNumber(ctx, "Expected status (0 = پیش‌فرض)");
        pairFieldsBox.addView(sniInput);
        pairFieldsBox.addView(hostInput);
        pairFieldsBox.addView(pathInput);
        pairFieldsBox.addView(expectedStatusInput);
        pairFieldsBox.setVisibility(View.GONE);
        engineCard.addView(pairFieldsBox);

        engineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                pairFieldsBox.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(engineCard);

        // ----- Big start/stop -----
        LinearLayout actions = UiKit.row(ctx);
        startBtn = UiKit.primaryButton(ctx, "▶ شروع اسکن");
        stopBtn = UiKit.button(ctx, "⏹ توقف", UiKit.DANGER);
        stopBtn.setEnabled(false);
        actions.addView(startBtn, UiKit.weight(ctx));
        actions.addView(stopBtn, UiKit.weight(ctx));
        root.addView(actions);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<String> ips = IpListParser.parse(targetInput.getText().toString());
                if (ips.isEmpty()) { Toast.makeText(ctx, "هدف خالی است", Toast.LENGTH_SHORT).show(); return; }
                ScanConfig cfg = buildConfig(false);
                cb.onStart(cfg, ips);
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cb.onStop(); }
        });

        // ----- Live progress -----
        LinearLayout progressCard = UiKit.card(ctx);
        progressCard.addView(UiKit.label(ctx, "📡 پیشرفت زنده"));
        progressBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressCard.addView(progressBar, UiKit.matchWidth(ctx));
        progressLine = UiKit.text(ctx, "آماده برای اسکن", 13, UiKit.TEXT_DIM, false);
        progressLine.setPadding(0, UiKit.dp(ctx, 8), 0, 0);
        progressCard.addView(progressLine);

        LinearLayout miniRow = UiKit.row(ctx);
        miniTotal = UiKit.text(ctx, "0", 18, UiKit.TEXT, true);
        miniOk    = UiKit.text(ctx, "0", 18, UiKit.SUCCESS, true);
        miniMaybe = UiKit.text(ctx, "0", 18, UiKit.WARNING, true);
        miniFail  = UiKit.text(ctx, "0", 18, UiKit.DANGER, true);
        miniRow.addView(miniStat(ctx, "کل", miniTotal), UiKit.weight(ctx));
        miniRow.addView(miniStat(ctx, "موفق", miniOk), UiKit.weight(ctx));
        miniRow.addView(miniStat(ctx, "نامطمئن", miniMaybe), UiKit.weight(ctx));
        miniRow.addView(miniStat(ctx, "ناموفق", miniFail), UiKit.weight(ctx));
        progressCard.addView(miniRow);
        root.addView(progressCard);

        // Safety footer
        TextView footer = UiKit.caption(ctx, "⚠️ فقط روی IPها/شبکه‌هایی که مجاز به تستشان هستید استفاده کنید.");
        footer.setGravity(android.view.Gravity.CENTER);
        root.addView(footer);
    }

    private LinearLayout miniStat(Context c, String label, TextView numberView) {
        LinearLayout col = UiKit.column(c);
        col.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(c, 12), UiKit.BORDER, 1));
        col.setPadding(UiKit.dp(c, 8), UiKit.dp(c, 10), UiKit.dp(c, 8), UiKit.dp(c, 10));
        col.setGravity(android.view.Gravity.CENTER);
        numberView.setGravity(android.view.Gravity.CENTER);
        col.addView(numberView);
        TextView lbl = UiKit.text(c, label, 11, UiKit.TEXT_DIM, false);
        lbl.setGravity(android.view.Gravity.CENTER);
        col.addView(lbl);
        return col;
    }

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) { Toast.makeText(ctx, "کلیپ‌بورد خالی است", Toast.LENGTH_SHORT).show(); return; }
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).getText();
        if (text == null) return;
        String cur = targetInput.getText().toString();
        targetInput.setText(cur.isEmpty() ? text.toString() : (cur + "\n" + text));
        Toast.makeText(ctx, "افزوده شد", Toast.LENGTH_SHORT).show();
    }

    void appendToTarget(List<String> ips) {
        if (ips == null || ips.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        String cur = targetInput.getText().toString().trim();
        if (!cur.isEmpty()) sb.append(cur).append('\n');
        for (String ip : ips) sb.append(ip).append('\n');
        targetInput.setText(sb.toString().trim());
    }

    /** Build a ScanConfig from current widget state. {@code applyAdvanced} controls whether
     * the AdvancedPage values are merged externally; here we just take what's on-screen. */
    ScanConfig buildConfig(boolean applyAdvanced) {
        ScanConfig.Builder b = ScanConfig.builder();
        b.engine(engineSpinner.getSelectedItemPosition() == 1 ? ScanConfig.Engine.PAIR : ScanConfig.Engine.QUICK);
        b.sni(sniInput == null ? "" : sniInput.getText().toString());
        b.host(hostInput == null ? "" : hostInput.getText().toString());
        b.path(pathInput == null ? "/" : pathInput.getText().toString());
        if (expectedStatusInput != null) {
            try { b.expectedStatus(Integer.parseInt(expectedStatusInput.getText().toString().trim())); }
            catch (Exception ignored) { b.expectedStatus(0); }
        }
        return b.build();
    }

    void setScanning(boolean scanning) {
        startBtn.setEnabled(!scanning);
        stopBtn.setEnabled(scanning);
    }

    void updateProgress(ScannerEngine.Phase phase, int done, int total, int ok, int maybe, int fail) {
        int max = Math.max(1, total);
        progressBar.setProgress(Math.min(1000, (int) ((done * 1000L) / max)));
        String phaseLabel;
        switch (phase) {
            case TCP:      phaseLabel = "TCP"; break;
            case TLS:      phaseLabel = "TLS"; break;
            case HTTP:     phaseLabel = "HTTP"; break;
            case SPEED:    phaseLabel = "Speed"; break;
            case FINALIZE: phaseLabel = "Finalize"; break;
            default:       phaseLabel = "Prep";
        }
        progressLine.setText(phaseLabel + " · " + done + "/" + total
                + " · ok " + ok + " · maybe " + maybe + " · fail " + fail);
        miniTotal.setText(String.valueOf(total));
        miniOk.setText(String.valueOf(ok));
        miniMaybe.setText(String.valueOf(maybe));
        miniFail.setText(String.valueOf(fail));
    }

    void resetProgress() {
        progressBar.setProgress(0);
        progressLine.setText("آماده برای اسکن");
        miniTotal.setText("0"); miniOk.setText("0"); miniMaybe.setText("0"); miniFail.setText("0");
    }
}
