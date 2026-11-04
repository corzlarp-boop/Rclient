package dev.rclient.gui;

/**
 * Per-frame list of clickable regions. Rendering registers each interactive widget at its real
 * (scrolled, animated) position, so hit-testing always matches what is on screen. Pooled: no per-frame
 * allocation after warm-up.
 */
public final class Regions {
    public enum Kind {
        TAB, CARD, TOGGLE, STAR, DOTS, SEARCH, CLOSE,
        S_BOOL, S_NUM, S_MODE, S_MODE_OPT, S_KEY, S_COLOR_HEAD, S_COLOR_SV, S_COLOR_HUE, S_COLOR_GRAD, S_COLOR_RAINBOW, S_COLOR_STOP,
        CFG_ROW, CFG_SAVE, CFG_LOAD, CFG_RENAME, CFG_DELETE, CFG_FIELD,
        FOOT_BLUR, FOOT_NOTIF, EDITOR_OPEN
    }

    public static final class Region {
        public int x, y, w, h, extra;
        public Kind kind;
        public Object obj;
        public boolean clipped;   // inside the scrolling viewport: only hit when the mouse is inside it
        public boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final Region[] pool = new Region[768];
    private int count;
    public int clipX, clipY, clipW, clipH;

    public Regions() { for (int i = 0; i < pool.length; i++) pool[i] = new Region(); }

    public void clear() { count = 0; }

    public Region add(Kind kind, int x, int y, int w, int h, Object obj, int extra, boolean clipped) {
        if (count >= pool.length) return pool[pool.length - 1];
        Region r = pool[count++];
        r.kind = kind; r.x = x; r.y = y; r.w = w; r.h = h; r.obj = obj; r.extra = extra; r.clipped = clipped;
        return r;
    }

    /** Topmost (last registered) region under the mouse. */
    public Region hit(double mx, double my) {
        for (int i = count - 1; i >= 0; i--) {
            Region r = pool[i];
            if (!r.contains(mx, my)) continue;
            if (r.clipped && !(mx >= clipX && mx < clipX + clipW && my >= clipY && my < clipY + clipH)) continue;
            return r;
        }
        return null;
    }

    /** Topmost region of a kind under the mouse (used for hover animations). */
    public Region hit(double mx, double my, Kind kind) {
        for (int i = count - 1; i >= 0; i--) {
            Region r = pool[i];
            if (r.kind != kind || !r.contains(mx, my)) continue;
            if (r.clipped && !(mx >= clipX && mx < clipX + clipW && my >= clipY && my < clipY + clipH)) continue;
            return r;
        }
        return null;
    }
}
