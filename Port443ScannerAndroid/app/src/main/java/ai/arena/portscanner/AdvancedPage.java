package ai.arena.portscanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced settings (⚙️). Persists to {@link SharedPreferences} so the next
 * launch reopens with the same knobs.
 */
final class AdvancedPage {

    static final String PREFS = "edgepulse_advanced_v2";
    private static final String K_PORT = "port";
    private static final String K_FAST = "fast_timeout";
    private static final String K_TLS  = "tls_timeout";
    private static final String K_HTTP = "http_timeout";
    private static final String K_CONC = "concurrency";
    private static final String K_ATT  = "attempts";
    private static final String K_MIN  = "min_success";
    private static final String K_VER  = "verify_names";
    private static final String K_ALPN = "alpn_prefer_h2";
    private static final String K_SPD  = "speed_enabled";
    private static final String K_S1   = "download_kb_s1";
    private static final String K_S2   = "download_kb_s2";
    private static final String K_UP   = "upload_enabled";
    private static final String K_UPK  = "upload_kb";
    private static final String K_TOPN = "two_stage_topn";
    private static final String K_PREFIX = "distinct_prefix";
    private static final String K_SKIP = "skip_private";
    private static final String K_THEME = "theme";

    interface Callbacks {
        /** Apply theme choice immediately. */
        void onThemeChanged(int themeIndex);
    }

    private final Context ctx;
    private final Callbacks cb;
    private final SharedPreferences prefs;
    private final LinearLayout root;

    EditText portInput, fastTimeoutInput, tlsTimeoutInput, httpTimeoutInput, concurrencyInput, attemptsInput, minSuccessInput;
    EditText verifyNamesInput;
    CheckBox alpnPreferH2Box;
    CheckBox speedEnabledBox, uploadEnabledBox;
    EditText downloadKb1Input, downloadKb2Input, uploadKbInput, twoStageTopNInput, distinctPrefixInput;
    CheckBox skipPrivateBox;
    Spinner themeSpinner;

    AdvancedPage(Context ctx, Callbacks cb) {
        this.ctx = ctx;
        this.cb = cb;
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.root = UiKit.column(ctx);
        build();
        load();
    }

    View view() { return root; }

    private void build() {
        TextView header = UiKit.label(ctx, "⚙️ تنظیمات پیشرفته");
        root.addView(header);

        // Connection
        LinearLayout connBody = UiKit.collapsible(ctx, root, "🔌 اتصال", true);
        LinearLayout r1 = UiKit.row(ctx);
        portInput = UiKit.editNumber(ctx, "443");
        concurrencyInput = UiKit.editNumber(ctx, "80");
        r1.addView(UiKit.field(ctx, "پورت", portInput));
        r1.addView(UiKit.field(ctx, "همزمانی", concurrencyInput));
        connBody.addView(r1);
        LinearLayout r2 = UiKit.row(ctx);
        fastTimeoutInput = UiKit.editNumber(ctx, "800");
        tlsTimeoutInput = UiKit.editNumber(ctx, "3000");
        r2.addView(UiKit.field(ctx, "TCP timeout ms", fastTimeoutInput));
        r2.addView(UiKit.field(ctx, "TLS timeout ms", tlsTimeoutInput));
        connBody.addView(r2);
        LinearLayout r3 = UiKit.row(ctx);
        httpTimeoutInput = UiKit.editNumber(ctx, "4000");
        attemptsInput = UiKit.editNumber(ctx, "2");
        r3.addView(UiKit.field(ctx, "HTTP timeout ms", httpTimeoutInput));
        r3.addView(UiKit.field(ctx, "تلاش/IP", attemptsInput));
        connBody.addView(r3);
        LinearLayout r4 = UiKit.row(ctx);
        minSuccessInput = UiKit.editNumber(ctx, "1");
        r4.addView(UiKit.field(ctx, "حداقل موفق", minSuccessInput));
        connBody.addView(r4);

        // TLS
        LinearLayout tlsBody = UiKit.collapsible(ctx, root, "🔐 TLS", false);
        tlsBody.addView(UiKit.caption(ctx, "اسامی verify (با کاما جدا شوند؛ خالی = SNI + Host)"));
        verifyNamesInput = UiKit.editText(ctx, "example.com, www.example.com", false);
        tlsBody.addView(verifyNamesInput);
        alpnPreferH2Box = UiKit.checkbox(ctx, "ALPN ترجیحی: h2 (در غیر این‌صورت http/1.1)");
        alpnPreferH2Box.setChecked(true);
        tlsBody.addView(alpnPreferH2Box);

        // Speed
        LinearLayout speedBody = UiKit.collapsible(ctx, root, "🚀 تست سرعت", false);
        speedEnabledBox = UiKit.checkbox(ctx, "فعال‌سازی تست دانلود");
        speedBody.addView(speedEnabledBox);
        LinearLayout sr1 = UiKit.row(ctx);
        downloadKb1Input = UiKit.editNumber(ctx, "256");
        downloadKb2Input = UiKit.editNumber(ctx, "2048");
        sr1.addView(UiKit.field(ctx, "مرحله ۱ KB", downloadKb1Input));
        sr1.addView(UiKit.field(ctx, "مرحله ۲ KB", downloadKb2Input));
        speedBody.addView(sr1);
        LinearLayout sr2 = UiKit.row(ctx);
        twoStageTopNInput = UiKit.editNumber(ctx, "10");
        sr2.addView(UiKit.field(ctx, "Top-N مرحله ۲", twoStageTopNInput));
        speedBody.addView(sr2);
        uploadEnabledBox = UiKit.checkbox(ctx, "فعال‌سازی تست آپلود (POST)");
        speedBody.addView(uploadEnabledBox);
        uploadKbInput = UiKit.editNumber(ctx, "256");
        speedBody.addView(UiKit.field(ctx, "آپلود KB", uploadKbInput));

        // Scoring
        LinearLayout scBody = UiKit.collapsible(ctx, root, "🏆 امتیازدهی", false);
        distinctPrefixInput = UiKit.editNumber(ctx, "24");
        scBody.addView(UiKit.field(ctx, "Distinct subnet prefix (مثلاً 24)", distinctPrefixInput));

        // Filtering
        LinearLayout filterBody = UiKit.collapsible(ctx, root, "🛡️ فیلتر", true);
        skipPrivateBox = UiKit.checkbox(ctx, "حذف IPهای private/reserved/bogon");
        skipPrivateBox.setChecked(true);
        filterBody.addView(skipPrivateBox);

        // Theme
        LinearLayout themeBody = UiKit.collapsible(ctx, root, "🎨 تم", false);
        themeSpinner = new Spinner(ctx);
        ArrayAdapter<String> ta = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item,
                new String[]{"سیستم", "تیره", "روشن"});
        ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(ta);
        themeBody.addView(themeSpinner, UiKit.matchWidth(ctx));
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                save();
                if (cb != null) cb.onThemeChanged(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout actions = UiKit.row(ctx);
        Button saveBtn = UiKit.button(ctx, "💾 ذخیره", UiKit.PRIMARY);
        Button resetBtn = UiKit.button(ctx, "↺ بازنشانی به پیش‌فرض", android.graphics.Color.parseColor("#1F2937"));
        actions.addView(saveBtn, UiKit.weight(ctx));
        actions.addView(resetBtn, UiKit.weight(ctx));
        root.addView(actions);

        saveBtn.setOnClickListener(v -> { save(); Toast.makeText(ctx, "ذخیره شد", Toast.LENGTH_SHORT).show(); });
        resetBtn.setOnClickListener(v -> { resetDefaults(); save(); Toast.makeText(ctx, "بازنشانی شد", Toast.LENGTH_SHORT).show(); });
    }

    /** Read current values into a ScanConfig builder. Caller may override fields. */
    void applyTo(ScanConfig.Builder b) {
        b.port(intOr(portInput, 443));
        b.fastTcpTimeoutMs(intOr(fastTimeoutInput, 800));
        b.tlsTimeoutMs(intOr(tlsTimeoutInput, 3000));
        b.httpTimeoutMs(intOr(httpTimeoutInput, 4000));
        b.concurrency(clamp(intOr(concurrencyInput, 80), 1, 400));
        b.attempts(clamp(intOr(attemptsInput, 2), 1, 5));
        b.minSuccess(clamp(intOr(minSuccessInput, 1), 1, 5));
        b.verifyNamesCsv(verifyNamesInput.getText().toString());
        b.alpnPreferH2(alpnPreferH2Box.isChecked());
        b.speedEnabled(speedEnabledBox.isChecked());
        b.downloadKbStage1(clamp(intOr(downloadKb1Input, 256), 16, 16384));
        b.downloadKbStage2(clamp(intOr(downloadKb2Input, 2048), 64, 65536));
        b.uploadEnabled(uploadEnabledBox.isChecked());
        b.uploadKb(clamp(intOr(uploadKbInput, 256), 16, 16384));
        b.twoStageTopN(clamp(intOr(twoStageTopNInput, 10), 1, 200));
        b.distinctPrefixLen(clamp(intOr(distinctPrefixInput, 24), 8, 32));
        b.skipPrivate(skipPrivateBox.isChecked());
    }

    private int themeIndex() {
        return themeSpinner.getSelectedItemPosition();
    }

    int currentTheme() { return prefs.getInt(K_THEME, 0); }

    private void save() {
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(K_PORT, intOr(portInput, 443));
        e.putInt(K_FAST, intOr(fastTimeoutInput, 800));
        e.putInt(K_TLS,  intOr(tlsTimeoutInput, 3000));
        e.putInt(K_HTTP, intOr(httpTimeoutInput, 4000));
        e.putInt(K_CONC, intOr(concurrencyInput, 80));
        e.putInt(K_ATT,  intOr(attemptsInput, 2));
        e.putInt(K_MIN,  intOr(minSuccessInput, 1));
        e.putString(K_VER, verifyNamesInput.getText().toString());
        e.putBoolean(K_ALPN, alpnPreferH2Box.isChecked());
        e.putBoolean(K_SPD, speedEnabledBox.isChecked());
        e.putInt(K_S1, intOr(downloadKb1Input, 256));
        e.putInt(K_S2, intOr(downloadKb2Input, 2048));
        e.putBoolean(K_UP, uploadEnabledBox.isChecked());
        e.putInt(K_UPK, intOr(uploadKbInput, 256));
        e.putInt(K_TOPN, intOr(twoStageTopNInput, 10));
        e.putInt(K_PREFIX, intOr(distinctPrefixInput, 24));
        e.putBoolean(K_SKIP, skipPrivateBox.isChecked());
        e.putInt(K_THEME, themeIndex());
        e.apply();
    }

    private void load() {
        portInput.setText(String.valueOf(prefs.getInt(K_PORT, 443)));
        fastTimeoutInput.setText(String.valueOf(prefs.getInt(K_FAST, 800)));
        tlsTimeoutInput.setText(String.valueOf(prefs.getInt(K_TLS, 3000)));
        httpTimeoutInput.setText(String.valueOf(prefs.getInt(K_HTTP, 4000)));
        concurrencyInput.setText(String.valueOf(prefs.getInt(K_CONC, 80)));
        attemptsInput.setText(String.valueOf(prefs.getInt(K_ATT, 2)));
        minSuccessInput.setText(String.valueOf(prefs.getInt(K_MIN, 1)));
        verifyNamesInput.setText(prefs.getString(K_VER, ""));
        alpnPreferH2Box.setChecked(prefs.getBoolean(K_ALPN, true));
        speedEnabledBox.setChecked(prefs.getBoolean(K_SPD, false));
        downloadKb1Input.setText(String.valueOf(prefs.getInt(K_S1, 256)));
        downloadKb2Input.setText(String.valueOf(prefs.getInt(K_S2, 2048)));
        uploadEnabledBox.setChecked(prefs.getBoolean(K_UP, false));
        uploadKbInput.setText(String.valueOf(prefs.getInt(K_UPK, 256)));
        twoStageTopNInput.setText(String.valueOf(prefs.getInt(K_TOPN, 10)));
        distinctPrefixInput.setText(String.valueOf(prefs.getInt(K_PREFIX, 24)));
        skipPrivateBox.setChecked(prefs.getBoolean(K_SKIP, true));
        themeSpinner.setSelection(prefs.getInt(K_THEME, 0));
    }

    private void resetDefaults() {
        portInput.setText("443");
        fastTimeoutInput.setText("800");
        tlsTimeoutInput.setText("3000");
        httpTimeoutInput.setText("4000");
        concurrencyInput.setText("80");
        attemptsInput.setText("2");
        minSuccessInput.setText("1");
        verifyNamesInput.setText("");
        alpnPreferH2Box.setChecked(true);
        speedEnabledBox.setChecked(false);
        downloadKb1Input.setText("256");
        downloadKb2Input.setText("2048");
        uploadEnabledBox.setChecked(false);
        uploadKbInput.setText("256");
        twoStageTopNInput.setText("10");
        distinctPrefixInput.setText("24");
        skipPrivateBox.setChecked(true);
        themeSpinner.setSelection(0);
    }

    private static int intOr(EditText e, int def) {
        if (e == null) return def;
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Exception ex) { return def; }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
