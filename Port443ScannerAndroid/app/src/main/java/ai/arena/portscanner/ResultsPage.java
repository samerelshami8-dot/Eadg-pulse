package ai.arena.portscanner;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Results page (📊). Shows filter chips, sort dropdown, Top-N picker, the
 * result cards, and the export toolbar. UI redraws are throttled — only every
 * 20th incoming result triggers a full rebuild unless callers pass {@code true}
 * to {@link #setResults(List, boolean)}.
 */
final class ResultsPage {

    /** Vendor filter values understood by the chip row. */
    enum Filter { ALL, OK, CLOUDFLARE, FASTLY, AKAMAI, OTHER }

    enum SortBy { SCORE, DOWNLOAD, TTFB, RTT }

    interface Callbacks {
        void onCopyIps(String text);
        void onShareIps(String text);
        void onSaveTxt(String text);
        void onSaveCsv(String text);
        void onSaveJson(String text);
    }

    private final Context ctx;
    private final Callbacks cb;
    private final LinearLayout root;
    private LinearLayout cardsHolder;
    private Spinner sortSpinner;
    private Filter activeFilter = Filter.ALL;
    private SortBy activeSort = SortBy.SCORE;
    private int topNCap = 0;
    private List<ScanResult> snapshot = new ArrayList<>();
    private int incrementalCounter = 0;

    private Button copyBtn, shareBtn, saveTxt, saveCsv, saveJson;
    private boolean scanning = false;

    ResultsPage(Context ctx, Callbacks cb) {
        this.ctx = ctx;
        this.cb = cb;
        this.root = UiKit.column(ctx);
        build();
    }

    View view() { return root; }

    private void build() {
        TextView header = UiKit.label(ctx, "📊 رتبه‌بندی نتایج");
        root.addView(header);

        // ---- Filter chips ----
        LinearLayout chipsRow = UiKit.row(ctx);
        chipsRow.setGravity(Gravity.START);
        addFilterChip(chipsRow, "همه", Filter.ALL);
        addFilterChip(chipsRow, "OK", Filter.OK);
        addFilterChip(chipsRow, "Cloudflare", Filter.CLOUDFLARE);
        addFilterChip(chipsRow, "Fastly", Filter.FASTLY);
        addFilterChip(chipsRow, "Akamai", Filter.AKAMAI);
        addFilterChip(chipsRow, "دیگران", Filter.OTHER);
        root.addView(chipsRow);

        // ---- Sort + TopN ----
        LinearLayout controlRow = UiKit.row(ctx);
        sortSpinner = new Spinner(ctx);
        final String[] sortLabels = new String[]{"امتیاز", "دانلود", "TTFB", "RTT"};
        ArrayAdapter<String> sa = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, sortLabels);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sa);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 1) activeSort = SortBy.DOWNLOAD;
                else if (position == 2) activeSort = SortBy.TTFB;
                else if (position == 3) activeSort = SortBy.RTT;
                else activeSort = SortBy.SCORE;
                rebuild();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        controlRow.addView(UiKit.field(ctx, "مرتب‌سازی", sortSpinner));

        final Spinner topSpinner = new Spinner(ctx);
        final String[] topLabels = new String[]{"همه", "10", "20", "50", "100"};
        final int[] topVals = new int[]{0, 10, 20, 50, 100};
        ArrayAdapter<String> ta = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, topLabels);
        ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        topSpinner.setAdapter(ta);
        topSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                topNCap = topVals[position];
                rebuild();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        controlRow.addView(UiKit.field(ctx, "Top-N", topSpinner));
        root.addView(controlRow);

        // ---- Cards container ----
        LinearLayout cardsCard = UiKit.card(ctx);
        cardsHolder = UiKit.column(ctx);
        cardsCard.addView(cardsHolder);
        root.addView(cardsCard);

        showEmpty();

        // ---- Footer toolbar ----
        LinearLayout footer = UiKit.row(ctx);
        copyBtn = UiKit.button(ctx, "📋 کپی", android.graphics.Color.parseColor("#1F2937"));
        shareBtn = UiKit.button(ctx, "📤 اشتراک", android.graphics.Color.parseColor("#1F2937"));
        saveTxt = UiKit.button(ctx, "💾 TXT", android.graphics.Color.parseColor("#1F2937"));
        footer.addView(copyBtn, UiKit.weight(ctx));
        footer.addView(shareBtn, UiKit.weight(ctx));
        footer.addView(saveTxt, UiKit.weight(ctx));
        root.addView(footer);

        LinearLayout footer2 = UiKit.row(ctx);
        saveCsv = UiKit.button(ctx, "💾 CSV", android.graphics.Color.parseColor("#1F2937"));
        saveJson = UiKit.button(ctx, "💾 JSON", android.graphics.Color.parseColor("#1F2937"));
        footer2.addView(saveCsv, UiKit.weight(ctx));
        footer2.addView(saveJson, UiKit.weight(ctx));
        root.addView(footer2);

        copyBtn.setOnClickListener(v -> cb.onCopyIps(ResultExporter.toTxt(snapshot, topNCap)));
        shareBtn.setOnClickListener(v -> cb.onShareIps(ResultExporter.toTxt(snapshot, topNCap)));
        saveTxt.setOnClickListener(v -> cb.onSaveTxt(ResultExporter.toTxt(snapshot, topNCap)));
        saveCsv.setOnClickListener(v -> cb.onSaveCsv(ResultExporter.toCsv(snapshot)));
        saveJson.setOnClickListener(v -> cb.onSaveJson(ResultExporter.toJson(snapshot)));

        updateExportEnabled();
    }

    private void addFilterChip(LinearLayout row, final String label, final Filter f) {
        final TextView chip = UiKit.chip(ctx, label, f == activeFilter ? UiKit.PRIMARY : UiKit.SURFACE);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                activeFilter = f;
                // refresh chip backgrounds
                for (int i = 0; i < row.getChildCount(); i++) {
                    View c = row.getChildAt(i);
                    if (c instanceof TextView) {
                        c.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(ctx, 12), UiKit.BORDER, 1));
                    }
                }
                chip.setBackground(UiKit.rounded(UiKit.PRIMARY, UiKit.dp(ctx, 12), UiKit.BORDER, 1));
                rebuild();
            }
        });
        row.addView(chip);
    }

    /** Fully replace the result snapshot. {@code force} causes an unconditional rebuild. */
    void setResults(List<ScanResult> results, boolean force) {
        snapshot = (results == null) ? new ArrayList<>() : new ArrayList<>(results);
        if (force) {
            incrementalCounter = 0;
            rebuild();
            updateExportEnabled();
            return;
        }
        incrementalCounter++;
        if (incrementalCounter % 20 == 0) {
            rebuild();
        }
        updateExportEnabled();
    }

    /** Hook called when the engine flips to scanning/idle. */
    void setScanning(boolean scanning) {
        this.scanning = scanning;
        updateExportEnabled();
    }

    private void updateExportEnabled() {
        boolean hasData = !snapshot.isEmpty();
        boolean enabled = hasData && !scanning;
        if (copyBtn != null) copyBtn.setEnabled(enabled);
        if (shareBtn != null) shareBtn.setEnabled(enabled);
        if (saveTxt != null) saveTxt.setEnabled(enabled);
        if (saveCsv != null) saveCsv.setEnabled(enabled);
        if (saveJson != null) saveJson.setEnabled(enabled);
    }

    private void rebuild() {
        cardsHolder.removeAllViews();
        if (snapshot.isEmpty()) { showEmpty(); return; }
        List<ScanResult> filtered = applyFilter(snapshot, activeFilter);
        sortBy(filtered, activeSort);
        int n = (topNCap > 0) ? Math.min(topNCap, filtered.size()) : filtered.size();
        if (n == 0) {
            cardsHolder.addView(UiKit.caption(ctx, "هیچ نتیجه‌ای با این فیلتر نیست."));
            return;
        }
        // Cap UI render to keep the page fast even for very large scans.
        int renderLimit = Math.min(n, 300);
        for (int i = 0; i < renderLimit; i++) {
            cardsHolder.addView(buildCard(filtered.get(i), i + 1));
        }
        if (renderLimit < n) {
            cardsHolder.addView(UiKit.caption(ctx, "... و " + (n - renderLimit) + " مورد دیگر (در خروجی شامل می‌شوند)"));
        }
    }

    private void showEmpty() {
        cardsHolder.removeAllViews();
        cardsHolder.addView(UiKit.caption(ctx, "هنوز نتیجه‌ای ثبت نشده. صفحه «اسکن» را اجرا کن."));
    }

    private View buildCard(ScanResult r, int rank) {
        LinearLayout card = UiKit.column(ctx);
        card.setPadding(UiKit.dp(ctx, 12), UiKit.dp(ctx, 10), UiKit.dp(ctx, 12), UiKit.dp(ctx, 10));
        card.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(ctx, 12), UiKit.BORDER, 1));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = UiKit.dp(ctx, 6); clp.bottomMargin = UiKit.dp(ctx, 6);
        card.setLayoutParams(clp);

        LinearLayout row1 = UiKit.row(ctx);
        TextView rankTv = UiKit.text(ctx, "#" + rank, 12, UiKit.TEXT_DIM, true);
        rankTv.setPadding(0, 0, UiKit.dp(ctx, 8), 0);
        row1.addView(rankTv);
        TextView ip = UiKit.text(ctx, r.ip, 17, UiKit.TEXT, true);
        ip.setTextIsSelectable(true);
        row1.addView(ip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int badgeColor = r.level == ScanResult.Level.OK ? UiKit.SUCCESS
                : (r.level == ScanResult.Level.MAYBE ? UiKit.WARNING : UiKit.DANGER);
        row1.addView(UiKit.pill(ctx, r.levelLabelFa(), badgeColor));
        card.addView(row1);

        LinearLayout row2 = UiKit.row(ctx);
        row2.addView(metric(ctx, "RTT", r.tcpRttMs > 0 ? r.tcpRttMs + "ms" : "—"), UiKit.weight(ctx));
        row2.addView(metric(ctx, "↓", r.downloadMbps > 0 ? String.format(Locale.US, "%.2f Mbps", r.downloadMbps) : "—"), UiKit.weight(ctx));
        row2.addView(metric(ctx, "TTFB", r.ttfbMs > 0 ? r.ttfbMs + "ms" : "—"), UiKit.weight(ctx));
        row2.addView(metric(ctx, "Score", String.format(Locale.US, "%.1f", r.score)), UiKit.weight(ctx));
        card.addView(row2);

        if (r.hasKnownVendor() || (r.cacheStatus != null && !r.cacheStatus.isEmpty()) || (r.alpn != null && !r.alpn.isEmpty())) {
            LinearLayout chips = UiKit.row(ctx);
            chips.setGravity(Gravity.START);
            if (r.hasKnownVendor()) chips.addView(UiKit.chip(ctx, r.cdnVendor, UiKit.CARD_HI));
            if (r.popId != null && !r.popId.isEmpty()) chips.addView(UiKit.chip(ctx, "PoP " + r.popId, UiKit.CARD_HI));
            if (r.cacheStatus != null && !r.cacheStatus.isEmpty()) chips.addView(UiKit.chip(ctx, r.cacheStatus, UiKit.CARD_HI));
            if (r.alpn != null && !r.alpn.isEmpty()) chips.addView(UiKit.chip(ctx, "ALPN " + r.alpn, UiKit.CARD_HI));
            card.addView(chips);
        }

        if (r.reason != null && !r.reason.isEmpty()) {
            TextView reason = UiKit.text(ctx, r.reason, 11, UiKit.TEXT_DIM, false);
            reason.setPadding(0, UiKit.dp(ctx, 4), 0, 0);
            card.addView(reason);
        }
        return card;
    }

    private LinearLayout metric(Context c, String label, String value) {
        LinearLayout box = UiKit.column(c);
        box.setGravity(Gravity.START);
        TextView l = UiKit.text(c, label, 10, UiKit.TEXT_DIM, false);
        TextView v = UiKit.text(c, value, 13, UiKit.TEXT, true);
        box.addView(l); box.addView(v);
        return box;
    }

    private List<ScanResult> applyFilter(List<ScanResult> all, Filter f) {
        List<ScanResult> out = new ArrayList<>(all.size());
        for (ScanResult r : all) {
            if (r == null) continue;
            switch (f) {
                case OK:         if (r.level == ScanResult.Level.OK) out.add(r); break;
                case CLOUDFLARE: if (HttpProbe.VENDOR_CLOUDFLARE.equalsIgnoreCase(r.cdnVendor)) out.add(r); break;
                case FASTLY:     if (HttpProbe.VENDOR_FASTLY.equalsIgnoreCase(r.cdnVendor)) out.add(r); break;
                case AKAMAI:     if (HttpProbe.VENDOR_AKAMAI.equalsIgnoreCase(r.cdnVendor)) out.add(r); break;
                case OTHER:      if (!r.hasKnownVendor()) out.add(r); break;
                default:         out.add(r);
            }
        }
        return out;
    }

    private void sortBy(List<ScanResult> list, SortBy s) {
        Comparator<ScanResult> c;
        switch (s) {
            case DOWNLOAD:
                c = (a, b) -> Double.compare(b.downloadMbps, a.downloadMbps);
                break;
            case TTFB:
                c = (a, b) -> {
                    int at = a.ttfbMs <= 0 ? Integer.MAX_VALUE : a.ttfbMs;
                    int bt = b.ttfbMs <= 0 ? Integer.MAX_VALUE : b.ttfbMs;
                    return Integer.compare(at, bt);
                };
                break;
            case RTT:
                c = (a, b) -> {
                    int at = a.tcpRttMs <= 0 ? Integer.MAX_VALUE : a.tcpRttMs;
                    int bt = b.tcpRttMs <= 0 ? Integer.MAX_VALUE : b.tcpRttMs;
                    return Integer.compare(at, bt);
                };
                break;
            default:
                c = (a, b) -> Double.compare(b.score, a.score);
        }
        Collections.sort(list, c);
    }
}
