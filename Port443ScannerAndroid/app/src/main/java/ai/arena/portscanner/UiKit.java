package ai.arena.portscanner;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared widget factories and drawable helpers. Every page builder funnels its
 * primitives through here so the dark palette stays consistent and we avoid
 * pulling in {@code material} / {@code appcompat}.
 */
final class UiKit {

    // Palette (spec'd values)
    static final int BG       = Color.parseColor("#0B0F17");
    static final int SURFACE  = Color.parseColor("#151B26");
    static final int CARD     = Color.parseColor("#1C2332");
    static final int CARD_HI  = Color.parseColor("#243049");
    static final int BORDER   = Color.parseColor("#2A3343");
    static final int TEXT     = Color.parseColor("#EEF2F8");
    static final int TEXT_DIM = Color.parseColor("#94A3B8");
    static final int TEXT_FAINT = Color.parseColor("#64748B");
    static final int PRIMARY  = Color.parseColor("#6D28D9");
    static final int PRIMARY2 = Color.parseColor("#8B5CF6");
    static final int ACCENT   = Color.parseColor("#4F46E5");
    static final int SUCCESS  = Color.parseColor("#10B981");
    static final int DANGER   = Color.parseColor("#EF4444");
    static final int WARNING  = Color.parseColor("#F59E0B");
    static final int INFO     = Color.parseColor("#0EA5E9");

    private UiKit() {}

    static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    // ---------- drawables ----------

    static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable rounded(int color, int radiusPx, int borderColor, int borderPx) {
        GradientDrawable g = rounded(color, radiusPx);
        if (borderPx > 0) g.setStroke(borderPx, borderColor);
        return g;
    }

    static GradientDrawable gradient(int top, int bottom, int radiusPx, int borderPx) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(radiusPx);
        if (borderPx > 0) g.setStroke(borderPx, BORDER);
        return g;
    }

    static RippleDrawable ripple(int baseColor, int radiusPx) {
        GradientDrawable base = rounded(baseColor, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), base, null);
    }

    static RippleDrawable rippleGradient(int top, int bottom, int radiusPx) {
        GradientDrawable base = gradient(top, bottom, radiusPx, 0);
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), base, null);
    }

    // ---------- widget factories ----------

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, s, 14, TEXT, true);
        t.setPadding(0, dp(c, 14), 0, dp(c, 6));
        return t;
    }

    static TextView caption(Context c, String s) {
        TextView t = text(c, s, 11, TEXT_DIM, false);
        t.setPadding(0, dp(c, 4), 0, dp(c, 4));
        return t;
    }

    static TextView chip(Context c, String s, int color) {
        TextView t = text(c, s, 11, TEXT, true);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        t.setBackground(rounded(color, dp(c, 12), BORDER, dp(c, 1) / 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(c, 4)); lp.setMarginEnd(dp(c, 4));
        lp.topMargin = dp(c, 4); lp.bottomMargin = dp(c, 4);
        t.setLayoutParams(lp);
        return t;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        l.setBackground(rounded(CARD, dp(c, 18), BORDER, dp(c, 1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8); lp.bottomMargin = dp(c, 8);
        l.setLayoutParams(lp);
        if (Build.VERSION.SDK_INT >= 21) l.setElevation(dp(c, 1));
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 4); lp.bottomMargin = dp(c, 4);
        l.setLayoutParams(lp);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    static Button button(Context c, String s, int color) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setBackground(ripple(color, dp(c, 14)));
        b.setStateListAnimator(null);
        return b;
    }

    static Button primaryButton(Context c, String s) {
        Button b = button(c, s, PRIMARY);
        b.setBackground(rippleGradient(PRIMARY2, PRIMARY, dp(c, 14)));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    static EditText editText(Context c, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(TEXT_FAINT);
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setBackground(rounded(SURFACE, dp(c, 12), BORDER, dp(c, 1)));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        if (multiline) {
            e.setSingleLine(false);
            e.setVerticalScrollBarEnabled(true);
            e.setMovementMethod(new ScrollingMovementMethod());
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 4); lp.bottomMargin = dp(c, 4);
        e.setLayoutParams(lp);
        return e;
    }

    static EditText editNumber(Context c, String hint) {
        EditText e = editText(c, hint, false);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    static EditText editMultilineFixed(Context c, String hint, int heightDp) {
        EditText e = editText(c, hint, true);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) e.getLayoutParams();
        lp.height = dp(c, heightDp);
        e.setLayoutParams(lp);
        return e;
    }

    static CheckBox checkbox(Context c, String label) {
        CheckBox cb = new CheckBox(c);
        cb.setText(label);
        cb.setTextColor(TEXT);
        cb.setTextSize(13);
        cb.setButtonTintList(ColorStateList.valueOf(PRIMARY));
        return cb;
    }

    static LinearLayout field(Context c, String labelText, View input) {
        LinearLayout box = column(c);
        TextView label = text(c, labelText, 11, TEXT_DIM, false);
        label.setPadding(dp(c, 2), dp(c, 4), 0, dp(c, 2));
        box.addView(label);
        box.addView(input);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(dp(c, 4)); lp.setMarginEnd(dp(c, 4));
        box.setLayoutParams(lp);
        return box;
    }

    /**
     * Container with a clickable header that toggles a body LinearLayout.
     *
     * <p>Returns the body so callers can {@code addView()} their content rows
     * into it.
     */
    static LinearLayout collapsible(Context c, LinearLayout parent, String title, boolean startOpen) {
        LinearLayout outer = card(c);
        final LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, dp(c, 4), 0, dp(c, 4));
        final TextView chev = text(c, startOpen ? "▼" : "▶", 13, TEXT_DIM, false);
        chev.setPadding(0, 0, dp(c, 8), 0);
        TextView ttv = text(c, title, 15, TEXT, true);
        header.addView(chev);
        header.addView(ttv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        outer.addView(header);
        final LinearLayout body = column(c);
        body.setPadding(0, dp(c, 4), 0, 0);
        body.setVisibility(startOpen ? View.VISIBLE : View.GONE);
        outer.addView(body);
        header.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean opening = body.getVisibility() != View.VISIBLE;
                body.setVisibility(opening ? View.VISIBLE : View.GONE);
                chev.setText(opening ? "▼" : "▶");
            }
        });
        parent.addView(outer);
        return body;
    }

    /** A small status pill: filled background, white text. */
    static TextView pill(Context c, String s, int color) {
        TextView t = text(c, s, 11, Color.WHITE, true);
        t.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3));
        t.setBackground(rounded(color, dp(c, 12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(c, 4)); lp.setMarginEnd(dp(c, 4));
        t.setLayoutParams(lp);
        return t;
    }

    static LinearLayout.LayoutParams weight(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(dp(c, 4)); lp.setMarginEnd(dp(c, 4));
        return lp;
    }

    static LinearLayout.LayoutParams matchWidth(Context c) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Lighten a hex color by mixing with white. Useful for hover/selected backgrounds. */
    static int lighten(int color, float factor) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        int nr = (int) (r + (255 - r) * factor);
        int ng = (int) (g + (255 - g) * factor);
        int nb = (int) (b + (255 - b) * factor);
        return Color.rgb(Math.min(255, nr), Math.min(255, ng), Math.min(255, nb));
    }
}
