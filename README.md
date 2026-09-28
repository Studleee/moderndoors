# Modern Doors

Big, smoothly animated modern doors for Minecraft 26.3 (Fabric): mansion-style pivot doors, folding glass doors that fold up like an accordion, and sliding glass doors.

- **Any size:** build door frame blocks around an empty opening, up to 8 wide and 6 tall, then right-click a frame with a door. The door fills the opening. The corners of the frame are optional. The door hinges on whichever side of the opening you clicked nearer to.
- **Pivot doors:** one wide panel that swings on a pivot half a block in from the hinge side. At least 1 wide.
- **Folding glass doors:** one glass panel per block across, folding up against each other at the hinge side. At least 2 wide.
- **Sliding glass doors:** the hinge-side half stays put and the other half glides behind it. At least 2 wide.
- **Materials:** oak, spruce, birch, dark oak, cherry, pale oak, black steel, and white aluminum for every kind; concrete and dark concrete for pivot doors. Wood pivot and concrete doors are solid; the rest are glass in a frame.
- **Opening:** right-click, or power any part with redstone. Doors open away from whoever opened them.
- **Breaking:** breaking any part takes down the whole door and drops it once (nothing in creative). Frames stay.

**Crafting**

```
Door frames:
  Wood:          P S P          P = planks, S = stick      makes 4
  Metal:         I D I          I = iron ingot, D = black or white dye   makes 8
  Concrete:      C S C          C = light gray or gray concrete           makes 4

Doors (M = planks or concrete, F = black steel or white aluminum door frame, G = glass, I = iron ingot):
  Wood / concrete pivot:   M M      Glass pivot:   F G
                           M I                     F I
                           M M                     F G
  Folding:   F G F    (wood folding doors use planks as F)
             F G F
             F G F
  Sliding:   F G      (wood sliding doors use planks as F)
             F G
             F G
```

## Quick start

Double-click `run.bat`, or run it from a terminal in this folder. Minecraft opens with the mod loaded, and everything shows up in the **Modern Doors** creative tab. The first launch downloads Minecraft and takes a few minutes.

## Where things live

```
src/main/java/com/moderndoors/
  door/DoorKind.java, DoorMaterial.java   the kinds of door and what they're made of
  block/ModernDoorBlock.java              the door's invisible blocks: opening, redstone, collision, breaking
  block/ModernDoorBlockEntity.java        the door's size and opening animation
  item/ModernDoorItem.java                finds the framed opening and fits the door
  registry/ModBlocks.java                 every door and frame

src/client/java/com/moderndoors/client/
  ModernDoorRenderer.java                 draws the moving door
```

All textures, models, recipes, and names come from `tools/gen-doors.ps1`. Edit the colors there and run:

```
powershell -ExecutionPolicy Bypass -File tools\gen-doors.ps1
```

## Sharing the mod

Run `build.bat`. The mod is `build/libs/moderndoors-1.0.0.jar`. Players need Fabric Loader and Fabric API for Minecraft 26.3, plus the jar in their `mods` folder.
