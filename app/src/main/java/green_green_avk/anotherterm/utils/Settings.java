package green_green_avk.anotherterm.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import androidx.annotation.AnyRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.NoSuchElementException;

public abstract class Settings {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Param {
        @AnyRes int defRes() default 0;
    }

    protected void onBeforeChange(@NonNull final String key, @Nullable final Object value) {
    }

    protected void onAfterChange(@NonNull final String key, @Nullable final Object value) {
    }

    protected void onBeforeInit(@NonNull final SharedPreferences sp) {
    }

    @Keep
    private final SharedPreferences.OnSharedPreferenceChangeListener onChange =
            (sharedPreferences, key) -> {
                try {
                    final Object value = get(key);
                    onBeforeChange(key, value);
                    set(key, sharedPreferences, value);
                    onAfterChange(key, value);
                } catch (final NoSuchElementException ignored) {
                } catch (final IllegalArgumentException ignored) {
                }
            };

    public final void init(@NonNull final Context ctx, @NonNull final SharedPreferences sp) {
        onBeforeInit(sp);
        final SharedPreferences.Editor editor = sp.edit(); // for repair
        final Resources rr = ctx.getResources();
        final Field[] ff = getClass().getFields();
        for (final Field f : ff) {
            final Param a = f.getAnnotation(Param.class);
            if (a == null) continue;
            Object v;
            try {
                v = f.get(this);
            } catch (final IllegalAccessException e) {
                continue;
            }
            final Class<?> c = f.getType();
            if (c.equals(String.class)) {
                if (a.defRes() != 0)
                    v = rr.getString(a.defRes());
                try {
                    v = sp.getString(f.getName(), (String) v);
                } catch (final ClassCastException e) {
                    editor.putString(f.getName(), (String) v);
                }
            } else if (c.equals(Integer.TYPE)) {
                if (a.defRes() != 0)
                    v = rr.getInteger(a.defRes());
                try {
                    v = sp.getInt(f.getName(), (int) v);
                } catch (final ClassCastException e) {
                    editor.putInt(f.getName(), (int) v);
                }
            } else if (c.equals(Boolean.TYPE)) {
                if (a.defRes() != 0)
                    v = rr.getBoolean(a.defRes());
                try {
                    v = sp.getBoolean(f.getName(), (boolean) v);
                } catch (final ClassCastException e) {
                    editor.putBoolean(f.getName(), (boolean) v);
                }
            } else continue;
            try {
                f.set(this, v);
            } catch (final IllegalAccessException ignored) {
            }
        }
        editor.apply();
        sp.registerOnSharedPreferenceChangeListener(onChange);
    }

    public final Object get(@NonNull final String k) {
        final Field f;
        try {
            f = getClass().getField(k);
        } catch (final NoSuchFieldException e) {
            throw new NoSuchElementException();
        }
        if (f.getAnnotation(Param.class) == null) return null;
        try {
            return f.get(this);
        } catch (final IllegalAccessException e) {
            throw new NoSuchElementException();
        }
    }

    public final void set(@NonNull final String k, @Nullable final Object v) {
        final Field f;
        try {
            f = getClass().getField(k);
        } catch (final NoSuchFieldException e) {
            throw new NoSuchElementException();
        }
        if (f.getAnnotation(Param.class) == null) throw new NoSuchElementException();
        try {
            f.set(this, v);
        } catch (final IllegalAccessException e) {
            throw new NoSuchElementException();
        }
    }

    public final void set(@NonNull final String k, @NonNull final SharedPreferences sp,
                          @Nullable final Object dv) {
        final Field f;
        try {
            f = getClass().getField(k);
        } catch (final NoSuchFieldException e) {
            throw new NoSuchElementException();
        }
        if (f.getAnnotation(Param.class) == null) throw new NoSuchElementException();
        final Object v;
        final Class<?> c = f.getType();
        try {
            if (c.equals(String.class)) {
                v = sp.getString(k, (String) dv);
            } else if (c.equals(Integer.TYPE)) {
                if (dv == null) throw new ClassCastException();
                v = sp.getInt(k, (int) dv);
            } else if (c.equals(Boolean.TYPE)) {
                if (dv == null) throw new ClassCastException();
                v = sp.getBoolean(k, (boolean) dv);
            } else {
                throw new UnsupportedOperationException();
            }
        } catch (final ClassCastException e) {
            throw new IllegalArgumentException("Wrong field type");
        }
        try {
            f.set(this, v);
        } catch (final IllegalAccessException e) {
            throw new NoSuchElementException();
        }
    }

    public final void fill(@NonNull final Map<String, ?> map) {
        final Field[] ff = getClass().getFields();
        for (final Field f : ff) {
            if (map.containsKey(f.getName()))
                set(f.getName(), map.get(f.getName()));
        }
    }
}
