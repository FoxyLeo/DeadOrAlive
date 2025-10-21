# 🂠 Dead or Alive — Minecraft Plugin

A **Spigot/Paper** plugin inspired by the first **Alice in Borderland** game, **Dead or Alive**.
It orchestrates the setup, logic, and round flow for a multiplayer challenge where players must
choose doors, survive lethal rooms, and advance before the timer runs out.

## ✨ Features
- **Room system**: define and persist Dead/Safe rooms, including the stage order for each round.
- **Teleport network**: configure interactive teleport pads that move players between rooms and stages.
- **Game flow**: start and stop the *Dead or Alive* event with bossbars, titles, chat briefings, and eliminations.
- **Countdown & damage loop**: configurable timers per stage with progressive reductions and lethal damage ticks when time expires.
- **Respawn & lobby handling**: eliminated players are sent back to a configurable lobby radius with adventure/spectator safeguards.
- **Configurable messages**: multi-language message files (English and Spanish) used across every workflow.
- **Setup protections**: setup modes cancel block breaks and store temporary data under `temporal/` to keep interactive flows safe.

## 🔧 Commands
All subcommands are under `/doa`:

- `/doa setrooms` — Start the interactive room setup workflow.
- `/doa setteleports` — Start the interactive teleport setup workflow.
- `/doa start` — Start the *Dead or Alive* event in the configured room network.
- `/doa reload` — Reload plugin configuration, rooms, teleports, and messages.

> The base command checks for permission; tab completion exposes only what the sender is allowed to run.

## 🔑 Permissions
- `deadoralive.use` — Use the base `/doa` command.
- `deadoralive.setrooms` — Run `/doa setrooms`.
- `deadoralive.setteleports` — Run `/doa setteleports`.
- `deadoralive.start` — Run `/doa start`.
- `deadoralive.reload` — Run `/doa reload`.

## 📂 Main Classes / Managers
- `DeadOrAlive.java` — Plugin main class; registers managers, listeners, and the command.
- `DoaCommand.java` — Command executor & tab completer for `/doa`.
- `EventManager.java` — Core *Dead or Alive* gameplay controller (stages, timer, bossbar, eliminations, disconnects).
- `RoomManager.java` — Loads and caches room definitions, including type (safe/dead) metadata.
- `RoomSetupManager.java` — Interactive room setup handler.
- `TeleportManager.java` — Loads teleports, listens for pad usage, and enforces protections.
- `TeleportSetupManager.java` — Interactive teleport setup handler.
- `MessageManager.java` — Centralized access to localized messages.

## 🗃️ Data / Config Files (in the plugin data folder)
- `config.yml` — Language, event timers, damage values, and lobby respawn coordinates.
- `rooms.yml` — Stored room locations and their type (safe/dead).
- `teleports.yml` — Teleport pad definitions and their destination rooms.
- `messages/en.yml` — English messages.
- `messages/es.yml` — Spanish messages.
- `temporal/players.yml` — Persisted participants for reconnect handling during events.

Temporary configuration states for setup flows are handled automatically inside the plugin data folder.

## 🕹️ Gameplay Summary
1. Operators configure rooms (`/doa setrooms`) and teleports (`/doa setteleports`) to map safe/dead paths.
2. The event starts with `/doa start`, teleporting online players into the first stage.
3. Players receive an intro title and chat briefing while the countdown prepares to start.
4. When the bossbar timer begins, players must choose teleport pads to reach safe rooms before time expires.
5. Entering a dead room queues the player for elimination after a configurable delay; safe rooms advance the stage and shorten the timer.
6. If the timer ends, remaining players take periodic damage until only survivors remain; eliminated players respawn at the lobby.
