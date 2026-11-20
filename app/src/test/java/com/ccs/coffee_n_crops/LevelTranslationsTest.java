package com.ccs.coffee_n_crops;

import com.ccs.coffee_n_crops.farm.FarmWorld;
import com.ccs.coffee_n_crops.lang.ast.stmt.Block;
import com.ccs.coffee_n_crops.progress.LevelCatalog;
import com.ccs.coffee_n_crops.progress.LevelDef;
import com.ccs.coffee_n_crops.progress.LevelTranslations;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class LevelTranslationsTest {
    @Test public void bothLanguagesCoverEveryLevelAndPreserveJavaNames() throws Exception {
        LevelTranslations ukrainian = bundledUkrainian();
        LevelTranslations english = LevelTranslations.ENGLISH;
        for (LevelDef level : LevelCatalog.all()) {
            LevelTranslations.Text translated = ukrainian.text(level);
            assertTrue(level.id, translated.title().startsWith("Рівень "));
            assertNotEquals(level.id, level.goalText, translated.goal());
            assertNotEquals(level.id, level.hint, translated.hint());
            assertEquals(new LevelTranslations.Text(level.title, level.goalText, level.hint), english.text(level));
        }
        assertTrue(ukrainian.text(LevelCatalog.get(50)).hint().contains("route::add"));
        assertTrue(ukrainian.text(LevelCatalog.get(54)).hint().contains("gate.lock()"));
    }

    @Test public void translatesFeedbackAndPreservesNumbersAndJavaDiagnostics() throws Exception {
        LevelTranslations ukrainian = bundledUkrainian();
        LevelDef lesson = LevelCatalog.get(0);
        FarmWorld world = lesson.createWorld();
        String remaining = lesson.check(world, new Block());
        assertTrue(ukrainian.message(remaining).contains("жнив — 5"));

        for (int x = 0; x < world.width - 1; x++) {
            world.walkTo(world.farmer, x, 0);
            world.harvest(world.farmer);
        }
        String finish = lesson.check(world, new Block());
        assertTrue(ukrainian.message(finish).contains("помаранчевим прапорцем"));

        String deadlock = "Deadlock: farmer 2 waits for pumpA held by farmer 1; "
                + "farmer 1 waits for pumpB held by farmer 2. Take the locks in the same order.";
        String translated = ukrainian.message(deadlock);
        assertTrue(translated.startsWith("Взаємне блокування."));
        assertTrue(translated.contains("Фермер 2 чекає на pumpA"));
        assertTrue(translated.contains("Фермер 1 чекає на pumpB"));

        String diagnostic = "cannot find symbol: variable cell";
        assertEquals(diagnostic, ukrainian.message(diagnostic));
        assertEquals(deadlock, LevelTranslations.ENGLISH.message(deadlock));
    }

    private static LevelTranslations bundledUkrainian() throws Exception {
        Path assets = Paths.get("src", "main", "assets", "i18n");
        if (!Files.isDirectory(assets)) assets = Paths.get("app").resolve(assets);
        return LevelTranslations.parse(
                new String(Files.readAllBytes(assets.resolve("uk_levels_01_28.json")), StandardCharsets.UTF_8),
                new String(Files.readAllBytes(assets.resolve("uk_levels_29_56.json")), StandardCharsets.UTF_8));
    }
}
