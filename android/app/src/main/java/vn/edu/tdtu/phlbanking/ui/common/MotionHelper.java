package vn.edu.tdtu.phlbanking.ui.common;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import vn.edu.tdtu.phlbanking.R;
import vn.edu.tdtu.phlbanking.utils.CurrencyUtils;

public final class MotionHelper {

    private static final TimeInterpolator FAST_OUT_SLOW_IN = new FastOutSlowInInterpolator();
    private static final TimeInterpolator LINEAR_OUT_SLOW_IN = new LinearOutSlowInInterpolator();
    private static final TimeInterpolator FAST_OUT_LINEAR_IN = new FastOutLinearInInterpolator();
    private static final TimeInterpolator ACCELERATE_DECELERATE =
        new AccelerateDecelerateInterpolator();
    private static final TimeInterpolator DECELERATE = new DecelerateInterpolator(1.5f);
    private static final TimeInterpolator ACCELERATE_FLIP = new AccelerateInterpolator(1.2f);
    private static final TimeInterpolator DECELERATE_FLIP = new DecelerateInterpolator(1.2f);
    private static final TimeInterpolator OVERSHOOT_SOFT = new OvershootInterpolator(1.4f);

    // Intentionally returns false to preserve View.OnClickListener, ripples, and scroll gestures.
    @SuppressLint("ClickableViewAccessibility")
    private static final View.OnTouchListener TACTILE_TOUCH_LISTENER = (v, event) -> {
        if (!v.isEnabled() || !areAnimationsEnabled(v.getContext())) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            v.animate().cancel();
            v.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(90L)
                .setInterpolator(DECELERATE)
                .start();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            v.animate().cancel();
            v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140L)
                .setInterpolator(OVERSHOOT_SOFT)
                .start();
        }
        return false;
    };

    private MotionHelper() {}

    public static void staggerEntrance(@Nullable View... views) {
        if (views == null || views.length == 0) {
            return;
        }
        int visibleIndex = 0;
        for (View view : views) {
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }
            view.animate().cancel();
            if (!areAnimationsEnabled(view.getContext())) {
                view.setAlpha(1f);
                view.setTranslationY(0f);
                continue;
            }
            ensureDetachCleanup(view);
            float offsetPx = dpToPx(view.getContext(), 18f);
            view.setAlpha(0f);
            view.setTranslationY(offsetPx);
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320L)
                .setStartDelay(visibleIndex * 55L)
                .setInterpolator(FAST_OUT_SLOW_IN)
                .start();
            visibleIndex++;
        }
    }

    public static void staggerChildren(@Nullable ViewGroup container, long baseDelayMs) {
        if (container == null) {
            return;
        }
        long safeDelay = Math.max(0L, baseDelayMs);
        boolean enabled = areAnimationsEnabled(container.getContext());
        float offsetPx = dpToPx(container.getContext(), 16f);
        int visibleIndex = 0;
        int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = container.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.animate().cancel();
            if (!enabled) {
                child.setAlpha(1f);
                child.setTranslationY(0f);
                continue;
            }
            ensureDetachCleanup(child);
            child.setAlpha(0f);
            child.setTranslationY(offsetPx);
            child
                .animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300L)
                .setStartDelay(safeDelay + visibleIndex * 50L)
                .setInterpolator(FAST_OUT_SLOW_IN)
                .start();
            visibleIndex++;
        }
    }

    public static void animateVndCounter(
        @Nullable TextView target,
        long fromValue,
        long toValue,
        long durationMs
    ) {
        if (target == null) {
            return;
        }
        cancelTagAnimator(target, R.id.phl_tag_counter_anim);
        if (
            fromValue == toValue || durationMs <= 0L || !areAnimationsEnabled(target.getContext())
        ) {
            target.setText(CurrencyUtils.formatVnd(toValue));
            return;
        }
        ensureDetachCleanup(target);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(Math.max(120L, durationMs));
        animator.setInterpolator(DECELERATE);
        animator.addUpdateListener(anim -> {
            float fraction = (float) anim.getAnimatedValue();
            long current = fromValue + Math.round((toValue - fromValue) * (double) fraction);
            target.setText(CurrencyUtils.formatVnd(current));
        });
        animator.addListener(
            new AnimatorListenerAdapter() {
                private boolean canceled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!canceled) {
                        target.setText(CurrencyUtils.formatVnd(toValue));
                    }
                    target.setTag(R.id.phl_tag_counter_anim, null);
                }
            }
        );
        target.setTag(R.id.phl_tag_counter_anim, animator);
        animator.start();
    }

    public static void crossfadeText(@Nullable TextView target, @Nullable CharSequence newText) {
        if (target == null) {
            return;
        }
        CharSequence safeText = newText == null ? "" : newText;
        if (TextUtils.equals(target.getText(), safeText)) {
            return;
        }
        target.animate().cancel();
        if (!areAnimationsEnabled(target.getContext())) {
            target.setText(safeText);
            target.setAlpha(1f);
            return;
        }
        ensureDetachCleanup(target);
        target
            .animate()
            .alpha(0f)
            .setDuration(110L)
            .setInterpolator(FAST_OUT_LINEAR_IN)
            .withEndAction(() -> {
                target.setText(safeText);
                target
                    .animate()
                    .alpha(1f)
                    .setDuration(160L)
                    .setInterpolator(LINEAR_OUT_SLOW_IN)
                    .start();
            })
            .start();
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void applyTactilePress(@Nullable View... views) {
        if (views == null || views.length == 0) {
            return;
        }
        for (View view : views) {
            if (view == null) {
                continue;
            }
            if (view.getTag(R.id.phl_tag_tactile_bound) == null) {
                view.setOnTouchListener(TACTILE_TOUCH_LISTENER);
                view.setTag(R.id.phl_tag_tactile_bound, Boolean.TRUE);
            }
        }
    }

    public static void startShimmerSweep(@Nullable View shimmerOverlay) {
        if (shimmerOverlay == null) {
            return;
        }
        stopShimmer(shimmerOverlay);
        if (!areAnimationsEnabled(shimmerOverlay.getContext())) {
            shimmerOverlay.setVisibility(View.GONE);
            return;
        }
        shimmerOverlay.setTag(R.id.phl_tag_shimmer_active, Boolean.TRUE);
        ensureDetachCleanup(shimmerOverlay);
        shimmerOverlay.post(() -> {
            if (
                !Boolean.TRUE.equals(shimmerOverlay.getTag(R.id.phl_tag_shimmer_active)) ||
                !shimmerOverlay.isAttachedToWindow()
            ) {
                return;
            }
            int parentWidth =
                shimmerOverlay.getParent() instanceof View
                    ? ((View) shimmerOverlay.getParent()).getWidth()
                    : 0;
            if (parentWidth <= 0) {
                parentWidth = shimmerOverlay.getResources().getDisplayMetrics().widthPixels;
            }
            float overlayW =
                shimmerOverlay.getWidth() > 0
                    ? shimmerOverlay.getWidth()
                    : dpToPx(shimmerOverlay.getContext(), 96f);
            float startX = -overlayW;
            float endX = parentWidth + overlayW;

            shimmerOverlay.setAlpha(0f);
            shimmerOverlay.setTranslationX(startX);
            shimmerOverlay.setVisibility(View.VISIBLE);

            ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(2600L);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(va -> {
                float p = (float) va.getAnimatedValue();
                if (p <= 0.55f) {
                    float local = p / 0.55f;
                    float eased = FAST_OUT_SLOW_IN.getInterpolation(local);
                    shimmerOverlay.setTranslationX(startX + (endX - startX) * eased);
                    shimmerOverlay.setAlpha(1f);
                } else {
                    shimmerOverlay.setAlpha(0f);
                }
            });
            shimmerOverlay.setTag(R.id.phl_tag_shimmer_anim, anim);
            anim.start();
        });
    }

    public static void stopShimmer(@Nullable View shimmerOverlay) {
        if (shimmerOverlay == null) {
            return;
        }
        shimmerOverlay.setTag(R.id.phl_tag_shimmer_active, Boolean.FALSE);
        cancelTagAnimator(shimmerOverlay, R.id.phl_tag_shimmer_anim);
        shimmerOverlay.animate().cancel();
        shimmerOverlay.setTranslationX(0f);
        shimmerOverlay.setAlpha(1f);
        shimmerOverlay.setVisibility(View.GONE);
    }

    public static void flipCard3D(
        @Nullable View frontView,
        @Nullable View backView,
        boolean showBack
    ) {
        if (frontView == null || backView == null) {
            return;
        }
        cancelTagAnimator(frontView, R.id.phl_tag_flip_anim);
        cancelTagAnimator(backView, R.id.phl_tag_flip_anim);

        float density = frontView.getResources().getDisplayMetrics().density;
        float cameraDistance = 8000f * density;
        frontView.setCameraDistance(cameraDistance);
        backView.setCameraDistance(cameraDistance);

        if (!areAnimationsEnabled(frontView.getContext())) {
            frontView.setRotationY(0f);
            backView.setRotationY(0f);
            frontView.setVisibility(showBack ? View.GONE : View.VISIBLE);
            backView.setVisibility(showBack ? View.VISIBLE : View.GONE);
            return;
        }

        View outgoing = showBack ? frontView : backView;
        View incoming = showBack ? backView : frontView;

        if (incoming.getVisibility() == View.VISIBLE && outgoing.getVisibility() != View.VISIBLE) {
            incoming.setRotationY(0f);
            return;
        }

        ensureDetachCleanup(frontView);
        ensureDetachCleanup(backView);

        ObjectAnimator outAnim = ObjectAnimator.ofFloat(
            outgoing,
            View.ROTATION_Y,
            0f,
            showBack ? 90f : -90f
        );
        outAnim.setDuration(180L);
        outAnim.setInterpolator(ACCELERATE_FLIP);

        ObjectAnimator inAnim = ObjectAnimator.ofFloat(
            incoming,
            View.ROTATION_Y,
            showBack ? -90f : 90f,
            0f
        );
        inAnim.setDuration(200L);
        inAnim.setInterpolator(DECELERATE_FLIP);

        outAnim.addListener(
            new AnimatorListenerAdapter() {
                private boolean canceled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                    outgoing.setRotationY(0f);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    outgoing.setTag(R.id.phl_tag_flip_anim, null);
                    if (canceled) {
                        return;
                    }
                    outgoing.setVisibility(View.GONE);
                    outgoing.setRotationY(0f);
                    incoming.setRotationY(showBack ? -90f : 90f);
                    incoming.setVisibility(View.VISIBLE);
                    incoming.setTag(R.id.phl_tag_flip_anim, inAnim);
                    inAnim.start();
                }
            }
        );

        inAnim.addListener(
            new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    incoming.setRotationY(0f);
                    incoming.setTag(R.id.phl_tag_flip_anim, null);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    incoming.setRotationY(0f);
                    incoming.setTag(R.id.phl_tag_flip_anim, null);
                }
            }
        );

        outgoing.setTag(R.id.phl_tag_flip_anim, outAnim);
        outAnim.start();
    }

    public static void shakeError(@Nullable View target) {
        if (target == null) {
            return;
        }
        cancelTagAnimator(target, R.id.phl_tag_shake_anim);
        target.setTranslationX(0f);
        if (!areAnimationsEnabled(target.getContext())) {
            return;
        }
        ensureDetachCleanup(target);
        float dx = dpToPx(target.getContext(), 7f);
        ObjectAnimator shake = ObjectAnimator.ofFloat(
            target,
            View.TRANSLATION_X,
            0f,
            -dx,
            dx,
            -dx * 0.7f,
            dx * 0.7f,
            -dx * 0.35f,
            0f
        );
        shake.setDuration(320L);
        shake.setInterpolator(ACCELERATE_DECELERATE);
        shake.addListener(
            new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    target.setTranslationX(0f);
                    target.setTag(R.id.phl_tag_shake_anim, null);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    target.setTranslationX(0f);
                    target.setTag(R.id.phl_tag_shake_anim, null);
                }
            }
        );
        target.setTag(R.id.phl_tag_shake_anim, shake);
        shake.start();
    }

    public static void pulseView(@Nullable View target) {
        if (target == null) {
            return;
        }
        cancelTagAnimator(target, R.id.phl_tag_pulse_anim);
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setAlpha(1f);
        if (!areAnimationsEnabled(target.getContext())) {
            return;
        }
        ensureDetachCleanup(target);
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.72f)
        );
        pulse.setDuration(950L);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setInterpolator(ACCELERATE_DECELERATE);
        target.setTag(R.id.phl_tag_pulse_anim, pulse);
        pulse.start();
    }

    public static void startScanlineLoop(@Nullable View scanline, @Nullable View container) {
        if (scanline == null || container == null) {
            return;
        }
        cancelTagAnimator(scanline, R.id.phl_tag_scanline_anim);
        if (!areAnimationsEnabled(scanline.getContext())) {
            scanline.setTranslationY(0f);
            scanline.setVisibility(View.VISIBLE);
            return;
        }
        scanline.setTag(R.id.phl_tag_scanline_active, Boolean.TRUE);
        ensureDetachCleanup(scanline);
        container.post(() -> {
            if (
                !Boolean.TRUE.equals(scanline.getTag(R.id.phl_tag_scanline_active)) ||
                !scanline.isAttachedToWindow() ||
                container.getHeight() <= 0
            ) {
                return;
            }
            float lineHeight =
                scanline.getHeight() > 0 ? scanline.getHeight() : dpToPx(scanline.getContext(), 3f);
            float travel = Math.max(0f, container.getHeight() - lineHeight);
            scanline.setVisibility(View.VISIBLE);
            scanline.setTranslationY(0f);

            ObjectAnimator anim = ObjectAnimator.ofFloat(scanline, View.TRANSLATION_Y, 0f, travel);
            anim.setDuration(1700L);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.setInterpolator(ACCELERATE_DECELERATE);
            scanline.setTag(R.id.phl_tag_scanline_anim, anim);
            anim.start();
        });
    }

    public static void animateTabSelection(@Nullable BottomNavigationView nav) {
        if (nav == null || !areAnimationsEnabled(nav.getContext())) {
            return;
        }
        nav.post(() -> {
            if (!nav.isAttachedToWindow()) {
                return;
            }
            int selectedId = nav.getSelectedItemId();
            View itemView = nav.findViewById(selectedId);
            if (itemView == null) {
                return;
            }
            View iconView = itemView.findViewById(
                com.google.android.material.R.id.navigation_bar_item_icon_view
            );
            View target = iconView != null ? iconView : itemView;
            target.animate().cancel();
            target.setScaleX(0.88f);
            target.setScaleY(0.88f);
            target
                .animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(OVERSHOOT_SOFT)
                .start();
        });
    }

    public static void applyFragmentTransition(@Nullable FragmentTransaction tx) {
        if (tx == null) {
            return;
        }
        tx.setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        );
    }

    public static void applyActivityTransition(@Nullable Activity activity) {
        if (activity == null) {
            return;
        }
        // overridePendingTransition remains the compatible post-startActivity/finish API across minSdk 24..34
        // (API 34's overrideActivityTransition requires pre-declaring open/close override behavior).
        if (!areAnimationsEnabled(activity)) {
            @SuppressWarnings("deprecation")
            Runnable noop = () -> activity.overridePendingTransition(0, 0);
            noop.run();
            return;
        }
        @SuppressWarnings("deprecation")
        Runnable transition = () ->
            activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        transition.run();
    }

    public static void stopViewAnimations(@Nullable View... views) {
        if (views == null || views.length == 0) {
            return;
        }
        for (View view : views) {
            if (view == null) {
                continue;
            }
            view.animate().cancel();
            view.setTag(R.id.phl_tag_shimmer_active, Boolean.FALSE);
            view.setTag(R.id.phl_tag_scanline_active, Boolean.FALSE);
            cancelTagAnimator(view, R.id.phl_tag_counter_anim);
            cancelTagAnimator(view, R.id.phl_tag_shimmer_anim);
            cancelTagAnimator(view, R.id.phl_tag_pulse_anim);
            cancelTagAnimator(view, R.id.phl_tag_scanline_anim);
            cancelTagAnimator(view, R.id.phl_tag_flip_anim);
            cancelTagAnimator(view, R.id.phl_tag_shake_anim);
        }
    }

    public static boolean areAnimationsEnabled(@Nullable Context context) {
        if (context == null) {
            return false;
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()
        ) {
            return false;
        }
        try {
            return (
                Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                ) > 0f
            );
        } catch (Exception ignored) {
            return true;
        }
    }

    private static void ensureDetachCleanup(@NonNull View view) {
        if (view.getTag(R.id.phl_tag_attach_listener) != null) {
            return;
        }
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                // No-op
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                v.animate().cancel();
                v.setTag(R.id.phl_tag_shimmer_active, Boolean.FALSE);
                v.setTag(R.id.phl_tag_scanline_active, Boolean.FALSE);
                cancelTagAnimator(v, R.id.phl_tag_counter_anim);
                cancelTagAnimator(v, R.id.phl_tag_shimmer_anim);
                cancelTagAnimator(v, R.id.phl_tag_pulse_anim);
                cancelTagAnimator(v, R.id.phl_tag_scanline_anim);
                cancelTagAnimator(v, R.id.phl_tag_flip_anim);
                cancelTagAnimator(v, R.id.phl_tag_shake_anim);
            }
        };
        view.addOnAttachStateChangeListener(listener);
        view.setTag(R.id.phl_tag_attach_listener, listener);
    }

    private static void cancelTagAnimator(@NonNull View view, int tagId) {
        Object tag = view.getTag(tagId);
        if (tag instanceof Animator) {
            ((Animator) tag).cancel();
        }
        view.setTag(tagId, null);
    }

    private static float dpToPx(@NonNull Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
