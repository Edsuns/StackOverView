package com.s0n1.stackview.views;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.s0n1.stackview.misc.StackViewConfiguration;

/* A task view */
public class StackViewCard extends FrameLayout {

    StackViewConfiguration mConfig;

    float mTaskProgress;
    ObjectAnimator mTaskProgressAnimator;
    LinearLayout mContentContainer;
    View mContent;

    // Optimizations
    ValueAnimator.AnimatorUpdateListener mUpdateDimListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setTaskProgress((Float) animation.getAnimatedValue());
                }
            };


    public StackViewCard(Context context) {
        this(context, null);
    }

    public StackViewCard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StackViewCard(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public StackViewCard(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        setBackground(new FakeShadowDrawable(context.getResources()));
        mContentContainer = new LinearLayout(context);
        mContentContainer.setOrientation(LinearLayout.VERTICAL);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        mContentContainer.setLayoutParams(params);
        addView(mContentContainer);
    }

    //将子view的宽高设置入此父view
    @Override
    public void getHitRect(Rect outRect) {
        Rect contentRect = new Rect();
        mContent.getHitRect(contentRect);
        super.getHitRect(outRect);
        outRect.left += contentRect.left;
        outRect.top += contentRect.top;
        outRect.right = outRect.left + contentRect.width();
        outRect.bottom = outRect.top + contentRect.height();
    }

    public void setConfig(StackViewConfiguration config) {
        mConfig = config;
    }

    public void setContent(View content) {
        mContent = content;
        mContentContainer.removeAllViews();
        if (mContent != null) {
            mContentContainer.addView(mContent);
        }
        setTaskProgress(getTaskProgress());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        int widthWithoutPadding = width - getPaddingLeft() - getPaddingRight();
        int heightWithoutPadding = height - getPaddingTop() - getPaddingBottom();

        // Measure the content
        mContentContainer.measure(MeasureSpec.makeMeasureSpec(widthWithoutPadding, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightWithoutPadding, MeasureSpec.EXACTLY));

        setMeasuredDimension(width, height);
    }

    /**
     * Synchronizes this view's properties with the task's transform
     */
    void updateViewPropertiesToCardTransform(StackViewCardTransform toTransform) {
        updateViewPropertiesToCardTransform(toTransform, 0, null);
    }

    void updateViewPropertiesToCardTransform(StackViewCardTransform toTransform, int duration,
                                             ValueAnimator.AnimatorUpdateListener updateCallback) {
        // Apply the transform
        toTransform.applyToTaskView(this, duration, mConfig.fastOutSlowInInterpolator, false,
                true, updateCallback);

        // Update the task progress
        if (mTaskProgressAnimator != null) {
            mTaskProgressAnimator.removeAllListeners();
            mTaskProgressAnimator.cancel();
        }
        if (duration <= 0) {
            setTaskProgress(toTransform.p);
        } else {
            mTaskProgressAnimator = ObjectAnimator.ofFloat(this, "taskProgress", toTransform.p);
            mTaskProgressAnimator.setDuration(duration);
            mTaskProgressAnimator.addUpdateListener(mUpdateDimListener);
            mTaskProgressAnimator.start();
        }
    }

    /**
     * Resets this view's properties
     */
    void resetViewProperties() {
        StackViewCardTransform.reset(this);
    }

    /**
     * Prepares this task view for the enter-recents animations.  This is called earlier in the
     * first layout because the actual animation into recents may take a long time.
     */
    void prepareEnterRecentsAnimation() {
    }

    /**
     * Sets the current task progress.
     */
    public void setTaskProgress(float p) {
        mTaskProgress = p;
    }

    /**
     * Returns the current task progress.
     */
    public float getTaskProgress() {
        return mTaskProgress;
    }

    /**
     * Enables/disables handling touch on this task view.
     */
    void setTouchEnabled(boolean enabled) {
        setOnClickListener(!enabled ? null : new OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }
}
