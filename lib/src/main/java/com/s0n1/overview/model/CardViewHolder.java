package com.s0n1.overview.model;

import android.view.View;

import com.s0n1.overview.views.OverViewCard;

public class CardViewHolder<V extends View, Model extends Object>
{
    public final V itemView;
    public Model model;

    private OverViewCard mContainer;

    private int mCurrentPosition = -1;
    private int mLastPosition = -1;

    public CardViewHolder(V view)
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

    public OverViewCard getContainer()
    {
        return mContainer;
    }

    protected void setContainer(OverViewCard container) {
        if (mContainer != null) {
            mContainer.setContent(null);
        }
        mContainer = container;
        if (mContainer != null && itemView != null) {
            mContainer.setContent(itemView);
        }
    }
}
