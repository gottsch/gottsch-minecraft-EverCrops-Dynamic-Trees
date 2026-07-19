# Changelog for EverCrops: Dynamic Trees (NeoForge 1.21.1)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [2.0.0] - 2026-07-15

### Changed

- Now requires EverCrops 4.0.0 or newer.
- Rebuilt on EverCrops 4.0.0's new shared add-on system. No change to how your trees grow.
- This add-on no longer needs to be installed on the client — only the server (or singleplayer world) needs it. Players can now join a server that has this add-on without installing it themselves.

### Fixed

- A tree that has just grown from a sapling now starts keeping up with its growth right away, instead of after a short delay.
- Fixed a lag spike that could happen when moving through the world quickly, especially over long-untouched or freshly explored land. A tree left alone for a very long time used to try to catch up on all its missed growth in one single burst, which could momentarily freeze the game. Catch-up growth is now spread out over multiple visits instead of happening all at once.
- Trees whose soil has lost all its fertility (and so can never grow again) are no longer tracked forever. The mod now stops keeping tabs on them after a few checks, keeping things lighter in worlds with a lot of untouched natural forest. If the soil becomes fertile again, tracking picks back up automatically.

### Added

- A setting for server owners to control how much catch-up growth is allowed to happen at once.
- A setting for server owners to control how quickly a tree that can no longer grow gets forgotten.

---

## [1.0.1] - 2026-06-06

### Fixed

- Fixed the add-on loading in the wrong order, which could make the game crash when certain other mods were installed. It now loads after Dynamic Trees and EverCrops, the way it always should have.

## [1.0.0] - 2026-5-27

### Added

- Trees from the Dynamic Trees mod now keep up with the world even when you're far away or logged off. When you return and the area loads back in, each tree grows by the amount it would have grown if you'd been standing there the whole time. No more coming home to a forest frozen exactly how you left it.
- Both freshly planted saplings and fully grown trees are covered, so a sapling you plant before heading out can sprout into a tree while you're gone.
- Trees only catch up once the area around them has fully loaded, so the missed growth is applied correctly instead of being skipped the instant a chunk pops in.
- Settings to make catch-up your own:
  - Turn the whole catch-up feature on or off.
  - Speed catch-up growth up or slow it down to match how fast your trees normally grow.
  - Turn sapling catch-up on or off on its own.
- Tidy-up that runs in the background and quietly forgets any tree that has been chopped down or has died off, so the feature stays light and doesn't track things that aren't there anymore.
- Commands for server owners and admins to hand-register trees, fast-forward growth, take a look at what a tree is tracking, and clean things up — handy for testing or sorting out a world.

