package com.example.playtimeroles;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlaytimeCommand implements CommandExecutor {

    private final PlaytimeRolesPlugin plugin;

    public PlaytimeCommand(PlaytimeRolesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Эта команда только для игроков!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid;

        if (args.length > 0) {
            // Проверка на разрешение для просмотра чужого времени
            if (!player.hasPermission("playtimeroles.admin")) {
                player.sendMessage("У вас нет разрешения на просмотр времени других игроков!");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("Игрок не найден!");
                return true;
            }
            uuid = target.getUniqueId();
            player.sendMessage("Время игры " + target.getName() + ": " + plugin.formatPlaytime(plugin.getTotalPlaytime(uuid)));
        } else {
            uuid = player.getUniqueId();
            player.sendMessage("Ваше время игры: " + plugin.formatPlaytime(plugin.getTotalPlaytime(uuid)));
        }

        return true;
    }
}
