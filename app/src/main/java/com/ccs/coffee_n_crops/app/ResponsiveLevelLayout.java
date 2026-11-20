package com.ccs.coffee_n_crops.app;

import android.app.Activity;
import android.content.res.Configuration;
import android.view.View;
import android.widget.LinearLayout;

import com.ccs.coffee_n_crops.R;

/** Places the farm beside the editor when there is room for both. */
final class ResponsiveLevelLayout {
    private static final int WIDE_DP = 600;

    private ResponsiveLevelLayout() { }

    static void apply(Activity activity) {
        Configuration config = activity.getResources().getConfiguration();
        if (config.screenWidthDp < WIDE_DP) return;
        LinearLayout body = activity.findViewById(R.id.level_body);
        View farm = activity.findViewById(R.id.farm_view);
        body.removeView(farm);
        LinearLayout code = new LinearLayout(activity);
        code.setOrientation(LinearLayout.VERTICAL);
        while (body.getChildCount() > 0) {
            View child = body.getChildAt(0);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
            body.removeViewAt(0);
            code.addView(child, params);
        }
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(farm, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f));
        body.addView(code, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3f));
    }
}
