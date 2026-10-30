package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;

/**
 * The screensaver's clock as a split-flap board: each digit on its own dark card with the seam a
 * real flip clock has across the middle.
 *
 * Drawn with Canvas primitives rather than a TextClock so the digits can be laid out per card and
 * weighted properly. The card height drives everything else, so the whole clock scales from the
 * view's height alone. It redraws once a minute, on the minute, and does nothing in between: a
 * screensaver that repainted continuously would keep a 1 GB TV's CPU busy all night.
 *
 * Deliberately not animated. A flip animation means holding two bitmaps per digit and running a
 * rotation every minute, which is the kind of thing this launcher avoids on purpose.
 */
public class FlipClockView extends View {

    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String time = "";

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            readTime();
            invalidate();
            handler.postDelayed(this, msToNextMinute());
        }
    };

    public FlipClockView(Context context) {
        this(context, null);
    }

    public FlipClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        card.setStyle(Paint.Style.FILL);
        // Nearly opaque: a lighter card let the wallpaper through the digits and made them hard to read
        // from across a room, which is the one thing a screensaver clock has to get right.
        card.setColor(0xF0141418);
        seam.setStyle(Paint.Style.FILL);
        seam.setColor(0x73000000);          // the split between the two halves
        gloss.setStyle(Paint.Style.FILL);
        gloss.setColor(0x0DFFFFFF);         // a touch of light on the upper half
        digit.setStyle(Paint.Style.FILL);
        digit.setColor(0xF7FFFFFF);
        digit.setTextAlign(Paint.Align.CENTER);
        Typeface tf = getResources().getFont(R.font.outfit);
        digit.setTypeface(tf);
        // Outfit is a variable font; ask for a heavy weight rather than letting it render at its default.
        digit.setFontVariationSettings("'wght' 700");
        readTime();
    }

    private void readTime() {
        Calendar c = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(getContext());
        int h = h24 ? c.get(Calendar.HOUR_OF_DAY) : c.get(Calendar.HOUR);
        if (!h24 && h == 0) h = 12;
        time = (h24 ? String.format("%02d", h) : String.valueOf(h))
                + ":" + String.format("%02d", c.get(Calendar.MINUTE));
    }

    private static long msToNextMinute() {
        Calendar c = Calendar.getInstance();
        return 60_000L - (c.get(Calendar.SECOND) * 1000L + c.get(Calendar.MILLISECOND)) + 50L;
    }

    /** Card height drives the rest, so the clock scales from the view's height alone. */
    private float cardH() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    private float cardW(float h) {
        return h * 0.64f;
    }

    private float gap(float h) {
        return h * 0.055f;
    }

    private float colonW(float h) {
        return h * 0.26f;
    }

    private float widthFor(float h) {
        float w = 0;
        for (int i = 0; i < time.length(); i++) {
            w += time.charAt(i) == ':' ? colonW(h) : cardW(h);
            if (i < time.length() - 1) w += gap(h);
        }
        return w;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = resolveSize((int) (getResources().getDisplayMetrics().density * 180), heightMeasureSpec);
        float inner = h - getPaddingTop() - getPaddingBottom();
        int w = (int) Math.ceil(widthFor(inner)) + getPaddingLeft() + getPaddingRight();
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), h);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        readTime();
        handler.postDelayed(tick, msToNextMinute());
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = cardH();
        if (h <= 0) return;
        float r = h * 0.09f;
        float x = getPaddingLeft();
        float top = getPaddingTop();
        digit.setTextSize(h * 0.70f);
        digit.getFontMetrics(fm);
        // Centre the glyphs on the card rather than on the baseline, so the seam cuts them in half.
        float baseline = top + h / 2f - (fm.ascent + fm.descent) / 2f;

        for (int i = 0; i < time.length(); i++) {
            char ch = time.charAt(i);
            if (ch == ':') {
                float cw = colonW(h);
                float dot = h * 0.055f;
                canvas.drawCircle(x + cw / 2f, top + h * 0.36f, dot, digit);
                canvas.drawCircle(x + cw / 2f, top + h * 0.64f, dot, digit);
                x += cw + gap(h);
                continue;
            }
            float w = cardW(h);
            box.set(x, top, x + w, top + h);
            canvas.drawRoundRect(box, r, r, card);
            // Upper half catches a little light, as the front flap of a real one does.
            box.set(x, top, x + w, top + h / 2f);
            canvas.drawRoundRect(box, r, r, gloss);
            canvas.drawRect(x, top + h / 2f - r, x + w, top + h / 2f, gloss);
            canvas.drawText(String.valueOf(ch), x + w / 2f, baseline, digit);
            // The seam goes over the digit, splitting it the way the two flaps do.
            canvas.drawRect(x, top + h / 2f - h * 0.008f, x + w, top + h / 2f + h * 0.008f, seam);
            x += w + gap(h);
        }
    }
}
