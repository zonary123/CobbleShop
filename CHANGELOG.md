# Changelog

## [1.0.1] - 2025-12-03

### Features

- Added new command `/cobbleleaderboard refresh`, which refreshes the leaderboards.
- Added new command `/cobbleleaderboard types`, which lists all available leaderboard types.
- Leaderboards now refresh automatically every 1 minute.
- Added progress for leaderboard votes.

### Bug Fixes

- Fixed `HatchEggEvent` requirements; they are now optional.
- Fixed leaderboard crafting progression.
- Fixed leaderboard death progression.
- Fixed leaderboard message progression.
- Fixed handling of cases where there are 0 leaderboards.
- Fixed leaderboard Type pokemon. Now check if the pokemon is valid.
- Fixed SQL connection is not giving the rewards now it will give the rewards properly.
- Fixed leaderboard message now it give points properly.

### Optimizations

- No optimizations in this release.

## [1.0.0] - 2025-12-01

### Features

- Initial release of the project.