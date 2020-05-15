
package com.s0n1.overview.model;

import android.content.Context;
import android.view.ViewGroup;

import com.s0n1.overview.misc.OverViewConfiguration;
import com.s0n1.overview.views.StackViewCard;

import java.util.ArrayList;
import java.util.List;

public abstract class StackViewAdapter<VH extends StackViewCardHolder, Model> {

    /** Task stack callbacks */
    public interface Callbacks {
        void onCardAdded();
        void onCardRemoved(StackViewAdapter adapter, int position);
    }

    private Callbacks mCallbacks;

    //这个只是单纯用来计数的
    private List<Model> mItems = new ArrayList <>();

    public StackViewAdapter() {}

    protected StackViewAdapter(List<Model> models) {
        if (models != null) {
            mItems = models;
        }
    }

    /** Sets the callbacks for this task stack */
    public void setCallbacks(Callbacks cb) {
        mCallbacks = cb;
    }

    public void notifyDataAdded(Model model) {
        mItems.add(model);
    }

    /** Removes a task */
    public void notifyDataSetRemoved(int position) {
        if (position < 0 || position >= mItems.size()) {
            throw new IllegalArgumentException("Position is out of bounds.");
        }

        mItems.remove(position);

        if (mCallbacks != null) {
            // Notify that a task has been removed
            mCallbacks.onCardRemoved(this, position);
        }
    }

    public List<Model> getData() { return mItems;}

    public abstract VH onCreateCardHolder(Context context, ViewGroup parent);

    /**
     * This method is expected to populate the view in vh with the model in vh.
     */
    public abstract void onBindCardHolder(VH vh);

    public final int getNumberOfItems() {
        return mItems.size();
    }

    public final VH createCardHolder(Context context, OverViewConfiguration config) {

        StackViewCard container = new StackViewCard(context);
        container.setConfig(config);
        VH vh = onCreateCardHolder(context, container);
        vh.setContainer(container);
        return vh;
    }

    public final void bindCardHolder(VH vh, int position) {
        vh.model = mItems.get(position);
        onBindCardHolder(vh);
    }
}