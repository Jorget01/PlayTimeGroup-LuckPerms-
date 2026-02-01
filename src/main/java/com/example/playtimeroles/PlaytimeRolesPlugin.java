package com.example.playtimeroles;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlaytimeRolesPlugin extends JavaPlugin implements Listener {

    private LuckPerms luckPerms;
    private Map<UUID, Long> playerJoinTimes = new HashMap<>();
    private File playtimeFile;
    private FileConfiguration playtimeConfig;

    @Override
    public void onEnable() {
        // Регистрация событий
        getServer().getPluginManager().registerEvents(this, this);

        // Получение LuckPerms API
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            getLogger().severe("LuckPerms не найден! Плагин отключен.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Загрузка конфига
        saveDefaultConfig();

        // Файл для хранения времени игры
        playtimeFile = new File(getDataFolder(), "playtime.yml");
        if (!playtimeFile.exists()) {
            try {
                playtimeFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Не удалось создать файл playtime.yml: " + e.getMessage());
            }
        }
        playtimeConfig = YamlConfiguration.loadConfiguration(playtimeFile);

        // Загрузка данных о времени игры
        loadPlaytimeData();

        // Регистрация команды
        getCommand("playtime").setExecutor(new PlaytimeCommand(this));

        // Планировщик для проверки ролей каждые 5 минут
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndGrantRoles();
            }
        }.runTaskTimer(this, 0L, 6000L); // 6000 ticks = 5 минут

        getLogger().info("PlaytimeRolesPlugin включен!");
    }

    @Override
    public void onDisable() {
        // Сохранить время для онлайн игроков перед отключением
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (playerJoinTimes.containsKey(uuid)) {
                long joinTime = playerJoinTimes.get(uuid);
                long playtime = System.currentTimeMillis() - joinTime;
                long totalPlaytime = getTotalPlaytime(uuid) + playtime;
                setTotalPlaytime(uuid, totalPlaytime);
            }
        }
        // Сохранение данных
        savePlaytimeData();
        getLogger().info("PlaytimeRolesPlugin отключен!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerJoinTimes.put(player.getUniqueId(), System.currentTimeMillis());
        // Проверка ролей при входе
        checkAndGrantRolesForPlayer(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (playerJoinTimes.containsKey(uuid)) {
            long joinTime = playerJoinTimes.get(uuid);
            long playtime = System.currentTimeMillis() - joinTime;
            long totalPlaytime = getTotalPlaytime(uuid) + playtime;
            setTotalPlaytime(uuid, totalPlaytime);
            playerJoinTimes.remove(uuid);
        }
    }

    public long getTotalPlaytime(UUID uuid) {
        long total = playtimeConfig.getLong(uuid.toString(), 0L);
        if (playerJoinTimes.containsKey(uuid)) {
            total += System.currentTimeMillis() - playerJoinTimes.get(uuid);
        }
        return total;
    }

    public void setTotalPlaytime(UUID uuid, long time) {
        playtimeConfig.set(uuid.toString(), time);
    }

    private void loadPlaytimeData() {
        // Данные загружаются при загрузке конфига
    }

    private void savePlaytimeData() {
        try {
            playtimeConfig.save(playtimeFile);
        } catch (IOException e) {
            getLogger().severe("Не удалось сохранить playtime.yml: " + e.getMessage());
        }
    }

    private void checkAndGrantRoles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAndGrantRolesForPlayer(player);
        }
    }

    private void checkAndGrantRolesForPlayer(Player player) {
        FileConfiguration config = getConfig();
        List<Map<?, ?>> roles = config.getMapList("roles");

        UUID uuid = player.getUniqueId();
        long totalPlaytime = getTotalPlaytime(uuid);
        getLogger().info("Игрок " + player.getName() + " общее время: " + formatPlaytime(totalPlaytime));

        for (Map<?, ?> roleMap : roles) {
            String role = (String) roleMap.get("role");
            long requiredTime = parseTime((String) roleMap.get("time"));
            getLogger().info("Проверка роли " + role + " требуется: " + formatPlaytime(requiredTime));

            if (totalPlaytime >= requiredTime) {
                grantRole(uuid, role);
            }
        }
    }

    private long parseTime(String timeStr) {
        // Парсинг времени, например "1h" -> 3600000 ms
        if (timeStr.endsWith("h")) {
            return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 3600000L;
        } else if (timeStr.endsWith("m")) {
            return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 60000L;
        } else if (timeStr.endsWith("s")) {
            return Long.parseLong(timeStr.substring(0, timeStr.length() - 1)) * 1000L;
        }
        return 0L;
    }

    private void grantRole(UUID uuid, String role) {
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user != null) {
            Node node = Node.builder("group." + role).build();
            if (!user.getNodes().contains(node)) {
                user.data().add(node);
                luckPerms.getUserManager().saveUser(user);
                getLogger().info("Игроку " + uuid + " выдана роль " + role);
            }
        }
    }

    public String formatPlaytime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }
}
