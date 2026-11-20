package com.ccs.coffee_n_crops.render;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.ccs.coffee_n_crops.R;
import com.ccs.coffee_n_crops.progress.LevelDef;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.farm.WorldClock;

/** Fits and paints the living farm model on a dedicated SurfaceView worker. */
public final class FarmSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private static final long FRAME_NANOS = 1_000_000_000L / 60;
    private static final int HUD_TEXT = 0xfff3e8cd;
    private static final int HUD_GOLD = 0xffe5ad5b;
    private static final int HUD_MINT = 0xffa8cc97;
    private static final int HUD_WATER = 0xff91c9c5;

    private final float density;
    private final String wheatLabel;
    private final String wateredLabel;
    private final String waterLabel;
    private static final Viewpoint INITIAL_VIEW = new Viewpoint(
            Scene3d.DEFAULT_YAW, Scene3d.DEFAULT_PITCH, 1, 0, 0);
    private record Viewpoint(float yaw, float pitch, float zoom, float panX, float panY) { }

    private volatile Viewpoint viewpoint = INITIAL_VIEW;
    private final GestureDetector orbitGesture;
    private final ScaleGestureDetector zoomGesture;
    private boolean multiTouch;
    private volatile Binding binding;
    private volatile Thread worker;

    private static final class Binding {
        final FarmWorld world;
        final WorldClock clock;
        final FarmDiorama.Tiles tiles;
        final LevelDef.Finish finish;
        final FarmTheme theme;

        Binding(FarmWorld world, WorldClock clock, LevelDef.Finish finish, FarmTheme theme) {
            this.finish = finish;
            this.world = world;
            this.clock = clock;
            this.theme = theme;
            tiles = (x, y) -> world.tile(x, y).state;
        }
    }

    public FarmSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        wheatLabel = context.getString(R.string.farm_hud_wheat);
        wateredLabel = context.getString(R.string.farm_hud_watered);
        waterLabel = context.getString(R.string.farm_hud_water);
        orbitGesture = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }

            @Override public boolean onDoubleTap(MotionEvent event) {
                viewpoint = INITIAL_VIEW;
                return true;
            }

            @Override public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                if (multiTouch) return true;
                Viewpoint view = viewpoint;
                viewpoint = new Viewpoint((view.yaw() + dx / density * .45f) % 360,
                        clamp(view.pitch() - dy / density * .28f, 18, 75),
                        view.zoom(), view.panX(), view.panY());
                return true;
            }
        });
        zoomGesture = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private float lastX, lastY;

            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                lastX = detector.getFocusX();
                lastY = detector.getFocusY();
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                Viewpoint view = viewpoint;
                float zoom = clamp(view.zoom() * detector.getScaleFactor(), 1, 3);
                float ratio = zoom / view.zoom();
                float centerX = getWidth() * .5f;
                float centerY = (getHeight() + 23 * density) * .5f;
                float panX = (view.panX() + centerX - lastX) * ratio + detector.getFocusX() - centerX;
                float panY = (view.panY() + centerY - lastY) * ratio + detector.getFocusY() - centerY;
                float limitX = getWidth() * (zoom - 1) * .5f;
                float limitY = getHeight() * (zoom - 1) * .5f;
                viewpoint = new Viewpoint(view.yaw(), view.pitch(), zoom,
                        clamp(panX, -limitX, limitX), clamp(panY, -limitY, limitY));
                lastX = detector.getFocusX();
                lastY = detector.getFocusY();
                return true;
            }
        });
        zoomGesture.setQuickScaleEnabled(false);
        getHolder().addCallback(this);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) multiTouch = false;
        if (!multiTouch && event.getPointerCount() > 1) {
            multiTouch = true;
            MotionEvent canceled = MotionEvent.obtain(event);
            canceled.setAction(MotionEvent.ACTION_CANCEL);
            orbitGesture.onTouchEvent(canceled);
            canceled.recycle();
        }
        zoomGesture.onTouchEvent(event);
        if (!multiTouch) orbitGesture.onTouchEvent(event);
        return true;
    }

    public void bind(FarmWorld world, WorldClock clock, LevelDef.Finish finish) {
        bind(world, clock, finish, FarmTheme.ORCHARD);
    }

    /** Binds the playable farm and its level-specific scenery. */
    public void bind(FarmWorld world, WorldClock clock, LevelDef.Finish finish, FarmTheme theme) {
        binding = new Binding(world, clock, finish, theme);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        Thread thread = new Thread(() -> render(holder), "farm-render");
        worker = thread;
        thread.start();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        Thread thread = worker;
        worker = null;
        if (thread != null) thread.interrupt();
    }

    private void render(SurfaceHolder holder) {
        Thread thread = Thread.currentThread();
        Frame frame = new Frame();
        while (worker == thread && !thread.isInterrupted()) {
            long started = System.nanoTime();
            Canvas canvas;
            try {
                if (!holder.getSurface().isValid()) break;
                canvas = holder.lockHardwareCanvas();
            } catch (IllegalStateException e) {
                // The platform can invalidate the surface between the checks above.
                if (worker != thread || !holder.getSurface().isValid()) break;
                canvas = null;
            }
            if (canvas != null) {
                try {
                    if (worker == thread) frame.draw(canvas, binding, started);
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
            long remaining = FRAME_NANOS - (System.nanoTime() - started);
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private final class Frame implements Scene3d.Target {
        private final FarmDiorama garden = new FarmDiorama();
        private final FarmBackdrop backdrop = new FarmBackdrop();
        private final FarmMotion motion = new FarmMotion();
        private final FarmMotion partnerMotion = new FarmMotion();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Typeface hudFont = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        private final String[] hudLabels = new String[3];
        private final int[] hudColors = new int[3];
        private final float[] hudWidths = new float[3];
        private Canvas canvas;
        private Scene3d.Camera camera;
        private Viewpoint appliedView;
        private int width;
        private int height;
        private int columns;
        private int rows;
        private int harvested = -1;
        private int watered = -1;
        private double water = Double.NaN;
        private FarmWorld hudWorld;
        private int hudCount;

        void draw(Canvas canvas, Binding binding, long now) {
            this.canvas = canvas;
            if (width != canvas.getWidth() || height != canvas.getHeight()) {
                width = canvas.getWidth();
                height = canvas.getHeight();
                camera = null;
            }
            backdrop.draw(canvas, binding == null ? FarmTheme.ORCHARD : binding.theme);
            if (binding == null) return;

            FarmWorld world = binding.world;
            refreshHud(world);
            Viewpoint currentView = viewpoint;
            if (camera == null || appliedView != currentView || columns != world.width || rows != world.height) {
                appliedView = currentView;
                columns = world.width;
                rows = world.height;
                camera = new Scene3d.Camera(10 * density, 31 * density, width - 10 * density,
                        height - 8 * density, columns, rows, currentView.yaw(), currentView.pitch(),
                        currentView.zoom(), currentView.panX(), currentView.panY());
            }
            garden.sync(columns, rows, binding.tiles, binding.theme);
            Farmhand.Pose pose = world.farmer.pose();
            long tickMs = binding.clock.millisPerTick();
            int speed = binding.clock.speed();
            motion.update(pose, now, tickMs, speed);
            Farmhand.Pose partner = world.farmers().size() > 1 ? world.farmers().get(1).pose() : null;
            if (partner != null) partnerMotion.update(partner, now, tickMs, speed);
            drawGroundShadow();
            garden.draw(this, camera, motion, pose.action, partner != null ? partnerMotion : null,
                    partner != null ? partner.action : null, binding.finish);
            drawHud();
        }

        private void drawGroundShadow() {
            float x = camera.x(columns * .5f, rows * .5f);
            float y = camera.y(columns * .5f, rows * .5f, -.65f);
            float half = (columns * .5f + .65f) * camera.scale;
            float thickness = (.42f + rows * .17f) * camera.scale;
            paint.setColor(0x48131e23);
            canvas.save();
            canvas.rotate(12, x, y);
            canvas.drawOval(x - half, y - thickness, x + half, y + thickness, paint);
            canvas.restore();
        }

        private void refreshHud(FarmWorld world) {
            int gathered = world.wheatHarvested(), irrigated = world.watered();
            double litres = world.tankLevel();
            if (world == hudWorld && gathered == harvested && irrigated == watered && litres == water) return;
            hudWorld = world;
            harvested = gathered;
            watered = irrigated;
            water = litres;
            hudCount = 0;
            if (world.totalWheat() > 0) addHud(wheatLabel + "  " + gathered + "/" + world.totalWheat(), HUD_GOLD);
            if (world.totalGrowing() > 0) addHud(wateredLabel + "  " + irrigated + "/" + world.totalGrowing(), HUD_MINT);
            if (world.tankCapacity() > 0) addHud(waterLabel + "  " + litres + " L", HUD_WATER);
        }

        private void addHud(String text, int color) {
            hudLabels[hudCount] = text;
            hudColors[hudCount++] = color;
        }

        private void drawHud() {
            paint.setTypeface(hudFont);
            float gap = 5 * density, padding = 20 * density;
            float textSize = 11.5f * density;
            paint.setTextSize(textSize);
            float total = Math.max(0, hudCount - 1) * gap;
            for (int i = 0; i < hudCount; i++) total += paint.measureText(hudLabels[i]) + padding;
            float fit = total == 0 ? 1 : Math.min(1, (width - 16 * density) / total);
            if (fit <= 0) return;
            paint.setTextSize(textSize * fit);
            gap *= fit;
            padding *= fit;
            for (int i = 0; i < hudCount; i++) hudWidths[i] = paint.measureText(hudLabels[i]) + padding;
            float left = (width - total * fit) * .5f;
            for (int i = 0; i < hudCount; i++) {
                float right = left + hudWidths[i];
                paint.setColor(0xe3192e27);
                canvas.drawRoundRect(left, 5 * density, right, 26 * density, 8 * density, 8 * density, paint);
                paint.setColor(hudColors[i]);
                canvas.drawCircle(left + 6 * density * fit, 15.3f * density, 2 * density * fit, paint);
                paint.setColor(HUD_TEXT);
                canvas.drawText(hudLabels[i], left + 12 * density * fit, 19.5f * density, paint);
                left = right + gap;
            }
        }

        @Override public void polygon(int color, float[] xy, int size, Scene3d.Camera camera) {
            path.rewind();
            path.moveTo(camera.offsetX + xy[0] * camera.scale, camera.offsetY + xy[1] * camera.scale);
            for (int i = 1; i < size; i++) {
                path.lineTo(camera.offsetX + xy[i * 2] * camera.scale, camera.offsetY + xy[i * 2 + 1] * camera.scale);
            }
            path.close();
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }
    }
}
