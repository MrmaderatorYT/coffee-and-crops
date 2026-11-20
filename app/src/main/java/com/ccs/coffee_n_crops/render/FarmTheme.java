package com.ccs.coffee_n_crops.render;

/** The fixed visual environment of a farm. Scenery never changes the playable grid. */
public enum FarmTheme {
    ORCHARD(new Sky(0xff6c9b8c, 0xffc8cfa0, 0xff365e51, 0xff819d79, 0xff486e56, 0xffffefb5),
            new Land(0xff799358, 0xff667c45, 0xffa76d4b, 0xff77503e, 0xff879b62, 0xff82975c)),
    TERRACE(new Sky(0xffa97e77, 0xffecc39b, 0xff805b50, 0xffbd8a6f, 0xff946d57, 0xffffdd9e),
            new Land(0xffc49a62, 0xff9e754e, 0xffb46e50, 0xff764f48, 0xffc9ad75, 0xffbf9e69)),
    COAST(new Sky(0xff4e859c, 0xffa9c9c2, 0xff236d79, 0xff81a6a7, 0xff497a88, 0xffe1eac4),
            new Land(0xffa2a371, 0xff7f896e, 0xffa0aa9c, 0xff5f7a7c, 0xff99aa7a, 0xff91a570)),
    AUTUMN(new Sky(0xff996565, 0xffe7b578, 0xff744e4f, 0xffb17a63, 0xff905b52, 0xffffd395),
            new Land(0xffa88b61, 0xff8e6f55, 0xffa4694f, 0xff6e4e49, 0xffb7a273, 0xffae9667)),
    FROST(new Sky(0xff718da9, 0xffc2d3df, 0xff6a8699, 0xffa4b8cb, 0xff829ba9, 0xffe2f1e9),
            new Land(0xffc6d5d7, 0xff879faa, 0xff8a8588, 0xff5f626f, 0xffd1d9d5, 0xffc1cfce)),
    TWILIGHT(new Sky(0xff2b3156, 0xff786783, 0xff333750, 0xff625470, 0xff45455d, 0xffe2e3da),
            new Land(0xff7b8183, 0xff626977, 0xff897184, 0xff554c62, 0xff859285, 0xff7d8880));

    private static final int LEVELS_PER_ENVIRONMENT = 3;
    private static final FarmTheme[] ROTATION = values();

    record Sky(int zenith, int horizon, int ground, int distant, int near, int light) { }
    record Land(int surface, int rim, int middle, int base, int turfLight, int turfDark) { }

    final Sky sky;
    final Land land;

    FarmTheme(Sky sky, Land land) {
        this.sky = sky;
        this.land = land;
    }

    /** Levels keep their scenery when reopened, including after changing the language. */
    public static FarmTheme forLevel(int levelIndex) {
        int band = Math.max(0, levelIndex) / LEVELS_PER_ENVIRONMENT;
        return ROTATION[band % ROTATION.length];
    }
}
