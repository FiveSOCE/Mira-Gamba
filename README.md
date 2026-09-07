# MiraGamba

Physical 3x3 slot machines for the Mira Minecraft ecosystem.

## Wager tiers

- $5,000
- $50,000
- $100,000

All machines use the same weighted symbol table. Payouts scale with the selected wager.

## Placement

Stand where you want the machine, face the direction it should be placed, then use:

`/gamba create 5000`

or:

`/gamba create 50000`

or:

`/gamba create 100000`

MiraGamba creates the backing wall, nine item frames, and the trigger button automatically.

## Gameplay

1. Player presses the machine button.
2. Vault charges the configured wager.
3. The nine item frames animate through weighted slot symbols.
4. Reels stop left-to-right.
5. Five paylines are evaluated:
   - top row
   - middle row
   - bottom row
   - diagonal down
   - diagonal up
6. Three matching symbols on a payline pays `bet × symbol multiplier`.
7. Multiple winning paylines stack, capped by the configured maximum payout multiplier.

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

- `/gamba create <5000|50000|100000>`
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
