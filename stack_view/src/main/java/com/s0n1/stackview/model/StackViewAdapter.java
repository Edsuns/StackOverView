package com.s0n1.stackview.model;

import android.content.Context;
import android.view.ViewGroup;

import com.s0n1.stackview.misc.StackViewConfiguration;
import com.s0n1.stackview.views.StackViewCard;

import java.util.ArrayList;
import java.util.List;

public abstract class StackViewAdapter<Model> {

    /**
     * Task stack callbacks
     */
    public interface Callback {
        void onCardAdded();

        void onCardRemoved(int position);

        void onCardChange(int position);
    }

    private Callback mCallback;

    private List<Model> mItems = new ArrayList<>();

    protected StackViewAdapter(List<Model> items) {
        if (items != null) {
            mItems = items;
        }
    }

    /**
     * Sets the callbacks for this task stack
     */
    public void setCallbacks(Callback cb) {
        mCallback = cb;
    }

    public void notifyDataAdded(Model item) {
        mItems.add(item);

        if (mCallback != null) {
            // Notify
            mCallback.onCardAdded();
        }
    }

    /**
     * Removes a task
     */
    public void notifyDataRemoved(int position) {
        if (position < 0 || position >= mItems.size()) {
            throw new IllegalArgumentException("Position is out of bounds.");
        }

        mItems.remove(position);

        if (mCallback != null) {
            // Notify that a task has been removed
            mCallback.onCardRemoved(position);
        }
    }

    public void notifyDataChange(Model newItem, int position) {
        mItems.remove(position);
        mItems.add(newItem);

        if (mCallback != null) {
            // Notify
            mCallback.onCardChange(position);
        }
    }

    public List<Model> getData() {
        return mItems;
    }

    public final int getNumberOfItems() {
        return mItems.size();
    }

    public final StackViewCardHolder<Model> createCardHolder(Context context, StackViewConfiguration config) {
        StackViewCard container = new StackViewCard(context);
        container.setConfig(config);
        StackViewCardHolder<Model> vh = onCreateCardHolder(context, container);
        vh.setContainer(container);
        return vh;
    }

    public final void bindCardHolder(StackViewCardHolder<Model> vh, int position) {
        vh.model = mItems.get(position);
        onBindCardHolder(vh);
    }

    public abstract StackViewCardHolder<Model> onCreateCardHolder(Context context, ViewGroup parent);

    /**
     * This method is expected to populate the view in vh with the model in vh.
     */
    public abstract void onBindCardHolder(StackViewCardHolder<Model> vh);
}