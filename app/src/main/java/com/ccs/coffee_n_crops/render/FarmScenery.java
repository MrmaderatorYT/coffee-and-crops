package com.ccs.coffee_n_crops.render;

/** Small scenic objects stay on the island's rim, clear of jobs and crop beds. */
final class FarmScenery {
    private final Scene3d scene;
    private final Scene3d shadows;
    private final int width;
    private final int height;

    static void add(FarmTheme theme, int width, int height, Scene3d scene, Scene3d shadows) {
        FarmScenery rim = new FarmScenery(width, height, scene, shadows);
        switch (theme) {
            case ORCHARD -> { }
            case TERRACE -> rim.terrace();
            case COAST -> rim.coast();
            case AUTUMN -> rim.autumn();
            case FROST -> rim.frost();
            case TWILIGHT -> rim.twilight();
        }
    }

    private FarmScenery(int width, int height, Scene3d scene, Scene3d shadows) {
        this.width = width;
        this.height = height;
        this.scene = scene;
        this.shadows = shadows;
    }

    private void terrace() {
        int plaster = 0xffd8b382;
        int stone = 0xffc29b6c;
        // The opening leaves a view of the pots behind the plaster gate.
        float x = .18f, y = -1.06f;
        shadows.shadow(x + .44f, y + .23f, .01f, .57f, .22f, 68);
        scene.box(x, y, 0, .26f, .36f, .68f, plaster);
        scene.box(x + .65f, y, 0, .26f, .36f, .68f, plaster);
        scene.box(x, y, .57f, .91f, .36f, .16f, stone);
        scene.box(x - .045f, y - .035f, .73f, 1, .42f, .075f, 0xffac694c);
        for (int side = 0; side < 2; side++) {
            float pillar = x + .65f * side;
            scene.box(pillar - .022f, y - .025f, 0, .304f, .414f, .095f, stone);
            scene.box(pillar - .014f, y - .017f, .52f, .288f, .394f, .05f, 0xffedc993);
        }
        pot(x + .46f, y + .22f, .104f);
        if (width >= 3) {
            float end = width - .48f;
            scene.box(end - .34f, -.99f, 0, .68f, .41f, .13f, stone);
            scene.face(0xff7ca99b, end - .27f, -.92f, .136f, end + .27f, -.92f, .136f,
                    end + .27f, -.65f, .136f, end - .27f, -.65f, .136f);
            pot(end - .61f, -.57f, .13f);
            pot(end + .22f, -.43f, .10f);
        }
        for (int i = 0; i < width; i += 2) stone(i + .43f, height + .16f, .125f, 0xffe4be86);
    }

    private void pot(float x, float y, float radius) {
        scene.tube(x, y, 0, x, y, .175f, radius * .65f, radius, 6, 0xffbc6e50);
        scene.tube(x, y, .143f, x, y, .184f, radius * 1.08f, radius * 1.08f, 6, 0xffdb8e60);
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2 / 5;
            float dx = (float) Math.cos(angle) * radius, dy = (float) Math.sin(angle) * radius;
            scene.face((i % 2 == 0) ? 0xff7c9d6e : 0xff58937b,
                    x, y, .181f, x + dx + .022f, y + dy, .29f,
                    x + dx * 1.45f, y + dy * 1.45f, .38f, x + dx, y + dy + .023f, .28f);
        }
    }

    private void coast() {
        float x = .47f, y = -.83f;
        shadows.shadow(x + .14f, y + .11f, .008f, .30f, .19f, 63);
        scene.tube(x, y, 0, x, y, .105f, .195f, .181f, 8, 0xffc9c3aa);
        for (int stripe = 0; stripe < 4; stripe++) {
            int color = (stripe % 2 == 0) ? 0xffe3dbbf : 0xffce7866;
            float z = .105f + stripe * .13f;
            float radius = .137f - stripe * .009f;
            scene.tube(x, y, z, x, y, z + .13f, radius, radius - .009f, 8, color);
        }
        scene.tube(x, y, .61f, x, y, .678f, .138f, .138f, 8, 0xff737f79);
        scene.tube(x, y, .676f, x, y, .808f, .081f, .08f, 6, 0xfff3d688);
        scene.tube(x, y, .804f, x, y, .924f, .136f, .013f, 6, 0xff627f7c);
        if (width >= 3) {
            float end = width - 1.06f;
            shadows.shadow(end + .42f, -.67f, .008f, .56f, .21f, 56);
            for (int board = 0; board < 5; board++) {
                scene.box(end, -.99f + board * .085f, .015f, .88f, .072f, .042f, 0xffad906c);
            }
            scene.box(end + .07f, -1.02f, .055f, .066f, .068f, .31f, 0xff816747);
            scene.box(end + .69f, -1.02f, .055f, .066f, .068f, .31f, 0xff816747);
            scene.tube(end + .10f, -.98f, .26f, end + .72f, -.98f, .28f, .019f, .019f, 5, 0xffe0cc9b);
            scene.gem(end + .49f, -.72f, .137f, .083f, .075f, .082f, 0xffdf946b);
        }
        for (int i = 0; i < width; i += 2) {
            stone(i + .31f, height + .18f, .107f, 0xffd4d6bd);
            stone(i + .49f, height + .21f, .083f, 0xffa4b3ac);
        }
    }

    private void autumn() {
        float x = .20f, y = -.90f;
        shadows.shadow(x + .48f, y + .21f, .01f, .59f, .25f, 70);
        scene.box(x, y, .18f, .8f, .41f, .09f, 0xff9b714a);
        scene.box(x, y + .36f, .27f, .8f, .055f, .10f, 0xffba8958);
        scene.box(x, y, .27f, .8f, .055f, .10f, 0xffba8958);
        for (int i = 0; i < 2; i++) {
            float axle = x + .15f + .54f * i;
            scene.tube(axle, y + .398f, .16f, axle, y + .45f, .16f, .152f, .152f, 10, 0xff695449);
            scene.tube(axle, y + .45f, .16f, axle, y + .47f, .16f, .105f, .105f, 9, 0xffaa8059);
        }
        pumpkin(x + .24f, y + .23f, .27f, .121f);
        pumpkin(x + .53f, y + .18f, .27f, .145f);
        if (width >= 3) {
            float end = width - .54f, edge = -.80f;
            shadows.shadow(end + .12f, edge + .15f, .01f, .37f, .19f, 70);
            scene.tube(end, edge, 0, end - .06f, edge + .03f, .69f, .069f, .041f, 6, 0xff9c694b);
            scene.gem(end - .17f, edge + .03f, .70f, .29f, .22f, .24f, 0xffbd6c4d);
            scene.gem(end + .13f, edge + .02f, .75f, .27f, .25f, .24f, 0xffe2a454);
            scene.gem(end - .02f, edge - .09f, .94f, .22f, .20f, .21f, 0xffd08450);
            pumpkin(end - .54f, edge + .15f, 0, .12f);
        }
        for (int i = 0; i < width; i++) {
            float px = i + .31f, py = height + .17f + (i % 2) * .072f;
            scene.face(i % 2 == 0 ? 0xffd28454 : 0xffe3aa68, px, py, .009f,
                    px + .08f, py - .03f, .014f, px + .17f, py + .017f, .019f,
                    px + .065f, py + .05f, .014f);
        }
    }

    private void pumpkin(float x, float y, float z, float size) {
        scene.gem(x, y, z + size * .75f, size, size * .87f, size * .76f, 0xffdf9151);
        scene.tube(x, y, z + size * 1.36f, x + .018f, y, z + size * 1.74f, .021f, .016f, 5, 0xff6b825a);
    }

    private void frost() {
        spruce(.47f, -.81f, 1);
        if (width >= 3) {
            spruce(width - .40f, -.83f, .78f);
            float x = width - 1.1f;
            shadows.shadow(x + .18f, -.66f, .01f, .40f, .15f, 55);
            for (int i = 0; i < 3; i++) {
                scene.tube(x, -.93f + i * .12f, .065f, x + .39f, -.93f + i * .12f, .065f,
                        .058f, .058f, 7, 0xff99816f);
            }
            scene.box(x - .025f, -.95f, .105f, .44f, .31f, .067f, 0xffe0e7e1);
        }
        for (int i = 0; i < width; i += 2) {
            stone(i + .52f, height + .20f, .132f, 0xffa2afb1);
            scene.gem(i + .51f, height + .20f, .072f, .115f, .064f, .041f, 0xffe3ecea);
        }
    }

    private void spruce(float x, float y, float scale) {
        shadows.shadow(x + .086f, y + .12f, .01f, .29f * scale, .20f * scale, 56);
        scene.tube(x, y, 0, x, y, .34f * scale, .048f * scale, .041f * scale, 6, 0xff827065);
        for (int tier = 0; tier < 3; tier++) {
            float bottom = (.23f + .19f * tier) * scale;
            float radius = (.265f - .058f * tier) * scale;
            scene.tube(x, y, bottom, x, y, bottom + .405f * scale, radius, 0, 6, 0xff4b867e);
            scene.tube(x, y, bottom + .084f * scale, x, y, bottom + .419f * scale,
                    radius * .83f, 0, 6, 0xffc8dad5);
        }
    }

    private void twilight() {
        float x = .19f, y = -.99f, e = x + .91f, s = y + .54f, roof = x + .455f;
        int frame = 0xff8caa99;
        shadows.shadow(x + .56f, y + .29f, .012f, .59f, .25f, 69);
        scene.box(x, y, 0, .91f, .54f, .073f, 0xffbd9977);
        scene.box(x + .12f, s - .051f, .12f, .69f, .018f, .24f, 0xffe3b877);
        scene.face(0xc381a7ac, x, s, .074f, e, s, .074f, e, s, .51f, x, s, .51f);
        scene.face(0xcc7f99ac, x, y, .074f, x, s, .074f, x, s, .51f, x, y, .51f);
        scene.face(0xcc72929f, e, s, .074f, e, y, .074f, e, y, .51f, e, s, .51f);
        scene.face(0xee8baca3, x, y, .51f, x, s, .51f, roof, s, .79f, roof, y, .79f);
        scene.face(0xee7197a2, roof, y, .79f, roof, s, .79f, e, s, .51f, e, y, .51f);
        for (int pane = 0; pane < 4; pane++) {
            scene.box(x + pane * .298f, s, .07f, .018f, .018f, .44f, frame);
        }
        scene.tube(x, s, .51f, roof, s, .79f, .016f, .016f, 5, frame);
        scene.tube(roof, s, .79f, e, s, .51f, .016f, .016f, 5, frame);
        scene.tube(roof, y, .79f, roof, s, .79f, .02f, .02f, 5, 0xffaad0b4);
        if (width >= 3) lantern(width - .47f, -.74f, .57f);
        for (int i = 1; i < width; i += 3) lantern(i - .36f, height + .19f, .235f);
    }

    private void lantern(float x, float y, float height) {
        shadows.disk(x, y, .014f, .205f, .15f, 0x48f5a766);
        scene.tube(x, y, 0, x, y, height, .026f, .026f, 5, 0xff695e68);
        scene.gem(x, y, height, .075f, .063f, .09f, 0xfffbca77);
        scene.tube(x, y, height + .071f, x, y, height + .116f, .092f, .019f, 5, 0xff756770);
    }

    private void stone(float x, float y, float radius, int color) {
        scene.gem(x, y, .035f, radius, radius * .54f, radius * .43f, color);
    }
}
