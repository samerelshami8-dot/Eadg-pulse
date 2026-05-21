package ai.arena.portscanner;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Hosts the four scroll views (Scan / Profiles / Results / Advanced) and the
 * bottom navigation. {@link MainActivity} wires pages with {@link #setPage(int, View)}.
 */
final class PageHost {

    static final int PAGE_SCAN     = 0;
    static final int PAGE_PROFILES = 1;
    static final int PAGE_RESULTS  = 2;
    static final int PAGE_ADVANCED = 3;

    private static final String[] LABELS  = new String[]{"اسکن", "پروفایل", "نتایج", "پیشرفته"};
    private static final String[] ICONS   = new String[]{"📡", "🗂️", "📊", "⚙️"};

    interface OnPageChange {
        void onPageChange(int index);
    }

    private final Context ctx;
    private final LinearLayout root;
    private final FrameLayout pageContainer;
    private final LinearLayout bottomNav;
    private final ScrollView[] scrolls = new ScrollView[4];
    private final LinearLayout[] tabViews = new LinearLayout[4];
    private final TextView[] tabIcons = new TextView[4];
    private final TextView[] tabLabels = new TextView[4];
    private int active = PAGE_SCAN;
    private OnPageChange listener;

    PageHost(Context ctx) {
        this.ctx = ctx;
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiKit.BG);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams pageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(pageContainer, pageLp);

        bottomNav = new LinearLayout(ctx);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackground(UiKit.rounded(UiKit.SURFACE, 0, UiKit.BORDER, 1));
        bottomNav.setPadding(UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(bottomNav, navLp);

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout tab = new LinearLayout(ctx);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(UiKit.dp(ctx, 6), UiKit.dp(ctx, 8), UiKit.dp(ctx, 6), UiKit.dp(ctx, 8));
            TextView icon = UiKit.text(ctx, ICONS[i], 22, UiKit.TEXT_DIM, false);
            icon.setGravity(Gravity.CENTER);
            TextView lbl = UiKit.text(ctx, LABELS[i], 11, UiKit.TEXT_DIM, true);
            lbl.setGravity(Gravity.CENTER);
            tab.addView(icon);
            tab.addView(lbl);
            tab.setBackground(UiKit.ripple(UiKit.SURFACE, UiKit.dp(ctx, 12)));
            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTo(idx); }
            });
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tabLp.setMargins(UiKit.dp(ctx, 2), 0, UiKit.dp(ctx, 2), 0);
            bottomNav.addView(tab, tabLp);
            tabViews[i] = tab;
            tabIcons[i] = icon;
            tabLabels[i] = lbl;
        }
        switchTo(PAGE_SCAN);
    }

    View view() { return root; }

    /**
     * Install a page body. {@code body} is wrapped in a {@link ScrollView} so
     * each page handles its own vertical overflow.
     */
    void setPage(int index, View body) {
        if (index < 0 || index >= 4 || body == null) return;
        ScrollView sv = scrolls[index];
        if (sv == null) {
            sv = new ScrollView(ctx);
            sv.setBackgroundColor(UiKit.BG);
            sv.setFillViewport(true);
            scrolls[index] = sv;
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            sv.setLayoutParams(flp);
            sv.setVisibility(index == active ? View.VISIBLE : View.GONE);
            pageContainer.addView(sv);
        } else {
            sv.removeAllViews();
        }
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 14), UiKit.dp(ctx, 14), UiKit.dp(ctx, 18));
        wrap.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sv.addView(wrap);
    }

    void setOnPageChange(OnPageChange l) { this.listener = l; }

    int activePage() { return active; }

    void switchTo(int index) {
        if (index < 0 || index >= 4) return;
        for (int i = 0; i < 4; i++) {
            boolean on = (i == index);
            if (scrolls[i] != null) scrolls[i].setVisibility(on ? View.VISIBLE : View.GONE);
            tabIcons[i].setTextColor(on ? UiKit.TEXT : UiKit.TEXT_DIM);
            tabLabels[i].setTextColor(on ? UiKit.TEXT : UiKit.TEXT_DIM);
            tabViews[i].setBackground(on
                    ? UiKit.rounded(UiKit.CARD_HI, UiKit.dp(ctx, 12), UiKit.PRIMARY, 1)
                    : UiKit.ripple(UiKit.SURFACE, UiKit.dp(ctx, 12)));
        }
        active = index;
        if (listener != null) listener.onPageChange(index);
    }
}
