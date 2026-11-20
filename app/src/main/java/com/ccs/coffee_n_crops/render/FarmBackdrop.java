package com.ccs.coffee_n_crops.render;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Builds the stationary horizon once per viewport and environment. */
final class FarmBackdrop {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path distant = new Path();
    private final Path near = new Path();
    private final Path detail = new Path();
    private final Path air = new Path();
    private Shader sky;
    private Shader glow;
    private FarmTheme theme;
    private int width;
    private int height;
    private float lightX;
    private float lightY;
    private float lightRadius;
    private int detailColor;
    private int airColor;

    void draw(Canvas canvas, FarmTheme nextTheme) {
        if (nextTheme != theme || width != canvas.getWidth() || height != canvas.getHeight()) {
            theme = nextTheme;
            width = canvas.getWidth();
            height = canvas.getHeight();
            build();
        }

        paint.setAlpha(255);
        paint.setShader(sky);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(glow);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        paint.setColor(theme.sky.light());
        canvas.drawCircle(lightX, lightY, lightRadius, paint);
        paint.setColor(airColor);
        canvas.drawPath(air, paint);
        paint.setColor(theme.sky.distant());
        canvas.drawPath(distant, paint);
        paint.setColor(theme.sky.near());
        canvas.drawPath(near, paint);
        paint.setColor(detailColor);
        canvas.drawPath(detail, paint);
    }

    private void build() {
        FarmTheme.Sky colors = theme.sky;
        sky = new LinearGradient(0, 0, 0, Math.max(1, height),
                new int[]{colors.zenith(), colors.horizon(), colors.ground()},
                new float[]{0, .54f, 1}, Shader.TileMode.CLAMP);
        distant.rewind();
        near.rewind();
        detail.rewind();
        air.rewind();
        switch (theme) {
            case ORCHARD -> orchard();
            case TERRACE -> terrace();
            case COAST -> coast();
            case AUTUMN -> autumn();
            case FROST -> frost();
            case TWILIGHT -> twilight();
        }
        glow = new RadialGradient(lightX, lightY, Math.max(1, height * .65f),
                withAlpha(colors.light(), 100), withAlpha(colors.light(), 0), Shader.TileMode.CLAMP);
    }

    private void orchard() {
        light(.22f, .33f, .105f);
        hills(distant, .53f, .32f, .63f, .40f, .53f);
        hills(near, .75f, .50f, .76f, .65f, .65f);
        detailColor = 0x397e9972;
        // One sweep of light over each distant pasture.
        face(detail, 0, .88f, .16f, .68f, .37f, .73f, .20f, .77f);
        face(detail, .61f, .86f, .83f, .68f, 1, .68f, 1, .72f, .85f, .73f);
        clouds(0x65e2e4bd);
    }

    private void terrace() {
        light(.77f, .31f, .14f);
        ridge(distant, 0, .63f, .15f, .37f, .28f, .37f, .38f, .55f,
                .57f, .30f, .69f, .30f, .82f, .58f, 1, .48f);
        ridge(near, 0, .72f, .20f, .49f, .33f, .49f, .46f, .66f,
                .66f, .60f, .82f, .74f, 1, .67f);
        detailColor = 0x4ae4b787;
        face(detail, .16f, .54f, .33f, .54f, .38f, .60f, .11f, .60f);
        face(detail, .19f, .64f, .41f, .64f, .46f, .68f, .15f, .68f);
        clouds(0x48f0d4b4);
    }

    private void coast() {
        light(.74f, .28f, .092f);
        ridge(distant, 0, .56f, .19f, .54f, .34f, .43f, .45f, .48f,
                .61f, .56f, .82f, .56f, 1, .48f);
        ridge(near, 0, .67f, .14f, .57f, .30f, .57f, .37f, .69f, .86f, .70f,
                .94f, .61f, 1, .60f);
        detailColor = 0x7ab1d7c9;
        for (int row = 0; row < 4; row++) {
            float y = .73f + row * .07f;
            for (int section = 0; section < 4; section++) {
                float x = .02f + section * .25f + (row % 2) * .09f;
                face(detail, x, y, x + .09f, y - .012f, x + .16f, y, x + .07f, y + .006f);
            }
        }
        clouds(0x62d2e4da);
    }

    private void autumn() {
        light(.32f, .29f, .16f);
        hills(distant, .70f, .36f, .62f, .24f, .43f);
        hills(near, .79f, .53f, .81f, .49f, .54f);
        detailColor = 0x4cbf8065;
        face(detail, .03f, .79f, .14f, .68f, .31f, .68f, .19f, .72f, .06f, .82f);
        face(detail, .59f, .82f, .77f, .61f, 1, .58f, 1, .62f, .80f, .67f);
        clouds(0x57f2d7aa);
    }

    private void frost() {
        light(.82f, .28f, .10f);
        ridge(distant, 0, .75f, .24f, .26f, .37f, .48f, .53f, .56f,
                .67f, .31f, .82f, .58f, .90f, .44f, 1, .57f);
        ridge(near, 0, .75f, .22f, .63f, .45f, .70f, .65f, .63f, 1, .72f);
        detailColor = 0xc5d4e1e5;
        face(detail, .11f, .53f, .24f, .26f, .36f, .47f, .29f, .42f, .27f, .48f, .21f, .42f);
        face(detail, .58f, .47f, .67f, .31f, .77f, .50f, .68f, .43f, .64f, .47f, .62f, .43f);
        flecks(0xb5edf4ef, 25, .023f, 3);
    }

    private void twilight() {
        light(.72f, .24f, .103f);
        ridge(distant, 0, .61f, .15f, .42f, .32f, .52f, .47f, .38f,
                .58f, .47f, .81f, .57f, 1, .46f);
        hills(near, .82f, .57f, .77f, .54f, .68f);
        detailColor = 0x408b7b91;
        face(detail, 0, .90f, .17f, .72f, .37f, .72f, .30f, .77f, .14f, .81f);
        flecks(0xffd8cfe0, 23, .013f, 5);
    }

    private void light(float x, float y, float radius) {
        lightX = x * width;
        lightY = y * height;
        lightRadius = radius * height;
    }

    private void clouds(int color) {
        airColor = color;
        face(air, .08f, .37f, .14f, .343f, .32f, .343f, .27f, .37f);
        face(air, .48f, .21f, .56f, .185f, .70f, .185f, .65f, .21f);
        face(air, .75f, .38f, .82f, .348f, .95f, .348f, .89f, .38f);
    }

    private void flecks(int color, int count, float scale, int seed) {
        airColor = color;
        for (int i = 0; i < count; i++) {
            float x = .035f + ((i * 47 + seed) % 99) * .0094f;
            float y = .063f + ((i * 23 + seed * 3) % 41) * .0115f;
            float radius = (1 + i % 3) * scale * height / 3.5f;
            if (Math.hypot(x * width - lightX, y * height - lightY) > lightRadius + radius) {
                air.addCircle(x * width, y * height, radius, Path.Direction.CW);
            }
        }
    }

    private void hills(Path shape, float left, float first, float middle, float second, float right) {
        shape.moveTo(0, height * left);
        shape.cubicTo(width * .17f, height * first, width * .28f, height * first, width * .48f, height * middle);
        shape.cubicTo(width * .66f, height * second, width * .85f, height * second, width, height * right);
        bottom(shape);
    }

    private void ridge(Path shape, float... points) {
        append(shape, points);
        bottom(shape);
    }

    private void face(Path shape, float... points) {
        append(shape, points);
        shape.close();
    }

    private void append(Path shape, float[] points) {
        shape.moveTo(points[0] * width, points[1] * height);
        for (int i = 2; i < points.length; i += 2) shape.lineTo(points[i] * width, points[i + 1] * height);
    }

    private void bottom(Path shape) {
        shape.lineTo(width, height);
        shape.lineTo(0, height);
        shape.close();
    }

    private static int withAlpha(int color, int alpha) {
        return color & 0x00ffffff | alpha << 24;
    }
}
