package com.ccs.coffee_n_crops.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.ccs.coffee_n_crops.lang.ast.ExprSlot;
import com.ccs.coffee_n_crops.lang.print.Token;
import com.ccs.coffee_n_crops.R;

import java.util.ArrayList;
import java.util.List;

/** Binds {@link CodeRow}s to views: indentation, syntax colours, tappable slots, debugger highlight. */
public final class BlockAdapter extends RecyclerView.Adapter<BlockAdapter.Holder> {

    public interface Listener {
        void onRowTapped(CodeRow row);

        void onSlotTapped(ExprSlot slot);

        default void onFoldTapped(CodeRow row) { }
    }

    private static final int NO_NODE = -1;
    private static final float CODE_SP = 14f;

    private final Listener listener;
    private List<CodeRow> rows = new ArrayList<>();
    private ExprSlot selectedSlot;
    private int highlightNodeId = NO_NODE;
    private int teammateNodeId = NO_NODE;
    private boolean highlightIsError;

    public BlockAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<CodeRow> rows, ExprSlot selectedSlot) {
        this.rows = rows;
        this.selectedSlot = selectedSlot;
        notifyDataSetChanged();
    }

    /**
     * Marks the row of the given node as executing (or failed).
     *
     * @return the row's position, or -1 if the node has no row
     */
    public int setHighlight(int nodeId, boolean error) {
        int old = positionOf(highlightNodeId);
        highlightNodeId = nodeId;
        highlightIsError = error;
        int now = positionOf(nodeId);
        if (old >= 0) notifyItemChanged(old);
        if (now >= 0) notifyItemChanged(now);
        return now;
    }

    public void clearHighlight() {
        setHighlight(NO_NODE, false);
        setTeammateHighlight(NO_NODE);
    }

    int caretPosition() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind == CodeRow.Kind.CARET) return i;
        }
        return -1;
    }

    int setTeammateHighlight(int nodeId) {
        int old = positionOf(teammateNodeId);
        teammateNodeId = nodeId;
        int now = positionOf(nodeId);
        if (old >= 0) notifyItemChanged(old);
        if (now >= 0) notifyItemChanged(now);
        return now;
    }

    int positionOf(int nodeId) {
        if (nodeId == NO_NODE) return -1;
        for (int i = 0; i < rows.size(); i++) {
            if (showsHighlight(rows.get(i), nodeId)) return i;
        }
        return -1;
    }

    /** A node is highlighted on the line that shows it; a compound statement, on its header only. */
    private static boolean showsHighlight(CodeRow row, int nodeId) {
        return row.showsHighlight(nodeId);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        RowLayout layout = new RowLayout(parent.getContext());
        layout.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(layout);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        CodeRow row = rows.get(position);
        RowLayout layout = holder.layout;
        Context context = layout.getContext();
        layout.removeAllViews();
        layout.setDepth(row.depth);

        boolean highlighted = showsHighlight(row, highlightNodeId);
        boolean teammate = showsHighlight(row, teammateNodeId);
        int background = row.kind == CodeRow.Kind.FRAME_OPEN ? SyntaxColors.PALETTE_BG : SyntaxColors.EDITOR_BG;
        int marker = 0;
        if (highlighted) {
            background = highlightIsError ? SyntaxColors.ROW_ERROR : SyntaxColors.ROW_RUNNING;
            marker = highlightIsError ? SyntaxColors.ERROR : SyntaxColors.ACCENT;
        } else if (teammate) {
            background = SyntaxColors.ROW_TEAMMATE;
            marker = SyntaxColors.TEAMMATE;
        }
        layout.setBackgroundColor(background);
        layout.setMarkerColor(marker);
        layout.setOnClickListener(v -> listener.onRowTapped(row));

        if (row.kind == CodeRow.Kind.CARET) {
            layout.addView(text(context, "▸ ", SyntaxColors.AMBER));
            layout.addView(text(context, context.getString(R.string.editor_caret), SyntaxColors.TEXT_DIM));
            return;
        }

        if (row.foldMember != null) {
            TextView disclosure = text(context, row.folded ? "▸" : "▾", SyntaxColors.ACCENT);
            int padding = Ui.dp(context, 8);
            disclosure.setPadding(padding, padding, padding, padding);
            disclosure.setContentDescription(context.getString(row.folded ? R.string.editor_unfold : R.string.editor_fold));
            disclosure.setOnClickListener(v -> listener.onFoldTapped(row));
            layout.addView(disclosure);
        }

        for (Token token : row.tokens) {
            TextView view = text(context, token.text, SyntaxColors.of(token.kind));
            if (token.slot != null) {
                ExprSlot slot = token.slot;
                int padH = Ui.dp(context, 5);
                int padV = Ui.dp(context, 3);
                view.setPadding(padH, padV, padH, padV);
                GradientDrawable chip = Ui.rounded(SyntaxColors.CHIP_BG, Ui.dp(context, 6));
                if (slot == selectedSlot) chip.setStroke(Ui.dp(context, 2), SyntaxColors.ACCENT);
                view.setBackground(Ui.pressable(chip));
                view.setOnClickListener(v -> listener.onSlotTapped(slot));
            }
            layout.addView(view);
        }
    }

    private static TextView text(Context context, String text, int color) {
        TextView view = new TextView(context);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(CODE_SP);
        view.setTextColor(color);
        view.setText(text);
        return view;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final RowLayout layout;

        Holder(RowLayout layout) {
            super(layout);
            this.layout = layout;
        }
    }

    /**
     * A code line. Indents by nesting depth, draws one vertical guide per enclosing block, and
     * soft-wraps: tokens that do not fit continue on the next visual line, indented further,
     * so a long statement is never cut off at the screen edge.
     */
    static final class RowLayout extends ViewGroup {
        private final Paint guide = new Paint();
        private final Paint marker = new Paint();
        private final int indent;
        private final int margin;
        private final int padVertical;
        private final int minHeight;
        private int depth;

        RowLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            indent = Ui.dp(context, 16);
            margin = Ui.dp(context, 14);
            padVertical = Ui.dp(context, 4);
            minHeight = Ui.dp(context, 38);
            guide.setColor(SyntaxColors.GUIDE);
            guide.setStrokeWidth(Ui.dp(context, 1));
            marker.setStrokeWidth(Ui.dp(context, 4));
        }

        void setDepth(int depth) {
            this.depth = depth;
            requestLayout();
            invalidate();
        }

        void setMarkerColor(int color) {
            marker.setColor(color);
            invalidate();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            int maxTokenWidth = Math.max(1, width - margin - codeStart(width) - indent * 2);
            int tokenSpec = MeasureSpec.makeMeasureSpec(maxTokenWidth, MeasureSpec.AT_MOST);
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(tokenSpec, unspecified);
            setMeasuredDimension(width, flow(width, false));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            flow(r - l, true);
        }

        /**
         * Places the tokens left to right, wrapping where they would pass the right edge.
         *
         * @param place false to only compute the height
         * @return the height the row needs
         */
        private int flow(int width, boolean place) {
            int start = codeStart(width);
            int continuation = start + 2 * indent; // wrapped parts sit deeper than the line's own code
            int right = width - margin;
            int count = getChildCount();

            int y = padVertical;
            int lineStart = 0;
            while (lineStart < count) {
                // Find how many tokens fit on this visual line; always take at least one.
                int x = lineStart == 0 ? start : continuation;
                int lineEnd = lineStart;
                int lineHeight = 0;
                int cursor = x;
                while (lineEnd < count) {
                    View child = getChildAt(lineEnd);
                    if (lineEnd > lineStart && cursor + child.getMeasuredWidth() > right) break;
                    cursor += child.getMeasuredWidth();
                    lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
                    lineEnd++;
                }
                if (place) {
                    for (int i = lineStart; i < lineEnd; i++) {
                        View child = getChildAt(i);
                        int top = y + (lineHeight - child.getMeasuredHeight()) / 2;
                        child.layout(x, top, x + child.getMeasuredWidth(), top + child.getMeasuredHeight());
                        x += child.getMeasuredWidth();
                    }
                }
                y += lineHeight;
                lineStart = lineEnd;
            }

            int content = y + padVertical;
            if (content >= minHeight) return content;
            if (place) {
                // A short row: centre its single line in the minimum height.
                int shift = (minHeight - content) / 2;
                for (int i = 0; i < count; i++) getChildAt(i).offsetTopAndBottom(shift);
            }
            return minHeight;
        }

        private int codeStart(int width) {
            return Math.min(margin + depth * indent, Math.max(margin, width / 3));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float markerEdge = marker.getStrokeWidth() / 2;
            canvas.drawLine(markerEdge, 0, markerEdge, getHeight(), marker);
            for (int d = 0; d < depth; d++) {
                float x = margin + d * indent + guide.getStrokeWidth();
                if (x >= codeStart(getWidth())) break;
                canvas.drawLine(x, 0, x, getHeight(), guide);
            }
        }
    }
}
