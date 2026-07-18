package me.evilzialstd.pillarFight;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Arena {
    private final int id;
    private final List<UUID> players = new ArrayList<>();
    private final List<UUID> alive = new ArrayList<>();
    private final List<Location> placedBlocks = new ArrayList<>();
    private GameState state = GameState.WAITING;
    private final PillarFight plugin;

    // Arena information
    private final Location lobby;
    private final Location center;
    private final List<Location> pillars;
    private final int yKill;
    private final int itemIntervalSeconds;
    private final int maxPlayers;
    private final int countdownSeconds;
    private final int itemBossbarStopBeforeEndSeconds;
    private final int cleanupRadius;
    private final Set<UUID> frozen = new HashSet<>();
    private final int matchDurationSeconds;
    private BukkitTask countdownTask;
    private BukkitTask matchTask;
    private BossBar matchBossbar;
    private int matchRemainingSeconds;
    private int itemRemainingSeconds;
    private boolean itemsEnabled;
    private boolean matchBarShown;

    // Constructor
    public Arena(int id, Location lobby, List<Location> pillars, int yKill, int itemIntervalSeconds, int maxPlayers, Location center, int countdownSeconds, int matchDurationSeconds, int itemBossbarStopBeforeEndSeconds, int cleanupRadius, PillarFight plugin) {
        this.id = id;
        this.lobby = lobby;
        this.pillars = pillars;
        this.yKill = yKill;
        this.itemIntervalSeconds = itemIntervalSeconds;
        this.maxPlayers = maxPlayers;
        this.center = center;
        this.countdownSeconds = countdownSeconds;
        this.matchDurationSeconds = matchDurationSeconds;
        this.itemBossbarStopBeforeEndSeconds = itemBossbarStopBeforeEndSeconds;
        this.cleanupRadius = cleanupRadius;
        this.plugin = plugin;
    }

    // --- start Getters ---
    public int getId() {
        return id;
    }
    public Location getLobby() {
        return lobby;
    }
    public int getyKill() {
        return yKill;
    }
    public int getMaxPlayers() {
        return maxPlayers;
    }
    public List<UUID> getPlayers() {
        return players;
    }
    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }
    // --- end Getters ---

    // --- start Players ---
    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }
    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }
    public void removePlayer(UUID uuid) {
        players.remove(uuid);
        alive.remove(uuid);
    }
    public boolean isAlive(UUID uuid) {
        return alive.contains(uuid);
    }
    // --- end Players ---

    public boolean isRunning() {
        return state == GameState.RUNNING;
    }

    // --- start match flow ---
    public void start(PillarFight plugin) {
        if (players.size() != maxPlayers) {
            return;
        }
        state = GameState.RUNNING;

        alive.clear();
        alive.addAll(players);

        // teleport players
        for (int i = 0; i < players.size(); i++) {
            Player p = Bukkit.getPlayer(players.get(i));
            if (p == null) continue;

            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setFireTicks(0);

            if (i < pillars.size()) {
                Location spawn = pillars.get(i).clone();
                spawn.setDirection(center.toVector().subtract(spawn.toVector()));
                p.teleport(spawn);
            }
        }

        frozen.clear();
        frozen.addAll(alive);
        startCountdown(plugin);
    }

    public void stop() {
        for (UUID uuid : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.getInventory().clear();
                p.getInventory().setArmorContents(null);
                p.teleport(lobby);
            }
        }
        endMatch();
    }

    public void eliminate(Player player) {
        UUID uuid = player.getUniqueId();
        if (!alive.contains(uuid))  {
            return;
        }
        alive.remove(uuid);
        players.remove(uuid);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.teleport(lobby);
        if (matchBossbar != null) {
            matchBossbar.removePlayer(player);
        }
        player.sendMessage(PillarFight.PREFIX + "§cВы выбыли.");
        checkWin();
    }
    public void checkWin() {
        if (state != GameState.RUNNING) {
            return;
        }
        if (alive.isEmpty()) {
            endMatch();
            return;
        }
        if (alive.size() == 1) {
            UUID uuid = alive.get(0);
            Player winner = Bukkit.getPlayer(uuid);

            if (winner != null) {
                int reward = calcReward();
                winner.sendMessage(PillarFight.PREFIX + "§6" + winner.getName() + " победил!");
                plugin.getEconomyHook().deposit(winner, reward);
                if (plugin.getEconomyHook().isEnabled()) {
                    winner.sendMessage(PillarFight.PREFIX + "§a+" + reward + " монет");
                }
                winner.getInventory().clear();
                winner.getInventory().setArmorContents(null);
                winner.teleport(lobby);
            }
            endMatch();
        }
    }
    private void endMatch() {
        itemsEnabled = false;
        matchBarShown = false;
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        frozen.clear();
        if (matchTask != null) {
            matchTask.cancel();
            matchTask = null;
        }
        if (matchBossbar != null) {
            matchBossbar.removeAll();
            matchBossbar = null;
        }
        clearPlacedBlocks();
        clearGravityBlocksAndLiquids();
        clearEntities();

        frozen.clear();
        state = GameState.WAITING;
        alive.clear();
        players.clear();
    }
    private  void beginFight(PillarFight plugin) {
        startItemSystem();
        startMatchTimer(plugin);
    }
    // --- end match flow ---

    //---start Arena cleaner---
    public void addPlacedBlock(Location loc) {
        placedBlocks.add(loc.clone());
    }

    public void clearPlacedBlocks() {
        for (Location loc : placedBlocks) {
            if (loc.getWorld() == null) continue;
            loc.getBlock().setType(Material.AIR);
        }
        placedBlocks.clear();
    }
    public void clearGravityBlocksAndLiquids() {
        if (center.getWorld() == null) return;
        int r = Math.min(cleanupRadius, 40);
        int minY = yKill;
        int maxY = center.getBlockY() + 40;
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        var world = center.getWorld();

        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = minY; y <= maxY; y++) {
                    var block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.hasGravity()
                            || type == Material.WATER
                            || type == Material.LAVA
                            || type == Material.BUBBLE_COLUMN) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }
    public void clearEntities() {
        for (Location pillar : pillars) {
            if (pillar.getWorld() == null) continue;
            for (Entity entity : pillar.getWorld().getNearbyEntities(
                    pillar, cleanupRadius, cleanupRadius, cleanupRadius)) {
                if (entity instanceof Player) continue;
                entity.remove();
            }
        }
    }
    // ---end Arena cleaner---

    // ---start countdown and timers---
    private void startCountdown(PillarFight plugin) {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        final int[] left = {
                countdownSeconds
        };

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isRunning()) {
                if (countdownTask != null) {
                    countdownTask.cancel();
                    countdownTask = null;
                }
                frozen.clear();
                return;
            }
            if (left[0] > 0) {
                for (UUID uuid : alive) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    p.sendTitle("§e" + left[0], "§7Приготовьтесь...", 0, 25, 5);
                }
                left[0]--;
                return;
            }
            for (UUID uuid : alive) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                p.sendTitle("§aСтарт!", "", 0, 20, 10);
            }
            frozen.clear();
            countdownTask.cancel();
            countdownTask = null;
            beginFight(plugin);
        }, 0L, 20L);
    }

    private String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }

    private void startMatchTimer(PillarFight plugin) {
        if (matchTask != null) {
            matchTask.cancel();
            matchTask = null;
        }
        if (matchBossbar != null) {
            matchBossbar.removeAll();
            matchBossbar = null;
        }

        matchRemainingSeconds = matchDurationSeconds;
        matchBarShown = false;

        matchTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isRunning()) {
                return;
            }
            matchRemainingSeconds--;

            if (matchRemainingSeconds < 0) {
                return;
            }

            if (itemsEnabled) {
                itemRemainingSeconds--;

                if (itemRemainingSeconds <= 0) {
                    giveRandomItems(plugin);
                    itemRemainingSeconds = itemIntervalSeconds;
                }

                for (UUID uuid : alive) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    p.sendActionBar(
                            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                    .legacySection()
                                    .deserialize("§aПредмет через: §e" + itemRemainingSeconds)
                    );
                }
            }

            if (!matchBarShown && matchRemainingSeconds <= itemBossbarStopBeforeEndSeconds) {
                stopItemSystems();
                startMatchBossBar();
            }

            if (matchBossbar != null) {
                matchBossbar.setTitle("До конца: " + formatTime(matchRemainingSeconds));
                matchBossbar.setProgress(
                        Math.max(0.0, matchRemainingSeconds / (double) itemBossbarStopBeforeEndSeconds)
                );
            }

            if (matchRemainingSeconds == 0) {
                int reward = calcReward();
                List<UUID> drawers = new ArrayList<>(alive);
                int share = drawers.isEmpty() ? 0 : reward / drawers.size();

                for (UUID uuid : drawers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;

                    p.sendMessage(PillarFight.PREFIX + "§eНичья! Время вышло.");
                    plugin.getEconomyHook().deposit(p, share);
                    if (plugin.getEconomyHook().isEnabled() && share > 0) {
                        p.sendMessage(PillarFight.PREFIX + "§a+" + share + " монет");
                    }
                    p.getInventory().clear();
                    p.getInventory().setArmorContents(null);
                    p.teleport(lobby);
                }
                endMatch();
            }
        }, 20L, 20L);
    }
    // ---end countdown and timers---

    // ---start items and bossbars---
    private void giveRandomItems(PillarFight plugin) {
        List<Material> items = plugin.getAllItems();
        if (items.isEmpty()) return;

        for (UUID uuid : alive) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            Material mat = items.get(ThreadLocalRandom.current().nextInt(items.size()));
            p.getInventory().addItem(new ItemStack(mat));
        }
    }

    private void startItemSystem() {

        itemRemainingSeconds = itemIntervalSeconds;
        itemsEnabled = true;

        for (UUID uuid : alive) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendActionBar(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacySection()
                                .deserialize("§aПредмет через: §e" + itemRemainingSeconds)
                );
            }
        }
    }

    private void stopItemSystems() {
        itemsEnabled = false;
    }

    private void startMatchBossBar() {
        if (matchBossbar != null) {
            matchBossbar.removeAll();
            matchBossbar = null;
        }

        matchBossbar = Bukkit.createBossBar(
                "До конца: " + formatTime(matchRemainingSeconds),
                BarColor.RED,
                BarStyle.SOLID
        );
        matchBossbar.setProgress(
                Math.max(0.0, matchRemainingSeconds / (double) itemBossbarStopBeforeEndSeconds)
        );

        for (UUID uuid : alive) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                matchBossbar.addPlayer(p);
            }
        }

        matchBarShown = true;
    }
    // ---end items and bossbars---

    // Economy
    private int calcReward() {
        int elapsed = matchDurationSeconds - Math.max(matchRemainingSeconds, 0);
        int reward = 1 + (int) ((elapsed / (double) matchDurationSeconds) * 9);
        if (reward < 1) reward = 1;
        if (reward > 10) reward = 10;
        return reward;
    }
}
