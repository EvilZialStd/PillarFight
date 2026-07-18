package me.evilzialstd.pillarFight;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ArenaManager {
    private final List<Arena> arenas = new ArrayList<>();

    public void loadArenas(PillarFight plugin) {
        arenas.clear();

        var section = plugin.getConfig().getConfigurationSection("arenas");
        if (section == null) {
            plugin.getLogger().warning("В config.yml нет секции arenas");
            return;
        }

        for (String key : section.getKeys(false)) {
            int id = Integer.parseInt(key);

            var arenaSection = section.getConfigurationSection(key);
            if (arenaSection == null) {
                continue;
            }

            String worldName = arenaSection.getString("lobby.world");
            double x = arenaSection.getDouble("lobby.x");
            double y = arenaSection.getDouble("lobby.y");
            double z = arenaSection.getDouble("lobby.z");

            World world = Bukkit.getWorld(worldName);

            String centerWorldName = arenaSection.getString("center.world");
            double cx = arenaSection.getDouble("center.x");
            double cy = arenaSection.getDouble("center.y");
            double cz = arenaSection.getDouble("center.z");
            World centerWorld = Bukkit.getWorld(centerWorldName);
            if (centerWorld == null) {
                plugin.getLogger().warning("Мир center не найден: " + centerWorldName);
                continue;
            }
            Location center = new Location(centerWorld, cx, cy, cz);

            if (world == null) {
                plugin.getLogger().warning("Мир не найден: " + worldName);
                continue;
            }
            Location lobby = new Location(world, x, y, z);

            List<Location> pillars = new ArrayList<>();

            for (Map<?, ?> map : arenaSection.getMapList("pillars")) {
                String pWorldName = String.valueOf(map.get("world"));
                double px = ((Number) map.get("x")).doubleValue();
                double py = ((Number) map.get("y")).doubleValue();
                double pz = ((Number) map.get("z")).doubleValue();

                World pWorld = Bukkit.getWorld(pWorldName);
                if (pWorld == null) continue;

                pillars.add(new Location(pWorld, px, py, pz));

            }

            int yKill = arenaSection.getInt("y-kill");
            int interval = arenaSection.getInt("item-interval-seconds");
            int maxPlayers = arenaSection.getInt("max-players");
            int countdown = arenaSection.getInt("countdown-seconds", 5);
            int matchDuration = arenaSection.getInt("match-duration-seconds", 900);
            int itemStopBefore = arenaSection.getInt("item-bossbar-stop-before-end-seconds", 180);
            int cleanupRadius = arenaSection.getInt("cleanup-radius", 64);


            Arena arena = new Arena(
                    id,
                    lobby,
                    pillars,
                    yKill,
                    interval,
                    maxPlayers,
                    center,
                    countdown,
                    matchDuration,
                    itemStopBefore,
                    cleanupRadius,
                    plugin
            );
            arenas.add(arena);
        }
    }

    public Arena getArenaById(int id) {
        for (Arena arena : arenas) {
            if (arena.getId() == id) {
                return arena;
            }
        }
        return null;
    }

    public Arena getArenaByPlayer(UUID uuid) {
        for (Arena arena : arenas) {
            if (arena.hasPlayer(uuid)) {
                return arena;
            }
        }
        return null;
    }

    public boolean join(Player player, PillarFight plugin, int mode) {
        UUID uuid = player.getUniqueId();
        if (getArenaByPlayer(uuid) != null) {
            player.sendMessage(PillarFight.PREFIX + "§cВы уже в игре.");
            return false;
        }
        for (Arena arena : arenas) {
            if (arena.isRunning()) {
                continue;
            }
            if (arena.getPlayers().size() >= arena.getMaxPlayers()) {
                continue;
            }
            if (mode != -1 && arena.getMaxPlayers() != mode) {
                continue;
            }

            arena.addPlayer(uuid);
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            player.setFireTicks(0);
            Location lobby = arena.getLobby();
            if (!player.getWorld().equals(lobby.getWorld())
                    || player.getLocation().distance(lobby) > 100) {
                player.teleport(lobby);
            }
            player.sendMessage(PillarFight.PREFIX + "§7Ожидание: " + arena.getPlayers().size()
                    + "/" + arena.getMaxPlayers() + " (арена " + arena.getId() + ")");
            if (arena.getPlayers().size() == arena.getMaxPlayers()) {
                arena.start(plugin);
            }
            return true;
        }
        if (mode == -1) {
            player.sendMessage(PillarFight.PREFIX + "§cНет свободной арены. Обратитесь к администратору.");
        } else {
            player.sendMessage(PillarFight.PREFIX + "§cНет свободной арены на " + mode
                    + " игроков. Обратитесь к администратору.");
        }
        return false;
    }

    public void leave(Player player) {
        UUID uuid = player.getUniqueId();
        Arena arena = getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            player.sendMessage(PillarFight.PREFIX + "§cВы не в игре.");
            return;
        }
        if (arena.isRunning() && arena.isAlive(uuid)) {
            arena.eliminate(player);
            return;
        }

        arena.removePlayer(uuid);
        player.sendMessage(PillarFight.PREFIX + "§aВы вышли с арены " + arena.getId());
    }

    public void start(Player player, PillarFight plugin) {
        Arena arena = getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            player.sendMessage(PillarFight.PREFIX + "§cВы не в игре.");
            return;
        }
        arena.start(plugin);
    }

    public void stop(Player player) {
        Arena arena = getArenaByPlayer(player.getUniqueId());
        if (arena == null) {
            player.sendMessage(PillarFight.PREFIX + "§cВы не в игре.");
            return;
        }

        arena.stop();
        player.sendMessage(PillarFight.PREFIX + "§eИгра на арене " + arena.getId() + " остановлена.");
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Arena arena = getArenaByPlayer(uuid);
        if (arena == null) {
            return;
        }
        if (arena.isRunning() && arena.isAlive(uuid)) {
            arena.eliminate(player);
        } else {
            arena.removePlayer(uuid);
        }
    }
    public void reload(PillarFight plugin) {
        for (Arena arena : new ArrayList<>(arenas)) {
            if (arena.isRunning()) {
                arena.stop();
            }
        }
        plugin.reloadConfig();
        loadArenas(plugin);
    }
}
