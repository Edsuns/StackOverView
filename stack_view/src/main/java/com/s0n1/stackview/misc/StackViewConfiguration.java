package com.s0n1.stackview.misc;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import com.s0n1.stackview.R;

public class StackViewConfiguration {

    /**
     * Interpolators
     */
    public Interpolator fastOutSlowInInterpolator;
    public Interpolator linearOutSlowInInterpolator;

    /**
     * Insets
     */
    private Rect displayRect = new Rect();

    /**
     * Task stack
     */
    public int taskStackScrollDuration;
    public int taskStackTopPaddingPx;
    public float taskStackWidthPaddingPct;
    public float taskStackOverscrollPct;

    public int taskViewTranslationZMinPx;
    public int taskViewTranslationZMaxPx;

    /**
     * Private constructor
     */
    public StackViewConfiguration(Context context) {
        fastOutSlowInInterpolator = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.accelerate_decelerate);
        linearOutSlowInInterpolator = AnimationUtils.loadInterpolator(context,
                android.R.interpolator.accelerate_decelerate);
        update(context);
    }

    /**
     * Updates the state, given the specified context
     */
    private void update(Context context) {
        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();

        // Insets
        displayRect.set(0, 0, dm.widthPixels, dm.heightPixels);

        // Task stack
        taskStackScrollDuration =
                res.getInteger(R.integer.recents_animate_task_stack_scroll_duration);

        //获取dimen资源值
        TypedValue widthPaddingPctValue = new TypedValue();
        res.getValue(R.dimen.recents_stack_width_padding_percentage, widthPaddingPctValue, true);
        taskStackWidthPaddingPct = widthPaddingPctValue.getFloat();

        //获取dimen资源值
        TypedValue stackOverscrollPctValue = new TypedValue();
        res.getValue(R.dimen.recents_stack_overscroll_percentage, stackOverscrollPctValue, true);
        taskStackOverscrollPct = stackOverscrollPctValue.getFloat();

        taskStackTopPaddingPx = res.getDimensionPixelSize(R.dimen.recents_stack_top_padding);

        // Task view animation and styles
        taskViewTranslationZMinPx = res.getDimensionPixelSize(R.dimen.recents_task_view_z_min);
        taskViewTranslationZMaxPx = res.getDimensionPixelSize(R.dimen.recents_task_view_z_max);
    }

    /**
     * Returns the task stack bounds in the current orientation. These bounds do not account for
     * the system insets.
     */
    public void getOverviewStackBounds(int windowWidth, int windowHeight,
                                       Rect taskStackBounds) {
        taskStackBounds.set(0, 64, windowWidth, windowHeight);
    }
}
