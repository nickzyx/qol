<div align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="assets/qol-pixel-logo.svg">
    <img alt="qol pixel logo" src="assets/qol-pixel-logo-dark.svg" width="420">
  </picture>
</div>

---

qol is a client-side Minecraft Forge 1.8.9 quality-of-life mod for Hypixel Mega Walls.

## Install

[OneConfig](https://github.com/Polyfrost/OneConfig) is required. If your instance does not already have OneConfig, install the OneConfig Bootstrap mod for Forge 1.8.9.

Download the latest qol release jar from [Releases](https://github.com/nickzyx/qol/releases), then drop the jar into your instance's mods folder.

## Features

### Energy Announcer

Sends your current ability energy as a chat message with an in-game keybind.

### Interaction Guard

Prevents accidental right-click interactions with crafting tables, chests, furnaces, while holding a sword. Also an optional empty-hand-only mode.

### Phoenix Resurrection Tracker

Tracks Phoenix resurrection state and displays it with colored heart indicators in the tablist and nametags.

![Phoenix Resurrection example](examples/phoenix_example.png)

![Phoenix Resurrection tablist example](examples/phoenix_example_2.png)

### Diamond Tracker

Detects diamond armor and diamond swords that are not part of a player's class kit.

![Diamond Tracker example](examples/diamond_example.png)

### Strength Tracker

Detects Zombie, Dreadlord, and Herobrine strength activations.

![Strength Tracker example](examples/strength_example.png)

### Mobility Alert

Warns when enemy Spider or Enderman players are within relevant threat range.

### Transparent Snowmen

Renders ally Snowman mobs translucently, with an option to apply the render to all Snowmen.

![Transparent Snowmen example](examples/snowman_example.gif)

### Auto Update

Checks GitHub releases for newer versions and links to the releases page when an update is available.

---

## Experimental

> [!NOTE]
> Experimental features are best-effort indicators and are not guaranteed to be 100% accurate.

### Potion Tracker

Tracks health potions for players. Default predm tracking with option for only deathmatch.

![Potion Tracker example](examples/potion_tracker_example.png)

![Potion chat example](examples/potion_example.png)

### Spider Leap Alert

Detects nearby Spider Leap activation.

![Spider Leap Alert compass example](examples/mobility_example_1.gif)

![Spider Leap Alert example](examples/mobility_example_2.gif)

---

## Configuration

Most modules are disabled by default and can be enabled independently in OneConfig.

### General

- `Energy Announcer`: Sends your current ability energy as a chat message with the configured keybind.
- `Interaction Guard`: Prevents accidental right-click interactions with crafting tables, chests, furnaces, while holding a sword. Also an optional empty-hand-only mode.
- `Phoenix Resurrection Tracker`: Enables resurrection tracking and optional chat notifications.
- `Diamond Tracker`: Enables non-kit diamond tracking, chat notifications, and deathmatch-only mode.
- `Strength Tracker`: Enables strength detection, Zombie strength detection, repeated alert behavior, and deathmatch-only mode.
- `Mobility Alert`: Enables enemy Spider and Enderman range alerts, chat notifications, chat interval, keybind toggle, and deathmatch-only mode.
- `Auto Update`: Checks GitHub releases for newer versions and links to the releases page when an update is available.

### Render

- `Phoenix Resurrection Tracker`: Shows resurrection hearts in the tablist and nametags.
- `Diamond Tracker`: Shows non-kit diamond armor and sword icons in the tablist.
- `Visuals`: Enables ally-only transparent Snowman rendering, optional all-team rendering, the render keybind toggle, and opacity.

![Nametag indicator example](examples/indicator_example.png)

Phoenix nametag hearts are green when resurrection is available and red when it has been used. Potion nametags show the tracked potion count after the player name, such as `[2]`.

### Experimental

- `Potion Tracker`: Enables potion tracking, tablist display, nametag display, nametag color, chat notifications, and deathmatch-only mode.
- `Mobility Alert`: Enables the Spider Leap alert and HUD. Reposition using OneConfig `Edit HUD` button. 
- `Mobility HUD`: Controls the compass HUD toggle, position, and marker radius.

### Development
> [!NOTE]
> Only use when developing or debugging.
- `Developer Debug`: Writes game logs to `.minecraft/qol-debug`.
