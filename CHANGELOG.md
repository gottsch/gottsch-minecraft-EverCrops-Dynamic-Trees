# Changelog for EverCrops: Dynamic Trees (NeoForge 1.21.1)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


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

