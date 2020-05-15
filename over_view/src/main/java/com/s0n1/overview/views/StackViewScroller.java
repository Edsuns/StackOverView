package com.s0n1.overview.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.widget.OverScroller;

import com.s0n1.overview.misc.OverViewConfiguration;

/* The scrolling logic for a TaskStackView */
class StackViewScroller {
    public interface Callbacks {
        void onScrollChanged(float p);
    }

    private OverViewConfiguration mConfig;
    private StackViewLayoutAlgorithm mLayoutAlgorithm;
    private Callbacks mCb;

    private float mStackScrollP;

    OverScroller mScroller;
    ObjectAnimator mScrollAnimator;

    StackViewScroller(Context context, OverViewConfiguration config, StackViewLayoutAlgorithm layoutAlgorithm) {
        mConfig = config;
        mScroller = new OverScroller(context);
        mLayoutAlgorithm = layoutAlgorithm;
        setStackScroll(getStackScroll());
    }

    /** Sets the callbacks */
    void setCallbacks(Callbacks cb) {
        mCb = cb;
    }

    /** Gets the current stack scroll */
    float getStackScroll() {
        return mStackScrollP;
    }

    /** Sets the current stack scroll */
    void setStackScroll(float s) {
        mStackScrollP = s;
        if (mCb != null) {
            mCb.onScrollChanged(mStackScrollP);
        }
    }

    /** Sets the current stack scroll without calling the callback. */
    private void setStackScrollRaw(float s) {
        mStackScrollP = s;
    }

    /** Sets the current stack scroll to the initial state when you first enter recents */
    void setStackScrollToInitialState() {
        setStackScroll(getBoundedStackScroll(mLayoutAlgorithm.mInitialScrollP));
    }

    /** Bounds the current scroll if necessary */
    void boundScroll() {
        float curScroll = getStackScroll();
        float newScroll = getBoundedStackScroll(curScroll);
        if (Float.compare(newScroll, curScroll) != 0) {
            setStackScroll(newScroll);
        }
    }

    /** Returns the bounded stack scroll */
    private float getBoundedStackScroll(float scroll) {
        return Math.max(mLayoutAlgorithm.mMinScrollP, Math.min(mLayoutAlgorithm.mMaxScrollP, scroll));
    }

    /** Returns the amount that the aboslute value of how much the scroll is out of bounds. */
    float getScrollAmountOutOfBounds(float scroll) {
        if (scroll < mLayoutAlgorithm.mMinScrollP) {
            return Math.abs(scroll - mLayoutAlgorithm.mMinScrollP);
        } else if (scroll > mLayoutAlgorithm.mMaxScrollP) {
            return Math.abs(scroll - mLayoutAlgorithm.mMaxScrollP);
        }
        return 0f;
    }

    /** Returns whether the specified scroll is out of bounds */
    boolean isScrollOutOfBounds() {
        return Float.compare(getScrollAmountOutOfBounds(mStackScrollP), 0f) != 0;
    }

    /** Animates the stack scroll into bounds */
    void animateBoundScroll() {
        float curScroll = getStackScroll();
        float newScroll = getBoundedStackScroll(curScroll);
        if (Float.compare(newScroll, curScroll) != 0) {
            // Start a new scroll animation
            animateScroll(curScroll, newScroll);
        }
    }

    /** Animates the stack scroll */
    private void animateScroll(float curScroll, float newScroll) {
        // Abort any current animations
        stopScroller();
        stopBoundScrollAnimation();

        mScrollAnimator = ObjectAnimator.ofFloat(this, "stackScroll", curScroll, newScroll);
        mScrollAnimator.setDuration(mConfig.taskStackScrollDuration);
        mScrollAnimator.setInterpolator(mConfig.linearOutSlowInInterpolator);
        mScrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setStackScroll((Float) animation.getAnimatedValue());
            }
        });
        mScrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mScrollAnimator.removeAllListeners();
            }
        });
        mScrollAnimator.start();
    }

    /** Aborts any current stack scrolls */
    void stopBoundScrollAnimation() {
        if (mScrollAnimator != null) {
            mScrollAnimator.removeAllListeners();
            mScrollAnimator.cancel();
        }
    }

    /**** OverScroller ****/

    int progressToScrollRange(float p) {
        return (int) (p * mLayoutAlgorithm.mStackVisibleRect.height());
    }

    private float scrollRangeToProgress(int s) {
        return (float) s / mLayoutAlgorithm.mStackVisibleRect.height();
    }

    /** Called from the view draw, computes the next scroll. */
    void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            float scroll = scrollRangeToProgress(mScroller.getCurrY());
            setStackScrollRaw(scroll);
            if (mCb != null) {
                mCb.onScrollChanged(scroll);
            }
        }
    }

    /** Returns whether the overscroller is scrolling. */
    boolean isScrolling() {
        return !mScroller.isFinished();
    }

    /** Stops the scroller and any current fling. */
    void stopScroller() {
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }
    }
}