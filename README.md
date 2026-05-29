# EverCrops: Dynamic Trees

**Dynamic Trees' trees keep growing while you're away.**

This is an expansion for **[EverCrops](https://www.curseforge.com/minecraft/mc-mods/evercrops)** that adds catch-up growth support for **[Dynamic Trees](https://www.curseforge.com/minecraft/mc-mods/dynamictrees)**. Install all three together — EverCrops handles the vanilla crops, Dynamic Trees provides the trees, and this expansion makes those trees grow even when nobody's nearby.

---

## How it works

Dynamic Trees, like vanilla, only grows trees while their chunk is loaded. EverCrops: Dynamic Trees doesn't simulate growth on unloaded chunks (that would be expensive and laggy). Instead, it tracks **when each tree last ticked**, and on the next random tick after the chunk reloads, it **catches the tree up** based on how much game time has elapsed.

Walk away for an hour, a day, a week — when you come back and the area reloads, your trees advance as if they had been growing the whole time. A sapling you planted before heading out can be a full tree when you return.

> **Growth is real Dynamic Trees growth.** Catch-up runs the *exact same* growth logic Dynamic Trees would have run normally — it just runs the steps that were missed. That means all of Dynamic Trees' own rules still apply: soil fertility, light, biome suitability, and space to grow. A tree that wouldn't have grown if you'd been standing there won't be force-grown by catch-up either.

> **Fully-loaded areas only.** Trees only catch up once the area around them has finished loading, so the missed growth is applied correctly instead of being skipped the instant a chunk pops in.

> **Pre-existing trees:** Trees that already existed in your world before this mod was installed get registered the first time their chunk reloads near you, so they'll start catching up from that point forward (no retroactive growth burst for trees planted before install).

> **Note:** In singleplayer, the world only ticks while you're playing. Catch-up is based on **in-game time elapsed**, not real-world time, so closing the game and coming back tomorrow won't fast-forward your forest.

---

## What's covered

- 🌳 **Grown trees** — keep expanding toward their full size while their chunk is unloaded, picking up every growth step they missed.
- 🌱 **Saplings** — sprout into trees while you're away, instead of waiting frozen until you walk back.

Both newly planted and long-established trees are tracked automatically.

---

## Settings

A server config lets you tune catch-up to your world:

- **Catch-up on/off** — master switch for the whole feature.
- **Catch-up speed** — tune how fast trees catch up to match your Dynamic Trees `treeGrowthMultiplier`.
- **Sapling catch-up on/off** — turn just the sapling side on or off, independently of grown trees.
- **Auto-cleanup** — a quiet background pass that forgets trees which have been chopped down or died, so the mod stays light and doesn't track things that aren't there anymore.

---

## Admin commands

Server operators (permission level 2) get a `/evercropsDT` command set for testing and troubleshooting:

- `/evercropsDT inspect [x y z]` — show what a tree is tracking, including its current soil fertility, so you can see exactly why it is or isn't growing.
- `/evercropsDT simulate <ticks> <radius>` — fast-forward tracked trees by a chosen amount of time.
- `/evercropsDT tick <radius>` — apply pending catch-up immediately.
- `/evercropsDT register <radius>` — hand-register nearby trees (handy in testing).
- `/evercropsDT cleanup` — remove tracking entries for trees that no longer exist.

---

## Compatibility

This mod uses **only** `@Inject` mixins — no overwrites, no replacements. It introduces **no custom blocks and no block entities**; its tracking data lives in its own per-dimension `evercrops_dynamictrees.dat` file, separate from your world's other data.

- ✅ Requires the EverCrops base mod and Dynamic Trees
- ✅ Server-side friendly (clients don't need it installed for the growth logic to work)
- ✅ Respects all of Dynamic Trees' own growth conditions — catch-up never grows a tree further than Dynamic Trees itself would have

---

## Companion mods

- **[EverCrops](https://www.curseforge.com/minecraft/mc-mods/evercrops)** — required; handles vanilla crops (wheat, carrots, melons, sugar cane, saplings, and more)
- **[EverCrops: Farmer's Delight](https://www.curseforge.com/minecraft/mc-mods/evercrops-farmers-delight)** — catch-up growth for Farmer's Delight crops and blocks
- **[EverFurnace](https://www.curseforge.com/minecraft/mc-mods/everfurnace)** — give the same catch-up treatment to your furnaces, blast furnaces, and smokers
- **[EverHopper](https://www.curseforge.com/minecraft/mc-mods/everhopper)** — give the same catch-up treatment to your hoppers

---

## License

Licensed under **LGPL-3.0**. Source available on [GitHub](https://github.com/gottsch/gottsch-minecraft-EverCrops-Dynamic-Trees).
