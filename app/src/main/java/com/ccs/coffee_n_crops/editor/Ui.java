package com.ccs.coffee_n_crops.editor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** Small view helpers shared by the editor classes. */
final class Ui {
    private Ui() {
    }

    static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** A bounded touch ripple that follows the chip's shape. */
    static Drawable pressable(Drawable background) {
        return new RippleDrawable(ColorStateList.valueOf(0x45F4E8D2), background, null);
    }
}
