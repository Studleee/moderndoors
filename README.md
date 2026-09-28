# Modern Doors

Big, smoothly animated modern doors for Minecraft 26.3 (Fabric): mansion-style pivot doors, folding glass doors that fold up like an accordion, sliding glass doors, and black aluminum panel doors that slide back into a base post.

- **Placing:** place a door anywhere and it's 3 tall: pivot and sliding doors are 2 wide, folding doors 3 wide. Clicking the left half of a block builds the door out to the right, and the other way around.
- **Pivot doors:** one wide panel that swings on a pivot half a block in from the hinge side.
- **Folding glass doors:** one glass panel per block across, folding up against each other at the hinge side.
- **Sliding glass doors:** the hinge-side half stays put and the other half glides behind it.
- **Materials:** oak, spruce, birch, dark oak, cherry, pale oak, black steel, and white aluminum for every kind; concrete and dark concrete for pivot doors. Wood pivot and concrete doors are solid; the rest are glass in a frame.
- **Opening:** right-click, or power any part with redstone. Doors open away from whoever opened them.
- **Breaking:** breaking any part takes down the whole door and drops it once (nothing in creative).
- **Black aluminum panel doors:** place a base post, then up to 8 panels in a straight line from it. Stack bases and panels up to 6 tall. Right-click any panel or the base (or power the base) and every panel slides back and stacks behind the post; do it again to slide them out.

**Crafting**

```
Doors (M = planks or concrete, F = planks or iron ingot, G = glass, I = iron ingot, D = black or white dye):
  Wood / concrete pivot:   M M      Wood glass pivot:   F G      Metal pivot:   D G
                           M I                          F I                     F I
                           M M                          F G                     F G
  Folding:   F G F        Metal folding:   F G F
             F G F                         F D F
             F G F                         F G F
  Sliding:   F G          Metal sliding:   F G
             F G                           F D
             F G                           F G

Black aluminum (N = iron nugget):
  Panel (makes 2):   N G N        Base (makes 2):   I
                     N G N                          D
                     N D N                          I
```

## Quick start

Double-click `run.bat`, or run it from a terminal in this folder. Minecraft opens with the mod loaded, and everything shows up in the **Modern Doors** creative tab. The first launch downloads Minecraft and takes a few minutes.

## Where things live

```
src/main/java/com/moderndoors/
  door/DoorKind.java, DoorMaterial.java   the kinds of door and what they're made of
  block/ModernDoorBlock.java              the door's invisible blocks: opening, redstone, collision, breaking
  block/ModernDoorBlockEntity.java        the door's size and opening animation
  block/DoorPanelBlock.java, DoorBaseBlock.java, DoorBaseBlockEntity.java   black aluminum panel doors
  item/ModernDoorItem.java                places a door
  registry/ModBlocks.java                 every block

src/client/java/com/moderndoors/client/
  ModernDoorRenderer.java                 draws the moving door
  DoorBaseRenderer.java                   draws panels sliding in and out of the base
```

All textures, models, recipes, and names come from `tools/gen-doors.ps1`. Edit the colors there and run:

```
powershell -ExecutionPolicy Bypass -File tools\gen-doors.ps1
```

## Sharing the mod

Run `build.bat`. The mod is `build/libs/moderndoors-1.0.0.jar`. Players need Fabric Loader and Fabric API for Minecraft 26.3, plus the jar in their `mods` folder.
