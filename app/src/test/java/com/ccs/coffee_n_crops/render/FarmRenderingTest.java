package com.ccs.coffee_n_crops.render;

import com.ccs.coffee_n_crops.farm.Direction;
import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.farm.Farmhand;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;

import org.junit.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Geometry and motion checks that exercise the same scene sent to Android's Canvas. */
public class FarmRenderingTest {
    private static final float EPSILON = .01f;

    @Test public void everyLevelFitsInANarrowViewportThroughTheActionPoses() {
        final float viewportWidth = 300, viewportHeight = 147;
        FarmDiorama garden = new FarmDiorama();
        for (int index = 0; index < LevelCatalog.all().size(); index++) {
            LevelDef level = LevelCatalog.all().get(index);
            FarmWorld world = level.createWorld();
            garden.sync(world.width, world.height, (x, y) -> world.tile(x, y).state, FarmTheme.forLevel(index));
            Scene3d.Camera camera = new Scene3d.Camera(10, 31, viewportWidth - 10, viewportHeight - 8,
                    world.width, world.height);
            assertTrue(level.id, camera.scale > 0);
            Scene3d.Target bounds = (color, xy, size, c) -> {
                for (int i = 0; i < size; i++) {
                    float x = c.offsetX + c.scale * xy[i * 2];
                    float y = c.offsetY + c.scale * xy[i * 2 + 1];
                    assertTrue(level.id + " x=" + x, x >= 0 && x <= viewportWidth);
                    assertTrue(level.id + " y=" + y, y >= 28 && y <= viewportHeight);
                }
            };
            garden.draw(bounds, camera, .5f, .5f, Direction.NORTH, Farmhand.Action.REFILL, 0, .5f, 0, level.finish());
            garden.draw(bounds, camera, world.width - .5f, world.height - .5f, Direction.SOUTH,
                    Farmhand.Action.WATER, 0, .5f, 0, level.finish());
            garden.draw(bounds, camera, .5f, .5f, Direction.EAST, Farmhand.Action.WALK, 1, .5f, 1,
                    new LevelDef.Finish(world.width - 1, world.height - 1));
        }
    }

    @Test public void rotationUsesEveryEnvironmentAndRebuildsSceneryOnTheSameFarm() {
        EnumSet<FarmTheme> assigned = EnumSet.noneOf(FarmTheme.class);
        for (int index = 0; index < LevelCatalog.all().size(); index++) {
            assigned.add(FarmTheme.forLevel(index));
        }
        assertEquals(EnumSet.allOf(FarmTheme.class), assigned);

        FarmWorld world = new FarmWorld(new String[]{"W.g", "..."}, 0, 0);
        FarmDiorama garden = new FarmDiorama();
        Scene3d.Camera camera = new Scene3d.Camera(10, 31, 290, 139, world.width, world.height);
        Set<Integer> silhouettes = new HashSet<>();
        for (FarmTheme theme : assigned) {
            garden.sync(world.width, world.height, (x, y) -> world.tile(x, y).state, theme);
            int[] polygons = {0};
            garden.draw((color, xy, size, c) -> polygons[0]++, camera, .5f, .5f, Direction.EAST,
                    Farmhand.Action.IDLE, 0, 0, 0, null);
            assertTrue(theme.name() + " uses different geometry", silhouettes.add(polygons[0]));
        }
    }

    @Test public void walkingFollowsBothLegsAndChangesFacingAtTheTurn() {
        FarmWorld world = new FarmWorld(new String[]{"...", "..."}, 0, 0);
        world.walkTo(world.farmer, 2, 1);
        Farmhand.Pose pose = world.farmer.pose();
        long tickMs = 500;
        long duration = Math.round(pose.durationTicks * tickMs * 1_000_000);
        FarmMotion motion = new FarmMotion();
        motion.update(pose, pose.startedNanos + duration / 2, tickMs, 1);
        assertEquals(2, motion.x, EPSILON);
        assertEquals(.5, motion.y, EPSILON);
        assertEquals(Direction.EAST, motion.facing);
        motion.update(pose, pose.startedNanos + duration * 5 / 6, tickMs, 1);
        assertEquals(2.5, motion.x, EPSILON);
        assertEquals(1, motion.y, EPSILON);
        assertEquals(Direction.SOUTH, motion.facing);
    }

    @Test public void aTeamFrameIncludesTheSecondFarmhandMesh() {
        FarmWorld world = new FarmWorld(new String[]{"...", "..."}, 0, 0, 0, 0, 2, 1);
        FarmDiorama garden = new FarmDiorama();
        garden.sync(world.width, world.height, (x, y) -> world.tile(x, y).state);
        Scene3d.Camera camera = new Scene3d.Camera(10, 31, 290, 139, world.width, world.height);
        Farmhand.Pose firstPose = world.farmer.pose();
        Farmhand.Pose secondPose = world.farmers().get(1).pose();
        FarmMotion first = new FarmMotion(), second = new FarmMotion();
        first.update(firstPose, firstPose.startedNanos, 0, 1);
        second.update(secondPose, secondPose.startedNanos, 0, 1);

        int[] faces = {0};
        Scene3d.Target target = (color, xy, size, c) -> faces[0]++;
        garden.draw(target, camera, first, firstPose.action, null, null, null);
        int single = faces[0];
        faces[0] = 0;
        garden.draw(target, camera, first, firstPose.action, second, secondPose.action, null);
        assertTrue(faces[0] > single);
    }

    @Test public void speedChangeAdvancesOnlyLaterFramesAndResetSnapsToStart() {
        FarmWorld world = new FarmWorld(new String[]{"..."}, 0, 0);
        world.move(world.farmer, Direction.EAST);
        Farmhand.Pose pose = world.farmer.pose();
        FarmMotion motion = new FarmMotion();
        motion.update(pose, pose.startedNanos + 100_000_000, 500, 1);
        assertEquals(.7, motion.x, EPSILON);
        motion.update(pose, pose.startedNanos + 200_000_000, 500, 2);
        assertEquals(1.1, motion.x, EPSILON);
        world.reset();
        Farmhand.Pose reset = world.farmer.pose();
        motion.update(reset, reset.startedNanos, 500, 2);
        assertEquals(.5, motion.x, EPSILON);
        assertEquals(0, motion.movement, EPSILON);
    }
}
