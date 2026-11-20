package com.ccs.coffee_n_crops.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Small orthographic mesh renderer. Geometry, camera and lighting are independent of Android. */
final class Scene3d {
    static final float DEFAULT_YAW = 24;
    static final float DEFAULT_PITCH = 35;
    private static final Comparator<Polygon> FAR_FIRST = Comparator.comparingDouble(p -> p.depth);

    interface Target {
        void polygon(int color, float[] xy, int size, Camera camera);
    }

    /** A fitted camera uses the same projection for all scene layers and screen sizes. */
    static final class Camera {
        final float scale;
        final float offsetX;
        final float offsetY;
        final float cosYaw, sinYaw, cosPitch, sinPitch;

        Camera(float left, float top, float right, float bottom, int columns, int rows) {
            this(left, top, right, bottom, columns, rows, DEFAULT_YAW, DEFAULT_PITCH, 1, 0, 0);
        }

        Camera(float left, float top, float right, float bottom, int columns, int rows,
               float yaw, float pitch, float zoom, float panX, float panY) {
            cosYaw = (float) Math.cos(Math.toRadians(yaw));
            sinYaw = (float) Math.sin(Math.toRadians(yaw));
            cosPitch = (float) Math.cos(Math.toRadians(pitch));
            sinPitch = (float) Math.sin(Math.toRadians(pitch));
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (float x : new float[]{-.76f, columns + .76f}) {
                for (float y : new float[]{-1.36f, rows + .48f}) {
                    float u = projectX(x, y);
                    float v = projectY(x, y, -.65f);
                    minX = Math.min(minX, u);
                    maxX = Math.max(maxX, u);
                    maxY = Math.max(maxY, v);
                    minY = Math.min(minY, projectY(x, y, y < 0 ? 1.17f : .93f));
                }
            }
            // The upper corners' tall decorations are set back from the extreme sides.
            minY += .14f;
            scale = zoom * Math.max(0, Math.min((right - left) / (maxX - minX), (bottom - top) / (maxY - minY)));
            offsetX = panX + (left + right - scale * (minX + maxX)) * .5f;
            offsetY = panY + (top + bottom - scale * (minY + maxY)) * .5f;
        }

        float projectX(float x, float y) { return cosYaw * x - sinYaw * y; }
        float projectY(float x, float y, float z) { return (sinYaw * x + cosYaw * y) * sinPitch - z * cosPitch; }
        float depth(float x, float y, float z) { return (sinYaw * x + cosYaw * y) * cosPitch + z * sinPitch; }

        float x(float x, float y) { return offsetX + scale * projectX(x, y); }
        float y(float x, float y, float z) { return offsetY + scale * projectY(x, y, z); }
    }

    private static final class Polygon {
        float[] points = new float[8];
        float[] xyz;
        float nx, ny, nz;
        boolean closed, visible, shade;
        int baseColor;
        int count;
        int color;
        float depth;
    }

    private final List<Polygon> polygons = new ArrayList<>();
    private final List<Polygon> pool = new ArrayList<>();
    private boolean sorted = true;
    private Camera lastCamera;

    void clear() {
        pool.addAll(polygons);
        polygons.clear();
        sorted = true;
    }

    /** Double-sided polygon for paper-thin leaves, fabric and ground markings. */
    void face(int color, float... xyz) { face(color, false, xyz); }

    void face(int color, boolean closed, float... xyz) {
        if (xyz.length < 9) return;
        float ax = xyz[3] - xyz[0], ay = xyz[4] - xyz[1], az = xyz[5] - xyz[2];
        float bx = xyz[6] - xyz[0], by = xyz[7] - xyz[1], bz = xyz[8] - xyz[2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < .000001f) return;
        Polygon p = add(color, xyz);
        p.nx = nx / length;
        p.ny = ny / length;
        p.nz = nz / length;
        p.closed = closed;
        p.shade = true;
    }

    private Polygon add(int color, float... xyz) {
        Polygon p = pool.isEmpty() ? new Polygon() : pool.remove(pool.size() - 1);
        p.count = xyz.length / 3;
        if (p.points.length < p.count * 2) p.points = new float[p.count * 2];
        p.xyz = xyz;
        p.baseColor = color;
        p.shade = false;
        p.closed = false;
        polygons.add(p);
        sorted = false;
        return p;
    }

    private static void project(Polygon p, Camera camera) {
        p.visible = true;
        p.color = p.baseColor;
        if (p.shade) {
            float view = camera.depth(p.nx, p.ny, p.nz);
            if (p.closed && view <= 0) {
                p.visible = false;
                return;
            }
            float side = view < 0 ? -1 : 1;
            float light = (-.54f * p.nx - .36f * p.ny + .76f * p.nz) * side;
            p.color = tint(p.color, .67f + .34f * Math.max(0, light) + .14f * Math.max(0, p.nz * side));
        }
        p.depth = 0;
        for (int i = 0; i < p.count; i++) {
            float x = p.xyz[3 * i], y = p.xyz[3 * i + 1], z = p.xyz[3 * i + 2];
            p.points[i * 2] = camera.projectX(x, y);
            p.points[i * 2 + 1] = camera.projectY(x, y, z);
            p.depth += camera.depth(x, y, z);
        }
        p.depth /= p.count;
    }

    private static int tint(int color, float light) {
        int r = Math.min(255, Math.round((color >> 16 & 255) * light));
        int g = Math.min(255, Math.round((color >> 8 & 255) * light));
        int b = Math.min(255, Math.round((color & 255) * light));
        return (color & 0xff000000) | (r << 16) | (g << 8) | b;
    }

    void box(float x, float y, float z, float width, float depth, float height, int color) {
        float e = x + width, s = y + depth, t = z + height;
        face(color, true, x, y, t, e, y, t, e, s, t, x, s, t);
        face(color, true, e, y, z, e, s, z, e, s, t, e, y, t);
        face(color, true, e, s, z, x, s, z, x, s, t, e, s, t);
        face(color, true, x, s, z, x, y, z, x, y, t, x, s, t);
        face(color, true, x, y, z, e, y, z, e, y, t, x, y, t);
    }

    /** A solid whose axis can point in any direction, used for limbs, stalks and tools. */
    void tube(float x1, float y1, float z1, float x2, float y2, float z2,
              float r1, float r2, int sides, int color) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < .0001f) return;
        dx /= length; dy /= length; dz /= length;
        float ux = -dy, uy = dx;
        float width = (float) Math.sqrt(ux * ux + uy * uy);
        if (width < .0001f) { ux = 1; uy = 0; }
        else { ux /= width; uy /= width; }
        float vx = -dz * uy, vy = dz * ux, vz = dx * uy - dy * ux;
        float[] bottom = new float[sides * 3], top = new float[sides * 3];
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float a = ux * cos + vx * sin, b = uy * cos + vy * sin, c = vz * sin;
            bottom[i * 3] = x1 + r1 * a;
            bottom[i * 3 + 1] = y1 + r1 * b;
            bottom[i * 3 + 2] = z1 + r1 * c;
            top[i * 3] = x2 + r2 * a;
            top[i * 3 + 1] = y2 + r2 * b;
            top[i * 3 + 2] = z2 + r2 * c;
        }
        for (int i = 0; i < sides; i++) {
            int a = i * 3, b = (i + 1) % sides * 3;
            face(color, true, bottom[a], bottom[a + 1], bottom[a + 2], bottom[b], bottom[b + 1], bottom[b + 2],
                    top[b], top[b + 1], top[b + 2], top[a], top[a + 1], top[a + 2]);
        }
        if (r2 > 0) face(color, true, top);
        if (r1 > 0) {
            for (int a = 0, b = bottom.length - 3; a < b; a += 3, b -= 3) {
                for (int j = 0; j < 3; j++) {
                    float temp = bottom[a + j]; bottom[a + j] = bottom[b + j]; bottom[b + j] = temp;
                }
            }
            face(color, true, bottom);
        }
    }

    /** Faceted volume with two shoulder rings. */
    void gem(float x, float y, float z, float rx, float ry, float rz, int color) {
        final int sides = 7;
        float[] lower = new float[sides * 3], upper = new float[sides * 3];
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            lower[i * 3] = x + cos * rx * .80f;
            lower[i * 3 + 1] = y + sin * ry * .80f;
            lower[i * 3 + 2] = z - rz * .47f;
            upper[i * 3] = x + cos * rx;
            upper[i * 3 + 1] = y + sin * ry;
            upper[i * 3 + 2] = z + rz * .37f;
        }
        for (int i = 0; i < sides; i++) {
            int a = i * 3, b = (i + 1) % sides * 3;
            face(color, true, x, y, z - rz, lower[b], lower[b + 1], lower[b + 2], lower[a], lower[a + 1], lower[a + 2]);
            face(color, true, lower[a], lower[a + 1], lower[a + 2], lower[b], lower[b + 1], lower[b + 2],
                    upper[b], upper[b + 1], upper[b + 2], upper[a], upper[a + 1], upper[a + 2]);
            face(color, true, upper[a], upper[a + 1], upper[a + 2], upper[b], upper[b + 1], upper[b + 2], x, y, z + rz);
        }
    }

    void shadow(float x, float y, float z, float rx, float ry, int alpha) {
        disk(x, y, z, rx, ry, alpha << 24 | 0x10201B);
    }

    void disk(float x, float y, float z, float rx, float ry, int color) {
        float[] points = new float[36];
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            points[i * 3] = x + rx * (float) Math.cos(a);
            points[i * 3 + 1] = y + ry * (float) Math.sin(a);
            points[i * 3 + 2] = z;
        }
        add(color, points);
    }

    private void sort(Camera camera) {
        if (sorted && lastCamera == camera) return;
        for (Polygon polygon : polygons) project(polygon, camera);
        polygons.sort(FAR_FIRST);
        sorted = true;
        lastCamera = camera;
    }

    void draw(Target target, Camera camera) { drawWith(null, target, camera); }

    /** Merge cached scenery with animated faces by camera depth. */
    void drawWith(Scene3d other, Target target, Camera camera) {
        sort(camera);
        if (other != null) other.sort(camera);
        int a = 0, b = 0, otherCount = other == null ? 0 : other.polygons.size();
        while (a < polygons.size() || b < otherCount) {
            Polygon p;
            if (b == otherCount || a < polygons.size() && polygons.get(a).depth <= other.polygons.get(b).depth) {
                p = polygons.get(a++);
            } else p = other.polygons.get(b++);
            if (p.visible) target.polygon(p.color, p.points, p.count, camera);
        }
    }
}
