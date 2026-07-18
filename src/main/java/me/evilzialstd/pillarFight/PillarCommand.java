package me.evilzialstd.pillarFight;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import java.util.*;

public class PillarCommand implements CommandExecutor, TabCompleter {
    private final PillarFight plugin;

    public PillarCommand(PillarFight plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PillarFight.PREFIX + "§cЭта команда только для игроков.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(PillarFight.PREFIX + "§cИспользуй: /pillar join <auto|2|4|8> | leave | start | stop | reload");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join" -> {
                int mode = -1; // auto
                if (args.length >= 2 && !args[1].equalsIgnoreCase("auto")) {
                    try {
                        mode = Integer.parseInt(args[1]);
                        if (mode != 2 && mode != 4 && mode !=8) {
                            player.sendMessage(PillarFight.PREFIX + "§cРежим: auto, 2, 4 или 8");
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(PillarFight.PREFIX + "§cРежим: auto, 2, 4 или 8");
                        return true;
                    }
                }
                plugin.getArenaManager().join(player, plugin, mode);
            }
            case "leave" -> plugin.getArenaManager().leave(player);
            case "start" -> plugin.getArenaManager().start(player, plugin);
            case "stop" -> plugin.getArenaManager().stop(player);
            case "reload" -> {
                if (!player.isOp()) {
                    player.sendMessage(PillarFight.PREFIX + "§cНет прав.");
                    return true;
                }
                plugin.reload(player);
            }
            default -> player.sendMessage(PillarFight.PREFIX + "§cНеизвестная команда");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(
                    args[0],
                    Arrays.asList("join", "leave", "start", "stop", "reload"),
                    new ArrayList<>()
            );
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return StringUtil.copyPartialMatches(
                    args[1],
                    Arrays.asList("auto", "2", "4", "8"),
                    new ArrayList<>()
            );
        }

        return Collections.emptyList();
    }
}
