package com.s0n1.overview.model;

import android.view.View;

import com.s0n1.overview.views.StackViewCard;

public class StackViewCardHolder<Model> {
    public final View itemView;
    public Model model;

    private StackViewCard mContainer;

    private int mCurrentPosition = -1;

    public StackViewCardHolder(View view) {
        itemView = view;
    }

    public void setPosition(int position) {
        mCurrentPosition = position;
    }

    public int getPosition() {
        return mCurrentPosition;
    }

    public StackViewCard getContainer() {
        return mContainer;
    }

    void setContainer(StackViewCard container) {
        if (mContainer != null) {
            mContainer.setContent(null);
        }
        mContainer = container;
        if (mContainer != null && itemView != null) {
            mContainer.setContent(itemView);
        }
    }
}
