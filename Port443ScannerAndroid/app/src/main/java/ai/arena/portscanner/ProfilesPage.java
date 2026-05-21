package ai.arena.portscanner;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Profiles page (🗂️). Lets the user create / select / export profiles and
 * curate the IP list inside each profile.
 */
final class ProfilesPage {

    interface Callbacks {
        /** Push the IPs of the last scan into the named profile. */
        void onSaveLastScan(String profileName);
        /** Hand a JSON dump out (clipboard). */
        void onExport(String json);
        /** Prompt for paste; return resulting text or null. */
        void onImportRequested();
    }

    private final Context ctx;
    private final ProfileStore store;
    private final Callbacks cb;

    private final LinearLayout root;
    private Spinner spinner;
    private ArrayAdapter<String> spinnerAdapter;
    private LinearLayout ipListBox;
    private TextView selectedHeader;

    ProfilesPage(Context ctx, ProfileStore store, Callbacks cb) {
        this.ctx = ctx;
        this.store = store;
        this.cb = cb;
        this.root = UiKit.column(ctx);
        build();
    }

    View view() { return root; }

    private void build() {
        TextView header = UiKit.label(ctx, "🗂️ پروفایل‌ها");
        root.addView(header);

        LinearLayout chooserCard = UiKit.card(ctx);
        chooserCard.addView(UiKit.text(ctx, "پروفایل فعلی", 12, UiKit.TEXT_DIM, false));
        LinearLayout chooserRow = UiKit.row(ctx);
        spinner = new Spinner(ctx);
        spinnerAdapter = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, store.names());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        chooserRow.addView(spinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button choose = UiKit.button(ctx, "انتخاب", UiKit.PRIMARY);
        chooserRow.addView(choose);
        chooserCard.addView(chooserRow);

        selectedHeader = UiKit.text(ctx, "", 13, UiKit.TEXT, true);
        selectedHeader.setPadding(0, UiKit.dp(ctx, 6), 0, 0);
        chooserCard.addView(selectedHeader);
        root.addView(chooserCard);

        // Actions
        LinearLayout actionRow1 = UiKit.row(ctx);
        Button add = UiKit.button(ctx, "+ پروفایل جدید", UiKit.SUCCESS);
        Button saveLast = UiKit.button(ctx, "💾 ذخیره IPهای اسکن", UiKit.ACCENT);
        actionRow1.addView(add, UiKit.weight(ctx));
        actionRow1.addView(saveLast, UiKit.weight(ctx));
        root.addView(actionRow1);

        LinearLayout actionRow2 = UiKit.row(ctx);
        Button exportBtn = UiKit.button(ctx, "📤 Export JSON", android.graphics.Color.parseColor("#1F2937"));
        Button importBtn = UiKit.button(ctx, "📥 Import JSON", android.graphics.Color.parseColor("#1F2937"));
        actionRow2.addView(exportBtn, UiKit.weight(ctx));
        actionRow2.addView(importBtn, UiKit.weight(ctx));
        root.addView(actionRow2);

        LinearLayout actionRow3 = UiKit.row(ctx);
        Button deleteBtn = UiKit.button(ctx, "🗑️ حذف پروفایل", UiKit.DANGER);
        actionRow3.addView(deleteBtn, UiKit.weight(ctx));
        root.addView(actionRow3);

        // IP list card
        LinearLayout ipCard = UiKit.card(ctx);
        ipCard.addView(UiKit.label(ctx, "📋 IPهای پروفایل"));
        ipListBox = UiKit.column(ctx);
        ipCard.addView(ipListBox);
        root.addView(ipCard);

        // Handlers
        choose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String name = spinner.getSelectedItem() == null ? "" : spinner.getSelectedItem().toString();
                if (name.isEmpty()) return;
                store.select(name);
                refresh();
                Toast.makeText(ctx, "انتخاب شد: " + name, Toast.LENGTH_SHORT).show();
            }
        });

        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { promptNewProfile(); }
        });

        saveLast.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String name = store.selected();
                if (name == null || name.isEmpty()) { Toast.makeText(ctx, "پروفایلی انتخاب نشده", Toast.LENGTH_SHORT).show(); return; }
                cb.onSaveLastScan(name);
            }
        });

        exportBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cb.onExport(store.exportJson()); }
        });
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cb.onImportRequested(); }
        });
        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String name = store.selected();
                if (name == null || name.isEmpty()) return;
                new AlertDialog.Builder(ctx)
                        .setTitle("حذف پروفایل")
                        .setMessage("حذف \"" + name + "\"؟")
                        .setPositiveButton("حذف", (d, w) -> {
                            if (store.delete(name)) {
                                refresh();
                                Toast.makeText(ctx, "حذف شد", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ctx, "حداقل یک پروفایل لازم است", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("لغو", null).show();
            }
        });

        refresh();
    }

    /** Re-sync spinner + ip list. Call after profile changes. */
    void refresh() {
        spinnerAdapter.clear();
        spinnerAdapter.addAll(store.names());
        spinnerAdapter.notifyDataSetChanged();

        String current = store.selected();
        if (current != null && !current.isEmpty()) {
            int pos = spinnerAdapter.getPosition(current);
            if (pos >= 0) spinner.setSelection(pos);
        }
        selectedHeader.setText("فعلی: " + (current == null ? "—" : current));

        ipListBox.removeAllViews();
        List<String> ips = store.ipsOf(current);
        if (ips.isEmpty()) {
            ipListBox.addView(UiKit.caption(ctx, "هیچ IP ذخیره‌شده‌ای نیست. از Results یا «ذخیره IPهای اسکن» اضافه کن."));
            return;
        }
        for (final String ip : ips) {
            LinearLayout rowLp = UiKit.row(ctx);
            rowLp.setBackground(UiKit.rounded(UiKit.SURFACE, UiKit.dp(ctx, 10), UiKit.BORDER, 1));
            rowLp.setPadding(UiKit.dp(ctx, 10), UiKit.dp(ctx, 8), UiKit.dp(ctx, 10), UiKit.dp(ctx, 8));
            TextView t = UiKit.text(ctx, ip, 14, UiKit.TEXT, false);
            t.setTextIsSelectable(true);
            rowLp.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button rm = UiKit.button(ctx, "حذف", android.graphics.Color.parseColor("#374151"));
            rm.setTextSize(11);
            rm.setPadding(UiKit.dp(ctx, 10), UiKit.dp(ctx, 4), UiKit.dp(ctx, 10), UiKit.dp(ctx, 4));
            rm.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (store.removeIp(store.selected(), ip)) refresh();
                }
            });
            rowLp.addView(rm);
            ipListBox.addView(rowLp);
        }
    }

    private void promptNewProfile() {
        final EditText et = UiKit.editText(ctx, "نام پروفایل", false);
        new AlertDialog.Builder(ctx)
                .setTitle("پروفایل جدید")
                .setView(et)
                .setPositiveButton("ذخیره", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(ctx, "نام لازم است", Toast.LENGTH_SHORT).show(); return; }
                    if (store.get(name) != null) { Toast.makeText(ctx, "این نام وجود دارد", Toast.LENGTH_SHORT).show(); return; }
                    ProfileStore.Profile p = new ProfileStore.Profile(name);
                    p.created = System.currentTimeMillis();
                    p.lastUsed = p.created;
                    if (store.save(p)) {
                        store.select(name);
                        refresh();
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }
}
