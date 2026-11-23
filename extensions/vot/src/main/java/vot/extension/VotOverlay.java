package vot.extension;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Кнопка-иконка перевода в правой части плеера.
 * Пока идёт подготовка перевода — внутри кнопки крутится маленький индикатор.
 */
public final class VotOverlay {
    private static final String BUTTON_TAG = "vot-button";

    private static WeakReference<Activity> current;
    private static WeakReference<Activity> host;
    private static FrameLayout button;
    private static TextView icon;
    private static ProgressBar ring;

    private static WeakReference<View> playerRef;
    private static WeakReference<View> controlsRef;

    private static boolean lastRunning;
    private static boolean lastLoading;

    private static final Choreographer.FrameCallback FRAME = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            Activity activity = current == null ? null : current.get();
            if (activity != null) {
                try {
                    refresh(activity);
                } catch (Throwable error) {
                    android.util.Log.e("VOT", "refresh failed", error);
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private VotOverlay() {
    }

    public static void install(Context context) {
        if (!(context instanceof Application)) {
            return;
        }
        try {
            ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityResumed(Activity activity) {
                    current = new WeakReference<>(activity);
                    playerRef = null;
                    controlsRef = null;
                    Choreographer.getInstance().removeFrameCallback(FRAME);
                    Choreographer.getInstance().postFrameCallback(FRAME);
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    if (current != null && current.get() == activity) {
                        current = null;
                        destroy();
                        Choreographer.getInstance().removeFrameCallback(FRAME);
                    }
                }

                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                }
            });

            Choreographer.getInstance().removeFrameCallback(FRAME);
            Choreographer.getInstance().postFrameCallback(FRAME);
        } catch (Throwable ignored) {
            // игнорируем
        }
    }

    private static void refresh(Activity activity) {
        View player = getPlayer(activity);
        if (player == null || player.getWidth() == 0 || player.getVisibility() != View.VISIBLE) {
            destroy();
            return;
        }

        // Только полноценный плеер: мини-плеер не подходит.
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int[] playerPos = new int[2];
        player.getLocationOnScreen(playerPos);
        boolean bigEnough = player.getWidth() >= screenWidth * 0.6f
                && player.getHeight() >= screenHeight * 0.2f;
        boolean atTop = playerPos[1] < screenHeight * 0.35f;
        if (!bigEnough || !atTop) {
            destroy();
            return;
        }

        View controls = getControls(activity);
        if (controls != null) {
            boolean visible = controls.getVisibility() == View.VISIBLE
                    && controls.getWidth() > 0
                    && controlsAlpha(controls) > 0.05f;
            if (!visible) {
                hide();
                return;
            }
        }

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (decor == null) {
            destroy();
            return;
        }

        ensureButton(activity, decor);

        boolean running = VotController.isRunning();
        boolean loading = VotController.isLoading();
        if (running != lastRunning) {
            lastRunning = running;
            button.setBackground(running ? activeBackground(activity) : idleBackground(activity));
        }
        if (loading != lastLoading) {
            lastLoading = loading;
            ring.setVisibility(loading ? View.VISIBLE : View.GONE);
            icon.setVisibility(loading ? View.GONE : View.VISIBLE);
        }

        int[] playerLocation = new int[2];
        player.getLocationOnScreen(playerLocation);
        int[] rootLocation = new int[2];
        decor.getLocationOnScreen(rootLocation);

        int size = dp(activity, 36);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = Math.max(0,
                playerLocation[0] - rootLocation[0] + player.getWidth() - size - dp(activity, 8));
        params.topMargin = Math.max(0,
                playerLocation[1] - rootLocation[1] + (player.getHeight() - size) / 2);

        button.setLayoutParams(params);
        button.setAlpha(controls == null ? 1f : Math.min(1f, controlsAlpha(controls)));
        button.setVisibility(View.VISIBLE);
    }

    private static View getPlayer(Activity activity) {
        View cached = playerRef == null ? null : playerRef.get();
        if (cached != null && cached.isAttachedToWindow()) {
            return cached;
        }
        View player = findByIdName(activity, "player_view_container", "player_view");
        playerRef = new WeakReference<>(player);
        return player;
    }

    private static View getControls(Activity activity) {
        View cached = controlsRef == null ? null : controlsRef.get();
        if (cached == null || !cached.isAttachedToWindow()) {
            cached = findByIdName(activity, "player_collapse_button");
            controlsRef = new WeakReference<>(cached);
        }
        return cached;
    }

    private static float controlsAlpha(View view) {
        float alpha = 1f;
        View current = view;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            alpha *= current.getAlpha();
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return alpha;
    }

    private static void ensureButton(Activity activity, ViewGroup decor) {
        if (button != null && host != null && host.get() == activity
                && button.getParent() == decor) {
            return;
        }
        if (button != null && button.getParent() instanceof ViewGroup) {
            ((ViewGroup) button.getParent()).removeView(button);
        }

        int size = dp(activity, 36);

        button = new FrameLayout(activity);
        button.setTag(BUTTON_TAG);
        lastRunning = VotController.isRunning();
        lastLoading = VotController.isLoading();
        button.setBackground(lastRunning ? activeBackground(activity) : idleBackground(activity));
        button.setElevation(dp(activity, 2));
        button.setClickable(true);
        button.setLongClickable(true);

        icon = new TextView(activity);
        icon.setText("文A");
        icon.setTextColor(0xFFFFFFFF);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        icon.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        icon.setGravity(Gravity.CENTER);
        icon.setIncludeFontPadding(false);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconParams.gravity = Gravity.CENTER;
        button.addView(icon, iconParams);

        ring = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        int ringSize = dp(activity, 16);
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(ringSize, ringSize);
        ringParams.gravity = Gravity.CENTER;
        try {
            ring.getIndeterminateDrawable().setTint(0xFFFFFFFF);
        } catch (Throwable ignored) {
            // старая версия
        }
        ring.setVisibility(View.GONE);
        button.addView(ring, ringParams);

        icon.setVisibility(lastLoading ? View.GONE : View.VISIBLE);
        ring.setVisibility(lastLoading ? View.VISIBLE : View.GONE);

        button.setOnClickListener(view -> {
            int result = VotController.translateCurrent();
            if (result == 0) {
                VotController.toast("VOT: открой видео");
            }
        });
        button.setOnLongClickListener(view -> {
            try {
                VotSettingsDialog.show(activity);
            } catch (Throwable ignored) {
                // игнорируем
            }
            return true;
        });

        decor.addView(button, new FrameLayout.LayoutParams(size, size));
        host = new WeakReference<>(activity);
    }

    private static GradientDrawable idleBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 10));
        background.setColor(0xCC2A2A2A);
        return background;
    }

    private static GradientDrawable activeBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(context, 10));
        background.setColor(0xE63D6DFF);
        return background;
    }

    /** Скрыть кнопку, но оставить её (например, контролы плеера спрятались). */
    private static void hide() {
        if (button != null) {
            button.setVisibility(View.GONE);
        }
    }

    /** Полностью удалить кнопку и освободить ссылки (плеер закрыт/мини-плеер). */
    private static void destroy() {
        if (button != null) {
            button.setOnClickListener(null);
            button.setOnLongClickListener(null);
            if (button.getParent() instanceof ViewGroup) {
                ((ViewGroup) button.getParent()).removeView(button);
            }
            button.removeAllViews();
        }
        button = null;
        icon = null;
        ring = null;
        host = null;
        lastRunning = false;
        lastLoading = false;
        playerRef = null;
        controlsRef = null;
    }

    private static View findByIdName(Activity activity, String... names) {
        return findByResourceName(activity.getWindow().getDecorView(),
                activity.getResources(), names);
    }

    private static View findByResourceName(View root, Resources resources, String... names) {
        if (root == null) {
            return null;
        }
        int id = root.getId();
        if (id != View.NO_ID) {
            try {
                String entry = resources.getResourceEntryName(id);
                for (String name : names) {
                    if (name.equals(entry)) {
                        return root;
                    }
                }
            } catch (Throwable ignored) {
                // ресурс из другого пакета
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findByResourceName(group.getChildAt(i), resources, names);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
