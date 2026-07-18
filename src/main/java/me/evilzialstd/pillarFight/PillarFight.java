package me.evilzialstd.pillarFight;

import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;


public final class PillarFight extends JavaPlugin {

    private ArenaManager arenaManager;
    private final List<Material> allItems = new ArrayList<>();
    private EconomyHook economyHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        arenaManager = new ArenaManager();
        arenaManager.loadArenas(this);

        for (Material material : Material.values()) {
            if (material.isItem() && material != Material.AIR) {
                allItems.add(material);
            }
        }

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        PillarCommand cmd = new PillarCommand(this);
        getCommand("pillar").setExecutor(cmd);
        getCommand("pillar").setTabCompleter(cmd);

        economyHook = new EconomyHook();
        economyHook.setup();
        if (economyHook.isEnabled()) {
            getLogger().info("Vault economy hooked.");
        } else {
            getLogger().info("Vault economy not found — rewards disabled.");
        }

        getLogger().info("enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("disabled.");
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
    public List<Material> getAllItems() {
        return allItems;
    }
    public static final String PREFIX = "§x§8§0§8§0§8§0§l[§r§x§0§0§D§4§F§F§lС§x§0§0§C§3§F§F§lт§x§0§0§B§2§F§F§lо§x§0§0§A§1§F§F§lл§x§0§0§9§0§F§F§lб§x§0§0§7§F§F§F§lы§r§x§8§0§8§0§8§0§l]§r ";
    public EconomyHook getEconomyHook() {
        return economyHook;
    }

    public void reload(org.bukkit.command.CommandSender sender) {
        arenaManager.reload(this);
        sender.sendMessage(PREFIX + "§aКонфиг перезагружен.");
    }
}
