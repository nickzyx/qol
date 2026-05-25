<div align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="assets/qol-pixel-logo.svg">
    <img alt="qol pixel logo" src="assets/qol-pixel-logo-dark.svg" width="480">
  </picture>
</div>

# qol

qol is a client-side Minecraft Forge 1.8.9 Mega Walls utility mod.

## Install

Copy `build\libs\qol-1.0.0.jar` into a Forge 1.8.9 mods folder that also has OneConfig.

## Features

### Energy Tracker

- Keybind to report current energy.
- Optional display of hits needed and ability names.
- Optional deathmatch-only mode.

### Phoenix Resurrection Tracker

- Shows Resurrection status in tablist with a Minecraft heart icon:
  - full heart: Resurrection available
  - empty heart: Resurrection used
- Optional chat notification when Resurrection is lost.
- Always runs during deathmatch only.

### Diamond Tracker

- Detects crafted diamond armor and diamond swords.
- Optional chat notifications for armor and swords.
- Optional deathmatch-only mode.

### Potion Tracker (Experimental)

- Tracks remaining healing potions per class.
- Optional deathmatch-only mode.

### Strength Tracker

- Detects Zombie, Dreadlord, and Herobrine strength.
- Strength alerts print 3 times by default.
- `Only Show One Alert Message` changes alerts to a single message.
- Optional deathmatch-only mode.

### Mobility Alert

- Alerts when enemy Spider or Enderman players are inside relevant mobility range.
- Keybind toggle for enabling or disabling alerts in-game.
- Configurable chat print interval from 1 to 5 seconds.
- Optional class toggles for Spider and Enderman.
- Optional deathmatch-only mode.

## Configuration

Open OneConfig and find the `qol` mod under the `Mega Walls` category. Most modules are disabled by default and can be enabled independently.

Tablist display for Phoenix, Diamond, and Potion modules can also be toggled with their configured keybinds while in Mega Walls.
