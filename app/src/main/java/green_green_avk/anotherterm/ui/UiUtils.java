package green_green_avk.anotherterm.ui;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.util.AndroidException;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ShareCompat;
import androidx.core.graphics.ColorUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import green_green_avk.anotherterm.LinksProvider;
import green_green_avk.anotherterm.R;
import green_green_avk.anotherterm.ScratchpadManager;
import green_green_avk.anotherterm.utils.Misc;
import green_green_avk.anotherterm.utils.ValueProvider;
import me.saket.bettermovementmethod.BetterLinkMovementMethod;

public final class UiUtils {
    private UiUtils() {
    }

    private static final String[] unitPrefixes = {"", "K", "M", "G", "T", "P", "E"};

    @NonNull
    public static String makeHumanReadableBytes(final long bytes) {
        final long v = Math.abs(bytes);
        int shift = 60;
        for (int i = 6; i >= 0; i--, shift -= 10) {
            if (v >> shift != 0) {
                return i > 0 ?
                        String.format(Locale.getDefault(), "%.3f %siB",
                                (float) bytes / (1 << shift), unitPrefixes[i]) :
                        String.format(Locale.getDefault(), "%d B",
                                bytes);
            }
        }
        return "0 B";
    }

    @NonNull
    public static String brief(@NonNull final Context ctx, @NonNull final String v, final int n) {
        return v.length() < n ? v :
                ctx.getString(R.string.msg_s___, v.substring(0, n));
    }

    @NonNull
    private static Uri _toScratchpad(@NonNull final Context ctx, @NonNull final String v)
            throws IOException {
        final ScratchpadManager sm = Misc.getApplication(ctx).scratchpadManager;
        return sm.getUri(sm.put(v));
    }

    public static void toScratchpad(@NonNull final Context ctx, @Nullable final String v) {
        if (v == null) {
            Toast.makeText(ctx, R.string.msg_nothing_to_save,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            _toScratchpad(ctx, v);
        } catch (final IOException e) {
            Toast.makeText(ctx,
                    ctx.getString(R.string.msg_scratchpad_malfunction_s,
                            e.getLocalizedMessage()),
                    Toast.LENGTH_SHORT).show();
        }
        Toast.makeText(ctx, R.string.msg_saved_to_scratchpad,
                Toast.LENGTH_SHORT).show();
    }

    /**
     * External URLs sharing.
     */
    public static void shareUri(@NonNull final Activity ctx, @NonNull final Uri uri,
                                @NonNull final String description) {
        // https://stackoverflow.com/questions/29907030/sharing-text-plain-string-via-bluetooth-converts-data-into-html
        // So, via ContentProvider...
        ShareCompat.IntentBuilder.from(ctx).setType("text/html")
                .setStream(LinksProvider.getHtmlWithLink(uri, description)).startChooser();
    }

    /**
     * Content URIs sharing.
     */
    public static void share(@NonNull final Activity ctx, @NonNull final Uri uri) {
        final String type = ctx.getContentResolver().getType(uri);
        ShareCompat.IntentBuilder.from(ctx)
                .setType(type != null ? type : "text/plain").setStream(uri).startChooser();
    }

    @NonNull
    private static ClipData.Item firstItemFromClipboard(@NonNull final Context ctx) {
        final ClipboardManager clipboard =
                (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null)
            throw new IllegalStateException("Can't get ClipboardManager");
        if (!clipboard.hasPrimaryClip())
            throw new IllegalStateException("Clipboard is empty");
        final ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() < 1)
            throw new IllegalStateException("Clipboard is empty");
        final ClipData.Item clipItem = clipData.getItemAt(0);
        if (clipItem == null)
            throw new IllegalStateException("Clipboard is empty");
        return clipItem;
    }

    @NonNull
    public static Uri uriFromClipboard(@NonNull final Context ctx) {
        final ClipData.Item clipItem = firstItemFromClipboard(ctx);
        Uri uri = clipItem.getUri();
        if (uri == null)
            uri = Uri.parse(clipItem.coerceToText(ctx).toString());
        return uri;
    }

    @NonNull
    public static CharSequence textFromClipboard(@NonNull final Context ctx) {
        final ClipData.Item clipItem = firstItemFromClipboard(ctx);
        return clipItem.coerceToText(ctx).toString();
    }

    public static void uriToClipboard(@NonNull final Context ctx, @NonNull final Uri uri,
                                      @NonNull final String title) {
        final ClipboardManager clipboard =
                (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null)
            throw new IllegalStateException("Can't get ClipboardManager");
        try {
            clipboard.setPrimaryClip(new ClipData(title,
                    new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN,
                            ClipDescription.MIMETYPE_TEXT_URILIST},
                    new ClipData.Item(uri.toString(), null, uri)));
        } catch (final Throwable e) {
            if (e instanceof AndroidException || e.getCause() instanceof AndroidException) {
                // TODO: workaround
                Toast.makeText(ctx, R.string.msg_too_large_to_copy_to_clipboard,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            throw e;
        }
    }

    public static void toClipboard(@NonNull final Context ctx, @NonNull final Uri uri,
                                   @NonNull final String title) {
        final ClipboardManager clipboard =
                (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null)
            throw new IllegalStateException("Can't get ClipboardManager");
        final String type = ctx.getContentResolver().getType(uri);
        clipboard.setPrimaryClip(new ClipData(title,
                new String[]{type != null ? type : "text/plain"},
                new ClipData.Item(uri)));
        Toast.makeText(ctx, R.string.msg_copied_to_clipboard,
                Toast.LENGTH_SHORT).show();
    }

    private static boolean canMarshall(@NonNull final Context ctx, @NonNull final String v) {
        final int max = ctx.getResources().getInteger(R.integer.scratchpad_use_threshold_max);
        final int limit = Misc.getApplication(ctx).settings.scratchpad_use_threshold;
        return limit >= max || v.length() / 512 < limit; // [KiB]
    }

    private static void toClipboardViaScratchpad(@NonNull final Context ctx,
                                                 @NonNull final String v,
                                                 @NonNull final String title) {
        try {
            toClipboard(ctx, _toScratchpad(ctx, v), title);
            Toast.makeText(ctx, R.string.msg_via_scratchpad,
                    Toast.LENGTH_SHORT).show();
        } catch (final IOException e) {
            Toast.makeText(ctx,
                    ctx.getString(R.string.msg_scratchpad_malfunction_s,
                            e.getLocalizedMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static void toClipboard(@NonNull final Context ctx, @Nullable final String v) {
        if (v == null) {
            Toast.makeText(ctx, R.string.msg_nothing_to_copy_to_clipboard,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final ClipboardManager clipboard =
                (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(ctx, R.string.msg_cannot_access_clipboard,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final String title = brief(ctx, v, 16);
        if (!canMarshall(ctx, v)) {
            toClipboardViaScratchpad(ctx, v, title);
            return;
        }
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(title, v));
        } catch (final Throwable e) {
            if (e instanceof AndroidException || e.getCause() instanceof AndroidException) {
                toClipboardViaScratchpad(ctx, v, title);
                return;
            }
            throw e;
        }
        Toast.makeText(ctx, R.string.msg_copied_to_clipboard,
                Toast.LENGTH_SHORT).show();
    }

    private static void sharePlainTextViaScratchpad(@NonNull final Activity ctx,
                                                    @NonNull final String v) {
        try {
            share(ctx, _toScratchpad(ctx, v));
            Toast.makeText(ctx, R.string.msg_via_scratchpad,
                    Toast.LENGTH_SHORT).show();
        } catch (final IOException e) {
            Toast.makeText(ctx,
                    ctx.getString(R.string.msg_scratchpad_malfunction_s,
                            e.getLocalizedMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static void sharePlainText(@NonNull final Activity ctx, @Nullable final String v) {
        if (v == null) {
            Toast.makeText(ctx, R.string.msg_nothing_to_share, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!canMarshall(ctx, v)) {
            sharePlainTextViaScratchpad(ctx, v);
            return;
        }
        try {
            ShareCompat.IntentBuilder.from(ctx)
                    .setType("text/plain").setText(v).startChooser();
        } catch (final Throwable e) {
            if (e instanceof AndroidException || e.getCause() instanceof AndroidException) {
                sharePlainTextViaScratchpad(ctx, v);
                return;
            }
            throw e;
        }
    }

    public static void tryWebSearch(@NonNull final Context ctx, @NonNull final String v) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(v.replaceAll("\\s+", ""))));
        } catch (final Throwable e) {
            if (!(e instanceof ActivityNotFoundException
                    || e instanceof AndroidException
                    || e.getCause() instanceof AndroidException))
                throw e;
            try {
                ctx.startActivity(new Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra(SearchManager.QUERY, v));
            } catch (final Throwable e2) {
                if (!(e2 instanceof ActivityNotFoundException
                        || e2 instanceof AndroidException
                        || e2.getCause() instanceof AndroidException))
                    throw e2;
                Toast.makeText(ctx, R.string.msg_too_large_to_web_search,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @NonNull
    public static Iterable<View> getIterable(@Nullable final View root) {
        return () -> new Iterator<View>() {
            private View v = root;
            private final List<Integer> ii = new ArrayList<>();

            @Override
            public boolean hasNext() {
                return v != null;
            }

            @Override
            public View next() {
                if (v == null)
                    throw new NoSuchElementException();
                final View r = v;
                if (v instanceof ViewGroup) {
                    v = ((ViewGroup) v).getChildAt(0);
                    ii.add(0);
                } else {
                    while (true) {
                        if (v == root) {
                            v = null;
                            break;
                        }
                        final ViewGroup p = (ViewGroup) v.getParent();
                        final int i = ii.remove(ii.size() - 1) + 1;
                        if (p.getChildCount() == i) {
                            v = p;
                            continue;
                        }
                        v = p.getChildAt(i);
                        ii.add(i);
                        break;
                    }
                }
                return r;
            }
        };
    }

    public static void enableAnimation(@NonNull final View root) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
            return;
        if (root instanceof ViewGroup) {
            final LayoutTransition lt = ((ViewGroup) root).getLayoutTransition();
            if (lt != null) {
                lt.enableTransitionType(LayoutTransition.CHANGING);
            }
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); ++i) {
                final View v = ((ViewGroup) root).getChildAt(i);
                enableAnimation(v);
            }
        }
    }

    /**
     * One can't simply get horizontal scrollability when `scrollHorizontally' is set...
     */
    public static boolean canScrollHorizontally(@NonNull final TextView w) {
        final Layout l = w.getLayout();
        float width = 0;
        for (int i = 0; i < l.getLineCount(); i++)
            width = Math.max(width, l.getLineRight(i));
        return width > (w.getWidth() - w.getTotalPaddingLeft() - w.getTotalPaddingRight());
    }

    @UiThread
    public static void confirm(@NonNull final Context ctx, @NonNull final CharSequence msg,
                               @NonNull final Runnable onConfirm) {
        DialogUtils.wrapLeakageSafe(new AlertDialog.Builder(ctx)
                .setMessage(msg)
                .setNegativeButton(android.R.string.no, (dialog, which) ->
                        dialog.cancel())
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    dialog.dismiss();
                    onConfirm.run();
                })
                .show(), null);
    }

    public static void wrapViewClickForConfirmation(@NonNull final View view,
                                                    @NonNull final ValueProvider<? extends CharSequence> msg,
                                                    @NonNull final Runnable onConfirm) {
        view.setOnClickListener(_view ->
                confirm(_view.getContext(), msg.get(), onConfirm));
        final Context ctx = view.getContext();
        final Resources rr = ctx.getResources();
        final int radius = rr.getDimensionPixelSize(R.dimen.uiConfirmationPopupRadius);
        final ConfirmationPopupViewWrapper wrapper = new ConfirmationPopupViewWrapper(view);
        wrapper.setPopupRadius(radius);
        wrapper.setPointerBackgroundDrawable(requireDrawable(ctx,
                R.drawable.bg_confirm_circle));
        wrapper.setPointerDrawable(requireDrawable(ctx,
                R.drawable.bg_confirm_ptr_circle));
        wrapper.setOnConfirmListener(onConfirm);
        final ConfirmationTooltip tooltip = new ConfirmationTooltip(view);
        tooltip.setOffset(radius);
        wrapper.setOnShow(() -> {
            tooltip.setText(msg.get());
            tooltip.show();
        });
        wrapper.setOnHide(tooltip::hide);
    }

    public static void showContextMenuOnBottom(@NonNull final View v) {
        if (Build.VERSION.SDK_INT < 24) {
            v.showContextMenu();
        } else {
            v.showContextMenu(0, v.getHeight());
        }
    }

    public static void setShowContextMenuOnClick(@NonNull final View v) {
        v.setOnLongClickListener(v1 -> {
            showContextMenuOnBottom(v1);
            return true;
        });
        v.setOnClickListener(UiUtils::showContextMenuOnBottom);
    }

    /**
     * @param out     [x, y] in the activity window
     * @param refView refers a window
     * @param x       local x
     * @param y       local y
     * @return a root view of the activity window
     */
    @NonNull
    public static View getWindowLocationInActivityWindow(@Size(2) @NonNull final int[] out,
                                                         @NonNull final View refView,
                                                         final int x, final int y) {
        final int[] pwc = new int[2];
        refView.getRootView().getLocationOnScreen(pwc);
        final View rv = requireActivity(refView.getContext())
                .getWindow().getDecorView().getRootView();
        final int[] rwc = new int[2];
        rv.getLocationOnScreen(rwc);
        out[0] = x + pwc[0] - rwc[0];
        out[1] = y + pwc[1] - rwc[1];
        return rv;
    }

    @NonNull
    public static Drawable requireDrawable(@NonNull final Context ctx,
                                           @DrawableRes final int res) {
        if (res == Resources.ID_NULL)
            throw new IllegalArgumentException("NULL resource ID passed");
        final Drawable r = AppCompatResources.getDrawable(ctx, res);
        if (r == null)
            throw new IllegalArgumentException("No drawable with ID " + res
                    + " in " + ctx.getPackageName());
        return r;
    }

    @Nullable
    public static Activity getActivity(@Nullable Context ctx) {
        while (!(ctx instanceof Activity)) {
            if (!(ctx instanceof ContextWrapper))
                return null;
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return (Activity) ctx;
    }

    @NonNull
    public static Activity requireActivity(@Nullable final Context ctx) {
        final Activity r = getActivity(ctx);
        if (r == null)
            throw new IllegalStateException("No underlying activity found");
        return r;
    }

    public static float getDimensionOrFraction(@NonNull final TypedValue v,
                                               @NonNull final DisplayMetrics metrics,
                                               final float base, final float pbase,
                                               final float def) {
        switch (v.type) {
            case TypedValue.TYPE_DIMENSION:
                return v.getDimension(metrics);
            case TypedValue.TYPE_FRACTION:
                return v.getFraction(base, pbase);
        }
        return def;
    }

    @NonNull
    public static MovementMethod getFixedLinkMovementMethod() {
        if (Build.VERSION.SDK_INT >= 16)
            // https://issuetracker.google.com/issues/37068143
            // https://stackoverflow.com/questions/22810147/error-when-selecting-text-from-textview-java-lang-indexoutofboundsexception-se
            // https://stackoverflow.com/questions/33821008/illegalargumentexception-while-selecting-text-in-android-textview/34072449
            // Spotted:
            // Android 8.1 (SDK 27)
            // java.lang.IndexOutOfBoundsException:
            //   at android.text.SpannableStringInternal.checkRange (SpannableStringInternal.java:442)
            // ...
            // Android 6.0 (SDK 23)
            // java.lang.IllegalArgumentException:
            //   at android.text.method.WordIterator.checkOffsetIsValid (WordIterator.java:380)
            // ...
            // Mitigated by 'me.saket:better-link-movement-method:2.2.0'.
            return BetterLinkMovementMethod.getInstance();
        else
            return LinkMovementMethod.getInstance();
    }

    public static void setBackgroundAlpha(@Nullable final Drawable drawable,
                                          @IntRange(from = 0, to = 255) final int alpha,
                                          @ColorInt final int defaultColor) {
        if (drawable instanceof GradientDrawable) {
            drawable.mutate();
            final GradientDrawable gd = (GradientDrawable) drawable;
            final int c;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                final ColorStateList cc = gd.getColor();
                c = cc != null ?
                        cc.getColorForState(gd.getState(), defaultColor)
                        : defaultColor;
            } else {
                c = defaultColor; // TODO: better tweak for old APIs
            }
            gd.setColor(ColorUtils.setAlphaComponent(c, alpha));
        } else if (drawable instanceof ShapeDrawable) {
            drawable.mutate();
            ((ShapeDrawable) drawable).getPaint().setAlpha(alpha);
        } else if (drawable != null) {
            drawable.mutate();
            drawable.setAlpha(alpha); // Giving up
        }
    }

    public static void replaceSelection(@NonNull final Editable editable,
                                        @NonNull final CharSequence replacement) {
        final int selStart = Selection.getSelectionStart(editable);
        final int selEnd = Selection.getSelectionEnd(editable);
        if (selStart < 0 || selEnd < 0)
            return;
        final int start = Math.min(selStart, selEnd);
        final int end = Math.max(selStart, selEnd);
        Selection.setSelection(editable, end);
        editable.replace(start, end, replacement);
    }
}
