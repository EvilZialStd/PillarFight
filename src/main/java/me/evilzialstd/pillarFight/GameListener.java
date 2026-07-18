package me.evilzialstd.pillarFight;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class GameListener implements Listener {

    private final PillarFight plugin;

    GameListener(PillarFight plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(uuid);

        if (arena == null || !arena.isRunning()) {
            return;
        }

        //---start freeze---
        if (arena.isFrozen(uuid)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) {
                return;
            }

            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location stuck = from.clone();
                stuck.setYaw(to.getYaw());
                stuck.setPitch(to.getPitch());
                event.setTo(stuck);
            }
            return;
        }
        //---end freeze---

        if (!arena.isAlive(uuid)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getTo().getY() < arena.getyKill()) {
            arena.eliminate(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());

        if (arena == null || !arena.isRunning()) {
            return;
        }
        if (!arena.isAlive(player.getUniqueId())) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        arena.eliminate(player);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || !arena.isRunning()) {
            return;
        }
        arena.addPlacedBlock(event.getBlock().getLocation());

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getArenaManager().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onBlockForm(BlockFormEvent event) {
        // ищем, есть ли рядом идущий матч
        for (Player p : event.getBlock().getWorld().getPlayers()) {
            Arena arena = plugin.getArenaManager().getArenaByPlayer(p.getUniqueId());
            if (arena == null || !arena.isRunning()) continue;
            if (!arena.isAlive(p.getUniqueId())) continue;

            if (p.getLocation().distanceSquared(event.getBlock().getLocation()) <= 64 * 64) {
                arena.addPlacedBlock(event.getBlock().getLocation());
                return;
            }
        }
    }
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player.getUniqueId());
        if (arena == null || !arena.isRunning() || !arena.isAlive(player.getUniqueId())) {
            return;
        }
        arena.addPlacedBlock(event.getBlock().getLocation());
    }
}
