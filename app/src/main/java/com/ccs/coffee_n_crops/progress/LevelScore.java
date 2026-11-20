package com.ccs.coffee_n_crops.progress;

/** The player's records for source length and simulated farm action cost. */
public record LevelScore(int firstLines, long firstTickTenths, int bestLines, long bestTickTenths) {
    private static final int TENTHS_PER_TICK = 10;

    /** Targets measured by playing a valid solution of this exact lesson. */
    public record Target(int lines, long tickTenths) {
        public double ticks() {
            return tickTenths / (double) TENTHS_PER_TICK;
        }
    }

    public static LevelScore firstRun(int lines, double ticks) {
        long tickTenths = Math.round(ticks * TENTHS_PER_TICK);
        return new LevelScore(lines, tickTenths, lines, tickTenths);
    }

    public LevelScore withRun(int lines, double ticks) {
        long tickTenths = Math.round(ticks * TENTHS_PER_TICK);
        return new LevelScore(firstLines, firstTickTenths, Math.min(bestLines, lines),
                Math.min(bestTickTenths, tickTenths));
    }

    public int stars(Target target) {
        boolean compact = target == null ? bestLines < firstLines : bestLines <= target.lines();
        boolean efficient = target == null ? bestTickTenths < firstTickTenths : bestTickTenths <= target.tickTenths();
        return 1 + (compact ? 1 : 0) + (efficient ? 1 : 0);
    }

    public double bestTicks() {
        return bestTickTenths / (double) TENTHS_PER_TICK;
    }
}
