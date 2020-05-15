package com.s0n1.overview.views;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import com.s0n1.overview.misc.OverViewConfiguration;

class StackViewTouchHandler implements SwipeHelper.Callback {
    private static int INACTIVE_POINTER_ID = -1;

    private static final int TaskStackOverscrollRange = 150;

    private OverViewConfiguration mConfig;
    private StackView mSv;
    private StackViewScroller mScroller;
    private VelocityTracker mVelocityTracker;

    private boolean mIsScrolling;

    private float mLastP;
    private int mInitialMotionY;
    private int mLastMotionY;
    private int mActivePointerId = INACTIVE_POINTER_ID;

    private int mMinimumVelocity;
    private int mMaximumVelocity;
    // The scroll touch slop is used to calculate when we start scrolling
    private int mScrollTouchSlop;

    private SwipeHelper mSwipeHelper;
    private boolean mInterceptedBySwipeHelper;

    StackViewTouchHandler(Context context, StackView sv,
                          OverViewConfiguration config, StackViewScroller scroller) {
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mScrollTouchSlop = configuration.getScaledTouchSlop();
        // The page touch slop is used to calculate when we start swiping
        float mPagingTouchSlop = configuration.getScaledPagingTouchSlop();
        mSv = sv;
        mScroller = scroller;
        mConfig = config;

        float densityScale = context.getResources().getDisplayMetrics().density;
        mSwipeHelper = new SwipeHelper(0, this, densityScale, mPagingTouchSlop, mConfig);
    }

    /** Velocity tracker helpers */
    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }
    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }
    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /** Returns the view at the specified coordinates */
    private StackViewCard findViewAtPoint(int x, int y) {
        int childCount = mSv.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            StackViewCard tv = (StackViewCard) mSv.getChildAt(i);
            if (tv.getVisibility() == View.VISIBLE) {
                if (mSv.isTransformedTouchPointInView(x, y, tv)) {
                    return tv;
                }
            }
        }
        return null;
    }

    /** Constructs a simulated motion event for the current stack scroll. */
    private MotionEvent createMotionEventForStackScroll(MotionEvent ev) {
        MotionEvent pev = MotionEvent.obtainNoHistory(ev);
        pev.setLocation(0, mScroller.progressToScrollRange(mScroller.getStackScroll()));
        return pev;
    }

    /** Touch preprocessing for handling below */
    boolean onInterceptTouchEvent(MotionEvent ev) {
        // Return early if we have no children
        boolean hasChildren = (mSv.getChildCount() > 0);
        if (!hasChildren) {
            return false;
        }

        // Pass through to swipe helper if we are swiping
        mInterceptedBySwipeHelper = mSwipeHelper.onInterceptTouchEvent(ev);
        if (mInterceptedBySwipeHelper) {
            return true;
        }

        boolean wasScrolling = mScroller.isScrolling() ||
                (mScroller.mScrollAnimator != null && mScroller.mScrollAnimator.isRunning());
        int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                // Save the touch down info
                mInitialMotionY = mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                // Stop the current scroll if it is still flinging
                mScroller.stopScroller();
                mScroller.stopBoundScrollAnimation();
                // Initialize the velocity tracker
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(createMotionEventForStackScroll(ev));
                // Check if the scroller is finished yet
                mIsScrolling = mScroller.isScrolling();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mActivePointerId == INACTIVE_POINTER_ID) break;

                int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                int y = (int) ev.getY(activePointerIndex);
                if (Math.abs(y - mInitialMotionY) > mScrollTouchSlop) {
                    // Save the touch move info
                    mIsScrolling = true;
                    // Initialize the velocity tracker if necessary
                    initVelocityTrackerIfNotExists();
                    mVelocityTracker.addMovement(createMotionEventForStackScroll(ev));
                    // Disallow parents from intercepting touch events
                    final ViewParent parent = mSv.getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                }

                mLastMotionY = y;
                mLastP = mSv.mLayoutAlgorithm.screenYToCurveProgress(mLastMotionY);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                // Animate the scroll back if we've cancelled
                mScroller.animateBoundScroll();
                // Reset the drag state and the velocity tracker
                mIsScrolling = false;
                mActivePointerId = INACTIVE_POINTER_ID;
                recycleVelocityTracker();
                break;
            }
        }

        return wasScrolling || mIsScrolling;
    }

    /** Handles touch events once we have intercepted them */
    boolean onTouchEvent(MotionEvent ev) {

        // Short circuit if we have no children
        boolean hasChildren = (mSv.getChildCount() > 0);
        if (!hasChildren) {
            return false;
        }

        // Pass through to swipe helper if we are swiping
        if (mInterceptedBySwipeHelper && mSwipeHelper.onTouchEvent(ev)) {
            return true;
        }

        // Update the velocity tracker
        initVelocityTrackerIfNotExists();

        int action = ev.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                // Save the touch down info
                mInitialMotionY = mLastMotionY = (int) ev.getY();
                mActivePointerId = ev.getPointerId(0);
                // Stop the current scroll if it is still flinging
                mScroller.stopScroller();
                mScroller.stopBoundScrollAnimation();
                // Initialize the velocity tracker
                initOrResetVelocityTracker();
                mVelocityTracker.addMovement(createMotionEventForStackScroll(ev));
                // Disallow parents from intercepting touch events
                final ViewParent parent = mSv.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mActivePointerId = ev.getPointerId(index);
                mLastMotionY = (int) ev.getY(index);
                mLastP = mSv.mLayoutAlgorithm.screenYToCurveProgress(mLastMotionY);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mActivePointerId == INACTIVE_POINTER_ID) break;

                int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                int y = (int) ev.getY(activePointerIndex);
                int yTotal = Math.abs(y - mInitialMotionY);
                float curP = mSv.mLayoutAlgorithm.screenYToCurveProgress(y);
                float deltaP = mLastP - curP;
                if (!mIsScrolling) {
                    if (yTotal > mScrollTouchSlop) {
                        mIsScrolling = true;
                        // Initialize the velocity tracker
                        initOrResetVelocityTracker();
                        mVelocityTracker.addMovement(createMotionEventForStackScroll(ev));
                        // Disallow parents from intercepting touch events
                        final ViewParent parent = mSv.getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                if (mIsScrolling) {
                    float curStackScroll = mScroller.getStackScroll();
                    float overScrollAmount = mScroller.getScrollAmountOutOfBounds(curStackScroll + deltaP);
                    if (Float.compare(overScrollAmount, 0f) != 0) {
                        // Bound the overscroll to a fixed amount, and inversely scale the y-movement
                        // relative to how close we are to the max overscroll
                        float maxOverScroll = mConfig.taskStackOverscrollPct;
                        deltaP *= (1f - (Math.min(maxOverScroll, overScrollAmount)
                                / maxOverScroll));
                    }
                    mScroller.setStackScroll(curStackScroll + deltaP);
                    if (mScroller.isScrollOutOfBounds()) {
                        mVelocityTracker.clear();
                    } else {
                        mVelocityTracker.addMovement(createMotionEventForStackScroll(ev));
                    }
                }
                mLastMotionY = y;
                mLastP = mSv.mLayoutAlgorithm.screenYToCurveProgress(mLastMotionY);
                break;
            }
            case MotionEvent.ACTION_UP: {
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocity = (int) velocityTracker.getYVelocity(mActivePointerId);
                if (mIsScrolling && (Math.abs(velocity) > mMinimumVelocity)) {
                    int overscrollRange = (int) (Math.min(1f,
                            Math.abs((float) velocity / mMaximumVelocity)) *
                            TaskStackOverscrollRange);
                    // Fling scroll
                    mScroller.mScroller.fling(0, mScroller.progressToScrollRange(mScroller.getStackScroll()),
                            0, velocity,
                            0, 0,
                            mScroller.progressToScrollRange(mSv.mLayoutAlgorithm.mMinScrollP),
                            mScroller.progressToScrollRange(mSv.mLayoutAlgorithm.mMaxScrollP),
                            0, overscrollRange);
                    // Invalidate to kick off computeScroll
                    mSv.invalidate();
                } else if (mScroller.isScrollOutOfBounds()) {
                    // Animate the scroll back into bounds
                    mScroller.animateBoundScroll();
                }

                mActivePointerId = INACTIVE_POINTER_ID;
                mIsScrolling = false;
                recycleVelocityTracker();
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int pointerIndex = ev.getActionIndex();
                int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    // Select a new active pointer id and reset the motion state
                    final int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                    mLastMotionY = (int) ev.getY(newPointerIndex);
                    mLastP = mSv.mLayoutAlgorithm.screenYToCurveProgress(mLastMotionY);
                    mVelocityTracker.clear();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (mScroller.isScrollOutOfBounds()) {
                    // Animate the scroll back into bounds
                    mScroller.animateBoundScroll();
                }
                mActivePointerId = INACTIVE_POINTER_ID;
                mIsScrolling = false;
                recycleVelocityTracker();
                break;
            }
        }
        return true;
    }

    /**** SwipeHelper Implementation ****/

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        return findViewAtPoint((int) ev.getX(), (int) ev.getY());
    }

    @Override
    public boolean canChildBeDismissed(View v) {
        return true;
    }

    @Override
    public void onBeginDrag(View v) {
        StackViewCard tv = (StackViewCard) v;
        // Disallow touch events from this task view
        tv.setTouchEnabled(false);
        // Disallow parents from intercepting touch events
        final ViewParent parent = mSv.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    @Override
    public void onSwipeChanged(View v, float delta) {
        // Do nothing
    }

    @Override
    public void onChildDismissed(View v) {
        StackViewCard tv = (StackViewCard) v;
        // Re-enable touch events from this task view
        tv.setTouchEnabled(true);
        // Remove the task view from the stack
        mSv.onCardDismissed(tv);
    }

    @Override
    public void onSnapBackCompleted(View v) {
        StackViewCard tv = (StackViewCard) v;
        // Re-enable touch events from this task view
        tv.setTouchEnabled(true);
    }

    @Override
    public void onDragCancelled(View v) {
        // Do nothing
    }
}
