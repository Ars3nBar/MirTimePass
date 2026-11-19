package ru.mirtime.pass;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimePass extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private File dataFile;
    private YamlConfiguration data;
    private PassMenu menu;
    private NamespacedKey keyTag;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyTag = new NamespacedKey(this, "mt_pass_universal_key");

        dataFile = new File(getDataFolder(), "players.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        menu = new PassMenu(this);

        if (getCommand("battlepass") != null) getCommand("battlepass").setExecutor(this);
        if (getCommand("mtpass") != null) {
            getCommand("mtpass").setExecutor(this);
            getCommand("mtpass").setTabCompleter(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(menu, this);
        getLogger().info("MirTimePass v2.6.0 (50 уровней, Единый Ключ и Покупка за Токены) запущен!");
    }

    public void saveData() {
        try { data.save(dataFile); } catch (IOException ignored) {}
    }

    public String getActiveSeason() {
        return getConfig().getString("active-season", "anarchy");
    }

    public int getLevel(UUID id) { return data.getInt(id + ".level", 1); }
    public int getXp(UUID id) { return data.getInt(id + ".xp", 0); }
    public boolean hasPremium(UUID id) { return data.getBoolean(id + ".premium", false); }
    public void setPremium(UUID id, boolean val) { data.set(id + ".premium", val); saveData(); }
    public boolean isClaimed(UUID id, int lvl, boolean prem) { return data.getBoolean(id + ".claimed." + (prem ? "prem_" : "free_") + lvl, false); }
    public int getQuestProgress(UUID id, String qKey) { return data.getInt(id + ".quests." + qKey, 0); }

    public ItemStack createUniversalKey(int amount) {
        ItemStack is = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&#FFD700&l🗝 Универсальный Ключ Пропуска"));
            meta.setLore(List.of(
                    color("&7Подходит для &#00E5FFлюбого сезона &7Боевого Пропуска!"),
                    color("&7Активирует &#FFD700PREMIUM-ветку &7наград."),
                    color("&c⚠ Действует 1 раз — ровно до следующего вайпа!"),
                    "",
                    color("&#00FF66➥ Нажмите ПКМ в руке &fили в меню &b/bp")
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, "universal");
            is.setItemMeta(meta);
        }
        return is;
    }

    public boolean hasUniversalKeyInInv(Player p) {
        for (ItemStack is : p.getInventory().getContents()) {
            if (is == null || !is.hasItemMeta()) continue;
            if (is.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)) return true;
        }
        return false;
    }

    public boolean consumeUniversalKeyFromInv(Player p) {
        for (ItemStack is : p.getInventory().getContents()) {
            if (is == null || !is.hasItemMeta()) continue;
            if (is.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)) {
                is.setAmount(is.getAmount() - 1);
                p.updateInventory();
                activatePremiumForWipe(p);
                return true;
            }
        }
        return false;
    }

    public void activatePremiumForWipe(Player p) {
        setPremium(p.getUniqueId(), true);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        p.sendTitle(color("&#FFD700&lPREMIUM АКТИВИРОВАН!"), color("&fДоступ открыт до следующего вайпа"), 10, 60, 15);
        p.sendMessage(color(getConfig().getString("prefix") + "&#FFD700&lПоздравляем! Вы активировали платный Боевой Пропуск на текущий вайп!"));
    }

    public int getTokensBalance(Player p) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method m = papi.getMethod("setPlaceholders", Player.class, String.class);
                String res = (String) m.invoke(null, p, "%playerpoints_points%");
                return Integer.parseInt(res.replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) {}
        return 0;
    }
    @EventHandler
    public void onKeyUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack is = e.getItem();
        if (is == null || !is.hasItemMeta()) return;
        if (!is.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        String prefix = color(getConfig().getString("prefix", "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» "));

        if (hasPremium(p.getUniqueId())) {
            p.sendMessage(prefix + color("&eУ вас уже активирован PREMIUM пропуск на этот вайп!"));
            return;
        }

        is.setAmount(is.getAmount() - 1);
        p.updateInventory();
        activatePremiumForWipe(p);
    }

    public void addXp(Player p, int amount) {
        UUID id = p.getUniqueId();
        int maxLvl = getConfig().getInt("max-level", 50);
        int curLvl = getLevel(id);
        if (curLvl >= maxLvl) return;

        int need = getConfig().getInt("xp-per-level", 100);
        int curXp = getXp(id) + amount;
        while (curXp >= need && curLvl < maxLvl) {
            curXp -= need;
            curLvl++;
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            p.sendMessage(color(getConfig().getString("prefix") + "&#00FF66&lНОВЫЙ УРОВЕНЬ ПРОПУСКА! &fВы достигли &#00E5FF&l" + curLvl + " ур. &7(/bp)"));
        }
        data.set(id + ".level", curLvl);
        data.set(id + ".xp", curXp);
        saveData();
    }

    public void claimReward(Player p, int lvl, boolean prem) {
        UUID id = p.getUniqueId();
        if (getLevel(id) < lvl) return;
        if (prem && !hasPremium(id)) {
            p.sendMessage(color(getConfig().getString("prefix") + "&cДля этой награды нужен PREMIUM Боевой Пропуск!"));
            return;
        }
        if (isClaimed(id, lvl, prem)) return;

        data.set(id + ".claimed." + (prem ? "prem_" : "free_") + lvl, true);
        saveData();

        String baseSec = "seasons." + getActiveSeason() + ".rewards.";
        String sec = getConfig().contains(baseSec + lvl) ? baseSec + lvl : baseSec + "default";
        if (!prem) {
            String cmd = getConfig().getString(sec + ".free-cmd", "");
            if (!cmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        } else {
            for (String cmd : getConfig().getStringList(sec + ".prem-cmds")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            }
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        p.sendMessage(color(getConfig().getString("prefix") + "&#00FF66Вы забрали награду за &f" + lvl + " уровень&#00FF66!"));
    }

    private void progressQuest(Player p, String type, int add) {
        String qPath = "seasons." + getActiveSeason() + ".quests";
        if (getConfig().getConfigurationSection(qPath) == null) return;
        for (String k : getConfig().getConfigurationSection(qPath).getKeys(false)) {
            if (!getConfig().getString(qPath + "." + k + ".type", "").equalsIgnoreCase(type)) continue;
            int target = getConfig().getInt(qPath + "." + k + ".target", 1);
            int cur = getQuestProgress(p.getUniqueId(), k);
            if (cur >= target) continue;

            int next = Math.min(target, cur + add);
            data.set(p.getUniqueId() + ".quests." + k, next);
            if (next >= target) {
                int xp = getConfig().getInt(qPath + "." + k + ".xp", 50);
                p.sendMessage(color(getConfig().getString("prefix") + "&#00FF66Задание выполнено! &fПолучено &#00E5FF+" + xp + " XP"));
                addXp(p, xp);
            } else {
                saveData();
            }
        }
    }

    @EventHandler public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (e.getEntity() instanceof Player) progressQuest(killer, "KILL_PLAYER", 1);
        else if (e.getEntity() instanceof Monster) progressQuest(killer, "KILL_MOB", 1);
    }

    @EventHandler public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.OBSIDIAN) progressQuest(e.getPlayer(), "BREAK_OBSIDIAN", 1);
    }

    @EventHandler public void onEat(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() == Material.GOLDEN_APPLE) progressQuest(e.getPlayer(), "EAT_GAPPLE", 1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = color(getConfig().getString("prefix", "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» "));
        if (cmd.getName().equalsIgnoreCase("battlepass")) {
            if (sender instanceof Player p) menu.openMain(p, 0);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("mtpass")) {
            List<String> list = List.of(args);
            if (list.size() >= 2 && list.get(0).equalsIgnoreCase("setseason")) {
                String newSeason = list.get(1).toLowerCase();
                if (!getConfig().contains("seasons." + newSeason)) {
                    sender.sendMessage(prefix + color("&cСезон не найден! Доступные: " + String.join(", ", getConfig().getConfigurationSection("seasons").getKeys(false))));
                    return true;
                }
                getConfig().set("active-season", newSeason);
                saveConfig();
                for (String k : data.getKeys(false)) data.set(k, null);
                saveData();

                String sTitle = getConfig().getString("seasons." + newSeason + ".display-name", newSeason);
                Bukkit.broadcastMessage(color(prefix + "&#00FF66&lНОВЫЙ СЕЗОН БОЕВОГО ПРОПУСКА! &fАктивирован: " + sTitle + " &7(/bp)"));
                return true;
            }
            if (list.size() >= 2 && list.get(0).equalsIgnoreCase("givekey")) {
                Player target = Bukkit.getPlayer(list.get(1));
                if (target == null) {
                    sender.sendMessage(prefix + color("&cИгрок не в сети."));
                    return true;
                }
                int amt = 1;
                if (list.size() >= 3) {
                    try { amt = Math.max(1, Integer.parseInt(list.get(2))); } catch (Exception ignored) {}
                }
                target.getInventory().addItem(createUniversalKey(amt));
                sender.sendMessage(prefix + color("&#00FF66Выдан Универсальный Ключ Пропуска &7(x" + amt + ") &#00FF66игроку &f" + target.getName()));
                target.sendMessage(prefix + color("&#FFD700Вы получили Универсальный Ключ Пропуска! &fАктивируйте его в &#00E5FF/bp &fили нажатием ПКМ."));
                return true;
            }
            if (list.size() >= 2 && list.get(0).equalsIgnoreCase("addxp")) {
                Player t = Bukkit.getPlayer(list.get(1));
                int xp = list.size() >= 3 ? Integer.parseInt(list.get(2)) : 100;
                if (t != null) { addXp(t, xp); sender.sendMessage(prefix + color("&#00FF66Выдано " + xp + " XP игроку " + t.getName())); }
                return true;
            }
            sender.sendMessage(prefix + color("&fКоманды: &#00FF66/mtpass setseason <anarchy|abyss|cyber> &8| &#00FF66/mtpass givekey <ник> [кол-во] &8| &#00FF66/mtpass addxp <ник> <xp>"));
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> seasons = new ArrayList<>(Objects.requireNonNull(getConfig().getConfigurationSection("seasons")).getKeys(false));
        if (args.length == 1) return List.of("setseason", "givekey", "addxp");
        if (args.length == 2 && List.of(args).get(0).equalsIgnoreCase("setseason")) return seasons;
        return null;
    }

    public static String color(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder rep = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) rep.append('§').append(c);
            m.appendReplacement(sb, rep.toString());
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
