package com.s0n1.sample;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.s0n1.stackview.model.StackViewAdapter;
import com.s0n1.stackview.model.StackViewCardHolder;
import com.s0n1.stackview.views.StackView;

import java.util.ArrayList;

/**
 * The main Recents activity that is started from AlternateRecentsComponent.
 */
public class MainActivity extends Activity implements StackView.OnDismissedListener {
    // Top level views
    private StackView<Integer> stackView;

    private ArrayList<Integer> models;
    private StackViewAdapter<Integer> stack;

    private MenuItem scrollMenuItem;
    private MenuItem changeMenuItem;

    /**
     * Called with the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recents);

        configureStackView();
    }

    private void configureStackView() {
        stackView = findViewById(R.id.recents_view);
        stackView.setCallback(this);

        models = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            models.add(randomColor());
        }

        stack = new StackViewAdapter<Integer>(models) {
            @Override
            public StackViewCardHolder<Integer> onCreateCardHolder(Context context, ViewGroup parent) {
                View v = View.inflate(context, R.layout.recents_dummy, null);
                return new StackViewCardHolder<>(v);
            }

            @Override
            public void onBindCardHolder(StackViewCardHolder<Integer> cardHolder) {
                final int position = cardHolder.getPosition();
                View card = cardHolder.itemView;
                card.setBackgroundColor(cardHolder.model);
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        MutableToast.show(getApplicationContext(), "Clicked: " + position);
                    }
                });
                ((TextView) card.findViewById(R.id.text_num)).setText(String.valueOf(position));
                card.findViewById(R.id.close_card).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        notifyDataRemoved(position);
                    }
                });
            }
        };

        stackView.setAdapter(stack);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        scrollMenuItem = menu.findItem(R.id.scroll_card)
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        int position = (int) (Math.random() * models.size());
                        stackView.animateScrollTo(position);
                        MutableToast.show(MainActivity.this, "Scroll To: " + position);
                        return true;
                    }
                });
        changeMenuItem = menu.findItem(R.id.change_card)
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        int position = models.size() - 2;
                        if (position < 0) {
                            position = 0;
                        }
                        stack.notifyDataChange(randomColor(), position);
                        MutableToast.show(MainActivity.this, "Change: " + position);
                        return true;
                    }
                });
        menu.findItem(R.id.add_card)
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        stack.notifyDataAdded(randomColor());
                        scrollMenuItem.setEnabled(true);
                        changeMenuItem.setEnabled(true);
                        return true;
                    }
                });
        return true;
    }

    private int randomColor() {
        return Color.argb(255, (int) (Math.random() * 255),
                (int) (Math.random() * 255), (int) (Math.random() * 255));
    }

    @Override
    public void onAllCardsDismissed() {
        MutableToast.show(this, "All Cards Dismissed");
        scrollMenuItem.setEnabled(false);
        changeMenuItem.setEnabled(false);
    }

    @Override
    public void onCardDismissed(int position) {
        MutableToast.show(this, "Dismissed: " + position);
    }
}
