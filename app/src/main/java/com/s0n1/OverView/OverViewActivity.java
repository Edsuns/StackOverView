
package com.s0n1.OverView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.s0n1.overview.misc.Utilities;
import com.s0n1.overview.model.OverViewAdapter;
import com.s0n1.overview.model.CardViewHolder;
import com.s0n1.overview.views.OverView;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Random;

/**
 * The main Recents activity that is started from AlternateRecentsComponent.
 */
public class OverViewActivity extends Activity implements OverView.RecentsViewCallbacks {
    // Top level views
    OverView mRecentsView;

    /** Called with the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // For the non-primary user, ensure that the SystemSericesProxy is initialized

        // Initialize the widget host (the host id is static and does not change)

        // Set the Recents layout
        setContentView(R.layout.recents);
        mRecentsView = findViewById(R.id.recents_view);
        mRecentsView.setCallbacks(this);
        mRecentsView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        // Register the broadcast receiver to handle messages when the screen is turned off
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);

        // Private API calls to make the shadows look better
        try {
            Utilities.setShadowProperty("ambientRatio", String.valueOf(1.5f));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        ArrayList<Integer> models = new ArrayList<>();
        for(int i = 0; i < 10; ++i) {
            Random random = new Random();
            random.setSeed(i);
            int color = Color.argb(255, random.nextInt(255), random.nextInt(255), random.nextInt(255));
            models.add(color);
        }

        final OverViewAdapter stack =
                new OverViewAdapter<CardViewHolder<View, Integer>, Integer>(models) {
            @Override
            public CardViewHolder<View, Integer> onCreateCardHolder(Context context, ViewGroup parent) {
                View v = View.inflate(context, R.layout.recents_dummy, null);
                return new CardViewHolder<>(v);
            }

            @Override
            public void onBindCardHolder(CardViewHolder<View, Integer> cardHolder) {
                final int position = cardHolder.getPosition();
                View view = cardHolder.itemView;
                ((View)view.getParent()).setBackgroundColor(cardHolder.model);
                view.setOnClickListener(new View.OnClickListener() {
                    @SuppressLint("ShowToast")
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(getApplicationContext(),"Clicked: "+position,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                ((TextView)view.findViewById(R.id.text_num)).setText(String.valueOf(position));
                view.findViewById(R.id.close_card).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        notifyDataSetRemoved(position);
                    }
                });
            }
        };

        mRecentsView.setTaskStack(stack);

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
    }

    @Override
    public void onAllCardsDismissed() {
    }

    @Override
    public void onCardDismissed(int position) {
        Toast.makeText(this,"Dismiss: "+position,Toast.LENGTH_SHORT).show();
    }
}
