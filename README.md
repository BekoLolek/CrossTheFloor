# CrossTheFloor

A multiplayer minigame where players race across a tiled floor of colored blocks. Every few seconds a safe block type is announced — all other tiles disappear, and anyone standing on the wrong block falls and respawns at the start. The floor rebuilds with a new random layout each round. Race to reach the end platform — the game can end when the first player finishes or after a configurable number of players cross the finish line.

**Paper 1.21.4+ | Java 21**

## Features

- **Color Floor Minigame** — Race across a 12-wide path of 2x2 colored concrete tiles. Stand on the announced safe block or fall!
- **Three Difficulty Tiers** — Easy (8 block types, 6s rounds), Medium (10 types, 4s), Hard (14 types, 3s). Safe blocks appear less frequently on harder modes.
- **Arena System** — Create multiple arenas with `/ctf create`. An outline preview shows the arena bounds before confirming.
- **Auto-Generated Paths** — The plugin builds the full arena structure: start platform, tiled path, end platform, glass walls, and fall zone.
- **Randomized Every Round** — Floor layout randomizes after each round to prevent memorization.
- **Ready Check System** — Players join the lobby, get hotbar items (Ready / Leave), and warnings if they don't ready up.
- **Join via Signs or Commands** — Place `[CTF]` signs or use `/ctf join <arena>`.
- **Multi-Winner Mode** — Configure how many players must finish before the game ends. Set `winners-required` to keep the game running until X players cross the finish line.
- **Per-Placement Rewards** — Define different Vault money and console command rewards for 1st, 2nd, 3rd place, etc. Extra finishers beyond the defined tiers receive the last defined reward.
- **Statistics & Leaderboards** — Tracks games played, wins, losses, falls, win rate, fastest win, and win streaks.
- **PlaceholderAPI Support** — All stats exposed as placeholders for scoreboards and holograms.
- **Inventory Protection** — Player inventories are saved on join and fully restored on leave/game end.
- **Hot-Reload** — Reload config and arenas without restarting.

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/ctf join <arena>` | Join an arena lobby | `crossthefloor.play` |
| `/ctf leave` | Leave the current game | `crossthefloor.play` |
| `/ctf ready` | Ready up in the lobby | `crossthefloor.play` |
| `/ctf create <name> <difficulty>` | Start arena creation | `crossthefloor.admin` |
| `/ctf confirm` | Confirm and build the arena | `crossthefloor.admin` |
| `/ctf cancel` | Cancel pending arena creation | `crossthefloor.admin` |
| `/ctf delete <name>` | Delete an arena | `crossthefloor.admin` |
| `/ctf list` | List all arenas | `crossthefloor.admin` |
| `/ctf reload` | Reload configuration | `crossthefloor.admin` |
| `/ctf stats` | View your own stats | `crossthefloor.stats` |
| `/ctf stats <player>` | View another player's stats | `crossthefloor.stats.others` |
| `/ctf stats top [stat]` | View top 10 leaderboard | `crossthefloor.stats` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `crossthefloor.play` | Join games via sign or command | Everyone |
| `crossthefloor.admin` | Create/delete arenas, reload config | OP |
| `crossthefloor.stats` | View own stats and leaderboards | Everyone |
| `crossthefloor.stats.others` | View other players' stats | OP |

## How It Works

### Creating an Arena

1. Stand where you want the arena's start platform and face the direction the path should extend.
2. Run `/ctf create <name> <easy|medium|hard>` — a particle outline shows the arena bounds.
3. Run `/ctf confirm` to build, or `/ctf cancel` to abort.

The plugin generates the full structure: a start platform, a tiled path (12 blocks wide, 60 blocks long), an end platform, glass walls on both sides, and a fall zone underneath.

### Creating a Join Sign

Place a sign with `[CTF]` on line 1 and the arena name on line 2. The sign auto-formats with the arena difficulty and player count.

### Playing

1. Right-click a join sign or use `/ctf join <arena>`.
2. Your inventory is saved and cleared. Use the green dye to ready up or the red dye to leave.
3. Once all players are ready (minimum 2), a 3-second countdown begins.
4. A safe block type is announced via title text. You have a few seconds to find and stand on it.
5. When the timer expires, all other tiles vanish. Wrong block? You fall and respawn at the start.
6. The floor rebuilds with a new random layout and a new safe block is chosen.
7. Players who reach the end platform are finished. In classic mode (`winners-required: 1`), the first finisher wins and the game ends immediately. In multi-winner mode, the game continues until the required number of players finish. All finishers receive placement-based rewards, and everyone is teleported back with inventories restored.

### Difficulty Tiers

| Setting | Easy | Medium | Hard |
|---------|------|--------|------|
| Block types | 8 | 10 | 14 |
| Round interval | 6 seconds | 4 seconds | 3 seconds |
| Safe block frequency | Every 2 tile rows | Every 3 tile rows | Every 4 tile rows |

## Configuration

```yaml
prefix: "&6[CTF] &r"
min-players: 2
winners-required: 1          # Players that must finish to end the game (1 = classic)
start-countdown: 3
rebuild-delay-ticks: 30      # Ticks between disappear and rebuild (30 = 1.5s)
fall-threshold: 3            # Blocks below path to trigger respawn
default-path-length: 60      # Path length in blocks

ready-check:
  warning-interval: 5        # Seconds between "not ready" warnings
  max-warnings: 3            # Kicked after this many warnings

difficulties:
  easy:
    block-count: 8
    safe-row-interval: 2
    round-interval: 6
    blocks: [WHITE_CONCRETE, ORANGE_CONCRETE, ...]
  # medium and hard similarly configured

# Per-placement rewards (1 = 1st place, 2 = 2nd place, etc.)
# If winners-required exceeds defined tiers, extra finishers get the last tier's reward.
default-rewards:
  1:
    money: 0                 # Vault money (0 = disabled)
    commands: []             # Console commands (%player% placeholder)
  # 2:
  #   money: 0
  #   commands: []
```

Per-arena rewards can be set in `arenas.yml` using the same numbered placement format.

## Placeholders

Requires [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/).

| Placeholder | Description |
|-------------|-------------|
| `%crossthefloor_stat_games_played%` | Player's total games |
| `%crossthefloor_stat_games_won%` | Player's wins |
| `%crossthefloor_stat_games_lost%` | Player's losses |
| `%crossthefloor_stat_total_falls%` | Player's total falls |
| `%crossthefloor_stat_win_rate%` | Player's win percentage |
| `%crossthefloor_stat_fastest_win%` | Player's fastest win (seconds) |
| `%crossthefloor_stat_best_streak%` | Player's best win streak |
| `%crossthefloor_global_total_games%` | Server-wide total games |
| `%crossthefloor_global_top_winner%` | Most winning player |
| `%crossthefloor_global_fastest_win_ever%` | Server fastest win |
| `%crossthefloor_top_<stat>_<pos>%` | Leaderboard player name |
| `%crossthefloor_topvalue_<stat>_<pos>%` | Leaderboard value |

## Dependencies

| Dependency | Required |
|------------|----------|
| [VersionAdapter](https://github.com/BekoLolek/VersionAdapter) | Yes |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | No (rewards disabled without it) |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | No |

## Installation

1. Place `CrossTheFloor.v1-BL.jar` in your server's `plugins/` folder.
2. Ensure `VersionAdapter.jar` is also in the `plugins/` folder.
3. Restart the server.
4. Edit `plugins/CrossTheFloor/config.yml` to customize settings.
5. Create arenas in-game with `/ctf create`.

## Part of the BekoLolek Plugin Ecosystem

Built by **Lolek** for the BekoLolek Minecraft network.
