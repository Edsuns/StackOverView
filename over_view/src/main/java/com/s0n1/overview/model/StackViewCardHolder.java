package com.s0n1.overview.model;

import android.view.View;

import com.s0n1.overview.views.StackViewCard;

public class StackViewCardHolder<V extends View, Model extends Object>
{
    public final V itemView;
    public Model model;

    private StackViewCard mContainer;

    private int mCurrentPosition = -1;
    private int mLastPosition = -1;

    public StackViewCardHolder(V view)
    {
        this.itemView = view;
    }

    public void setPosition(int position) {
        mLastPosition = mCurrentPosition;
        mCurrentPosition = position;
    }

    public int getPosition() {
        return mCurrentPosition;
    }

    public int getLastPosition() {
        return mLastPosition;
    }

    public StackViewCard getContainer()
    {
        return mContainer;
    }

    protected void setContainer(StackViewCard container) {
        if (mContainer != null) {
            mContainer.setContent(null);
        }
        mContainer = container;
        if (mContainer != null && itemView != null) {
            mContainer.setContent(itemView);
        }
    }
}
