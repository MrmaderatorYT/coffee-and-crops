package com.ccs.coffee_n_crops.render;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.Farmhand;

/** An original low-poly gardener, with articulated limbs and tools in tile-sized world units. */
final class FarmhandModel {
    private static final int LINEN = 0xFFF5DBA8;
    private static final int LINEN_CUFF = 0xFFDBB985;
    private static final int APRON = 0xFFAE5840;
    private static final int APRON_EDGE = 0xFF873F2D;
    private static final int POCKET = 0xFFC46D4C;
    private static final int BUTTON = 0xFFFFCA7C;
    private static final int SCARF = 0xFF809773;
    private static final int TROUSER = 0xFF45554B;
    private static final int SOLE = 0xFF392822;
    private static final int BOOT = 0xFF755038;
    private static final int SKIN = 0xFFDAA171;
    private static final int SKIN_LIGHT = 0xFFE9B583;
    private static final int HAIR = 0xFF50372B;
    private static final int EYE = 0xFF302920;
    private static final int STRAW = 0xFFE5B961;
    private static final int STRAW_EDGE = 0xFFC59147;
    private static final int STRAW_TOP = 0xFFF2CD79;
    private static final int HAT_RIBBON = 0xFF7A5035;
    private static final int PARTNER_APRON = 0xFF397E91;
    private static final int PARTNER_APRON_EDGE = 0xFF285262;
    private static final int PARTNER_POCKET = 0xFF65A9BC;
    private static final int PARTNER_RIBBON = 0xFF4A5AA8;
    private static final int CAN = 0xFF5B9290;
    private static final int CAN_SHADE = 0xFF345C5A;
    private static final int CAN_TRIM = 0xFFA8C4AA;
    private static final int WATER = 0xFF9BDAD6;
    private static final int TOOL_HANDLE = 0xFF9B633D;
    private static final int TOOL_METAL = 0xFFBAC9BA;
    private static final float PI = (float) Math.PI;
    private static final float HIP_HEIGHT = .357f;
    private static final float SHOULDER_HEIGHT = .587f;
    private static final float ARM_OUT = .136f;

    private Scene3d scene;
    private float centerX;
    private float centerY;
    private int forwardX;
    private int forwardY;
    private int apron;
    private int apronEdge;
    private int pocket;
    private int ribbon;

    void draw(Scene3d scene, float x, float y, Direction facing, Farmhand.Action action,
              float stridePhase, float actionPhase, float movement, boolean partner) {
        this.scene = scene;
        centerX = x;
        centerY = y;
        forwardX = facing.dx;
        forwardY = facing.dy;
        apron = partner ? PARTNER_APRON : APRON;
        apronEdge = partner ? PARTNER_APRON_EDGE : APRON_EDGE;
        pocket = partner ? PARTNER_POCKET : POCKET;
        ribbon = partner ? PARTNER_RIBBON : HAT_RIBBON;

        float progress = Math.max(0f, Math.min(1f, actionPhase));
        float pulse = (float) Math.sin(progress * PI);
        float harvesting = action == Farmhand.Action.HARVEST ? pulse : 0f;
        float pouring = action == Farmhand.Action.WATER ? pulse : 0f;
        float filling = action == Farmhand.Action.REFILL ? pulse : 0f;
        float stride = (float) Math.sin(stridePhase) * movement;
        float rise = Math.abs(stride) * .018f - harvesting * .081f - pouring * .031f;
        float lean = harvesting * .108f + pouring * .037f;

        scene.shadow(x + .037f, y - .015f, .007f, .210f, .158f, 85);
        leg(-1f, stridePhase, movement, rise, lean);
        leg(1f, stridePhase + PI, movement, rise, lean);
        torso(rise, lean);
        head(rise, lean);
        arm(-1f, -stride, rise, lean, harvesting, pouring, filling, progress);
        arm(1f, stride, rise, lean, harvesting, pouring, filling, progress);
    }

    private void leg(float side, float phase, float movement, float rise, float lean) {
        float hip = side * .061f;
        float foot = side * .072f;
        float step = (float) Math.sin(phase) * movement;
        float footForward = .015f + .113f * step;
        float footLift = Math.max(0f, (float) Math.cos(phase)) * movement * .053f;
        float kneeForward = footForward * .51f + .026f + lean * .58f;
        float kneeHeight = .181f + footLift * .58f + rise * .37f;
        tube(hip, lean * .31f, HIP_HEIGHT + rise,
                foot, kneeForward, kneeHeight, .060f, .045f, 6, TROUSER);
        tube(foot, kneeForward, kneeHeight,
                foot, footForward, .075f + footLift, .045f, .034f, 6, TROUSER);
        box(foot, footForward + .015f, .012f + footLift, .091f, .141f, .023f, SOLE);
        gem(foot, footForward + .021f, .040f + footLift, .046f, .074f, .031f, BOOT);
        tube(foot, footForward - .018f, .035f + footLift,
                foot, footForward, .089f + footLift, .036f, .037f, 6, BOOT);
    }

    private void torso(float rise, float lean) {
        float hipZ = HIP_HEIGHT + rise;
        float beltZ = .420f + rise;
        float shoulderZ = SHOULDER_HEIGHT + rise;
        float beltY = lean * .40f;
        float bibY = lean * .84f;
        float bibZ = shoulderZ - .044f;
        bodyRing(lean * .30f, hipZ - .026f, .105f, .067f,
                beltY, beltZ, .111f, .072f, apron, apronEdge);
        bodyRing(beltY, beltZ, .111f, .072f,
                bibY, bibZ, .122f, .077f, apron, LINEN);
        bodyRing(bibY, bibZ, .122f, .077f,
                lean, shoulderZ, .126f, .080f, LINEN, LINEN);
        gem(0f, lean, shoulderZ - .008f, .135f, .081f, .037f, LINEN);

        // Waist seam and pocket sit slightly proud of the apron to define it at small scales.
        float bibBottomY = beltY + .080f;
        float bibTopY = bibY + .083f;
        quad(apronEdge, -.102f, bibBottomY + .005f, beltZ - .009f,
                .102f, bibBottomY + .005f, beltZ - .009f,
                .102f, bibBottomY + .006f, beltZ + .009f,
                -.102f, bibBottomY + .006f, beltZ + .009f);
        float pocketY = (bibBottomY + bibTopY) * .5f + .013f;
        gem(-.016f, pocketY, beltZ + .042f, .040f, .010f, .027f, pocket);
        for (int side = -1; side <= 1; side += 2) {
            tube(side * .067f, bibTopY + .003f, shoulderZ - .063f,
                    side * .083f, lean + .032f, shoulderZ + .020f, .013f, .015f, 4, apronEdge);
            gem(side * .065f, bibTopY + .014f, shoulderZ - .055f, .010f, .009f, .010f, BUTTON);
        }

        tube(0f, lean, shoulderZ, 0f, lean, shoulderZ + .064f, .040f, .042f, 6, SKIN);
        tube(0f, lean, shoulderZ + .010f, 0f, lean, shoulderZ + .035f, .073f, .063f, 7, SCARF);
        gem(.025f, lean + .069f, shoulderZ + .011f, .029f, .021f, .026f, SCARF);
    }

    private void head(float rise, float lean) {
        float z = .667f + rise;
        float y = lean * 1.15f;
        gem(0f, y - .022f, z + .017f, .084f, .072f, .083f, HAIR);
        gem(0f, y + .005f, z, .075f, .067f, .080f, SKIN);
        for (int side = -1; side <= 1; side += 2) {
            gem(side * .074f, y + .001f, z - .002f, .015f, .024f, .024f, SKIN);
            gem(side * .028f, y + .065f, z + .011f, .009f, .006f, .010f, EYE);
        }
        gem(0f, y + .071f, z - .003f, .015f, .014f, .015f, SKIN_LIGHT);
        tube(-.016f, y + .057f, z - .031f, .016f, y + .057f, z - .031f, .0038f, .0038f, 4, HAIR);

        // The crown and two-tone brim are built as rings so they shade in every direction.
        float hatY = y - .007f;
        float brimZ = z + .073f;
        tube(0f, hatY, brimZ, 0f, hatY, brimZ + .011f, .182f, .192f, 12, STRAW_EDGE);
        tube(0f, hatY, brimZ + .011f, 0f, hatY, brimZ + .023f, .192f, .151f, 12, STRAW);
        tube(0f, hatY, brimZ + .020f, 0f, hatY, brimZ + .042f, .101f, .099f, 10, ribbon);
        tube(0f, hatY, brimZ + .042f, 0f, hatY, brimZ + .088f, .099f, .078f, 10, STRAW);
        tube(0f, hatY, brimZ + .088f, 0f, hatY, brimZ + .100f, .078f, .060f, 10, STRAW_TOP);
    }

    private void arm(float side, float swing, float rise, float lean, float harvesting,
                     float pouring, float filling, float progress) {
        float shoulder = SHOULDER_HEIGHT + rise - .013f;
        boolean workingHand = side > 0f;
        float reach = workingHand ? harvesting * .159f + pouring * .124f + filling * .043f : 0f;
        float up = workingHand ? pouring * .052f + filling * .146f - harvesting * .057f : 0f;
        float handX = side * (.158f + (workingHand ? harvesting * .020f : 0f));
        float handY = lean + swing * .094f + reach + .012f;
        float handZ = shoulder - .205f + up;
        float elbowY = lean + (handY - lean) * .43f - .011f;
        float elbowZ = shoulder - .108f + up * .49f;
        float elbowX = side * .165f;
        tube(side * ARM_OUT, lean, shoulder, elbowX, elbowY, elbowZ,
                .042f, .034f, 6, LINEN);
        gem(elbowX, elbowY, elbowZ, .034f, .034f, .027f, LINEN_CUFF);
        tube(elbowX, elbowY, elbowZ, handX, handY, handZ,
                .028f, .020f, 6, SKIN);
        gem(handX, handY, handZ, .026f, .024f, .029f, SKIN);
        if (workingHand && (pouring > .014f || filling > .014f)) {
            wateringCan(handX, handY, handZ, pouring, filling, progress);
        } else if (workingHand && harvesting > .014f) {
            handTrowel(handX, handY, handZ, harvesting);
        }
    }

    private void wateringCan(float handX, float handY, float handZ, float tilt, float fill, float phase) {
        float cx = handX + .025f;
        float cy = handY + .037f;
        float cz = handZ - .061f;
        tube(cx, cy, cz - .055f, cx, cy, cz + .038f, .053f, .062f, 8, CAN);
        tube(cx, cy, cz + .037f, cx, cy, cz + .042f, .060f, .055f, 8, CAN_TRIM);
        tube(cx, cy, cz + .042f, cx, cy, cz + .044f, .038f, .037f, 8, CAN_SHADE);
        tube(cx, cy - .049f, cz + .014f, cx, cy - .067f, handZ + .008f,
                .011f, .010f, 5, CAN_SHADE);
        tube(cx, cy - .067f, handZ + .008f, handX, handY, handZ + .014f,
                .010f, .010f, 5, CAN_SHADE);
        float tipY = cy + .147f;
        float tipZ = cz + .046f - tilt * .082f + fill * .037f;
        tube(cx, cy + .035f, cz - .025f, cx, cy + .108f, tipZ,
                .022f, .013f, 6, CAN);
        tube(cx, cy + .108f, tipZ, cx, tipY, tipZ,
                .013f, .022f, 6, CAN_TRIM);
        if (tilt > .55f && tipZ > .03f) {
            for (int i = 0; i < 5; i++) {
                float q = (phase * 4f + i / 5f) % 1f;
                float fall = Math.min(tipZ - .012f, .26f * q * q);
                gem(cx + (i % 2 == 0 ? -.010f : .009f) * q, tipY + .114f * q,
                        tipZ - fall, .009f, .011f, .014f, WATER);
            }
        }
    }

    private void handTrowel(float x, float y, float z, float reach) {
        float tipZ = z - .143f * reach;
        tube(x, y - .017f, z + .014f, x, y + .077f, (z + tipZ) * .5f,
                .015f, .012f, 5, TOOL_HANDLE);
        quad(TOOL_METAL, x - .023f, y + .068f, (z + tipZ) * .5f,
                x + .023f, y + .068f, (z + tipZ) * .5f,
                x + .018f, y + .130f, tipZ, x - .012f, y + .145f, tipZ);
        tube(x, y + .076f, (z + tipZ) * .5f + .004f, x, y + .133f, tipZ + .005f,
                .005f, .003f, 4, CAN_TRIM);
    }

    private void bodyRing(float y1, float z1, float width1, float depth1,
                          float y2, float z2, float width2, float depth2, int front, int color) {
        float corner1 = width1 * .74f;
        float corner2 = width2 * .74f;
        float front1 = y1 + depth1;
        float front2 = y2 + depth2;
        float back1 = y1 - depth1;
        float back2 = y2 - depth2;
        quad(front, -corner1, front1, z1, corner1, front1, z1,
                corner2, front2, z2, -corner2, front2, z2);
        quad(color, corner1, front1, z1, width1, y1, z1, width2, y2, z2, corner2, front2, z2);
        quad(color, width1, y1, z1, corner1, back1, z1, corner2, back2, z2, width2, y2, z2);
        quad(color, corner1, back1, z1, -corner1, back1, z1,
                -corner2, back2, z2, corner2, back2, z2);
        quad(color, -corner1, back1, z1, -width1, y1, z1, -width2, y2, z2, -corner2, back2, z2);
        quad(color, -width1, y1, z1, -corner1, front1, z1, -corner2, front2, z2, -width2, y2, z2);
    }

    /** Convert model right/forward/height to world coordinates without changing the farm grid. */
    private float x(float right, float forward) {
        return centerX + right * forwardY + forward * forwardX;
    }

    private float y(float right, float forward) {
        return centerY - right * forwardX + forward * forwardY;
    }

    private void gem(float x, float y, float z, float rx, float ry, float rz, int color) {
        scene.gem(x(x, y), y(x, y), z, forwardX == 0 ? rx : ry, forwardX == 0 ? ry : rx, rz, color);
    }

    private void box(float x, float y, float z, float width, float depth, float height, int color) {
        float worldWidth = forwardX == 0 ? width : depth;
        float worldDepth = forwardX == 0 ? depth : width;
        scene.box(x(x, y) - worldWidth * .5f, y(x, y) - worldDepth * .5f, z,
                worldWidth, worldDepth, height, color);
    }

    private void tube(float ax, float ay, float az, float bx, float by, float bz,
                      float r1, float r2, int sides, int color) {
        scene.tube(x(ax, ay), y(ax, ay), az, x(bx, by), y(bx, by), bz, r1, r2, sides, color);
    }

    private void quad(int color, float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz) {
        scene.face(color, x(ax, ay), y(ax, ay), az, x(bx, by), y(bx, by), bz,
                x(cx, cy), y(cx, cy), cz, x(dx, dy), y(dx, dy), dz);
    }
}
