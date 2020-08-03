package com.s0n1.stackview.views;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.s0n1.stackview.misc.StackViewConfiguration;
import com.s0n1.stackview.model.StackViewAdapter;
import com.s0n1.stackview.model.StackViewCardHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/* The visual representation of a task stack view */
@SuppressLint("ViewConstructor")
public class StackView<Model> extends FrameLayout implements StackViewAdapter.Callback, StackViewScroller.Callbacks,
        ObjectPool.ObjectPoolConsumer<StackViewCardHolder<Model>, Integer> {

    /**
     * The TaskView callbacks
     */
    public interface OnDismissedListener {
        void onCardDismissed(int position);

        void onAllCardsDismissed();
    }

    StackViewConfiguration mConfig;

    StackViewAdapter<Model> mStack;
    StackViewLayoutAlgorithm mLayoutAlgorithm;
    StackViewScroller mStackScroller;
    StackViewTouchHandler<Model> mTouchHandler;
    OnDismissedListener dismissedListener;
    ObjectPool<StackViewCardHolder<Model>, Integer> mViewPool;
    ArrayList<StackViewCardTransform> mCurrentCardTransforms = new ArrayList<>();
    HashMap<StackViewCard, StackViewCardHolder<Model>> mViewHolderMap = new HashMap<>();

    Rect mOverviewStackBounds = new Rect();

    // Optimizations
    int mStackViewsAnimationDuration;
    boolean mStackViewsDirty = true;
    boolean mStackViewsClipDirty = true;
    boolean mAwaitingFirstLayout = true;
    int[] mTmpVisibleRange = new int[2];
    Rect mTmpRect = new Rect();
    StackViewCardTransform mTmpTransform = new StackViewCardTransform();
    LayoutInflater mInflater;

    ValueAnimator.AnimatorUpdateListener mRequestUpdateClippingListener =
            new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    requestUpdateStackViewsClip();
                }
            };

    public StackView(Context context) {
        this(context, null);
    }

    public StackView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StackView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mConfig = new StackViewConfiguration(context);
        mViewPool = new ObjectPool<>(context, this);
        mInflater = LayoutInflater.from(context);
        mLayoutAlgorithm = new StackViewLayoutAlgorithm(mConfig);
        mStackScroller = new StackViewScroller(context, mConfig, mLayoutAlgorithm);
        mStackScroller.setCallbacks(this);
        mTouchHandler = new StackViewTouchHandler<>(context, this, mConfig, mStackScroller);
    }

    public void setAdapter(StackViewAdapter<Model> adapter) {
        mStack = adapter;
        mStack.setCallbacks(this);
    }

    /**
     * Sets the callback
     */
    public void setCallback(OnDismissedListener cb) {
        dismissedListener = cb;
    }

    /**
     * Requests that the views be synchronized with the model
     */
    void requestSynchronizeStackViewsWithModel() {
        requestSynchronizeStackViewsWithModel(0);
    }

    void requestSynchronizeStackViewsWithModel(int duration) {
        if (!mStackViewsDirty) {
            invalidate();
            mStackViewsDirty = true;
        }
        if (mAwaitingFirstLayout) {
            // Skip the animation if we are awaiting first layout
            mStackViewsAnimationDuration = 0;
        } else {
            mStackViewsAnimationDuration = Math.max(mStackViewsAnimationDuration, duration);
        }
        requestLayout();
    }

    /**
     * Requests that the views clipping be updated.
     */
    void requestUpdateStackViewsClip() {
        if (!mStackViewsClipDirty) {
            invalidate();
            mStackViewsClipDirty = true;
        }
    }

    public StackViewCard getChildViewForIndex(int index) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            StackViewCard tv = (StackViewCard) getChildAt(i);
            StackViewCardHolder<Model> holder = mViewHolderMap.get(tv);
            if (holder != null && holder.getPosition() == index) {
                return tv;
            }
        }
        return null;
    }

    private boolean updateStackTransforms(ArrayList<StackViewCardTransform> cardTransforms,
                                          int itemCount,
                                          float stackScroll,
                                          int[] visibleRangeOut) {
        // XXX: We should be intelligent about where to look for the visible stack range using the
        //      current stack scroll.
        // XXX: We should log extra cases like the ones below where we don't expect to hit very often
        // XXX: Print out approximately how many indices we have to go through to find the first visible transform

        int transformCount = cardTransforms.size();
        int frontMostVisibleIndex = -1;
        int backMostVisibleIndex = -1;

        // We can reuse the card transforms where possible to reduce object allocation
        if (transformCount < itemCount) {
            // If there are less transforms than cards, then add as many transforms as necessary
            for (int i = transformCount; i < itemCount; i++) {
                cardTransforms.add(new StackViewCardTransform());
            }
        } else if (transformCount > itemCount) {
            // If there are more transforms than cards, then just subset the transform list
            cardTransforms.subList(0, itemCount);
        }

        // Update the stack transforms
        StackViewCardTransform prevTransform = null;
        for (int i = itemCount - 1; i >= 0; i--) {

            // 这里将空的 OverviewCardTransform 丢进去
            StackViewCardTransform transform = mLayoutAlgorithm.getStackTransform(i,
                    stackScroll, cardTransforms.get(i), prevTransform);
            if (transform.visible) {
                if (frontMostVisibleIndex < 0) {
                    frontMostVisibleIndex = i;
                }
                backMostVisibleIndex = i;
            } else {
                if (backMostVisibleIndex != -1) {
                    // We've reached the end of the visible range, so going down the rest of the
                    // stack, we can just reset the transforms accordingly
                    while (i >= 0) {
                        cardTransforms.get(i).reset();
                        i--;
                    }
                    break;
                }
            }

            prevTransform = transform;
        }
        if (visibleRangeOut != null) {
            visibleRangeOut[0] = frontMostVisibleIndex;
            visibleRangeOut[1] = backMostVisibleIndex;
        }
        return frontMostVisibleIndex != -1;
    }

    /**
     * Synchronizes the views with the model
     */
    void synchronizeStackViewsWithModel() {
        if (mStackViewsDirty) {

            float stackScroll = mStackScroller.getStackScroll();
            int[] visibleRange = mTmpVisibleRange;
            boolean isValidVisibleRange = updateStackTransforms(mCurrentCardTransforms, mStack.getNumberOfItems(),
                    stackScroll, visibleRange);

            ArrayList<Map.Entry<StackViewCard, StackViewCardHolder<Model>>> entrySet = new ArrayList<>(mViewHolderMap.entrySet());

            Map<Integer, StackViewCardHolder<Model>> reusedMap = new HashMap<>();

            for (Map.Entry<StackViewCard, StackViewCardHolder<Model>> entry : entrySet) {
                int position = entry.getValue().getPosition();
                if (visibleRange[1] <= position && position <= visibleRange[0]) {
                    StackViewCardHolder<Model> vh = entry.getValue();
                    reusedMap.put(position, vh);
                } else {
                    mViewPool.returnObjectToPool(entry.getValue());
                }
            }

            // Pick up all the newly visible children and update all the existing children
            for (int i = visibleRange[0]; isValidVisibleRange && i >= visibleRange[1]; i--) {
                StackViewCardTransform transform = mCurrentCardTransforms.get(i);

                StackViewCardHolder<Model> vh = reusedMap.get(i);
                if (vh == null) {
                    vh = mViewPool.pickUpObjectFromPool(i, i);

                    if (mStackViewsAnimationDuration > 0) {
                        // For items in the list, put them in start animating them from the
                        // approriate ends of the list where they are expected to appear
                        if (Float.compare(transform.p, 0f) <= 0) {
                            mLayoutAlgorithm.getStackTransform(0f, 0f, mTmpTransform, null);
                        } else {
                            mLayoutAlgorithm.getStackTransform(1f, 0f, mTmpTransform, null);
                        }
                        vh.getContainer().updateViewPropertiesToCardTransform(mTmpTransform);
                    }
                }

                // Animate the card into place
                vh.getContainer().updateViewPropertiesToCardTransform(mCurrentCardTransforms.get(i),
                        mStackViewsAnimationDuration, mRequestUpdateClippingListener);
            }

            // Reset the request-synchronize params
            mStackViewsAnimationDuration = 0;
            mStackViewsDirty = false;
            mStackViewsClipDirty = true;
        }
    }

    public void animateScrollTo(int position) {
        int maxPosition = mStack.getNumberOfItems() - 1;
        if (maxPosition < 0) return;
        if (position < 0) {
            position = 0;
        }
        if (position > maxPosition) {
            position = maxPosition;
        }

        float targetScroll;
        if (position == maxPosition) {
            targetScroll = mLayoutAlgorithm.mMaxScrollP;
        } else {
            targetScroll = mLayoutAlgorithm.getStackScrollForTask(position) - 0.5f;
        }

        mStackScroller.animateScroll(mStackScroller.getStackScroll(), targetScroll);
    }

    /**
     * Updates the clip for each of the task views.
     */
    void clipTaskViews() {
        mStackViewsClipDirty = false;
    }

    /**
     * The stack insets to apply to the stack contents
     */
    public void setStackInsetRect(Rect r) {
        mOverviewStackBounds.set(r);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void setOnTouchListener(OnTouchListener l) {
        super.setOnTouchListener(l);
    }

    /**
     * Updates the min and max virtual scroll bounds
     */
    void updateMinMaxScroll(boolean boundScrollToNewMinMax) {
        // Compute the min and max scroll values
        mLayoutAlgorithm.computeMinMaxScroll(mStack.getNumberOfItems());

        // Debug logging
        if (boundScrollToNewMinMax) {
            mStackScroller.boundScroll();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mTouchHandler.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mTouchHandler.onTouchEvent(ev);
    }

    @Override
    public void computeScroll() {
        mStackScroller.computeScroll();
        // Synchronize the views
        synchronizeStackViewsWithModel();
        clipTaskViews();
    }

    /**
     * Computes the stack and task rects
     */
    void computeRects(int windowWidth, int windowHeight, Rect taskStackBounds) {
        // Compute the rects in the stack algorithm
        mLayoutAlgorithm.computeRects(windowWidth, windowHeight, taskStackBounds);

        // Update the scroll bounds
        updateMinMaxScroll(false);
    }

    /**
     * This is called with the full window width and height to allow stack view children to
     * perform the full screen transition down.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        @SuppressLint("DrawAllocation")
        Rect stackBounds = new Rect();
        mConfig.getOverviewStackBounds(width, height, stackBounds);
        setStackInsetRect(stackBounds);

        //空间大部分的初始化都在这里

        // Compute our stack/task rects
        @SuppressLint("DrawAllocation")
        Rect taskStackBounds = new Rect(mOverviewStackBounds);
        computeRects(width, height, taskStackBounds);

        // If this is the first layout, then scroll to the front of the stack and synchronize the
        // stack views immediately to load all the views
        if (mAwaitingFirstLayout) {
            mStackScroller.setStackScrollToInitialState();
            requestSynchronizeStackViewsWithModel();
            synchronizeStackViewsWithModel();
        }

        // Measure each of the TaskViews
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            StackViewCard tv = (StackViewCard) getChildAt(i);
            if (tv.getBackground() != null) {
                tv.getBackground().getPadding(mTmpRect);
            } else {
                mTmpRect.setEmpty();
            }
            tv.measure(
                    MeasureSpec.makeMeasureSpec(
                            mLayoutAlgorithm.mTaskRect.width() + mTmpRect.left + mTmpRect.right,
                            MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(
                            mLayoutAlgorithm.mTaskRect.height() + mTmpRect.top + mTmpRect.bottom, MeasureSpec.EXACTLY));
        }

        setMeasuredDimension(width, height);
    }

    /**
     * This is called with the size of the space not including the top or right insets, or the
     * search bar height in portrait (but including the search bar width in landscape, since we want
     * to draw under it.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // Layout each of the children
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            StackViewCard tv = (StackViewCard) getChildAt(i);
            if (tv.getBackground() != null) {
                tv.getBackground().getPadding(mTmpRect);
            } else {
                mTmpRect.setEmpty();
            }
            tv.layout(mLayoutAlgorithm.mTaskRect.left - mTmpRect.left,
                    mLayoutAlgorithm.mTaskRect.top - mTmpRect.top,
                    mLayoutAlgorithm.mTaskRect.right + mTmpRect.right,
                    mLayoutAlgorithm.mTaskRect.bottom + mTmpRect.bottom);
        }

        if (mAwaitingFirstLayout) {
            mAwaitingFirstLayout = false;
            onFirstLayout();
        }

        if (changed && !mAwaitingFirstLayout) {// Fix view display issue after orientation changed.
            float scroll = mStackScroller.getStackScroll();
            mStackScroller.setStackScrollToInitialState();
            if (scroll > mLayoutAlgorithm.mMaxScrollP) {
                scroll = mLayoutAlgorithm.mMaxScrollP;
            }
            mStackScroller.animateScroll(scroll, scroll);
        }
    }

    /**
     * Handler for the first layout.
     */
    void onFirstLayout() {
        for (Map.Entry<StackViewCard, StackViewCardHolder<Model>> entry : mViewHolderMap.entrySet()) {
            entry.getKey().prepareEnterRecentsAnimation();
        }
    }

    public boolean isTransformedTouchPointInView(float x, float y, View child) {
        final Rect frame = new Rect();
        child.getHitRect(frame);
        return frame.contains((int) x, (int) y);
    }

    @Override
    public void onCardChange(int position) {
        StackViewCard tv = getChildViewForIndex(position);
        StackViewCardHolder<Model> holder = mViewHolderMap.get(tv);
        if (holder != null) {
            mStack.bindCardHolder(holder, position);
            requestSynchronizeStackViewsWithModel();
        }
    }

    public void onCardAdded() {
        // Update the min/max scroll
        updateMinMaxScroll(false);

        requestSynchronizeStackViewsWithModel();
        animateScrollTo(mStack.getNumberOfItems() - 1);
    }

    public void onCardRemoved(int position) {
        // Remove the view associated with this task, we can't rely on updateTransforms
        // to work here because the task is no longer in the list
        StackViewCard tv = getChildViewForIndex(position);
        StackViewCardHolder<Model> holder = mViewHolderMap.get(tv);

        // Notify the callback that we've removed the task and it can clean up after it
        dismissedListener.onCardDismissed(position);

        if (tv != null && holder != null) {
            holder.setPosition(-1);
            mViewPool.returnObjectToPool(holder);
        }

        for (StackViewCardHolder<Model> vh : mViewHolderMap.values()) {
            if (vh.getPosition() > position) {
                int newPosition = vh.getPosition() - 1;
                vh.setPosition(newPosition);
                mStack.bindCardHolder(vh, newPosition);
            }
        }

        // Update the min/max scroll and animate other task views into their new positions
        updateMinMaxScroll(true);

        // Animate all the tasks into place
        requestSynchronizeStackViewsWithModel(200);

        // If there are no remaining tasks, then either unfilter the current stack, or just close
        // the activity if there are no filtered stacks
        if (mStack.getNumberOfItems() == 0) {
            dismissedListener.onAllCardsDismissed();
        }
    }

    void onCardDismissed(StackViewCard tv) {
        StackViewCardHolder<Model> vh = mViewHolderMap.get(tv);
        if (vh != null) {
            int taskIndex = vh.getPosition();
            mStack.notifyDataRemoved(taskIndex);
        }
    }

    @Override
    public StackViewCardHolder<Model> createObject(Context context) {
        return mStack.createCardHolder(context, mConfig);
    }

    @Override
    public void prepareObjectToEnterPool(StackViewCardHolder<Model> vh) {

        mViewHolderMap.remove(vh.getContainer());
        // Detach the view from the hierarchy
        detachViewFromParent(vh.getContainer());

        // Reset the view properties
        vh.getContainer().resetViewProperties();
    }

    @Override
    public void prepareObjectToLeavePool(StackViewCardHolder<Model> vh, Integer position, boolean isNewView) {
        // Rebind the task and request that this task's data be filled into the TaskView

        mViewHolderMap.put(vh.getContainer(), vh);
        vh.setPosition(position);
        mStack.bindCardHolder(vh, position);
        StackViewCard container = vh.getContainer();

        // Find the index where this task should be placed in the stack
        int insertIndex = -1;
        int taskIndex = position;
        if (taskIndex != -1) {
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                StackViewCard insertTV = (StackViewCard) getChildAt(i);
                StackViewCardHolder<Model> holder = mViewHolderMap.get(insertTV);
                if (holder != null && taskIndex < holder.getPosition()) {
                    insertIndex = i;
                    break;
                }
            }
        }

        // Add/attach the view to the hierarchy
        if (isNewView) {
            addView(container, insertIndex);

            // Set the callbacks and listeners for this new view
            container.setTouchEnabled(true);
        } else {
            attachViewToParent(container, insertIndex, container.getLayoutParams());
        }
    }

    @Override
    public boolean hasPreferredData(StackViewCardHolder<Model> vh, Integer preferredData) {
        return (vh.getPosition() == preferredData);
    }

    /**** TaskStackViewScroller.TaskStackViewScrollerCallbacks ****/
    @Override
    public void onScrollChanged(float p) {
        requestSynchronizeStackViewsWithModel();
        invalidateOnAnimation();
    }

    private void invalidateOnAnimation() {
        if (Build.VERSION.SDK_INT >= 16) {
            postInvalidateOnAnimation();
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            });
        }
    }
}
