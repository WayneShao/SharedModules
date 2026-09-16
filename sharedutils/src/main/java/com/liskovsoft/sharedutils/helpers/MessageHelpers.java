package com.liskovsoft.sharedutils.helpers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.R;

public class MessageHelpers {
    private static final int MAX_LEN = 300;
    private static final int CLEANUP_TIMEOUT_MS = 5_000;
    private static @Nullable Toast sLastToast;
    private static final Runnable sCleanupContext = MessageHelpers::cancelSystemToast;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static float sTextSize;

    /** Optional process-wide presentation. Callbacks run on the main thread.
     * Implementations must not retain an Activity or re-enter MessageHelpers.
     */
    public interface MessagePresenter {
        /** Return true if displayed; false keeps the normal Toast fallback. */
        boolean show(Context context, String message, boolean isLong);
        void cancel();
    }

    private static @Nullable MessagePresenter sMessagePresenter;

    /** Register once at application startup; null restores the default presentation. */
    public static void setMessagePresenter(@Nullable MessagePresenter presenter) {
        runOnMain(() -> {
            if (sMessagePresenter == presenter) return;
            cancelSystemToast();
            cancelPresentation();
            sMessagePresenter = presenter;
        });
    }

    public static void showMessage(final Context ctx, final String TAG, final Throwable ex) {
        showMessage(ctx, TAG + ": %s", Helpers.toString(ex));
    }

    public static void showMessage(final Context ctx, final String template, Object... params) {
        showMessage(ctx, String.format(template, params));
    }

    public static void showMessage(final Context ctx, final String msg) {
        showMessage(ctx, msg, false);
    }

    public static void showMessage(final Context ctx, final String msg, final boolean isLong) {
        if (ctx == null || msg == null || msg.isEmpty()) {
            return;
        }

        final Context context = ctx.getApplicationContext(); // memory leak fix

        Runnable toast = () -> {
            try {
                String finalMsg = Helpers.ellipsize(msg, MAX_LEN);
                cancelSystemToast();
                cancelPresentation();
                if (presentMessage(context, finalMsg, isLong)) return;
                Toast currentToast = Toast.makeText(context, finalMsg, isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
                fixTextSize(currentToast, context);
                addAndCancelPrev(currentToast);
                currentToast.show();

                setupCleanup();
            } catch (Exception ex) { // NPE fix
                ex.printStackTrace();
            }
        };

        runOnMain(toast);
    }

    /**
     * Shows toast message.<br/>
     * Uses resource id as message.
     * @param ctx context
     * @param resId resource id
     */
    public static void showMessage(Context ctx, int resId) {
        if (ctx != null) {
            showMessage(ctx, ctx.getResources().getString(resId));
        }
    }

    /**
     * Shows formatted toast message.<br/>
     * Uses resource id as message.
     * @param ctx context
     * @param resId resource id
     * @param formatArgs format arguments
     */
    public static void showMessage(Context ctx, int resId, Object... formatArgs) {
        if (ctx != null) {
            showMessage(ctx, ctx.getResources().getString(resId, formatArgs));
        }
    }

    /**
     * Shows long toast message.<br/>
     * Uses resource id as message.
     * @param ctx context
     * @param resId resource id
     */
    public static void showLongMessage(Context ctx, int resId) {
        if (ctx != null) {
            showLongMessage(ctx, ctx.getResources().getString(resId));
        }
    }

    public static void showLongMessage(Context ctx, String msg) {
        if (ctx != null) {
            showMessage(ctx, msg, true);
        }
    }

    public static void showLongMessage(Context ctx, String template, Object... params) {
        if (ctx != null) {
            showMessage(ctx, String.format(template, params), true);
        }
    }

    public static void showLongMessageEndPause(Context context, int resId) {
        showLongMessage(context, resId);

        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void cancelToasts() {
        runOnMain(() -> {
            cancelSystemToast();
            cancelPresentation();
        });
    }

    private static boolean presentMessage(Context context, String message, boolean isLong) {
        if (sMessagePresenter == null) return false;
        try {
            return sMessagePresenter.show(context, message, isLong);
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            cancelPresentation();
            return false;
        }
    }

    private static void cancelPresentation() {
        if (sMessagePresenter == null) return;
        try {
            sMessagePresenter.cancel();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        }
    }

    private static void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else sHandler.post(action);
    }

    // Native Toast timers must never cancel a newer custom presentation.
    private static void cancelSystemToast() {
        sHandler.removeCallbacks(sCleanupContext);
        if (sLastToast != null) {
            sLastToast.cancel();
            sLastToast = null;
        }
    }

    private static void fixTextSize(Toast toast, Context context) {
        if (sTextSize == 0) {
            // Maintain text size between app rebooting
            sTextSize = context.getResources().getDimension(R.dimen.dialog_text_size);
        }

        TextView messageTextView = extractMessageView(toast);

        if (messageTextView != null) {
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sTextSize);
        }
    }

    private static void addAndCancelPrev(Toast newToast) {
        cancelSystemToast();

        sLastToast = newToast;
    }

    private static @Nullable TextView extractMessageView(Toast toast) {
        ViewGroup group = (ViewGroup) toast.getView();

        if (group == null) {
            return null;
        }

        return (TextView) group.getChildAt(0);
    }

    private static void setupCleanup() {
        sHandler.removeCallbacks(sCleanupContext);
        sHandler.postDelayed(sCleanupContext, CLEANUP_TIMEOUT_MS);
    }
}
