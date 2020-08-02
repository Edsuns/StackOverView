package com.s0n1.overview.views;

import android.graphics.Rect;
import android.util.Log;

import com.s0n1.overview.misc.OverViewConfiguration;
import com.s0n1.overview.misc.Utilities;

import java.util.HashMap;

class StackViewLayoutAlgorithm {

    //最小卡片的显示比率
    private static final float StackPeekMinScale = 0.8f; // The min scale of the last card in the peek area

    private OverViewConfiguration mConfig;

    // The various rects that define the stack view
    private Rect mViewRect = new Rect();
    Rect mStackVisibleRect = new Rect();
    private Rect mStackRect = new Rect();
    Rect mTaskRect = new Rect();

    // The min/max scroll progress
    float mMinScrollP;
    float mMaxScrollP;
    float mInitialScrollP;
    private int mBetweenAffiliationOffset;

    //存放个个cardview的比率
    private HashMap<Integer, Float> mTaskProgressMap = new HashMap<>();

    // Log function
    private static final float XScale = 1.75f;  // The large the XScale, the longer the flat area of the curve
    private static final float LogBase = 3000;
    private static final int PrecisionSteps = 250;

    // 最底部的卡片被挡住的区域 (0f to 0.7f)
    private static final float SCROLL_HIDED_BOTTOM_RATE = 0.38f;

    //xp[PrecisionSteps] 这个是每段x的平均渐进累加值，与弧度渐进值的靠近比率。也就是说会越来越快
    private static float[] xp;

    //当前阶段总弧度，所占总体的百分比() （0->1）
    private static float[] px;

    StackViewLayoutAlgorithm(OverViewConfiguration config) {
        mConfig = config;

        // Precompute the path
        initializeCurve();
    }

    /**
     * Computes the stack and task rects
     */
    void computeRects(int windowWidth, int windowHeight, Rect taskStackBounds) {
        // Compute the stack rects
        mViewRect.set(0, 0, windowWidth, windowHeight);
        mStackRect.set(taskStackBounds);
        mStackVisibleRect.set(taskStackBounds);
        mStackVisibleRect.bottom = mViewRect.bottom;

        int widthPadding = (int) (mConfig.taskStackWidthPaddingPct * mStackRect.width());
        int heightPadding = mConfig.taskStackTopPaddingPx;
        mStackRect.inset(widthPadding, heightPadding);

        // Compute the task rect
        int width = mStackRect.width();
        int height = mStackRect.height();
        int left = mStackRect.left + (mStackRect.width() - width) / 2;
        mTaskRect.set(left, mStackRect.top,
                left + width, mStackRect.top + height);

        // Update the affiliation offsets
        //这里设置cardview之间的各种参数
        float visibleTaskPct = 0.5f;
        mBetweenAffiliationOffset = (int) (visibleTaskPct * mTaskRect.height());
    }

    /**
     * Computes the minimum and maximum scroll progress values.  This method may be called before
     * the RecentsConfiguration is set, so we need to pass in the alt-tab state.
     */
    void computeMinMaxScroll(int itemCount) {
        // Clear the progress map
        mTaskProgressMap.clear();

        // Return early if we have no tasks
        if (itemCount < 1) {
            mMinScrollP = mMaxScrollP = 0;
            return;
        }

        // Note that we should account for the scale difference of the offsets at the screen bottom
        int taskHeight = mTaskRect.height();
        float pAtBottomOfStackRect = screenYToCurveProgress(mStackVisibleRect.bottom);
        float pBetweenAffiliateOffset = pAtBottomOfStackRect -
                screenYToCurveProgress(mStackVisibleRect.bottom - mBetweenAffiliationOffset);
        float pTaskHeightOffset = pAtBottomOfStackRect -
                screenYToCurveProgress(mStackVisibleRect.bottom - taskHeight);
        float pNavBarOffset = pAtBottomOfStackRect -
                screenYToCurveProgress(mStackVisibleRect.bottom - (mStackVisibleRect.bottom - mStackRect.bottom));

        // Update the task offsets
        float pAtFrontMostCardTop = 0.5f;// also is pAtBackMostCardTop
        for (int i = 0; i < itemCount; i++) {
            mTaskProgressMap.put(i, pAtFrontMostCardTop);

            if (i < (itemCount - 1)) {
                // Increment the peek height
                pAtFrontMostCardTop += pBetweenAffiliateOffset;
            }
        }

        mMaxScrollP = pAtFrontMostCardTop - ((1f - pTaskHeightOffset - pNavBarOffset));
        mMinScrollP = itemCount == 1 ? Math.max(mMaxScrollP, 0f) : 0f;
        mInitialScrollP = Math.max(0, pAtFrontMostCardTop);

        if (itemCount != 1)
            mMaxScrollP -= SCROLL_HIDED_BOTTOM_RATE;
    }

    /**
     * Update/get the transform
     * 由初始化的mTaskProgressMap来构建 view各自OverviewCardTransform 的绘制
     */
    StackViewCardTransform getStackTransform(int position, float stackScroll, StackViewCardTransform transformOut,
                                             StackViewCardTransform prevTransform) {
        // Return early if we have an invalid index
        if (!mTaskProgressMap.containsKey(position)) {
            transformOut.reset();
            return transformOut;
        }
        return getStackTransform(mTaskProgressMap.get(position), stackScroll, transformOut, prevTransform);
    }

    /**
     * Update/get the transform
     */
    StackViewCardTransform getStackTransform(float taskProgress, float stackScroll, StackViewCardTransform transformOut, StackViewCardTransform prevTransform) {
        float pTaskRelative = taskProgress - stackScroll;
        float pBounded = Math.max(0, Math.min(pTaskRelative, 1f));
        // 大于1就说明已经扩大到屏幕外了 If the task top is outside of the bounds below the screen, then immediately reset it
        if (pTaskRelative > 1f) {
            transformOut.reset();
            transformOut.rect.set(mTaskRect);
            return transformOut;
        }
        // The check for the top is trickier, since we want to show the next task if it is at all
        // visible, even if p < 0.
        if (pTaskRelative < 0f) {
            if (prevTransform != null && Float.compare(prevTransform.p, 0f) <= 0) {
                transformOut.reset();
                transformOut.rect.set(mTaskRect);
                return transformOut;
            }
        }
        float scale = curveProgressToScale(pBounded);
        int scaleYOffset = (int) (((1f - scale) * mTaskRect.height()) / 2);
        int minZ = mConfig.taskViewTranslationZMinPx;
        int maxZ = mConfig.taskViewTranslationZMaxPx;
        transformOut.scale = scale;
        transformOut.translationY = curveProgressToScreenY(pBounded) - mStackVisibleRect.top -
                scaleYOffset;
        transformOut.translationZ = Math.max(minZ, minZ + (pBounded * (maxZ - minZ)));
        transformOut.rect.set(mTaskRect);
        transformOut.rect.offset(0, transformOut.translationY);
        Utilities.scaleRectAboutCenter(transformOut.rect, transformOut.scale);
        transformOut.visible = true;
        transformOut.p = pTaskRelative;
        return transformOut;
    }

    /**
     * Returns the scroll to such task top = 1f;
     */
    float getStackScrollForTask(int index) {
        return mTaskProgressMap.get(index);
    }

    /**
     * Initializes the curve.
     */
    private static void initializeCurve() {
        if (xp != null && px != null) return;
        xp = new float[PrecisionSteps + 1];
        px = new float[PrecisionSteps + 1];

        // Approximate f(x)
        float[] fx = new float[PrecisionSteps + 1];
        float step = 1f / PrecisionSteps;
        float x = 0;

        for (int xStep = 0; xStep <= PrecisionSteps; xStep++) {

            //1-(3000^(1-1.75*x))/3000 就是这样一个先快后面的函数步骤 （0->1）
            //fx[xStep]：每个阶段的y值  （0->1）

            fx[xStep] = logFunc(x);
            Log.e("fx[xStep]: ", "fx[xStep] : " + xStep + "  " + fx[xStep]);
            x += step;
        }

        // Calculate the arc length for x:1->0
        float pLength = 0;
        float[] dx = new float[PrecisionSteps + 1];
        dx[0] = 0;

        //dx[xStep]：每个阶段差值间的距离，即直线弧度
        //pLength: 总弧度 （0->1.783）

        for (int xStep = 1; xStep < PrecisionSteps; xStep++) {
            dx[xStep] = (float) Math.sqrt(Math.pow(fx[xStep] - fx[xStep - 1], 2) + Math.pow(step, 2));
            pLength += dx[xStep];
        }

        // Approximate p(x), a function of cumulative progress with x, normalized to 0..1
        float p = 0;
        px[0] = 0f;
        px[PrecisionSteps] = 1f;

        for (int xStep = 1; xStep <= PrecisionSteps; xStep++) {

            //px[xStep]: 当前阶段总弧度，所占总体的百分比() （0->1）

            p += Math.abs(dx[xStep] / pLength);
            px[xStep] = p;
        }
        // Given p(x), calculate the inverse function x(p). This assumes that x(p) is also a valid
        // function.

        int xStep = 0;
        p = 0;
        xp[0] = 0f;
        xp[PrecisionSteps] = 1f;

        //xp[PrecisionSteps] 这个是每段x的平均渐进累加值，与弧度渐进值的靠近比率。也就是说会越来越快  （0->1）

        for (int pStep = 0; pStep < PrecisionSteps; pStep++) {
            // Walk forward in px and find the x where px <= p && p < px+1
            while (xStep < PrecisionSteps) {
                if (px[xStep] > p) break;
                xStep++;
            }
            // Now, px[xStep-1] <= p < px[xStep]
            if (xStep == 0) {
                xp[pStep] = 0;
            } else {
                // Find x such that proportionally, x is correct
                float fraction = (p - px[xStep - 1]) / (px[xStep] - px[xStep - 1]);
                x = (xStep - 1 + fraction) * step;
                xp[pStep] = x;
            }
            p += step;
        }

    }

    /**
     * Reverses and scales out x.
     */
    private static float reverse(float x) {
        return (-x * XScale) + 1;
    }

    /**
     * The log function describing the curve.
     */
    private static float logFunc(float x) {
        return 1f - (float) (Math.pow(LogBase, reverse(x))) / (LogBase);
    }

    /**
     * Converts from the progress along the curve to a screen coordinate.
     */
    private int curveProgressToScreenY(float p) {
        if (p < 0 || p > 1) return mStackVisibleRect.top + (int) (p * mStackVisibleRect.height());
        float pIndex = p * PrecisionSteps;
        int pFloorIndex = (int) Math.floor(pIndex);
        int pCeilIndex = (int) Math.ceil(pIndex);
        float xFraction = 0;
        if (pFloorIndex < PrecisionSteps && (pCeilIndex != pFloorIndex)) {
            float pFraction = (pIndex - pFloorIndex) / (pCeilIndex - pFloorIndex);
            xFraction = (xp[pCeilIndex] - xp[pFloorIndex]) * pFraction;
        }
        float x = xp[pFloorIndex] + xFraction;
        return mStackVisibleRect.top + (int) (x * mStackVisibleRect.height());
    }

    /**
     * Converts from the progress along the curve to a scale.
     * 扩大的范围线性计算
     *
     * @param p 当前比率
     * @return 经由缩小值计算后的比率
     */
    private float curveProgressToScale(float p) {
        if (p < 0) return StackPeekMinScale;
        if (p > 1) return 1f;
        float scaleRange = (1f - StackPeekMinScale);
        return StackPeekMinScale + (p * scaleRange);// scale
    }


    /**
     * 纵坐标装换成曲线走势
     * Converts from a screen coordinate to the progress along the curve.
     *
     * @param screenY 需要转换的高度
     * @return 输入高度实际占曲线的百分比
     */
    float screenYToCurveProgress(int screenY) {
        float x = (float) (screenY - mStackVisibleRect.top) / mStackVisibleRect.height();
        if (x < 0 || x > 1) return x;
        float xIndex = x * PrecisionSteps;
        int xFloorIndex = (int) Math.floor(xIndex);
        int xCeilIndex = (int) Math.ceil(xIndex);
        float pFraction = 0;
        if (xFloorIndex < PrecisionSteps && (xCeilIndex != xFloorIndex)) {

            //精确到小数部分值计算
            float xFraction = (xIndex - xFloorIndex) / (xCeilIndex - xFloorIndex);
            pFraction = (px[xCeilIndex] - px[xFloorIndex]) * xFraction;
        }

        //转换后的弧度比 和 其小数弧度 的和
        return px[xFloorIndex] + pFraction;
    }
}
