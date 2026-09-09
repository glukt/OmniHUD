# OmniHUD

**OmniHUD** is a single companion panel for OSRS: an interactive World Map guide, a vendor
directory, a monster bestiary with weakness info, a live combat / encounter HUD, loot tracking with
dryness and drop-date info, global search, and a Slayer assistant with built-in location and map
indicators.

> **Status — community plugin, stable but still in dev.** If you're trying it out, bug reports and
> rough-edge feedback are very welcome — see *Feedback* below.

---

## What's in it

| Tab | What it does |
|-----|--------------|
| **World Map** | Vendor/town markers drawn on the native map, an animated focus beacon, dungeon-aware panning, minimap vendor blips, and minimap toggle buttons |
| **Bestiary** | Monsters with full drop tables, per-kill rates, weaknesses/defences, spawn zones, Slayer level and quest locks; portrait art loaded from the wiki and cached to disk |
| **Combat HUD & Encounter tab** | Live DPS / DTPS / HPS meter with Live / Current Session / Total scopes, a browsable stack of past fights (same-target "×N" merging, e.g. 21 Hill Giant kills collapse to one row), hitsplat attribution, spell-name resolution, buff & aura uptime, a consumable + GP ledger, an Action Ledger, and a floating fight graph |
| **Loot tracker** | Per-source icon grid (obtained items only), kill counts, GP totals, GP/hr, sortable and searchable; click a source for every drop and how "dry" you are versus the wiki rate; one-time import from RuneLite's built-in Loot Tracker |
| **Slayer** | Task tracking, cross-sync with RuneLite's Slayer plugin, in-game master requirements, streak / point milestones, reward-unlock tracking (see what's locked/unlocked without visiting a master) |
| **Shops / Towns / Cart** | ~470 shops and ~100 towns, buy/sell price formulas, live in-game stock sync, and a shopping-cart planner with bank-GP sync |
| **Search** | One box across every monster drop table and every shop inventory — jump to any result on the map |

## Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/ec9f0df4-ae19-4002-b013-37a33d1be4bb" alt="OmniHUD panel and the World Map guide" width="840">
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/f48bf714-efd2-4e10-a962-5a88f9e72ad3" alt="Combat HUD and encounter breakdown" width="720">
</p>

<div align="center">
<table>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/ec297f82-cd2b-4bb0-ba53-d1804f2ffcc1" alt="Slayer task tracker" width="210"></td>
    <td><img src="https://github.com/user-attachments/assets/f11913b9-41c2-4739-bcc3-8020356550fe" alt="Slayer reward unlocks" width="210"></td>
    <td><img src="https://github.com/user-attachments/assets/c777ac34-65d4-4686-8d98-f5dab2a5ac28" alt="Shop directory and cart" width="210"></td>
    <td><img src="https://github.com/user-attachments/assets/162e0462-bf2b-4c3a-bdfc-2fcc171c9916" alt="Global search" width="210"></td>
  </tr>
  <tr>
    <td align="center"><sub>Slayer tasks</sub></td>
    <td align="center"><sub>Reward unlocks</sub></td>
    <td align="center"><sub>Shops &amp; cart</sub></td>
    <td align="center"><sub>Search</sub></td>
  </tr>
</table>
</div>

<p align="center">
  <img src="https://github.com/user-attachments/assets/70a3fd28-dbaa-492f-b11b-65542d895f67" alt="Combat / Encounter tab — damage breakdown, weapon/spell split, and the Action Ledger" width="460">
</p>

## Feedback

Testing and hitting bugs or awkward UX? Open an issue at
<https://github.com/glukt/OmniHUD/issues> with what you did and what you saw (a screenshot
helps a lot). Combat-HUD and loot-tab feedback especially — those areas are moving fast.

## Rules & privacy

Built to the [RuneLite third-party client rules](https://github.com/runelite/plugin-hub) and the
[rejected-features list](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features):

- **No chat automation.** The "share summary" buttons only put text on your clipboard — the
  plugin never types into, or sends, chat.
- **No opponent freeze timers.** Bind/freeze casts are noted in the fight log as a record of what
  you did; there is no on-screen "target frozen for Ns" indicator.
- **No reflection, no native code, no runtime downloads.** Pure Java 11.
- **No telemetry.** Monster/shop/Slayer data ships inside the jar and is read once at startup;
  loot history is stored locally per character. The only network calls are GETs to
  `oldschool.runescape.wiki` for monster portrait images (User-Agent identifies the plugin;
  results are disk-cached), the same pattern as RuneLite's own Wiki plugin.

## Notes

- **OmniHUD `0.1.0`** — JDK 11, built against RuneLite `1.12.38`.
- Internals keep the original `com.osrscopilot` package and `osrscopilot` config group, so
  existing installs keep their settings. Installs from the even older `worldmapdirectory` name are
  carried across on first start-up by a one-time settings migration
  (`OsrsCopilotPlugin.runConfigMigrations`) plus a data-dir move to `RUNELITE_DIR/osrscopilot`.

## License

BSD 2-Clause — see [`LICENSE`](LICENSE).
