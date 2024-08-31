package com.finalwindserver.banplugin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TempBanPlugin extends JavaPlugin implements CommandExecutor, Listener {
    private static final String BAN_LIST_FILE = "banned_data.json";
    private Map<UUID, Long> bannedPlayers;
    private Map<String, Long> bannedIPs;
    private MyLogFilter logFilter;
    private final Gson gson = new Gson();

    @Override
    public void onEnable() {
        this.getCommand("tempban").setExecutor(this);
        this.getCommand("tempbanip").setExecutor(this);
        this.getCommand("unban").setExecutor(this);
        this.getCommand("unbanip").setExecutor(this);
        logFilter = new MyLogFilter();
        getServer().getPluginManager().registerEvents(this, this);

        loadBanData();
    }

    @Override
    public void onDisable() {
        saveBanData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (label.equalsIgnoreCase("tempban")) {
            return handleTempBan(sender, args);
        } else if (label.equalsIgnoreCase("tempbanip")) {
            return handleTempBanIP(sender, args);
        } else if (label.equalsIgnoreCase("unban")) {
            return handleUnban(sender, args);
        } else if (label.equalsIgnoreCase("unbanip")) {
            return handleUnbanIP(sender, args);
        }
        return false;
    }

    private boolean handleTempBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tempban <player> <duration>");
            return true;
        }

        OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(args[0]);

        long durationMillis = parseDuration(args[1], sender);
        if (durationMillis == -1) return true;

        UUID playerId = offlinePlayer.getUniqueId();
        long unbanTime = System.currentTimeMillis() + durationMillis;
        bannedPlayers.put(playerId, unbanTime);
        saveBanData();

        if (offlinePlayer.isOnline()) {
            ((Player) offlinePlayer).kickPlayer(createBanMessage());
        }
        return true;
    }

    private boolean handleTempBanIP(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /tempbanip <IP> <duration>");
            return true;
        }
        String ip = args[0];
        long durationMillis = parseDuration(args[1], sender);
        if (durationMillis == -1) return true;

        long unbanTime = System.currentTimeMillis() + durationMillis;
        bannedIPs.put(ip, unbanTime);
        saveBanData();
        Bukkit.getLogger().info(ChatColor.GREEN + "IP" + ip + "has been temporarily banned.");
        sender.sendMessage(ChatColor.GREEN + "IP " + ip + " has been temporarily banned.");
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unban <player or IP>");
            return true;
        }

        String target = args[0];
        if (bannedPlayers.remove(UUID.fromString(target)) != null || bannedIPs.remove(target) != null) {
            saveBanData();
            sender.sendMessage(ChatColor.GREEN + target + " has been unbanned.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + target + " is not currently banned.");
        }
        return true;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Long unbanTime = bannedPlayers.get(playerId);
        if (unbanTime != null && System.currentTimeMillis() < unbanTime) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, createBanMessage());
        }

        String ip = event.getAddress().getHostAddress();
        Long ipUnbanTime = bannedIPs.get(ip);
        if (ipUnbanTime != null && System.currentTimeMillis() < ipUnbanTime) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, "Your IP is temporarily banned.");
        }
    }

    private void saveBanData() {
        try (Writer writer = new FileWriter(BAN_LIST_FILE)) {
            Map<String, Object> allBans = new HashMap<>();
            Map<String, Long> playerBansToSave = new HashMap<>();
            bannedPlayers.forEach((uuid, time) -> playerBansToSave.put(uuid.toString(), time));
            allBans.put("players", playerBansToSave);
            allBans.put("ips", bannedIPs);
            gson.toJson(allBans, writer);
        } catch (IOException e) {
            getLogger().severe("Could not save banned data: " + e.getMessage());
        }
    }

    private void loadBanData() {
        File file = new File(BAN_LIST_FILE);
        if (!file.exists()) {
            bannedPlayers = new HashMap<>();
            bannedIPs = new HashMap<>();
            return;
        }

        try (Reader reader = new FileReader(file)) {
            Type setType = new TypeToken<Map<String, Map<String, Long>>>() {}.getType();
            Map<String, Map<String, Long>> allBans = gson.fromJson(reader, setType);

            // Load banned players
            Map<String, Long> loadedPlayerBans = allBans.getOrDefault("players", new HashMap<>());
            bannedPlayers = new HashMap<>();
            loadedPlayerBans.forEach((uuidString, time) -> bannedPlayers.put(UUID.fromString(uuidString), time));

            // Load banned IPs
            bannedIPs = allBans.getOrDefault("ips", new HashMap<>());
        } catch (IOException e) {
            getLogger().severe("Could not load banned data: " + e.getMessage());
            bannedPlayers = new HashMap<>();
            bannedIPs = new HashMap<>();
        }
    }

    private long parseDuration(String durationStr, CommandSender sender) {
        long durationMillis = 0;
        try {
            if (durationStr.endsWith("m")) {
                int minutes = Integer.parseInt(durationStr.replace("m", ""));
                durationMillis = TimeUnit.MINUTES.toMillis(minutes);
            } else if (durationStr.endsWith("h")) {
                int hours = Integer.parseInt(durationStr.replace("h", ""));
                durationMillis = TimeUnit.HOURS.toMillis(hours);
            } else if (durationStr.endsWith("d")) {
                int days = Integer.parseInt(durationStr.replace("d", ""));
                durationMillis = TimeUnit.DAYS.toMillis(days);
            } else {
                int hours = Integer.parseInt(durationStr);
                durationMillis = TimeUnit.HOURS.toMillis(hours);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid duration. Please enter a number followed by 'm', 'h', or 'd'.");
            return -1;
        }
        return durationMillis;
    }

    private String createBanMessage() {
        return "You are banned from this server.";
    }


    private boolean handleUnbanIP(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /unbanip <IP>");
            return true;
        }

        String ip = args[0];
        if (bannedIPs.containsKey(ip)) {
            bannedIPs.remove(ip);
            saveBanData();
            sender.sendMessage(ChatColor.GREEN + "IP " + ip + " has been unbanned.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "IP " + ip + " is not currently banned.");
        }
        return true;
    }
}
