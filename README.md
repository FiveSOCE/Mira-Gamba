# MiraGamba

## Download

**Latest compatibility release: v0.4.1**

[**Download MiraGamba-0.4.1.jar**](https://github.com/FiveSOCE/Mira-Gamba/releases/download/v0.4.1/MiraGamba-0.4.1.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Gamba/releases)

Physical 3x5 slot machines for the Mira Minecraft ecosystem.

## Wager tiers

- $5,000
- $50,000
- $100,000
- $1,000,000

All machines use the same weighted symbol table. Payouts scale with the selected wager.

## Placement

Stand where you want the machine, face the direction it should be placed, then use:

`/gamba create 5000`

or:

`/gamba create 50000`

or:

`/gamba create 100000`

or:

`/gamba create 1000000`

MiraGamba creates the backing wall, fifteen item frames, and a wall-mounted trigger button automatically.

## Gameplay

1. Player presses the machine button.
2. Vault charges the configured wager.
3. The fifteen item frames animate through weighted slot symbols.
4. Reels stop left-to-right.
5. Nine 5-reel paylines are evaluated:
   - top row
   - middle row
   - bottom row
   - diagonal down
   - diagonal up
6. Normal line wins start at 3 matching adjacent reels from the leftmost reel.
7. 4- and 5-reel matches pay progressively more.
8. Wild substitutes for normal symbols.
9. 3+ Scatter symbols pay anywhere on the board.
10. 6+ Orb symbols trigger Hold & Spin: 3 respins, new orbs lock and reset the counter to 3, and every locked orb carries a bet multiplier.
11. Filling all 15 positions awards the configured Grand multiplier.

## Default symbols

- Coal — 1.5x
- Iron — 2x
- Gold — 3x
- Emerald — 5x
- Diamond — 10x
- Netherite — 20x
- Nether Star jackpot — 50x

Weights, materials and multipliers are editable in `config.yml`.

## Commands

- `/gamba create <5000|50000|100000|1000000>`
- `/gamba remove`
- `/gamba list`
- `/gamba reload`

## Permissions

- `miragamba.use`
- `miragamba.admin`
- `miragamba.*`

## Requirements

- Paper 1.21.11
- Java 21
- Vault
- Vault-compatible economy provider


## v0.2.0 changes

- Machines are now 3 rows x 5 reels (15 item frames).
- Trigger buttons are explicitly mounted to the cabinet wall instead of floating.
- Different machines can spin concurrently, including multiple machines triggered by the same player.
- The $1,000,000 wager tier is enabled.
- Existing saved machines are upgraded to the 15-frame layout when missing frames are rebuilt.


## Four Guess card table

Place a table with:

`/gamba cards create <5000|50000|100000|1000000>`

The physical table has four named-card item frames and labeled buttons.

Stages:

1. Red or Black
2. Higher or Lower than the first card
3. Inside or Outside the values of the first two cards
4. Spades, Clubs, Hearts or Diamonds

Each correct guess reveals the next card. A wrong guess ends the run immediately. Completing all four stages pays **10x the table wager**.

Cards are drawn randomly from a shuffled 52-card deck without duplicate cards during a single game. Higher/Lower ties fail, and values equal to either boundary fail Inside/Outside.

The revealed item in each frame is named like `Queen of Hearts`, `7 of Clubs`, etc.

Commands:

- `/gamba cards create <bet>`
- `/gamba cards remove`
- `/gamba cards list`


## v0.4.0 slot payout overhaul

The 5x3 slot no longer requires five identical symbols to produce a normal win.

Base game:
- 3 matching adjacent reels from the leftmost reel can pay
- 4 and 5 matches scale upward
- Wild substitutes for normal symbols
- Scatter pays anywhere

Hold & Spin:
- 6+ Orbs on the initial spin trigger the feature
- starts with 3 respins
- every newly landed Orb locks and resets respins to 3
- each Orb carries a configurable multiplier value
- filling all 15 spaces awards the configured Grand multiplier

All symbol weights, 3/4/5 payouts, scatter values, orb values, respin chance and Grand multiplier are configurable.
