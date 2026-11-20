# Coffee & Crops art

The farm is an original low-poly 3D diorama built from Java geometry. Six environments rotate
every three levels: orchard, terracotta terrace, coast, autumn, frost, and twilight. The ground
palette, horizon, and props change with the environment. All art is generated locally from geometry.

| Source | Role |
|---|---|
| `render/Scene3d.java` | Orthographic camera, face lighting, depth order and primitive meshes. |
| `render/FarmDiorama.java` | Terrain layers, crop states, shadows and the farmhands' ground markers. |
| `render/FarmTheme.java` | Scenery assignment and palettes for each environment. |
| `render/FarmBackdrop.java` | Cached Canvas horizon paths and background colors. |
| `render/FarmScenery.java` | Environment meshes including the coastal beacon, autumn cart and twilight lanterns. |
| `render/FarmhandModel.java` | Articulated human figure, straw hat, work clothes, tools and action poses. |
| `render/FarmMotion.java` | World-space movement and animation driven by the action snapshot and game speed. |
| `render/FarmSurfaceView.java` | Canvas surface, landscape backdrop, camera fitting and counters. |
| `res/drawable/ic_launcher.xml` | Original garden-plot launcher mark. |

Coordinates use farm tiles for X and Y, with Z up. Tiles remain legible through differences in
silhouette and material. Ripe stems are tall and golden. Dry sprouts have short green leaves.
Watered beds have dark soil and a small blue puddle. Harvested beds retain low stubble. The pale
ring follows the human character, including through a turn during `walkTo`.

`FarmRenderingTest` checks the assigned environments in narrow viewports on all level layouts and
checks the route and reset behavior. See [ROADMAP.md](ROADMAP.md) for the latest device checks.
