# 🂠 Dead or Alive — Minecraft Plugin

A **Spigot/Paper** plugin inspired by the first **Alice in Borderland** game, “Dead or Alive”.  
It manages playable rooms, teleports, and an event flow that moves players through the game logic.

## ✨ Features
- **Room system**: define and store playable rooms and their areas.
- **Teleport system**: define named teleport points and movement between locations.
- **Event flow**: start/stop the “Dead or Alive” game with titles, bossbar, eliminations, success/failure states.
- **Message manager**: centralized, configurable messages.

## 🔧 Commands
All subcommands are under `/doa`:

- `/doa reload` — Reload plugin settings and managers.
- `/doa setrooms` — Start the interactive room setup workflow.
- `/doa setteleports` — Start the interactive teleport setup workflow.
- `/doa start` — Start the “Dead or Alive” event in the configured room.

> The base command checks for use permission; tab completion exposes only what the sender is allowed to run.

## 🔑 Permissions
- `deadoralive.use` — Use the base `/doa` command.
- `deadoralive.reload` — Run `/doa reload`.
- `deadoralive.setrooms` — Run `/doa setrooms`.
- `deadoralive.setteleports` — Run `/doa setteleports`.
- `deadoralive.start` — Run `/doa start`.

## 📂 Main Classes / Managers
- `DeadOrAlive.java` — Plugin main; registers managers and command.
- `DoaCommand.java` — Command executor & tab completer for `/doa`.
- `RoomManager.java` — Loads/saves rooms; room lookups and cache.
- `RoomSetupManager.java` — Interactive room setup flow (click/place protections, etc.).
- `TeleportManager.java` — Loads/saves teleports; movement rules and checks.
- `TeleportSetupManager.java` — Interactive teleport setup flow.
- `EventManager.java` — Core “Dead or Alive” game logic (titles, bossbar, elimination, success/failure).
- `MessageManager.java` — Centralized, reloadable message access.

## 🗃️ Data / Config Files (in the plugin data folder)
- `rooms.yml` — Stored room definitions.
- `teleports.yml` — Stored teleport points.
- `players.yml` — Player-related temporary/state data (when used).
- `messages/*` — Message storage handled by `MessageManager`.
- Temporary folders/files such as `teleports_temp/` or `temporal/` during setup flows.

