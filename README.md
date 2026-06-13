# HandRefill

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A lightweight Paper plugin that **auto-refills your main hand** from your inventory the instant a held stack runs out, and shows a **live item count** on the scoreboard sidebar while you hold anything.

---

## Features

| Feature | Description |
|---|---|
| **Sidebar HUD** | While holding any item, a scoreboard panel on the right side of the screen shows the item name and total count across your whole inventory. Counts above 999 display as `999+`. |
| **Auto-Refill** | When your held stack hits zero (block placed, food eaten, arrow shot, etc.), the next matching stack is instantly moved to your hand. |
| **Hotbar priority** | Searches other hotbar slots first, then main inventory. Always picks the **largest available stack**. |
| **Exact item matching** | Matches by material **and** ItemMeta — enchanted swords won't accidentally refill from plain ones. |
| **Smart guards** | Refill is suppressed during any inventory interaction to prevent false triggers (see below). |
| **Per-player toggle** | `/handrefill toggle` lets each player opt out without affecting others. |

---

## Sidebar HUD

While you hold any item, a panel appears on the **right side of the screen**:

```
┌─────────────────┐
│  Stone Slab     │  ← item name (gold, bold)
│  ▣ Total: 192   │  ← total in inventory ("999+" if over 999)
└─────────────────┘
```

The display updates every ~200 ms and disappears automatically when your hand is empty.

---

## How It Works

The plugin runs a **1-tick polling loop** that compares the current held item to what was held on the previous tick. If the same hotbar slot went from non-empty → empty, a refill is attempted.

### Depletion causes caught automatically
- Placing blocks
- Eating food / drinking potions
- Shooting arrows or throwing tridents
- Any plugin-based item consumption

### Refill guards — what does NOT trigger a refill

| Action | Guard |
|---|---|
| Opening any inventory screen (chest, furnace, player inv…) | Inventory-open guard |
| Dragging the item away with your cursor | Cursor-empty guard |
| **Shift+Click** the hand slot (quick-move to inventory) | Manual-vacate flag |
| **Q / Ctrl+Q** (drop key) on the hand slot | Manual-vacate flag |
| **F** (off-hand swap) on the hand slot | Manual-vacate flag |
| **Number key** (1–9) while hovering the hand slot | Manual-vacate flag |
| **Number key** (1–9) targeting the hand slot from any other hovered slot | Manual-vacate flag |

---

## Usage

Just hold any item — the sidebar appears automatically. No configuration needed.

```
/handrefill toggle    — enable / disable auto-refill for yourself
/handrefill status    — check whether auto-refill is on or off
```

**Permission:** `handrefill.use` (default: all players)

---

## Requirements

| Requirement | Version |
|---|---|
| Server software | [Paper](https://papermc.io/downloads/paper) |
| Minecraft / Paper API | 26.1 (Minecraft 1.21.x) |
| Java | 25 |

---

## Installation

1. Download the latest `handrefill-*.jar` from the [Releases](../../releases) page.
2. Place the JAR in your server's `plugins/` directory.
3. Restart your server.
4. No configuration needed — the plugin is ready to use.

---

## Building from Source

```bash
git clone <repo-url>
cd handrefill
mvn package
```

The compiled JAR will be at `target/handrefill-1.0.0.jar`.

---

## License

This project is released under the [MIT License](LICENSE).
