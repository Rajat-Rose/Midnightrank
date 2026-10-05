package com.midnightsmp.midnightranks;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class MidnightRanks extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Set<UUID> mutedPlayers = new HashSet<>();
    private final Map<UUID, Long> timeoutPlayers = new HashMap<>();
    private final Set<UUID> godModePlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        String[] cmds = {
            "rank", "setrank", "sc", "vanish", "ban", "unban", "kick", 
            "mute", "timeout", "warn", "tp", "gm", "fly", "invsee", "god", "heal", "staff"
        };
        for (String c : cmds) {
            if (getCommand(c) != null) getCommand(c).setExecutor(this);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayerPermissions(p);
        }

        getLogger().info("MidnightRanks v1.3 (Full Code Loaded Successfully)!");
    }

    @Override
    public void onDisable() {
        for (PermissionAttachment attachment : attachments.values()) {
            attachment.remove();
        }
        attachments.clear();
        vanishedPlayers.clear();
        mutedPlayers.clear();
        timeoutPlayers.clear();
        godModePlayers.clear();
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

    public boolean isStaff(Player player) {
        return getSenderTier(player) >= 20.0;
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

        if (hasStarPerm || currentTier >= 60.0) {
            if (!player.isOp()) player.setOp(true);
        } else {
            if (player.isOp()) player.setOp(false);
        }

        String prefix = getRankPrefix(currentRank);
        player.setPlayerListName(prefix + " §f" + player.getName());

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        int priority = 1000 - (int) currentTier;
        String teamName = (priority < 100 ? (priority < 10 ? "00" : "0") : "") + priority + "_" + currentRank;
        if (teamName.length() > 16) teamName = teamName.substring(0, 16);

        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        team.setPrefix(prefix + " ");
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updatePlayerPermissions(player);

        for (UUID vUuid : vanishedPlayers) {
            Player vPlayer = Bukkit.getPlayer(vUuid);
            if (vPlayer != null && getSenderTier(player) < 20.0) {
                player.hidePlayer(this, vPlayer);
            }
        }

        String rank = getPlayerRank(player);
        String prefix = getRankPrefix(rank);
        event.setJoinMessage(prefix + " §f" + player.getName() + " §ejoined the game");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String rank = getPlayerRank(player);
        String prefix = getRankPrefix(rank);

        if (vanishedPlayers.contains(player.getUniqueId())) {
            event.setQuitMessage(null);
            vanishedPlayers.remove(player.getUniqueId());
        } else {
            event.setQuitMessage(prefix + " §f" + player.getName() + " §eleft the game");
        }

        UUID uuid = player.getUniqueId();
        if (attachments.containsKey(uuid)) {
            player.removeAttachment(attachments.get(uuid));
            attachments.remove(uuid);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (mutedPlayers.contains(uuid)) {
            player.sendMessage("§cAap muted hain! Aap chat nahi kar sakte.");
            event.setCancelled(true);
            return;
        }

        if (timeoutPlayers.containsKey(uuid)) {
            long expireTime = timeoutPlayers.get(uuid);
            if (System.currentTimeMillis() < expireTime) {
                long remainingMillis = expireTime - System.currentTimeMillis();
                long mins = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
                long secs = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60;

                player.sendMessage("§cAap timeout par hain! Chat " + mins + "m " + secs + "s baad unblock hogi.");
                event.setCancelled(true);
                return;
            } else {
                timeoutPlayers.remove(uuid);
                player.sendMessage("§aAapka timeout khatam ho gaya hai! Ab aap chat kar sakte hain.");
            }
        }

        String rank = getPlayerRank(player);
        String prefix = getRankPrefix(rank);
        event.setFormat(prefix + " §f" + player.getName() + "§7: §f" + event.getMessage());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (godModePlayers.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        double senderTier = getSenderTier(sender);
        String cmd = command.getName().toLowerCase();

        // 1. TIMEOUT COMMAND
        if (cmd.equals("timeout")) {
            if (senderTier < 20.0) {
                sender.sendMessage("§cTimeout ki permission Helper+ ko hai!");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /timeout <player> <minutes> [reason]");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer online nahi hai!");
                return true;
            }

            if (isStaff(target) && senderTier < 80.0) {
                sender.sendMessage("§cStaff ko timeout dene ke liye DC Owner, Owner, ya Founder hona zaroori hai!");
                return true;
            }

            int minutes;
            try {
                minutes = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cMinutes number hone chahiye!");
                return true;
            }

            String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Muted by Staff";
            long expireTime = System.currentTimeMillis() + ((long) minutes * 60 * 1000);
            timeoutPlayers.put(target.getUniqueId(), expireTime);

            target.sendMessage("§c§l[TIMEOUT] §eAapko " + minutes + " minutes ke liye chat se timeout kar diya gaya hai! Reason: §f" + reason);
            sender.sendMessage("§aSuccessfully " + target.getName() + " ko " + minutes + " minutes ke liye timeout diya!");
            return true;
        }

        // 2. STAFF COMMAND SUITE (/staff warn/kick/ban/mute/timeout)
        if (cmd.equals("staff")) {
            if (senderTier < 20.0) {
                sender.sendMessage("§cSirf Staff members hi /staff command use kar sakte hain!");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§e--- Staff Actions ---");
                sender.sendMessage("§a/staff warn <player> <reason>");
                sender.sendMessage("§a/staff timeout <player> <minutes> [reason]");
                sender.sendMessage("§a/staff mute <player>");
                sender.sendMessage("§a/staff kick <player> [reason]");
                sender.sendMessage("§a/staff ban <player> [reason]");
                return true;
            }

            String sub = args[0].toLowerCase();

            if (sub.equals("warn")) {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /staff warn <player> <reason>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer online nahi hai!");
                    return true;
                }
                String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                target.sendMessage("§c§l[STAFF WARNING] §eStaff (" + sender.getName() + ") se warning: §f" + reason);
                sender.sendMessage("§aWarning successfully " + target.getName() + " ko bhej di gayi!");
                return true;
            }

            if (sub.equals("kick")) {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /staff kick <player> [reason]");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer online nahi hai!");
                    return true;
                }

                if (isStaff(target) && senderTier < 80.0) {
                    sender.sendMessage("§cDoosre staff ko kick sirf DC Owner, Owner, ya Founder (Tier 80+) hi kar sakte hain!");
                    return true;
                }
                if (!isStaff(target) && senderTier < 40.0) {
                    sender.sendMessage("§cNormal players ko kick karne ke liye Mod+ hona zaroori hai!");
                    return true;
                }

                String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Kicked by Staff";
                target.kickPlayer("§c[Staff Kick]\nReason: " + reason);
                Bukkit.broadcastMessage("§e[Staff Action] " + target.getName() + " ko kick kar diya gaya. Reason: " + reason);
                return true;
            }

            if (sub.equals("ban")) {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /staff ban <player> [reason]");
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

                boolean targetIsStaff = false;
                if (target.isOnline() && target.getPlayer() != null) {
                    targetIsStaff = isStaff(target.getPlayer());
                } else {
                    String r = getConfig().getString("players." + target.getUniqueId().toString(), "MEMBER");
                    targetIsStaff = getRankTier(r) >= 20.0;
                }

                if (targetIsStaff && senderTier < 80.0) {
                    sender.sendMessage("§cStaff member ko ban karne ki permission sirf DC Owner, Owner, aur Founder ko hai!");
                    return true;
                }
                if (!targetIsStaff && senderTier < 60.0) {
                    sender.sendMessage("§cBan karne ke liye Admin+ hona zaroori hai!");
                    return true;
                }

                String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Banned by Staff";
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), reason, null, sender.getName());
                if (target.isOnline() && target.getPlayer() != null) {
                    target.getPlayer().kickPlayer("§c[Staff Ban]\nReason: " + reason);
                }
                Bukkit.broadcastMessage("§c[Staff Action] " + target.getName() + " ko ban kar diya gaya hai. Reason: " + reason);
                return true;
            }

            if (sub.equals("timeout")) {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /staff timeout <player> <minutes> [reason]");
                    return true;
                }
                return onCommand(sender, getCommand("timeout"), "timeout", Arrays.copyOfRange(args, 1, args.length));
            }

            if (sub.equals("mute")) {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /staff mute <player>");
                    return true;
                }
                return onCommand(sender, getCommand("mute"), "mute", Arrays.copyOfRange(args, 1, args.length));
            }

            return true;
        }

        // 3. CORE UTILITIES & MODERATION
        if (cmd.equals("vanish") || cmd.equals("v")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (senderTier < 20.0) {
                player.sendMessage("§cSirf Staff members hi vanish mode use kar sakte hain!");
                return true;
            }
            UUID uuid = player.getUniqueId();
            String prefix = getRankPrefix(getPlayerRank(player));

            if (vanishedPlayers.contains(uuid)) {
                vanishedPlayers.remove(uuid);
                for (Player p : Bukkit.getOnlinePlayers()) p.showPlayer(this, player);
                player.sendMessage("§a[Vanish] Aap ab visible ho gaye hain!");
                Bukkit.broadcastMessage(prefix + " §f" + player.getName() + " §ejoined the game");
            } else {
                vanishedPlayers.add(uuid);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (getSenderTier(p) < 20.0) p.hidePlayer(this, player);
                }
                player.sendMessage("§c[Vanish] Aap vanish ho gaye hain!");
                Bukkit.broadcastMessage(prefix + " §f" + player.getName() + " §eleft the game");
            }
            return true;
        }

        if (cmd.equals("ban")) {
            return onCommand(sender, getCommand("staff"), "staff", new String[]{"ban", args.length > 0 ? args[0] : "", args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : ""});
        }

        if (cmd.equals("unban")) {
            if (senderTier < 60.0) {
                sender.sendMessage("§cUnban karne ki permission sirf Admin+ ko hai!");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("§cUsage: /unban <player>");
                return true;
            }
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(args[0]);
            sender.sendMessage("§aSuccessfully " + args[0] + " ko unban kar diya!");
            return true;
        }

        if (cmd.equals("kick")) {
            return onCommand(sender, getCommand("staff"), "staff", new String[]{"kick", args.length > 0 ? args[0] : "", args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : ""});
        }

        if (cmd.equals("mute")) {
            if (senderTier < 20.0) {
                sender.sendMessage("§cMute ki permission Helper+ ko hai!");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage("§cUsage: /mute <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer online nahi hai!");
                return true;
            }
            if (isStaff(target) && senderTier < 80.0) {
                sender.sendMessage("§cStaff ko mute dene ke liye DC Owner, Owner, ya Founder hona zaroori hai!");
                return true;
            }
            UUID tUuid = target.getUniqueId();
            if (mutedPlayers.contains(tUuid)) {
                mutedPlayers.remove(tUuid);
                sender.sendMessage("§a" + target.getName() + " ko unmute kar diya!");
                target.sendMessage("§aAap ab unmute ho gaye hain.");
            } else {
                mutedPlayers.add(tUuid);
                sender.sendMessage("§c" + target.getName() + " ko mute kar diya!");
                target.sendMessage("§cAapko staff dwara mute kar diya gaya hai.");
            }
            return true;
        }

        if (cmd.equals("warn")) {
            return onCommand(sender, getCommand("staff"), "staff", new String[]{"warn", args.length > 0 ? args[0] : "", args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : ""});
        }

        if (cmd.equals("tp")) {
            if (senderTier < 20.0) {
                sender.sendMessage("§cTeleport ki permission Helper+ ko hai!");
                return true;
            }
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (args.length < 1) {
                player.sendMessage("§cUsage: /tp <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("§cPlayer online nahi hai!");
                return true;
            }
            player.teleport(target.getLocation());
            player.sendMessage("§aTeleported to " + target.getName());
            return true;
        }

        if (cmd.equals("gm") || cmd.equals("gamemode")) {
            if (senderTier < 60.0) {
                sender.sendMessage("§cGamemode change ki permission Admin+ ko hai!");
                return true;
            }
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (args.length < 1) {
                player.sendMessage("§cUsage: /gm <0/1/2/3/s/c/a/sp>");
                return true;
            }
            String mode = args[0].toLowerCase();
            if (mode.equals("0") || mode.equals("s") || mode.equals("survival")) {
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage("§aGamemode set to SURVIVAL");
            } else if (mode.equals("1") || mode.equals("c") || mode.equals("creative")) {
                player.setGameMode(GameMode.CREATIVE);
                player.sendMessage("§aGamemode set to CREATIVE");
            } else if (mode.equals("2") || mode.equals("a") || mode.equals("adventure")) {
                player.setGameMode(GameMode.ADVENTURE);
                player.sendMessage("§aGamemode set to ADVENTURE");
            } else if (mode.equals("3") || mode.equals("sp") || mode.equals("spectator")) {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§aGamemode set to SPECTATOR");
            } else {
                player.sendMessage("§cInvalid gamemode!");
            }
            return true;
        }

        if (cmd.equals("fly")) {
            if (senderTier < 40.0) {
                sender.sendMessage("§cFly ki permission Mod+ ko hai!");
                return true;
            }
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            boolean flyState = !player.getAllowFlight();
            player.setAllowFlight(flyState);
            player.setFlying(flyState);
            player.sendMessage(flyState ? "§aFly mode Enabled!" : "§cFly mode Disabled!");
            return true;
        }

        if (cmd.equals("invsee")) {
            if (senderTier < 40.0) {
                sender.sendMessage("§cInvsee ki permission Mod+ ko hai!");
                return true;
            }
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (args.length < 1) {
                player.sendMessage("§cUsage: /invsee <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("§cPlayer online nahi hai!");
                return true;
            }
            player.openInventory(target.getInventory());
            return true;
        }

        if (cmd.equals("god")) {
            if (senderTier < 60.0) {
                sender.sendMessage("§cGodmode ki permission Admin+ ko hai!");
                return true;
            }
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            if (godModePlayers.contains(uuid)) {
                godModePlayers.remove(uuid);
                player.sendMessage("§cGod mode Disabled!");
            } else {
                godModePlayers.add(uuid);
                player.sendMessage("§aGod mode Enabled!");
            }
            return true;
        }

        if (cmd.equals("heal")) {
            if (senderTier < 60.0) {
                sender.sendMessage("§cHeal ki permission Admin+ ko hai!");
                return true;
            }
            Player target = (sender instanceof Player) ? (Player) sender : null;
            if (args.length >= 1) {
                target = Bukkit.getPlayer(args[0]);
            }
            if (target == null) {
                sender.sendMessage("§cPlayer online nahi hai!");
                return true;
            }
            target.setHealth(target.getMaxHealth());
            target.setFoodLevel(20);
            target.setFireTicks(0);
            target.sendMessage("§aAap full heal ho gaye hain!");
            sender.sendMessage("§aHealed " + target.getName());
            return true;
        }

        // 4. RANK MANAGEMENT
        if (cmd.equals("rank")) {
            if (args.length == 0) {
                sender.sendMessage("§e--- MidnightRanks Commands ---");
                sender.sendMessage("§a/rank introduce <name> <tier> <prefix>");
                sender.sendMessage("§a/rank prefix <rank> <new_prefix>");
                sender.sendMessage("§a/rank give <player> <rank>");
                sender.sendMessage("§a/rank addperm <rank> <perm>");
                sender.sendMessage("§a/rank list");
                return true;
            }

            String sub = args[0].toLowerCase();

            if (sub.equals("introduce") || sub.equals("create")) {
                if (senderTier < 60.0) {
                    sender.sendMessage("§cSirf FOUNDER tier (Tier 60+) hi naya rank introduce kar sakta hai!");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /rank introduce <rankName> <tier> <prefix>");
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

            if (sub.equals("prefix") || sub.equals("setprefix")) {
                if (senderTier < 60.0) {
                    sender.sendMessage("§cSirf FOUNDER tier hi rank prefix edit kar sakta hai!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rank prefix <rank> <new_prefix>");
                    return true;
                }

                String rankName = args[1].toUpperCase();
                if (!rankExists(rankName)) {
                    sender.sendMessage("§cRank exist nahi karta!");
                    return true;
                }

                StringBuilder prefixBuilder = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    prefixBuilder.append(args[i]).append(" ");
                }
                String newPrefix = prefixBuilder.toString().trim();

                getConfig().set("ranks." + rankName + ".prefix", newPrefix);
                saveConfig();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    updatePlayerPermissions(p);
                }

                sender.sendMessage("§aSuccessfully " + rankName + " ka prefix update karke " + colorize(newPrefix) + " §akar diya!");
                return true;
            }

            if (sub.equals("give")) {
                if (senderTier < 30.0) {
                    sender.sendMessage("§cAapke paas rank set karne ki permission nahi hai!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /rank give <player> <rank>");
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

                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    sender.sendMessage("§cIs name ka player server par kabhi nahi aaya!");
                    return true;
                }

                setPlayerRank(target.getUniqueId(), targetRank);
                String prefix = getRankPrefix(targetRank);
                sender.sendMessage("§aSuccessfully " + target.getName() + " ko " + prefix + " §arank de diya!");

                if (target.isOnline() && target.getPlayer() != null) {
                    target.getPlayer().sendMessage("§aAapka rank update hokar " + prefix + " §aho gaya hai!");
                }
                return true;
            }

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

        if (cmd.equals("setrank")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /setrank <player> <rank>");
                return true;
            }
            return onCommand(sender, getCommand("rank"), label, new String[]{"give", args[0], args[1]});
        }

        if (cmd.equals("sc")) {
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
