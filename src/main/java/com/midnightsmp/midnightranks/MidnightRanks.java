package com.midnightsmp.midnightranks;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class MidnightRanks extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("rank") != null) getCommand("rank").setExecutor(this);
        if (getCommand("setrank") != null) getCommand("setrank").setExecutor(this);
        if (getCommand("sc") != null) getCommand("sc").setExecutor(this);

        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayerPermissions(p);
        }

        getLogger().info("MidnightRanks (Tablist Fix & OP Sync) Loaded!");
    }

    @Override
    public void onDisable() {
        for (PermissionAttachment attachment : attachments.values()) {
            attachment.remove();
        }
        attachments.clear();
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }

    public double getRankTier(String rankName) {
        return getConfig().getDouble("ranks." + rankName.toUpperCase() + ".tier", 0.0);
    }

    public String getRankPrefix(String rankName) {
        String rawPrefix = getConfig().getString("ranks." + rankName.toUpperCase() + ".prefix", "&7[MEMBER]");
        return colorize(rawPrefix);
    }

    public boolean rankExists(String rankName) {
        return getConfig().contains("ranks." + rankName.toUpperCase());
    }

    public String getPlayerRank(Player player) {
        return getConfig().getString("players." + player.getUniqueId().toString(), "MEMBER").toUpperCase();
    }

    public void setPlayerRank(UUID uuid, String rankName) {
        getConfig().set("players." + uuid.toString(), rankName.toUpperCase());
        saveConfig();

        Player target = Bukkit.getPlayer(uuid);
        if (target != null && target.isOnline()) {
            updatePlayerPermissions(target);
        }
    }

    public double getSenderTier(CommandSender sender) {
        if (!(sender instanceof Player)) return 999.0;
        return getRankTier(getPlayerRank((Player) sender));
    }

    public void updatePlayerPermissions(Player player) {
        UUID uuid = player.getUniqueId();
        if (attachments.containsKey(uuid)) {
            player.removeAttachment(attachments.get(uuid));
        }

        PermissionAttachment attachment = player.addAttachment(this);
        attachments.put(uuid, attachment);

        String currentRank = getPlayerRank(player);
        double currentTier = getRankTier(currentRank);

        boolean hasStarPerm = false;

        if (getConfig().contains("ranks")) {
            for (String rName : getConfig().getConfigurationSection("ranks").getKeys(false)) {
                double rTier = getRankTier(rName);
                if (rTier <= currentTier) {
                    List<String> perms = getConfig().getStringList("ranks." + rName + ".permissions");
                    for (String perm : perms) {
                        if (perm.equals("*")) {
                            hasStarPerm = true;
                        } else {
                            attachment.setPermission(perm, true);
                        }
                    }
                }
            }
        }

        // FOUNDER/Tier 60+ ya '*' perm wale players ko OP sync karega taaki Vanilla F3+F switcher chal sake
        if (hasStarPerm || currentTier >= 60.0) {
            if (!player.isOp()) player.setOp(true);
        } else {
            if (player.isOp()) player.setOp(false);
        }

        // Tablist Name Prefix Fix
        String prefix = getRankPrefix(currentRank);
        player.setPlayerListName(prefix + " §f" + player.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updatePlayerPermissions(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (attachments.containsKey(uuid)) {
            event.getPlayer().removeAttachment(attachments.get(uuid));
            attachments.remove(uuid);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String rank = getPlayerRank(player);
        String prefix = getRankPrefix(rank);

        event.setFormat(prefix + " §f" + player.getName() + "§7: §f" + event.getMessage());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        double senderTier = getSenderTier(sender);

        if (command.getName().equalsIgnoreCase("rank")) {
            if (args.length == 0) {
                sender.sendMessage("§e--- MidnightRanks Commands ---");
                sender.sendMessage("§a/rank introduce <name> <tier> <prefix>");
                sender.sendMessage("§a/rank give <player> <rank>");
                sender.sendMessage("§a/rank addperm <rank> <perm>");
                sender.sendMessage("§a/rank list");
                return true;
            }

            String sub = args[0].toLowerCase();

            // 1. /rank introduce <name> <tier> <prefix>
            if (sub.equals("introduce") || sub.equals("create")) {
                if (senderTier < 60.0) {
                    sender.sendMessage("§cSirf FOUNDER tier (Tier 60+) hi naya rank introduce kar sakta hai!");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /rank introduce <rankName> <tier> <prefix>");
                    sender.sendMessage("§eExample: /rank introduce HERO 15 &a[HERO]");
                    return true;
                }

                String rankName = args[1].toUpperCase();
                double tier;
                try {
                    tier = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cTier number hona chahiye!");
                    return true;
                }

                StringBuilder prefixBuilder = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    prefixBuilder.append(args[i]).append(" ");
                }
                String prefix = prefixBuilder.toString().trim();

                getConfig().set("ranks." + rankName + ".tier", tier);
                getConfig().set("ranks." + rankName + ".prefix", prefix);
                if (!getConfig().contains("ranks." + rankName + ".permissions")) {
                    getConfig().set("ranks." + rankName + ".permissions", new ArrayList<String>());
                }
                saveConfig();

                sender.sendMessage("§aNew rank §e" + rankName + " §asuccessfully introduce kar diya gaya hai!");
                sender.sendMessage("§7Tier: §f" + tier + " §7| Prefix: " + colorize(prefix));
                return true;
            }

            // 2. /rank give <player> <rank>
            if (sub.equals("give")) {
                if (senderTier < 30.0) {
                    sender.sendMessage("§cAapke paas rank set karne ki permission nahi hai!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rank give <player> <rank>");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer online nahi hai!");
                    return true;
                }

                String targetRank = args[2].toUpperCase();
                if (!rankExists(targetRank)) {
                    sender.sendMessage("§cRank exist nahi karta! Pehle /rank introduce se banao.");
                    return true;
                }

                double targetTier = getRankTier(targetRank);
                if (sender instanceof Player && targetTier >= senderTier) {
                    sender.sendMessage("§cAap sirf apne se kam tier assign kar sakte hain!");
                    return true;
                }

                setPlayerRank(target.getUniqueId(), targetRank);
                String prefix = getRankPrefix(targetRank);
                sender.sendMessage("§aSuccessfully " + target.getName() + " ko " + prefix + " §arank de diya!");
                target.sendMessage("§aAapka rank update hokar " + prefix + " §aho gaya hai!");
                return true;
            }

            // 3. /rank addperm <rank> <permission>
            if (sub.equals("addperm")) {
                if (senderTier < 60.0) {
                    sender.sendMessage("§cSirf FOUNDER tier hi permissions add kar sakta hai!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rank addperm <rank> <permission.node>");
                    return true;
                }

                String rankName = args[1].toUpperCase();
                if (!rankExists(rankName)) {
                    sender.sendMessage("§cRank exist nahi karta!");
                    return true;
                }

                String perm = args[2].toLowerCase();
                List<String> perms = getConfig().getStringList("ranks." + rankName + ".permissions");
                if (!perms.contains(perm)) {
                    perms.add(perm);
                    getConfig().set("ranks." + rankName + ".permissions", perms);
                    saveConfig();

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        updatePlayerPermissions(p);
                    }
                    sender.sendMessage("§aPermission §e" + perm + " §a" + rankName + " rank par add ho gaya!");
                } else {
                    sender.sendMessage("§cYe permission pehle se added hai!");
                }
                return true;
            }

            // 4. /rank list
            if (sub.equals("list")) {
                Set<String> rankKeys = getConfig().getConfigurationSection("ranks").getKeys(false);
                sender.sendMessage("§e--- Available Ranks ---");
                for (String r : rankKeys) {
                    double t = getRankTier(r);
                    String pfx = getRankPrefix(r);
                    sender.sendMessage(pfx + " §8- §7Name: §f" + r + " §8| §7Tier: §f" + t);
                }
                return true;
            }
        }

        if (command.getName().equalsIgnoreCase("setrank")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /setrank <player> <rank>");
                return true;
            }
            return onCommand(sender, getCommand("rank"), label, new String[]{"give", args[0], args[1]});
        }

        if (command.getName().equalsIgnoreCase("sc")) {
            if (senderTier < 10.0) {
                sender.sendMessage("§cAapke paas staff chat ki permission nahi hai!");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage("§cUsage: /sc <message>");
                return true;
            }

            String msg = String.join(" ", args);
            String staffMsg = "§8[§cStaffChat§8] §e" + sender.getName() + "§7: §f" + msg;

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (getSenderTier(p) >= 10.0 || p.hasPermission("staff.chat")) {
                    p.sendMessage(staffMsg);
                }
            }
            return true;
        }

        return false;
    }
}
