package com.ccs.coffee_n_crops.editor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.ccs.coffee_n_crops.R;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Jumps directly to the program or a helper body while keeping creation within reach. */
@SuppressLint("ViewConstructor") // Created in code with editor navigation callbacks.
final class MethodNavigationView extends LinearLayout {
    private static final int CHIP_HEIGHT_DP = 44;
    private static final int CHIP_GAP_DP = 6;
    private static final int CHIP_RADIUS_DP = 8;
    private static final int CHIP_HORIZONTAL_PADDING_DP = 12;
    private static final int CHIP_VERTICAL_PADDING_DP = 6;
    private static final int CHIP_TEXT_SP = 13;
    private static final float LOCKED_ALPHA = 0.55f;

    private final Consumer<MethodDecl> navigate;
    private final HorizontalScrollView scroller;
    private final LinearLayout tabs;
    private final Button runButton;
    private final Button createButton;
    private final List<MethodDecl> shownMethods = new ArrayList<>();
    private final Map<MethodDecl, Button> methodButtons = new HashMap<>();
    private Button activeButton;
    private boolean locked;
    private boolean revealActive;

    MethodNavigationView(Context context, Consumer<MethodDecl> navigate, Runnable create) {
        super(context);
        this.navigate = navigate;
        setOrientation(HORIZONTAL);
        setBaselineAligned(false);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(SyntaxColors.PALETTE_BG);
        int gap = Ui.dp(context, CHIP_GAP_DP);
        setPadding(gap, gap, gap, gap);

        runButton = methodButton(null);
        addView(runButton, tabLayout());
        tabs = new LinearLayout(context);
        tabs.setOrientation(HORIZONTAL);
        tabs.setBaselineAligned(false);
        scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(tabs);
        addView(scroller, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        createButton = chip(context.getString(R.string.editor_new_method_short), Typeface.DEFAULT_BOLD);
        createButton.setOnClickListener(view -> {
            if (createButton.isEnabled()) create.run();
        });
        LayoutParams createLayout = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        createLayout.setMarginStart(gap);
        addView(createButton, createLayout);
        showMethods(shownMethods, null, false, false);
    }

    void showMethods(List<MethodDecl> methods, MethodDecl active, boolean canCreate, boolean locked) {
        boolean changedMethods = !shownMethods.equals(methods);
        if (changedMethods) {
            shownMethods.clear();
            shownMethods.addAll(methods);
            tabs.removeAllViews();
            methodButtons.clear();
            for (MethodDecl method : shownMethods) {
                Button button = methodButton(method);
                methodButtons.put(method, button);
                tabs.addView(button, tabLayout());
            }
        }

        Button selected = methodButtons.get(active);
        if (selected == null) selected = runButton;
        boolean changedSelection = activeButton != selected;
        if (changedMethods || changedSelection || this.locked != locked) {
            this.locked = locked;
            activeButton = selected;
            style(runButton, selected == runButton, !locked, SyntaxColors.TEXT);
            for (Button button : methodButtons.values()) {
                style(button, button == selected, !locked, SyntaxColors.TEXT);
            }
        }
        if (createButton.isEnabled() != (canCreate && !locked)) {
            style(createButton, false, canCreate && !locked, SyntaxColors.AMBER);
        }
        if (changedMethods || changedSelection) {
            revealActive = true;
            requestLayout();
        }
    }

    private Button methodButton(MethodDecl method) {
        String label = method == null ? "run()" : method.name + "()";
        Button button = chip(label, Typeface.MONOSPACE);
        button.setContentDescription(getContext().getString(R.string.editor_open_method_description, label));
        button.setOnClickListener(view -> {
            if (!locked) navigate.accept(method);
        });
        return button;
    }

    private Button chip(String label, Typeface typeface) {
        Context context = getContext();
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextSize(CHIP_TEXT_SP);
        button.setTypeface(typeface);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        button.setMinimumHeight(Ui.dp(context, CHIP_HEIGHT_DP));
        button.setMinHeight(Ui.dp(context, CHIP_HEIGHT_DP));
        int horizontal = Ui.dp(context, CHIP_HORIZONTAL_PADDING_DP);
        int vertical = Ui.dp(context, CHIP_VERTICAL_PADDING_DP);
        button.setPadding(horizontal, vertical, horizontal, vertical);
        button.setStateListAnimator(null);
        return button;
    }

    private void style(Button button, boolean selected, boolean enabled, int color) {
        button.setSelected(selected);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : LOCKED_ALPHA);
        button.setTextColor(selected ? SyntaxColors.EDITOR_BG : color);
        button.setBackground(Ui.pressable(Ui.rounded(selected ? SyntaxColors.ACCENT : SyntaxColors.CHIP_BG,
                Ui.dp(getContext(), CHIP_RADIUS_DP))));
    }

    private LayoutParams tabLayout() {
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Ui.dp(getContext(), CHIP_GAP_DP));
        return params;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth) revealActive = true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!revealActive || activeButton == null || scroller.getWidth() == 0) return;
        revealActive = false;
        if (activeButton == runButton) return;
        // Preserve the user's scroll unless the active tab would otherwise be clipped.
        int end = activeButton.getRight() - scroller.getWidth();
        int start = activeButton.getLeft();
        int current = scroller.getScrollX();
        scroller.scrollTo(Math.min(start, Math.max(end, current)), 0);
    }
}
