package dev.rclient.anim;

/** Easing curves. All take t in [0,1] and return the eased progress (may overshoot for back/elastic). */
public enum Easing {
    LINEAR { public float apply(float t) { return t; } },
    CUBIC_OUT { public float apply(float t) { float u = 1 - t; return 1 - u * u * u; } },
    CUBIC_IN_OUT { public float apply(float t) { return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; } },
    QUINT_OUT { public float apply(float t) { float u = 1 - t; return 1 - u * u * u * u * u; } },
    QUINT_IN_OUT { public float apply(float t) { return t < 0.5f ? 16 * t * t * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 5) / 2; } },
    EXPO_OUT { public float apply(float t) { return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t); } },
    EXPO_IN_OUT {
        public float apply(float t) {
            if (t <= 0) return 0;
            if (t >= 1) return 1;
            return t < 0.5f ? (float) Math.pow(2, 20 * t - 10) / 2 : (2 - (float) Math.pow(2, -20 * t + 10)) / 2;
        }
    },
    BACK_OUT {
        public float apply(float t) {
            final float c1 = 1.70158f, c3 = c1 + 1;
            float u = t - 1;
            return 1 + c3 * u * u * u + c1 * u * u;
        }
    },
    ELASTIC_OUT {
        public float apply(float t) {
            if (t <= 0) return 0;
            if (t >= 1) return 1;
            final float c4 = (float) (2 * Math.PI / 3);
            return (float) Math.pow(2, -10 * t) * (float) Math.sin((t * 10 - 0.75f) * c4) + 1;
        }
    };

    public abstract float apply(float t);
}
