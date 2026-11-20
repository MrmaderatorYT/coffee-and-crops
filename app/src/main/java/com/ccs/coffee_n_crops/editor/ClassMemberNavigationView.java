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
import com.ccs.coffee_n_crops.lang.ast.ClassDecl;
import com.ccs.coffee_n_crops.lang.ast.ConstructorDecl;
import com.ccs.coffee_n_crops.lang.ast.MethodDecl;
import com.ccs.coffee_n_crops.lang.ast.Node;
import com.ccs.coffee_n_crops.lang.ast.stmt.VarDecl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;

/** Keeps member navigation and creation within reach while scrolling through a class. */
@SuppressLint("ViewConstructor") // Created in code with the editor's navigation callback.
final class ClassMemberNavigationView extends LinearLayout {
    private static final int CHIP_MIN_DP = 48;
    private static final int CHIP_MAX_WIDTH_DP = 220;
    private static final int CHIP_GAP_DP = 6;
    private static final int CHIP_RADIUS_DP = 8;
    private static final int CHIP_HORIZONTAL_PADDING_DP = 8;
    private static final int CHIP_TEXT_SP = 13;
    private static final int FADE_WIDTH_DP = 12;
    private static final float LOCKED_ALPHA = 0.55f;

    private final HorizontalScrollView memberScroller;
    private final LinearLayout chips;
    private final LinearLayout creationRow;
    private final Button addFieldButton;
    private final Button addConstructorButton;
    private final Button addMethodButton;
    private final Consumer<Node> openMember;
    private final List<Node> shownMembers = new ArrayList<>();
    private final Map<Node, Button> memberButtons = new LinkedHashMap<>();
    private ClassDecl shownClass;
    private Button activeButton;
    private boolean locked;
    private boolean revealActive;

    ClassMemberNavigationView(Context context, Consumer<Node> openMember,
                              Runnable addField, Runnable addConstructor, Runnable addMethod) {
        super(context);
        this.openMember = openMember;
        setOrientation(VERTICAL);
        setBackgroundColor(SyntaxColors.PALETTE_BG);

        memberScroller = new HorizontalScrollView(context);
        memberScroller.setHorizontalScrollBarEnabled(false);
        memberScroller.setHorizontalFadingEdgeEnabled(true);
        memberScroller.setFadingEdgeLength(Ui.dp(context, FADE_WIDTH_DP));

        chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setBaselineAligned(false);
        int gap = Ui.dp(context, CHIP_GAP_DP);
        chips.setPaddingRelative(gap, 0, 0, gap);
        memberScroller.addView(chips);
        addView(memberScroller, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        creationRow = new LinearLayout(context);
        creationRow.setOrientation(HORIZONTAL);
        creationRow.setBaselineAligned(false);
        creationRow.setPaddingRelative(gap, 0, 0, gap);
        addFieldButton = addCreationButton(R.string.editor_add_field_short, SyntaxColors.TEAMMATE, addField);
        addConstructorButton = addCreationButton(R.string.editor_add_constructor_short, SyntaxColors.AMBER, addConstructor);
        addMethodButton = addCreationButton(R.string.editor_new_method_short, SyntaxColors.ACCENT, addMethod);
        addView(creationRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setVisibility(GONE);
    }

    void showMembers(ClassDecl cls, Node active, boolean locked, boolean canAddField,
                     boolean showConstructor, boolean canAddConstructor, boolean canAddMethod) {
        List<Node> members = new ArrayList<>();
        if (cls != null) {
            members.addAll(cls.fields());
            if (cls.constructor() != null) members.add(cls.constructor());
            members.addAll(cls.methods());
        }
        boolean showActions = cls != null && (canAddField || showConstructor || canAddMethod);
        setVisibility(members.isEmpty() && !showActions ? GONE : VISIBLE);
        memberScroller.setVisibility(members.isEmpty() ? GONE : VISIBLE);
        creationRow.setVisibility(showActions ? VISIBLE : GONE);
        updateCreationButton(addFieldButton, canAddField, !locked && canAddField);
        updateCreationButton(addConstructorButton, showConstructor, !locked && canAddConstructor);
        updateCreationButton(addMethodButton, canAddMethod, !locked && canAddMethod);

        boolean changedClass = shownClass != cls;
        boolean changedMembers = !shownMembers.equals(members);
        if (changedMembers) {
            shownMembers.clear();
            shownMembers.addAll(members);
            chips.removeAllViews();
            memberButtons.clear();
            for (Node member : members) {
                Button button = createChip(Typeface.MONOSPACE);
                button.setOnClickListener(view -> {
                    if (!this.locked) openMember.accept(member);
                });
                memberButtons.put(member, button);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                params.setMarginEnd(Ui.dp(getContext(), CHIP_GAP_DP));
                chips.addView(button, params);
            }
        }

        Button selected = memberButtons.get(active);
        boolean changedSelection = activeButton != selected;
        boolean changedStyle = changedMembers || changedSelection || this.locked != locked;
        boolean changedLabel = false;
        for (Map.Entry<Node, Button> entry : memberButtons.entrySet()) {
            Node member = entry.getKey();
            Button button = entry.getValue();
            String label = chipLabel(cls, member);
            if (!TextUtils.equals(button.getText(), label)) {
                button.setText(label);
                button.setContentDescription(getContext().getString(descriptionId(cls, member), label));
                changedLabel = true;
            }
            if (changedStyle) style(button, member, button == selected, locked);
        }

        shownClass = cls;
        activeButton = selected;
        this.locked = locked;
        if (changedClass) memberScroller.scrollTo(0, 0);
        if (changedClass || changedMembers || changedSelection || changedLabel) {
            revealActive = true;
            requestLayout();
        }
    }

    private Button addCreationButton(int label, int color, Runnable action) {
        Context context = getContext();
        Button button = createChip(Typeface.DEFAULT_BOLD);
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setText(label);
        button.setTextColor(color);
        button.setBackground(Ui.pressable(Ui.rounded(SyntaxColors.CHIP_BG, Ui.dp(context, CHIP_RADIUS_DP))));
        button.setOnClickListener(view -> {
            if (view.isEnabled()) action.run();
        });
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 1f);
        params.setMarginEnd(Ui.dp(context, CHIP_GAP_DP));
        creationRow.addView(button, params);
        return button;
    }

    private static void updateCreationButton(Button button, boolean visible, boolean enabled) {
        button.setVisibility(visible ? VISIBLE : GONE);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : LOCKED_ALPHA);
    }

    private Button createChip(Typeface typeface) {
        Context context = getContext();
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextSize(CHIP_TEXT_SP);
        button.setTypeface(typeface);
        button.setGravity(Gravity.CENTER);
        int min = Ui.dp(context, CHIP_MIN_DP);
        button.setMinimumWidth(min);
        button.setMinWidth(min);
        button.setMinimumHeight(min);
        button.setMinHeight(min);
        button.setMaxWidth(Ui.dp(context, CHIP_MAX_WIDTH_DP));
        int side = Ui.dp(context, CHIP_HORIZONTAL_PADDING_DP);
        button.setPaddingRelative(side, 0, side, 0);
        button.setStateListAnimator(null);
        return button;
    }

    private static String chipLabel(ClassDecl cls, Node member) {
        if (member instanceof VarDecl) return ((VarDecl) member).name;
        String name;
        List<VarDecl> parameters;
        if (member instanceof ConstructorDecl) {
            name = cls.name;
            parameters = ((ConstructorDecl) member).parameters;
        } else {
            MethodDecl method = (MethodDecl) member;
            name = method.name;
            parameters = method.parameters;
        }
        StringJoiner signature = new StringJoiner(", ", name + '(', ")");
        for (VarDecl parameter : parameters) signature.add(parameter.type.javaName);
        return signature.toString();
    }

    private static int descriptionId(ClassDecl cls, Node member) {
        if (member instanceof VarDecl) return R.string.editor_open_field_description;
        if (member instanceof ConstructorDecl) return R.string.editor_open_constructor_description;
        return cls.isInterface ? R.string.editor_open_signature_description : R.string.editor_open_class_method_description;
    }

    private void style(Button button, Node member, boolean selected, boolean locked) {
        int color = SyntaxColors.ACCENT;
        if (member instanceof VarDecl) color = SyntaxColors.TEAMMATE;
        if (member instanceof ConstructorDecl) color = SyntaxColors.AMBER;
        button.setSelected(selected);
        button.setEnabled(!locked);
        button.setAlpha(locked ? LOCKED_ALPHA : 1f);
        button.setTextColor(selected ? SyntaxColors.EDITOR_BG : color);
        button.setBackground(Ui.pressable(Ui.rounded(selected ? color : SyntaxColors.CHIP_BG,
                Ui.dp(getContext(), CHIP_RADIUS_DP))));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth) revealActive = true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!revealActive || memberScroller.getWidth() == 0) return;
        revealActive = false;
        if (activeButton == null) return;
        // Preserve manual scrolling unless the selected member would be clipped.
        int start = activeButton.getLeft();
        int end = activeButton.getRight() - memberScroller.getWidth();
        memberScroller.scrollTo(Math.min(start, Math.max(end, memberScroller.getScrollX())), 0);
    }
}
