package com.byd.dashcast.phase1.widget;

import com.byd.dashcast.phase1.ncm.LogHub;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class Ui {

    private Ui() {
    }

    private static final int DESIGN_WIDTH_PX = 1080;
    private static final float MIN_SCALE = 0.72f;
    private static final float MAX_SCALE = 1.25f;

    public static float scale(Context c) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        float s = m.widthPixels / (float) DESIGN_WIDTH_PX;
        if (s < MIN_SCALE) s = MIN_SCALE;
        if (s > MAX_SCALE) s = MAX_SCALE;
        return s;
    }

    public static int screenWidth(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    public static int screenHeight(Context c) {
        return c.getResources().getDisplayMetrics().heightPixels;
    }

    public static View toggle(Context c, final boolean on, Runnable onChanged) {
        final ToggleState st = new ToggleState();
        st.on = on;
        st.callback = onChanged;

        final View knob = new View(c);
        knob.setElevation(dp(c, 2));

        final LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(android.view.Gravity.CENTER_VERTICAL);
        box.setClickable(true);
        box.setFocusable(true);

        final int w = dp(c, 54);
        final int h = dp(c, 32);
        final int knobSize = dp(c, 24);
        final int pad = dp(c, 4);
        final int inset = dp(c, 3);

        st.paint = new Runnable() {
            public void run() {
                box.setBackground(pill(c, col(c, st.on ? ACCENT : IDLE),
                        0x00000000, h / 2));
                GradientDrawable k = new GradientDrawable();
                k.setShape(GradientDrawable.OVAL);
                k.setColor(0xFFFFFFFF);
                k.setStroke(Math.max(1, dp(c, 1)), 0x26000000);
                knob.setBackground(k);
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                        knobSize, knobSize);
                klp.leftMargin = st.on ? w - pad - knobSize - inset : pad;
                knob.setLayoutParams(klp);
            }
        };

        box.addView(knob, new LinearLayout.LayoutParams(knobSize, knobSize));
        box.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        st.paint.run();
        box.setTag(st);

        Ui.onClick(box, new Runnable() {
            public void run() {
                st.on = !st.on;
                st.paint.run();
                if (st.callback != null) st.callback.run();
            }
        });
        return box;
    }

    private static final class ToggleState {
        boolean on;
        Runnable paint;
        Runnable callback;
    }

    public static void setToggle(View v, boolean on) {
        if (v != null && v.getTag() instanceof ToggleState) {
            ToggleState st = (ToggleState) v.getTag();
            st.on = on;
            st.paint.run();
        }
    }

    public static void setSwitch(LinearLayout switchRow, boolean on) {
        if (switchRow != null && switchRow.getTag() instanceof View) {
            setToggle((View) switchRow.getTag(), on);
        }
    }

    public static boolean switchIsOnSafe(LinearLayout switchRow) {
        if (switchRow != null && switchRow.getTag() instanceof View) {
            View toggle = (View) switchRow.getTag();
            if (toggle.getTag() instanceof ToggleState) {
                return ((ToggleState) toggle.getTag()).on;
            }
        }
        return false;
    }
    public static int dp(Context c, int v) {
        float s = scale(c);
        return Math.max(1, Math.round(v * s));
    }

    public static int dpWidth(Context c, int shareOf1080) {
        return Math.max(1, Math.round(screenWidth(c)
                * (shareOf1080 / (float) DESIGN_WIDTH_PX)));
    }

    public static int share(Context c, int partsOf1080) {
        return dpWidth(c, partsOf1080);
    }

    public static void setTextSizePx(TextView t, int designSize) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(t.getContext(), designSize));
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c, View... children) {
        LinearLayout l = column(c);
        if (children != null) {
            for (View v : children) if (v != null) l.addView(v);
        }
        return l;
    }

    public static LinearLayout columnPad(Context c, int pad) {
        LinearLayout l = column(c);
        int p = dp(c, pad);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout cardTight(Context c, String title) {
        LinearLayout wrap = card(c);
        LinearLayout body = columnPad(c, 7);
        wrap.addView(body);
        wrap.setTag(body);
        if (title != null && !title.isEmpty()) {
            TextView t = new TextView(c);
            t.setText(title);
            Ui.setTextSizePx(t, 12);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setTextColor(col(c, TEXT));
            t.setPadding(0, 0, 0, dp(c, 4));
            t.setIncludeFontPadding(false);
            body.addView(t);
        }
        return wrap;
    }

    public static TextView hintTight(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        Ui.setTextSizePx(t, 16);
        t.setTextColor(col(c, MUTE));
        t.setLineSpacing(0, 1.05f);
        t.setPadding(0, dp(c, 2), 0, 0);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static LinearLayout row(Context c, int gap) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setTag(Integer.valueOf(dp(c, gap)));
        return l;
    }

    public static LinearLayout.LayoutParams lp(Context c, int wDp, int hDp, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                wDp < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(c, wDp),
                hDp < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(c, hDp));
        p.weight = weight;
        return p;
    }

    public static LinearLayout.LayoutParams lpBtn(Context c) {
        LinearLayout.LayoutParams p = lp(c, 0, H_BTN, 1f);
        p.rightMargin = dp(c, 8);
        return p;
    }

    public static LinearLayout.LayoutParams lpBtnFit(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, H_BTN));
        p.rightMargin = dp(c, 14);
        return p;
    }

    public static LinearLayout.LayoutParams lpIconFit(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, H_FIELD));
        p.rightMargin = dp(c, 14);
        return p;
    }

    public static LinearLayout card(Context c, String title) {
        LinearLayout wrap = card(c);
        LinearLayout body = columnPad(c, 12);
        wrap.addView(body);
        wrap.setTag(body);
        if (title != null && !title.isEmpty()) {
            TextView t = new TextView(c);
            t.setText(title);
            Ui.setTextSizePx(t, 14);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setTextColor(col(c, TEXT));
            t.setPadding(0, 0, 0, dp(c, 8));
            body.addView(t);
        }
        return wrap;
    }

    public static LinearLayout body(View card) {
        Object t = card.getTag();
        if (t instanceof LinearLayout) return (LinearLayout) t;
        return (LinearLayout) card;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(pill(c, col(c, CARD), col(c, LINE), R_CARD));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(c, 10);
        l.setLayoutParams(p);
        return l;
    }

    public static final class SCard {
        public final LinearLayout card;
        public final LinearLayout body;
        public final TextView note;

        private SCard(LinearLayout card, LinearLayout body, TextView note) {
            this.card = card;
            this.body = body;
            this.note = note;
        }
    }

    public static SCard sectionCard(Context c, String title, String glyph) {
        LinearLayout card = card(c);
        LinearLayout body = columnPad(c, 12);
        card.addView(body);
        if (title != null && !title.isEmpty()) {
            body.addView(group(c, glyph == null || glyph.isEmpty() ? "·" : glyph, title));
        }
        TextView note = hintTight(c, "");
        note.setVisibility(View.GONE);
        body.addView(note);
        return new SCard(card, body, note);
    }

    public static SCard sectionCard(LinearLayout pane, String title, String glyph) {
        SCard sc = sectionCard(pane.getContext(), title, glyph);
        pane.addView(sc.card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    public static void setSCardEnabled(SCard sc, boolean on, String reason) {
        if (sc == null) return;
        setEnabledRecursive(sc.body, on);
        for (int i = 0; i < sc.body.getChildCount(); i++) {
            View child = sc.body.getChildAt(i);

            child.setAlpha(child == sc.note ? 1f : (on ? 1f : 0.4f));
        }
        if (on) {
            sc.note.setVisibility(View.GONE);
        } else {
            sc.note.setText("");
            sc.note.setVisibility(View.GONE);
        }
    }

    public static void setCellEnabled(View cell, boolean on) {
        if (cell == null) return;
        setEnabledRecursive(cell, on);
        cell.setAlpha(on ? 1f : 0.4f);
    }

    private static void setEnabledRecursive(View v, boolean enabled) {
        if (v == null) return;
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                setEnabledRecursive(g.getChildAt(i), enabled);
            }
        }
    }

    public static LinearLayout titleRow(Context c, String title, String sub) {
        LinearLayout l = row(c, 0);
        TextView t = new TextView(c);
        t.setText(title);
        Ui.setTextSizePx(t, 15);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(col(c, TEXT));
        l.addView(t);
        if (sub != null) {
            TextView s = new TextView(c);
            s.setText("  " + sub);
            Ui.setTextSizePx(s, 12);
            s.setTextColor(col(c, DIM));
            l.addView(s);
        }
        return l;
    }

    public static TextView label(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        Ui.setTextSizePx(t, 12);
        t.setTextColor(col(c, DIM));
        t.setPadding(0, 0, 0, dp(c, 6));
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView value(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        Ui.setTextSizePx(t, 14);
        t.setTextColor(col(c, TEXT));
        return t;
    }

    public static TextView mono(Context c, String s) {
        TextView t = new TextView(c);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        Ui.setTextSizePx(t, 11);
        t.setTextColor(col(c, DIM));
        t.setBackground(new android.graphics.drawable.ColorDrawable(0x00000000));
        t.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        t.setText(s == null ? "" : s);
        t.setTextIsSelectable(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, 6);
        t.setLayoutParams(p);
        return t;
    }

    public static Button button(Context c, String text, boolean primary) {
        Button b = new Button(c);
        b.setText(text);
        Ui.setTextSizePx(b, 20);
        b.setTypeface(android.graphics.Typeface.DEFAULT);
        b.setAllCaps(false);

        if (primary) {
            b.setTextColor(0xFFFFFFFF);
            b.setBackground(pill(c, col(c, ACCENT), 0x00000000, R_BTN));
        } else {
            b.setTextColor(col(c, TEXT));
            b.setBackground(pill(c, col(c, SOFT), col(c, LINE), R_SMALL));
        }
        b.setMinHeight(dp(c, H_BTN));
        b.setMinimumHeight(dp(c, H_BTN));
        b.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        b.setGravity(android.view.Gravity.CENTER);
        pressableBg(c, b);
        return b;
    }

    public static Button buttonSmall(Context c, String text, boolean primary) {
        Button b = button(c, text, primary);
        Ui.setTextSizePx(b, 12);
        b.setMinHeight(dp(c, 42));
        b.setMinimumHeight(dp(c, 42));
        b.setPadding(dp(c, 6), 0, dp(c, 6), 0);
        return b;
    }

    public static Button buttonCompact(Context c, String text, boolean primary) {
        Button b = button(c, text, primary);
        Ui.setTextSizePx(b, 12);
        b.setMinHeight(dp(c, 40));
        b.setMinimumHeight(dp(c, 40));
        b.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        return b;
    }

    public static GradientDrawable pill(Context c, int fill, int stroke, int radiusDesign) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        float r = dp(c, radiusDesign);
        g.setCornerRadii(new float[]{r, r, r, r, r, r, r, r});
        g.setColor(fill);
        if (stroke != fill) g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    public static EditText field(Context c, String hint, String text) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(text == null ? "" : text);
        Ui.setTextSizePx(e, 18);
        e.setSingleLine(true);
        e.setTextColor(col(c, TEXT));
        e.setHintTextColor(col(c, MUTE));
        e.setBackground(pill(c, col(c, SOFT), col(c, LINE), R_SMALL));
        e.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        e.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, H_FIELD));
        p.topMargin = dp(c, 3);
        e.setLayoutParams(p);
        return e;
    }

    public static EditText num(Context c, String hint, String text) {
        EditText e = field(c, hint, text);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }

    public static View led(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(c, 12), dp(c, 12));
        p.rightMargin = dp(c, 8);
        v.setLayoutParams(p);
        ShapeDrawable sd = new ShapeDrawable(new OvalShape());
        sd.getPaint().setColor(col(c, IDLE));
        sd.setIntrinsicWidth(dp(c, 12));
        sd.setIntrinsicHeight(dp(c, 12));
        v.setBackgroundDrawable(sd);
        return v;
    }

    public static void tint(View led, String state) {
        int color;
        if ("ok".equals(state)) color = col(led.getContext(), OK);
        else if ("busy".equals(state)) color = col(led.getContext(), WARN);
        else if ("bad".equals(state)) color = col(led.getContext(), BAD);
        else if ("net".equals(state)) color = col(led.getContext(), ACCENT);
        else color = col(led.getContext(), IDLE);
        ShapeDrawable sd = new ShapeDrawable(new OvalShape());
        sd.getPaint().setColor(color);
        sd.setIntrinsicWidth(led.getLayoutParams().width);
        sd.setIntrinsicHeight(led.getLayoutParams().height);
        led.setBackgroundDrawable(sd);
    }

    public static void onClick(View v, Runnable r) {
        final Runnable f = r;
        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    f.run();
                } catch (Throwable t) {
                    CrashHandler.record(t);
                    LogHub.throwable("ui", "点击事件异常", t);
                }
            }
        });
    }

    public static ScrollView scroll(Context c, View child) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        s.addView(child);
        return s;
    }

    public static String text(TextView t) {
        CharSequence s = t.getText();
        return s == null ? "" : s.toString().trim();
    }

    public static int intOf(EditText e, int def) {
        try {
            String s = e.getText().toString().trim();
            if (TextUtils.isEmpty(s)) return def;
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return def;
        }
    }

    public static GradientDrawable ring(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    public static final int S_1 = 2;
    public static final int S_2 = 6;
    public static final int S_3 = 12;
    public static final int S_4 = 20;
    public static final int S_5 = 32;

    public static final int H_ROW = 44;
    public static final int H_BTN = 44;
    public static final int H_STEP = 48;
    public static final int H_FIELD = 36;
    public static final int H_CHIP = 32;
    public static final int R_SMALL = 8;
    public static final int R_BTN = 18;
    public static final int R_CHIP = 8;
    public static final int R_CARD = 12;
    public static final int R_SHEET = 20;

    public static LinearLayout sheet(Context c) {
        LinearLayout l = column(c);
        l.setBackground(pill(c, col(c, CARD), col(c, CARD), R_SHEET));
        int p = dp(c, 18);
        l.setPadding(p, dp(c, 14), p, dp(c, 12));
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return l;
    }

    public static LinearLayout panes(Context c) {
        LinearLayout outer = column(c);
        LinearLayout line = row(c, 0);
        line.setGravity(Gravity.TOP);

        LinearLayout left = column(c);
        LinearLayout right = column(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(c, 14);
        left.setLayoutParams(lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rp.leftMargin = dp(c, 14);
        right.setLayoutParams(rp);

        line.addView(left);
        line.addView(right);
        outer.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.setTag(new LinearLayout[]{left, right});
        return outer;
    }

    public static View spacer(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        v.setLayoutParams(p);
        return v;
    }

    public static View gap(Context c, int dpDesign) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, dpDesign)));
        return v;
    }

    public static LinearLayout pane(LinearLayout panes, int idx) {
        if (panes == null) return null;
        Object t = panes.getTag();
        if (t instanceof LinearLayout[]) {
            LinearLayout[] a = (LinearLayout[]) t;
            if (idx >= 0 && idx < a.length) return a[idx];
        }
        return null;
    }

    public static LinearLayout group(Context c, String glyph, String title) {
        LinearLayout l = row(c, 0);
        l.setGravity(Gravity.CENTER_VERTICAL);
        if (glyph != null && !glyph.isEmpty()) {
            TextView ic = new TextView(c);
            ic.setText(glyph);
            Ui.setTextSizePx(ic, 26);
            ic.setGravity(Gravity.CENTER);
            ic.setTextColor(col(c, ACCENT));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30));
            ip.rightMargin = dp(c, 8);
            ic.setLayoutParams(ip);
            l.addView(ic);
        }
        TextView t = new TextView(c);
        t.setText(title);
        Ui.setTextSizePx(t, 26);
        t.setTextColor(col(c, ACCENT));
        t.setSingleLine(true);
        l.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static LinearLayout listItem(Context c, String glyph, String title, String sub,
                                        View right) {
        LinearLayout l = row(c, 0);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(pill(c, col(c, SOFT), col(c, LINE), R_CARD));
        l.setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11));

        if (glyph != null && !glyph.isEmpty()) {
            TextView ic = new TextView(c);
            ic.setText(glyph);
            Ui.setTextSizePx(ic, 15);
            ic.setGravity(Gravity.CENTER);
            ic.setTextColor(col(c, ACCENT));
            ic.setBackground(pill(c, col(c, CARD), col(c, LINE), R_SMALL));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(c, 34), dp(c, 34));
            ip.rightMargin = dp(c, 12);
            ic.setLayoutParams(ip);
            l.addView(ic);
        }

        LinearLayout names = column(c);
        TextView t = new TextView(c);
        t.setText(title);
        Ui.setTextSizePx(t, 15);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(col(c, TEXT));
        names.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(c);
            s.setText(sub);
            Ui.setTextSizePx(s, 11);
            s.setTextColor(col(c, DIM));
            s.setPadding(0, dp(c, 3), 0, 0);
            names.addView(s);
        }
        l.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (right != null) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.leftMargin = dp(c, 12);
            l.addView(right, rp);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 9);
        l.setLayoutParams(lp);
        pressableBg(c, l);
        return l;
    }

    public static LinearLayout footRow(Context c, String left, String right, Runnable onRight) {
        TextView a = null;
        if (right != null && !right.isEmpty()) {
            a = new TextView(c);
            a.setText(right);
            Ui.setTextSizePx(a, 12);
            a.setTextColor(col(c, TEXT));
            a.setGravity(Gravity.CENTER);
            a.setPadding(dp(c, 14), 0, dp(c, 14), 0);
            a.setClickable(true);
            a.setFocusable(true);
            if (onRight != null) Ui.onClick(a, onRight);
        }
        return footRow(c, left, a);
    }

    public static LinearLayout footRow(Context c, String left, View right) {
        LinearLayout l = row(c, 0);
        l.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(c);
        t.setText(left == null ? "" : left);
        Ui.setTextSizePx(t, 11);
        t.setTextColor(col(c, MUTE));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null) {
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.leftMargin = dp(c, 12);
            l.addView(right, rp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static final int BG = 0;
    public static final int CARD = 1;
    public static final int SOFT = 2;
    public static final int TEXT = 3;
    public static final int DIM = 4;
    public static final int MUTE = 5;
    public static final int LINE = 6;
    public static final int ACCENT = 7;
    public static final int ON = 8;
    public static final int OK = 9;
    public static final int WARN = 10;
    public static final int BAD = 11;
    public static final int IDLE = 12;

    public static volatile boolean night = true;
    public static volatile Runnable themeFlip;

    private static final int[] PAL_DAY = {
            0xFFEDF2F8, 0xFFFFFFFF, 0xFFDEE8F7,
            0xFF15243B, 0xFF53647D, 0xFF8A97AB,
            0xFFCBD8E8, 0xFF2678E8, 0xFFD6E6FA,
            0xFF2E9E5B, 0xFFD98A0B, 0xFFD23F31, 0xFFAEBCCF,
    };

    private static final int[] PAL_NIGHT = {
            0xFF0B1322, 0xFF162237, 0xFF263953,
            0xFFF1F5FC, 0xFFA4B3CB, 0xFF6F819C,
            0xFF334A68, 0xFF2678E8, 0xFF1E3A5F,
            0xFF35B36F, 0xFFE0A63A, 0xFFE2604F, 0xFF45556F,
    };

    public static int col(Context c, int slot) {
        int[] pal = night ? PAL_NIGHT : PAL_DAY;
        return pal[slot];
    }

    public static int text(Context c) {
        return col(c, TEXT);
    }

    public static int dim(Context c) {
        return col(c, DIM);
    }

    public static int mute(Context c) {
        return col(c, MUTE);
    }

    public static int line(Context c) {
        return col(c, LINE);
    }

    public static int accent(Context c) {
        return col(c, ACCENT);
    }

    public static int soft(Context c) {
        return col(c, SOFT);
    }

    public static int onColor(Context c) {
        return col(c, ON);
    }

    public static TextView text(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s == null ? "" : s);
        Ui.setTextSizePx(t, 11);
        t.setTextColor(col(c, DIM));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public interface OnNum {
        void onNum(int value);
    }

    public interface OnPick {
        void onPick(int index);
    }

    public static TextView hint(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        Ui.setTextSizePx(t, 16);
        t.setTextColor(col(c, MUTE));
        t.setLineSpacing(0, 1.15f);
        t.setPadding(0, dp(c, 3), 0, 0);
        return t;
    }

    public static LinearLayout section(Context c, String title, String note) {
        LinearLayout l = row(c, 0);
        l.setGravity(Gravity.CENTER_VERTICAL);

        View bar = new View(c);
        bar.setBackgroundColor(col(c, ACCENT));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(c, 3), dp(c, 14));
        bp.rightMargin = dp(c, 7);
        bar.setLayoutParams(bp);
        l.addView(bar);

        TextView t = new TextView(c);
        t.setText(title);
        Ui.setTextSizePx(t, 13);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(col(c, TEXT));
        l.addView(t);

        if (note != null && !note.isEmpty()) {
            TextView n = new TextView(c);
            n.setText(note);
            Ui.setTextSizePx(n, 11);
            n.setTextColor(col(c, MUTE));
            n.setSingleLine(true);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            np.leftMargin = dp(c, 8);
            n.setLayoutParams(np);
            l.addView(n);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 4);
        lp.bottomMargin = dp(c, 6);
        l.setLayoutParams(lp);
        return l;
    }

public static LinearLayout stepper(Context c, int value, final int min, final int max,
                                       final int step, String unit, final OnNum cb) {
        return new Stepp(c, clamp(value, min, max), min, max, step, unit, cb).box;
    }

    public static final class StepTag {
        final TextView tv;
        final int min, max;
        final String unit;
        private int value;

        StepTag(TextView tv, int value, int min, int max, String unit) {
            this.tv = tv;
            this.min = min;
            this.max = max;
            this.unit = unit;
            this.value = clamp(value, min, max);
        }

        public int value() {
            return value;
        }

        public void set(int v) {
            value = clamp(v, min, max);
            tv.setText(unit == null ? String.valueOf(value) : value + unit);
        }
    }

    public static boolean setNum(View control, int v) {
        if (!(control instanceof LinearLayout)) return false;
        Object t = ((LinearLayout) control).getTag();
        if (t instanceof StepTag) {
            ((StepTag) t).set(v);
            return true;
        }

        if (t instanceof TextView) {
            ((TextView) t).setText(String.valueOf(v));
            return true;
        }
        return false;
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static final class Stepp {
        final LinearLayout box;
        LinearLayout skin;
        private final StepTag tag;
        private final Context c;
        private final int min, max, step;
        private final String unit;
        private final OnNum cb;
        private int value;
        private final android.os.Handler rep = new android.os.Handler();
        private Runnable repeat;

        Stepp(Context c, int v0, int min, int max, int step, String unit, OnNum cb) {
            this.c = c;
            this.min = min;
            this.max = max;
            this.step = step <= 0 ? 1 : step;
            this.unit = unit;
            this.cb = cb;
            this.value = v0;
            box = column(c);
            box.setGravity(Gravity.CENTER_HORIZONTAL);

            int bh = dp(c, 46);
            skin = row(c, 0);
            skin.setBackground(pill(c, col(c, SOFT), col(c, LINE), R_SMALL));

            TextView minus = square(c, "－");
            TextView num = new TextView(c);
            Ui.setTextSizePx(num, 22);
            num.setTextColor(col(c, TEXT));
            num.setGravity(Gravity.CENTER);
            TextView plus = square(c, "＋");

            minus.setBackgroundColor(0x00000000);
            plus.setBackgroundColor(0x00000000);

            skin.addView(minus, new LinearLayout.LayoutParams(dp(c, 38), bh));
            skin.addView(sep(c), new LinearLayout.LayoutParams(dp(c, 1), dp(c, 22)));
            LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(0, bh, 1f);
            mid.leftMargin = dp(c, 6);
            mid.rightMargin = dp(c, 6);
            skin.addView(num, mid);
            skin.addView(sep(c), new LinearLayout.LayoutParams(dp(c, 1), dp(c, 22)));
            skin.addView(plus, new LinearLayout.LayoutParams(dp(c, 38), bh));
            box.addView(skin, new LinearLayout.LayoutParams(dp(c, 150), bh));
            tag = new StepTag(num, value, min, max, unit);
            box.setTag(tag);

            minus.setOnTouchListener(hold(minus, -1));
            plus.setOnTouchListener(hold(plus, 1));
            paint();
        }

        private View sep(Context c) {
            View v = new View(c);
            v.setBackgroundColor(col(c, LINE));
            return v;
        }

        private View.OnTouchListener hold(final TextView btn, final int dir) {
            final Context bc = c;
            return new View.OnTouchListener() {
                public boolean onTouch(View view, android.view.MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            skin.setBackground(pill(bc, col(bc, ON), col(bc, ACCENT), R_SMALL));
                            set(value + dir * step, true);
                            stopRepeat();
                            repeat = new Runnable() {
                                int n;

                                public void run() {
                                    if (n++ > 40) {
                                        stopRepeat();
                                        return;
                                    }
                                    set(value + dir * step, true);
                                    rep.postDelayed(this, 110);
                                }
                            };
                            rep.postDelayed(repeat, 400);
                            return true;
                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                            stopRepeat();
                            skin.setBackground(pill(bc, col(bc, SOFT), col(bc, LINE), R_SMALL));
                            return true;
                        default:
                            return true;
                    }
                }
            };
        }

        void set(int v, boolean fromUser) {
            int nv = clamp(v, min, max);
            if (nv == value) {
                if (fromUser) flash(skin);
                return;
            }
            value = nv;
            paint();
            if (fromUser && cb != null) cb.onNum(value);
        }

        void paint() {
            tag.set(value);
        }

        void stopRepeat() {
            if (repeat != null) {
                rep.removeCallbacks(repeat);
                repeat = null;
            }
        }
    }

    private static void flash(final View v) {
        final Context c = v.getContext();
        if (c == null) return;
        Object prev = v.getTag();
        if (prev instanceof Runnable) v.removeCallbacks((Runnable) prev);
        v.setBackgroundColor(col(c, BAD));
        Runnable restore = new Runnable() {
            public void run() {
                v.setBackground(pill(c, col(c, SOFT), col(c, LINE), R_SMALL));
            }
        };
        v.setTag(restore);
        v.postDelayed(restore, 220);
    }

    public static int numOf(View control, int def) {
        if (!(control instanceof LinearLayout)) return def;
        Object t = ((LinearLayout) control).getTag();
        if (t instanceof StepTag) return ((StepTag) t).value();
        if (!(t instanceof TextView)) return def;
        CharSequence s = ((TextView) t).getText();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') digits.append(ch);
            else if (digits.length() > 0) break;
        }
        if (digits.length() == 0) return def;
        try {
            return Integer.parseInt(digits.toString());
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static TextView square(Context c, String glyph) {
        TextView t = new TextView(c);
        t.setText(glyph);
        Ui.setTextSizePx(t, 22);
        t.setTextColor(col(c, TEXT));
        t.setGravity(Gravity.CENTER);
        t.setBackground(pill(c, col(c, SOFT), col(c, LINE), R_SMALL));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    public static LinearLayout setting(Context c, String title, String sub, View control) {
        return setting(c, title, sub, control, false);
    }

    public static LinearLayout setting(Context c, String title, String sub, View control,
                                       boolean controlWide) {
        LinearLayout l = row(c, 0);
        l.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = column(c);
        TextView t = new TextView(c);
        t.setText(title);
        Ui.setTextSizePx(t, 20);
        t.setTextColor(col(c, TEXT));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(c);
            s.setText(sub);
            Ui.setTextSizePx(s, 15);
            s.setTextColor(col(c, MUTE));
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setPadding(0, dp(c, 2), 0, 0);
            names.addView(s);
        }
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                control == null ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                control == null ? 1f : 0f);
        l.addView(names, nlp);

        if (control != null) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    controlWide ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    controlWide ? 0.5f : 0f);
            cp.leftMargin = dp(c, 10);
            cp.gravity = Gravity.CENTER_VERTICAL;
            l.addView(control, cp);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 20);
        l.setLayoutParams(lp);
        return l;
    }

    public static LinearLayout settingFill(Context c, String title, String sub, View control) {
        LinearLayout l = row(c, 0);
        l.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = column(c);
        TextView t = new TextView(c);
        t.setText(title);
        Ui.setTextSizePx(t, 20);
        t.setTextColor(col(c, TEXT));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(c);
            s.setText(sub);
            Ui.setTextSizePx(s, 15);
            s.setTextColor(col(c, MUTE));
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setPadding(0, dp(c, 2), 0, 0);
            names.addView(s);
        }
        l.addView(names);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(c, 10);
        l.addView(control, cp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 20);
        l.setLayoutParams(lp);
        return l;
    }

    public static LinearLayout switchRow(Context c, String title, String sub,
                                         boolean on, final Runnable onChanged) {
        LinearLayout l = row(c, 0);
        l.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = column(c);
        TextView t = new TextView(c);
        t.setText(title);
        Ui.setTextSizePx(t, 20);
        t.setTextColor(col(c, TEXT));
        t.setSingleLine(true);
        names.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(c);
            s.setText(sub);
            Ui.setTextSizePx(s, 15);
            s.setTextColor(col(c, MUTE));
            s.setSingleLine(true);
            s.setPadding(0, dp(c, 3), 0, 0);
            names.addView(s);
        }
        l.addView(names, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final View tg = toggle(c, on, onChanged);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = dp(c, 12);
        l.addView(tg, tp);
        l.setTag(tg);
        l.setClickable(true);
        l.setFocusable(true);
        l.setMinimumHeight(dp(c, 48));
        Ui.onClick(l, new Runnable() {
            public void run() {
                tg.performClick();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 20);
        l.setLayoutParams(lp);
        return l;
    }

    public static View rowLine(Context c) {
        View v = new View(c);
        v.setBackgroundColor(col(c, LINE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        p.topMargin = dp(c, 8);
        p.bottomMargin = dp(c, 8);
        v.setLayoutParams(p);
        return v;
    }

    private static void applyChip(TextView t, boolean selected) {
        Context c = t.getContext();
        t.setTextColor(col(c, selected ? ACCENT : DIM));
        t.setTypeface(selected ? android.graphics.Typeface.DEFAULT_BOLD
                : android.graphics.Typeface.DEFAULT);
        t.setBackground(pill(c, selected ? col(c, CARD) : col(c, SOFT),
                col(c, selected ? ACCENT : LINE), R_CHIP));
        t.setPadding(dp(c, 14), 0, dp(c, 14), 0);
    }

    private static TextView chip(Context c, String text, boolean selected) {
        TextView t = new TextView(c);
        t.setText(text);
        Ui.setTextSizePx(t, 13);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        applyChip(t, selected);
        t.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        t.setClickable(true);
        t.setFocusable(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, H_CHIP));
        p.rightMargin = dp(c, 7);
        p.bottomMargin = dp(c, 7);
        t.setLayoutParams(p);
        return t;
    }

    private static LinearLayout.LayoutParams lpChip(Context c, int minWidthPx) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                Math.max(minWidthPx, dp(c, 88)),
                dp(c, H_CHIP));
        p.bottomMargin = dp(c, S_2);
        p.rightMargin = dp(c, S_2);
        return p;
    }

    private static int widest(Context c, String[] options) {
        int w = 0;
        if (options == null) return 0;
        android.text.TextPaint tp = new android.text.TextPaint();
        tp.setTextSize(dp(c, 13));
        tp.setTypeface(android.graphics.Typeface.DEFAULT);
        for (String o : options) {
            if (o == null) continue;
            int t = (int) tp.measureText("✓ " + o);
            if (t > w) w = t;
        }
        return w + dp(c, 16);
    }

    public static LinearLayout chips(Context c, final String[] options, int selected, final OnPick cb) {
        return chips(c, options, selected == -1 ? null : new int[]{selected}, cb);
    }

    public static LinearLayout chipsMulti(Context c, final String[] options, boolean[] on,
                                          final OnPick cb) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        final TextView[] views = new TextView[options.length];
        final int w = widest(c, options);
        final int perRow = 3;
        for (int i = 0; i < options.length; i += perRow) {
            LinearLayout line = row(c, 0);
            line.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            for (int j = i; j < Math.min(i + perRow, options.length); j++) {
                final int idx = j;
                boolean sel = on != null && j < on.length && on[j];
                TextView chip = chip(c, options[j], sel);
                if (sel) chip.setText("✓ " + options[j]);
                if (cb != null) {
                    Ui.onClick(chip, new Runnable() {
                        public void run() {
                            cb.onPick(idx);
                        }
                    });
                }
                views[j] = chip;
                line.addView(chip, lpChip(c, w));
            }
            wrap.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        wrap.setTag(views);
        return wrap;
    }

    private static LinearLayout chips(Context c, final String[] options, final int[] selected,
                                      final OnPick cb) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        final TextView[] views = new TextView[options.length];
        final int w = widest(c, options);
        final int perRow = perRow(c, w, options.length);
        for (int i = 0; i < options.length; i += perRow) {
            LinearLayout line = row(c, 0);
            line.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            for (int j = i; j < Math.min(i + perRow, options.length); j++) {
                final int idx = j;
                boolean sel = selected != null && contains(selected, j);
                TextView chip = chip(c, options[j], sel);
                if (sel) chip.setText("✓ " + options[j]);
                if (cb != null) {
                    Ui.onClick(chip, new Runnable() {
                        public void run() {
                            cb.onPick(idx);
                        }
                    });
                }
                views[j] = chip;
                line.addView(chip, lpChip(c, w));
            }
            wrap.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        wrap.setTag(views);
        return wrap;
    }

    private static int perRow(Context c, int chipW, int total) {
        int avail = (int) (screenWidth(c) * 0.44f);
        int each = chipW + dp(c, S_2) * 2;
        if (each <= 0) return 3;
        int n = avail / each;
        if (n < 1) n = 1;
        if (n > total) n = total;
        return n;
    }

    private static boolean contains(int[] arr, int v) {
        if (arr == null) return false;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == v) return true;
        }
        return false;
    }

    public static void pickChip(LinearLayout chipRow, String[] options, int selected) {
        boolean[] on = new boolean[options.length];
        if (selected >= 0 && selected < on.length) on[selected] = true;
        pickChipMulti(chipRow, options, on);
    }

    public static void pickChipMulti(LinearLayout chipRow, String[] options, boolean[] on) {
        if (chipRow == null) return;
        Object t = chipRow.getTag();
        if (!(t instanceof TextView[])) return;
        TextView[] vs = (TextView[]) t;
        for (int i = 0; i < vs.length && i < options.length; i++) {
            boolean sel = on != null && i < on.length && on[i];
            vs[i].setText(sel ? "✓ " + options[i] : options[i]);
            applyChip(vs[i], sel);
        }
    }

    public static LinearLayout tabStrip(Context c, final String[] names, final OnPick cb) {
        return tabStrip(c, names, cb, false);
    }

    public static LinearLayout tabStrip(Context c, final String[] names, final OnPick cb,
                                        final boolean fit) {
        LinearLayout strip = row(c, 0);
        strip.setGravity(fit ? (Gravity.LEFT | Gravity.CENTER_VERTICAL)
                : Gravity.CENTER_VERTICAL);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout cell = column(c);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            TextView t = new TextView(c);
            t.setText(names[i]);
            Ui.setTextSizePx(t, 14);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(t, new LinearLayout.LayoutParams(
                    fit ? ViewGroup.LayoutParams.WRAP_CONTENT
                           : ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(c, 30)));

            View bar = new View(c);
            bar.setBackgroundColor(0x00000000);
            int barW = Math.max(dp(c, 40),
                    (int) t.getPaint().measureText(names[i]) + dp(c, 8));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(barW, dp(c, 3));
            bp.topMargin = dp(c, 4);
            bar.setLayoutParams(bp);
            bar.setTag("tabbar");
            cell.addView(bar);

            LinearLayout.LayoutParams lp;
            if (fit) {
                lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = dp(c, 4);
                lp.rightMargin = dp(c, 14);
            } else {
                lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            }
            cell.setLayoutParams(lp);
            if (cb != null) {
                Ui.onClick(cell, new Runnable() {
                    public void run() {
                        cb.onPick(idx);
                    }
                });
                Ui.onClick(t, new Runnable() {
                    public void run() {
                        cb.onPick(idx);
                    }
                });
            }
            pressableBg(c, cell);
            strip.addView(cell);
        }

        LinearLayout outer = column(c);
        outer.addView(strip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.setTag(strip);
        return outer;
    }

    public static void pickTab(LinearLayout stripBox, int selected) {
        if (stripBox == null) return;
        Object t = stripBox.getTag();
        if (!(t instanceof LinearLayout)) return;
        LinearLayout strip = (LinearLayout) t;
        for (int i = 0; i < strip.getChildCount(); i++) {
            View cell = strip.getChildAt(i);
            if (!(cell instanceof LinearLayout) || ((LinearLayout) cell).getChildCount() < 2) continue;
            LinearLayout col2 = (LinearLayout) cell;
            View tv = col2.getChildAt(0);
            View bar = col2.getChildAt(1);
            if (!(tv instanceof TextView)) continue;
            Context c = tv.getContext();
            boolean sel = i == selected;
            TextView text = (TextView) tv;
            text.setTextColor(sel ? col(c, TEXT) : col(c, DIM));
            text.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
                    sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            bar.setBackgroundColor(sel ? col(c, ACCENT) : 0x00000000);
        }
    }

    public static TextView actionText(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        Ui.setTextSizePx(t, 14);
        t.setSingleLine(true);
        t.setTextColor(col(c, TEXT));
        t.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        t.setClickable(true);
        t.setFocusable(true);
        pressable(c, t);
        return t;
    }

    public static void pressable(final Context c, final View v) {
        pressable(c, v, true);
    }

    public static void pressableBg(Context c, View v) {
        pressable(c, v, false);
    }

    private static void pressable(final Context c, final View v, final boolean tintText) {
        v.setOnTouchListener(new View.OnTouchListener() {
            private android.graphics.drawable.Drawable downBg;
            private int downText;
            public boolean onTouch(View view, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downBg = view.getBackground();
                        downText = view instanceof TextView
                                ? ((TextView) view).getCurrentTextColor() : 0;
                        if (tintText && view instanceof TextView) {
                            ((TextView) view).setTextColor(col(c, ACCENT));
                        }
                        view.setBackground(pill(c, col(c, ON), 0x00000000, R_SMALL));
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (tintText && view instanceof TextView) {
                            ((TextView) view).setTextColor(downText);
                        }
                        view.setBackground(downBg);
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    public static TextView stateAction(Context c, String s, boolean on) {
        TextView t = new TextView(c);
        t.setText(s);
        Ui.setTextSizePx(t, 14);
        t.setSingleLine(true);
        t.setTextColor(col(c, on ? ACCENT : DIM));
        t.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        t.setClickable(true);
        t.setFocusable(true);
        pressableBg(c, t);
        return t;
    }

    public static Button wideButton(Context c, String text, boolean primary) {
        Button b = button(c, text, primary);
        b.setLayoutParams(lp(c, -1, H_BTN, 0f));
        return b;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(col(c, LINE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        p.topMargin = dp(c, 8);
        p.bottomMargin = dp(c, 8);
        v.setLayoutParams(p);
        return v;
    }

    public static final class Grid extends LinearLayout {
        private final int cols;
        private LinearLayout cur;
        private int inCur;

        Grid(Context c, int cols) {
            super(c);
            this.cols = Math.max(1, cols);
            setOrientation(VERTICAL);
        }

        public int room() {
            return cur == null ? cols : cols - inCur;
        }

        public boolean add(View v) {
            if (v == null) return false;
            if (cur == null || inCur >= cols) newRow();
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            p.rightMargin = dp(getContext(), 10);
            v.setLayoutParams(p);
            cur.addView(v);
            inCur++;
            return true;
        }

        public void reset() {
            removeAllViews();
            cur = null;
            inCur = 0;
        }

        private void newRow() {
            cur = row(getContext(), 0);
            addView(cur, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            inCur = 0;
        }
    }

    public static Grid cols(Context c, int cols) {
        return new Grid(c, cols);
    }

    public static Grid rowOf(Context c, int cols, View... children) {
        Grid g = new Grid(c, cols);
        if (children != null) {
            for (View v : children) g.add(v);
        }
        return g;
    }

    public static void fillWidth(View v, int shareOf1080) {
        if (v == null) return;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                dpWidth(v.getContext(), shareOf1080), ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(v.getContext(), 8);
        v.setLayoutParams(p);
    }

    public static LinearLayout segment(Context c, final String[] options, int selected,
                                       final OnPick cb) {
        LinearLayout wrap = row(c, 0);
        wrap.setBackground(pill(c, col(c, SOFT), 0x00000000, R_SMALL));
        final TextView[] views = new TextView[options.length];
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(options[i]);
            Ui.setTextSizePx(t, 18);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setClickable(true);
            t.setFocusable(true);
            applySeg(c, t, i == selected);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, dp(c, H_STEP), 1f);
            p.leftMargin = dp(c, 2);
            p.rightMargin = dp(c, 2);
            p.topMargin = dp(c, 2);
            p.bottomMargin = dp(c, 2);
            wrap.addView(t, p);
            if (cb != null) {
                Ui.onClick(t, new Runnable() {
                    public void run() {
                        cb.onPick(idx);
                    }
                });
            }
            pressableBg(c, t);
            views[i] = t;
        }
        wrap.setTag(views);
        return wrap;
    }

    private static void applySeg(Context c, TextView t, boolean selected) {
        t.setTypeface(selected ? android.graphics.Typeface.DEFAULT_BOLD
                : android.graphics.Typeface.DEFAULT);
        t.setTextColor(col(c, selected ? ACCENT : DIM));
        t.setBackground(selected
                ? pill(c, col(c, ON), 0x00000000, R_SMALL)
                : new android.graphics.drawable.ColorDrawable(0x00000000));
    }

    public static void pickSeg(LinearLayout seg, int selected) {
        if (seg == null) return;
        Object t = seg.getTag();
        if (!(t instanceof TextView[])) return;
        TextView[] vs = (TextView[]) t;
        for (int i = 0; i < vs.length; i++) {
            applySeg(seg.getContext(), vs[i], i == selected);
        }
    }

    public static final class Sheet {
        public final Dialog dialog;
        private final LinearLayout body;
        private final LinearLayout btnRow;
        private final Context c;

        private Sheet(Context ctx) {
            c = ctx;
            dialog = new Dialog(c);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout card = column(c);
            card.setBackground(pill(c, col(c, CARD), col(c, LINE), R_SHEET));
            int pad = dp(c, 18);
            card.setPadding(pad, dp(c, 16), pad, dp(c, 14));

            body = column(c);
            card.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            btnRow = row(c, 0);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = dp(c, 14);
            card.addView(btnRow, bp);

            dialog.setContentView(card);
            Window w = dialog.getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(dpWidth(c, 620), ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        public static Sheet of(Context c, String title) {
            Sheet s = new Sheet(c);
            TextView t = new TextView(c);
            t.setText(title);
            Ui.setTextSizePx(t, 22);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setTextColor(col(c, TEXT));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.bottomMargin = dp(c, 8);
            s.body.addView(t, tp);
            return s;
        }

        public Sheet msg(String text) {
            TextView t = Ui.text(c, text == null ? "" : text);
            Ui.setTextSizePx(t, 18);
            body.addView(t, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return this;
        }

        public Sheet content(View v) {
            if (v != null) body.addView(v);
            return this;
        }

        public Sheet button(String text, boolean primary, final Runnable action) {
            Button b = Ui.button(c, text, primary);
            Ui.onClick(b, new Runnable() {
                public void run() {
                    dismiss();
                    if (action != null) action.run();
                }
            });
            LinearLayout.LayoutParams p = lp(c, 0, H_BTN, 1f);
            p.rightMargin = dp(c, 10);
            btnRow.addView(b, p);
            return this;
        }

        public void show() {
            int n = btnRow.getChildCount();
            if (n > 0) {
                View last = btnRow.getChildAt(n - 1);
                ((LinearLayout.LayoutParams) last.getLayoutParams()).rightMargin = 0;
            }
            try {
                dialog.show();
            } catch (Throwable t) {
                LogHub.throwable("ui", "对话框显示失败", t);
            }
        }

        public void dismiss() {
            try {
                dialog.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void notice(Context c, String title, String msg, String okText) {
        Sheet.of(c, title).msg(msg).button(okText == null ? "知道了" : okText, true, null).show();
    }
}
