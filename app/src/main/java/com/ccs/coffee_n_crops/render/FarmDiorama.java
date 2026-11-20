package com.ccs.coffee_n_crops.render;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.farm.TileState;
import com.ccs.coffee_n_crops.progress.LevelDef;

/** Original low-poly garden with reusable crop meshes and environment-specific scenery. */
final class FarmDiorama {
    private static final int SOIL = 0xff996047;
    private static final int WET_SOIL = 0xff523e43;
    private static final int WHEAT = 0xfff6bc63;
    private static final int LEAF = 0xff87bb62;
    private static final int BED_EDGE = 0xffa47655;
    private static final int FARMER_DISK = 0x4064d4bd;
    private static final int FARMER_RING = 0xffc3f2b0;
    private static final int PARTNER_DISK = 0x408abfe9;
    private static final int PARTNER_RING = 0xffa0d6ff;
    private static final float BED_HEIGHT = .04f;

    interface Tiles { TileState at(int x, int y); }

    private final Scene3d island = new Scene3d();
    private final Scene3d tiles = new Scene3d();
    private final Scene3d furrows = new Scene3d();
    private final Scene3d scenery = new Scene3d();
    private final Scene3d castShadows = new Scene3d();
    private final Scene3d marker = new Scene3d();
    private final Scene3d farmer = new Scene3d();
    private final FarmhandModel model = new FarmhandModel();
    private int width;
    private int height;
    private FarmTheme theme = FarmTheme.ORCHARD;
    private TileState[] tileStates = new TileState[0];

    /** Reads the same tile state the running program sees, with the original orchard scenery. */
    void sync(int width, int height, Tiles source) {
        sync(width, height, source, FarmTheme.ORCHARD);
    }

    void sync(int width, int height, Tiles source, FarmTheme theme) {
        boolean resized = this.width != width || this.height != height;
        boolean themeChanged = this.theme != theme;
        if (resized) {
            this.width = width;
            this.height = height;
            tileStates = new TileState[width * height];
        }
        if (resized || themeChanged) {
            this.theme = theme;
            buildIsland();
        }
        boolean changed = resized || themeChanged;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int at = y * width + x;
                TileState state = source.at(x, y);
                if (tileStates[at] != state) {
                    tileStates[at] = state;
                    changed = true;
                }
            }
        }
        if (changed) buildGarden();
    }

    void draw(Scene3d.Target target, Scene3d.Camera camera, float x, float y, Direction facing,
              Farmhand.Action action, float stride, float phase, float movement, LevelDef.Finish finish) {
        if (camera.scale == 0) return;
        drawLand(target, camera, finish);
        addFarmhand(x, y, facing, action, stride, phase, movement, false);
        drawPeople(target, camera);
    }

    void draw(Scene3d.Target target, Scene3d.Camera camera, FarmMotion first, Farmhand.Action firstAction,
              FarmMotion second, Farmhand.Action secondAction, LevelDef.Finish finish) {
        if (camera.scale == 0) return;
        drawLand(target, camera, finish);
        addFarmhand(first.x, first.y, first.facing, firstAction, first.stride, first.phase, first.movement, false);
        if (second != null) {
            addFarmhand(second.x, second.y, second.facing, secondAction,
                    second.stride, second.phase, second.movement, true);
        }
        drawPeople(target, camera);
    }

    private void drawLand(Scene3d.Target target, Scene3d.Camera camera, LevelDef.Finish finish) {
        island.draw(target, camera);
        tiles.draw(target, camera);
        furrows.draw(target, camera);
        castShadows.draw(target, camera);
        marker.clear();
        farmer.clear();
        if (finish != null) {
            ring(marker, finish.x() + .5f, finish.y() + .5f, .066f, .37f, .335f, 0xffffc870);
            drawFlag(finish.x() + .5f, finish.y() + .5f);
        }
    }

    private void addFarmhand(float x, float y, Direction facing, Farmhand.Action action, float stride,
                             float phase, float movement, boolean partner) {
        marker.disk(x, y, .061f, .32f, .32f, partner ? PARTNER_DISK : FARMER_DISK);
        ring(marker, x, y, .066f, .326f, .302f, partner ? PARTNER_RING : FARMER_RING);
        model.draw(farmer, x, y, facing, action, stride, phase, movement, partner);
    }

    private void drawPeople(Scene3d.Target target, Scene3d.Camera camera) {
        marker.draw(target, camera);
        scenery.drawWith(farmer, target, camera);
    }

    private void drawFlag(float x, float y) {
        farmer.tube(x, y, .05f, x, y, .82f, .022f, .016f, 6, 0xfff0d6a8);
        farmer.gem(x, y, .83f, .027f, .027f, .031f, 0xffffde92);
        farmer.face(0xffffa845, x, y, .79f, x + .34f, y, .71f, x, y, .58f);
    }

    private void buildIsland() {
        island.clear();
        float[] top = {- .64f, -.90f, -.34f, -1.24f, width + .29f, -1.24f,
                width + .61f, -.91f, width + .61f, height + .12f,
                width + .31f, height + .44f, -.32f, height + .44f, -.64f, height + .09f};
        float cx = width * .5f, cy = (height - 1) * .5f;
        float[] shoulder = rim(top, cx, cy, .995f, -.18f);
        float[] seam = rim(top, cx, cy, .97f, -.42f);
        float[] base = rim(top, cx, cy, .94f, -.62f);
        float[] surface = rim(top, cx, cy, 1, 0);
        FarmTheme.Land land = theme.land;
        band(island, base, seam, land.base());
        band(island, seam, shoulder, land.middle());
        band(island, shoulder, surface, land.rim());
        island.face(land.surface(), surface);
    }

    private static float[] rim(float[] shape, float cx, float cy, float ratio, float z) {
        float[] xyz = new float[shape.length / 2 * 3];
        for (int i = 0; i < shape.length / 2; i++) {
            xyz[i * 3] = cx + (shape[i * 2] - cx) * ratio;
            xyz[i * 3 + 1] = cy + (shape[i * 2 + 1] - cy) * ratio;
            xyz[i * 3 + 2] = z;
        }
        return xyz;
    }

    private static void band(Scene3d scene, float[] low, float[] high, int color) {
        for (int i = 0; i < low.length; i += 3) {
            int j = (i + 3) % low.length;
            scene.face(color, true, low[i], low[i + 1], low[i + 2], low[j], low[j + 1], low[j + 2],
                    high[j], high[j + 1], high[j + 2], high[i], high[i + 1], high[i + 2]);
        }
    }

    private void buildGarden() {
        tiles.clear();
        furrows.clear();
        scenery.clear();
        castShadows.clear();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileState state = tileStates[y * width + x];
                boolean turf = state == TileState.GRASS;
                int alternate = (x + y) & 1;
                int ground = turf ? (alternate == 0 ? theme.land.turfLight() : theme.land.turfDark()) : BED_EDGE;
                tiles.box(x + .025f, y + .025f, .003f, .95f, .95f, .013f, ground);
                if (turf) {
                    turfDetails(x, y);
                } else {
                    boolean wet = state == TileState.WHEAT_GROWING_WATERED;
                    tiles.box(x + .078f, y + .078f, .017f, .844f, .844f, BED_HEIGHT - .017f,
                            wet ? WET_SOIL : SOIL);
                    plotDetails(x, y, state, wet);
                }
            }
        }
        if (theme == FarmTheme.ORCHARD) orchardDetails();
        else FarmScenery.add(theme, width, height, scenery, castShadows);
    }

    private void plotDetails(int x, int y, TileState state, boolean wet) {
        for (int lane = 0; lane < 3; lane++) {
            float yLane = y + .25f + lane * .25f;
            int furrow = wet ? 0xff554446 : 0xff85523e;
            furrows.face(furrow, x + .145f, yLane - .031f, .044f, x + .855f, yLane - .031f, .044f,
                    x + .855f, yLane + .029f, .044f, x + .145f, yLane + .029f, .044f);
            for (int column = 0; column < 3; column++) {
                int seed = (x * 13 + y * 17 + lane * 7 + column * 5) & 7;
                float px = x + .25f + .25f * column + (seed - 3) * .011f;
                float py = yLane + (3 - seed) * .009f;
                if (state == TileState.WHEAT_RIPE) wheat(px, py, seed);
                else if (state.isGrowing()) sprout(px, py, seed, wet);
                else if ((seed & 1) == 0) {
                    scenery.tube(px, py, BED_HEIGHT, px + .012f, py, .115f, .018f, .011f, 4, 0xffba8753);
                }
            }
        }
        if (wet) {
            castShadows.disk(x + .62f, y + .72f, .048f, .143f, .066f, 0x874c9d9e);
            castShadows.disk(x + .60f, y + .71f, .051f, .058f, .013f, 0x9598c7bf);
        }
    }

    private void wheat(float x, float y, int seed) {
        float height = .30f + seed * .013f;
        float tip = BED_HEIGHT + height;
        int gold = (seed % 3 == 0) ? 0xffe2a651 : WHEAT;
        scenery.tube(x, y, BED_HEIGHT, x + .025f, y - .012f, tip - .03f, .013f, .009f, 4, 0xffb99b51);
        scenery.tube(x, y - .01f, tip - .11f, x + .033f, y - .022f, tip + .035f, .035f, .016f, 5, gold);
        leaf(x, y, BED_HEIGHT + .10f, .082f, .023f, .082f, 0xffc6a157);
        leaf(x, y, BED_HEIGHT + .15f, -.067f, -.015f, .061f, 0xffe5b55f);
        if ((seed & 1) == 0) castShadows.shadow(x + .056f, y + .044f, .052f, .087f, .047f, 47);
    }

    private void sprout(float x, float y, int seed, boolean wet) {
        float z = .105f + .01f * (seed % 4);
        int bright = wet ? 0xff65b58a : LEAF;
        scenery.tube(x, y, BED_HEIGHT, x, y, z + .058f, .015f, .008f, 4, 0xff588449);
        leaf(x, y, z - .027f, .095f, .013f, .079f, bright);
        leaf(x, y, z - .04f, -.074f, -.038f, .057f, 0xff498e62);
        leaf(x, y, z, -.027f, .051f, .073f, bright);
    }

    private void leaf(float x, float y, float z, float dx, float dy, float dz, int color) {
        float mx = x + dx * .56f, my = y + dy * .56f, mz = z + dz * .57f;
        scenery.face(color, x, y, z, mx - dy * .47f, my + dx * .47f, mz,
                x + dx, y + dy, z + dz, mx, my, mz + .015f);
        scenery.face(color ^ 0x00060509, x, y, z, mx, my, mz + .015f,
                x + dx, y + dy, z + dz, mx + dy * .47f, my - dx * .47f, mz);
    }

    private void turfDetails(int x, int y) {
        for (int i = 0; i < 3; i++) {
            float px = x + .24f + i * .22f, py = y + .26f + ((x * 3 + y + i * 3) & 3) * .13f;
            if (theme == FarmTheme.FROST) furrows.disk(px, py, .017f, .055f, .038f, 0xcceaf2ef);
            else leaf(px, py, .02f, .06f, .008f, .05f, theme.land.rim());
        }
    }

    private void orchardDetails() {
        shed(.18f, -1.09f);
        if (width >= 3) {
            float x = width - .39f;
            tree(x, -.79f);
            barrels(x - .68f, -.72f);
        }
        for (int x = 0; x < width; x++) {
            if (x % 3 == 1) {
                float z = -.32f + (x % 2) * .038f;
                scenery.gem(x + .34f, height + .40f, z, .18f, .065f, .081f, 0xffad8567);
            }
            if (x % 2 == 0) {
                float tx = x + .42f;
                leaf(tx, height + .18f, .008f, .071f, .08f, .095f, 0xff97a865);
                leaf(tx, height + .18f, .008f, -.055f, .045f, .069f, 0xff63814f);
            }
        }
        if (width >= 5) {
            float x = width * .43f;
            scenery.box(x, -.79f, 0, .5f, .27f, .125f, 0xff987356);
            scenery.box(x + .03f, -.76f, .12f, .44f, .21f, .035f, 0xff6c593d);
            for (int i = 0; i < 4; i++) sprout(x + .11f + i * .10f, -.66f, i, false);
            castShadows.shadow(x + .21f, -.63f, .009f, .34f, .18f, 62);
        }
    }

    private void shed(float x, float y) {
        float w = .99f, d = .61f, e = x + w, s = y + d;
        float gable = x + w * .54f;
        castShadows.shadow(x + .55f, y + .40f, .01f, .76f, .40f, 65);
        scenery.box(x, y, 0, w, d, .56f, 0xffd3b68c);
        scenery.face(0xffe0c09a, x, s, .56f, e, s, .56f, gable, s, .88f);
        scenery.face(0xffd3b68c, x, y, .56f, e, y, .56f, gable, y, .88f);
        scenery.face(0xff437667, x - .085f, y - .055f, .56f, gable, y - .055f, .91f,
                gable, s + .084f, .91f, x - .085f, s + .084f, .56f);
        scenery.face(0xff548878, gable, y - .055f, .91f, e + .093f, y - .055f, .56f,
                e + .093f, s + .084f, .56f, gable, s + .084f, .91f);
        scenery.tube(gable, y - .061f, .915f, gable, s + .088f, .915f, .024f, .024f, 5, 0xff8da686);
        scenery.box(x + .12f, s, .02f, .28f, .027f, .45f, 0xff815340);
        scenery.box(x + .08f, s + .018f, .005f, .37f, .13f, .055f, 0xffb69165);
        scenery.box(x + .55f, s, .24f, .29f, .027f, .21f, 0xff715b4e);
        scenery.box(x + .576f, s + .026f, .265f, .235f, .012f, .16f, 0xffead49e);
        scenery.box(x + .68f, s + .035f, .265f, .019f, .012f, .16f, 0xffa58464);
        for (int i = 1; i < 4; i++) {
            float yr = y + d * i / 4f;
            scenery.face(0xffb59576, e + .001f, yr, .031f, e + .001f, yr + .019f, .031f,
                    e + .001f, yr + .019f, .54f, e + .001f, yr, .54f);
        }
    }

    private void tree(float x, float y) {
        castShadows.shadow(x + .13f, y + .13f, .011f, .39f, .24f, 70);
        scenery.tube(x, y, 0, x -.04f, y + .02f, .71f, .077f, .043f, 6, 0xff936649);
        scenery.tube(x, y, .36f, x + .19f, y + .04f, .67f, .047f, .026f, 5, 0xff8e6244);
        scenery.gem(x - .13f, y + .04f, .71f, .27f, .22f, .27f, 0xff648957);
        scenery.gem(x + .18f, y + .025f, .77f, .28f, .23f, .25f, 0xff81a365);
        scenery.gem(x - .018f, y - .07f, .94f, .26f, .22f, .24f, 0xff95aa66);
        scenery.gem(x + .06f, y + .21f, .84f, .055f, .051f, .053f, 0xffdc804e);
        scenery.gem(x + .36f, y + .11f, .77f, .049f, .048f, .049f, 0xffe19b54);
        scenery.gem(x - .20f, y + .218f, .73f, .043f, .045f, .047f, 0xffda9053);
    }

    private void barrels(float x, float y) {
        castShadows.shadow(x + .04f, y + .045f, .01f, .25f, .20f, 50);
        scenery.tube(x, y, 0, x, y, .29f, .125f, .117f, 8, 0xff936548);
        scenery.tube(x, y, .07f, x, y, .105f, .127f, .126f, 8, 0xff425b58);
        scenery.tube(x, y, .21f, x, y, .243f, .124f, .123f, 8, 0xff425b58);
        scenery.tube(x -.25f, y + .11f, 0, x -.25f, y + .11f, .195f, .08f, .10f, 7, 0xffcc935e);
    }

    private static void ring(Scene3d scene, float x, float y, float z, float outer, float inner, int color) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            double a = i * 2 * Math.PI / segments, b = (i + 1) * 2 * Math.PI / segments;
            float ax = (float) Math.cos(a), ay = (float) Math.sin(a);
            float bx = (float) Math.cos(b), by = (float) Math.sin(b);
            scene.face(color, x + ax * outer, y + ay * outer, z, x + bx * outer, y + by * outer, z,
                    x + bx * inner, y + by * inner, z, x + ax * inner, y + ay * inner, z);
        }
    }
}
