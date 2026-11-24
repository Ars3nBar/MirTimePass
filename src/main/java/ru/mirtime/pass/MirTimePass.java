package ru.mirtime.pass;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
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

    private NamespacedKey keyTag;
    private File dataFile;
    private YamlConfiguration data;
    private int questOffset = 0;
    public static class PassHolder implements org.bukkit.inventory.InventoryHolder {
    public final int page;
    public PassHolder(int page) { this.page = page; }
    @Override public Inventory getInventory() { return null; }
}


    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyTag = new NamespacedKey(this, "mt_pass_key");

        dataFile = new File(getDataFolder(), "players.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (getCommand("bp") != null) getCommand("bp").setExecutor(this);
        if (getCommand("mtpass") != null) {
            getCommand("mtpass").setExecutor(this);
            getCommand("mtpass").setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PassPlaceholders(this).register();
        }
        getLogger().info("MirTimePass v4.0.0 (Конфиг-сезоны, покупка уровней, 24ч квесты и скорборд) запущен!");
    }

    public void saveData() {
        try { data.save(dataFile); } catch (IOException ignored) {}
    }

    public String getActiveSeasonId() {
        return getConfig().getString("active-season", "anarchy");
    }

    public ConfigurationSection getSeasonSec() {
        ConfigurationSection sec = getConfig().getConfigurationSection("seasons." + getActiveSeasonId());
        return sec != null ? sec : getConfig().getConfigurationSection("seasons.anarchy");
    }

    public int getMaxLevel() {
        return getConfig().getInt("max-level", 50);
    }

    public int getXpPerLevel() {
        return getConfig().getInt("xp-per-level", 100);
    }

    public int getLevel(UUID id) {
        return Math.min(getMaxLevel(), Math.max(1, data.getInt(id + ".level", 1)));
    }

    public int getXp(UUID id) {
        return data.getInt(id + ".xp", 0);
    }

    public boolean isPremium(UUID id) {
        return data.getBoolean(id + ".premium", false);
    }

    public void addXp(Player p, int amount) {
        UUID id = p.getUniqueId();
        int bonus = isPremium(id) ? (int) Math.round(amount * 1.5) : amount;
        int lvl = getLevel(id);
        int xp = getXp(id) + bonus;
        int req = getXpPerLevel();
        int max = getMaxLevel();

        while (lvl < max && xp >= req) {
            xp -= req;
            lvl++;
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            p.sendMessage(color(getConfig().getString("prefix") + "&#00FF66&lНОВЫЙ УРОВЕНЬ ПРОПУСКА! &fВы достигли &#00E5FF&l" + lvl + " ур. &7(Заберите награду в /bp)"));
        }
        data.set(id + ".level", lvl);
        data.set(id + ".xp", xp);
        saveData();
    }

    public String getTimeUntilReset() {
        long now = System.currentTimeMillis();
        long nextDay = ((now / 86400000L) + 1L) * 86400000L;
        long diff = Math.max(0L, nextDay - now);
        return (diff / 3600000L) + "ч " + ((diff % 3600000L) / 60000L) + "м";
    }
    public void openMenu(Player p, int page) {
        ConfigurationSection sec = getSeasonSec();
        String sName = sec != null ? sec.getString("display-name", "&c&l⚔ Сезон") : "&c&l⚔ Сезон";
        Material cMat = Material.matchMaterial(sec != null ? sec.getString("corner-glass", "BLUE_STAINED_GLASS_PANE") : "BLUE_STAINED_GLASS_PANE");
        Material bMat = Material.matchMaterial(sec != null ? sec.getString("border-glass", "LIGHT_BLUE_STAINED_GLASS_PANE") : "LIGHT_BLUE_STAINED_GLASS_PANE");
        if (cMat == null) cMat = Material.BLUE_STAINED_GLASS_PANE;
        if (bMat == null) bMat = Material.LIGHT_BLUE_STAINED_GLASS_PANE;

        UUID id = p.getUniqueId();
        int lvl = getLevel(id);
        int xp = getXp(id);
        boolean prem = isPremium(id);
        int maxLvl = getMaxLevel();
        int maxPages = (int) Math.ceil(maxLvl / 7.0);
        int safePage = Math.max(1, Math.min(maxPages, page));

        PassHolder holder = new PassHolder(safePage);
        Inventory inv = Bukkit.createInventory(holder, 54, color(sName + " &8| &fСтр. " + safePage + "/" + maxPages));

        ItemStack corner = makeItem(cMat, "&#0055ff&l▪", null);
        ItemStack border = makeItem(bMat, "&#00E5FF▫", null);
        for (int s : new int[]{0, 8, 45, 53}) inv.setItem(s, corner);
        for (int s : new int[]{1, 2, 6, 7, 9, 17, 18, 26, 27, 35, 36, 44}) inv.setItem(s, border);

        long currentDay = (System.currentTimeMillis() / 86400000L) + questOffset;
        if (data.getLong(id + ".questDay", -1L) != currentDay) {
            data.set(id + ".questDay", currentDay);
            data.set(id + ".quests", null);
            saveData();
        }

        inv.setItem(2, makeItem(Material.CLOCK, "&#00E5FF&l⏳ ЕЖЕДНЕВНЫЕ ЗАДАНИЯ", List.of(
                "&7Задания обновляются каждые &f24 часа&7!",
                "&7До обновления: &#00FF66" + getTimeUntilReset(),
                "",
                prem ? "&#FFD700★ Активен бонус PREMIUM: x1.5 XP!" : "&7С пропуском PREMIUM вы получаете &#00FF66x1.5 XP&7!"
        )));

        ConfigurationSection qSec = sec != null ? sec.getConfigurationSection("quests") : null;
        if (qSec != null) {
            int qSlot = 3;
            for (String qKey : qSec.getKeys(false)) {
                if (qSlot > 6) break;
                String qName = qSec.getString(qKey + ".name", "Задание");
                String qDesc = qSec.getString(qKey + ".desc", "");
                int target = qSec.getInt(qKey + ".target", 10);
                int qXp = qSec.getInt(qKey + ".xp", 100);
                int realXp = prem ? (int) Math.round(qXp * 1.5) : qXp;

                int prog = data.getInt(id + ".quests." + qKey + ".prog", 0);
                boolean done = data.getBoolean(id + ".quests." + qKey + ".done", false);

                inv.setItem(qSlot++, makeItem(done ? Material.LIME_DYE : Material.PAPER,
                        (done ? "&#00FF66&l✔ " : "") + qName,
                        List.of(
                                "&7" + qDesc,
                                "",
                                "&fПрогресс: &#00E5FF" + Math.min(prog, target) + " &8/ &f" + target,
                                "&fНаграда: &#00FF66+" + realXp + " XP " + (prem ? "&#FFD700(x1.5)" : ""),
                                done ? "&#00FF66✔ Выполнено сегодня!" : "&eВыполняется автоматически!"
                        )));
            }
        }
        int startLvl = (safePage - 1) * 7 + 1;
        ConfigurationSection rSec = sec != null ? sec.getConfigurationSection("rewards") : null;
        for (int i = 0; i < 7 && (startLvl + i) <= maxLvl; i++) {
            int curLvl = startLvl + i;
            boolean reached = lvl >= curLvl;
            boolean freeClaimed = data.getBoolean(id + ".claimed.free." + curLvl, false);
            boolean premClaimed = data.getBoolean(id + ".claimed.prem." + curLvl, false);

            String lvlKey = (rSec != null && rSec.contains(String.valueOf(curLvl))) ? String.valueOf(curLvl) : "default";
            String freeName = rSec != null ? rSec.getString(lvlKey + ".free-name", "&eНаграда") : "&eНаграда";
            String premName = rSec != null ? rSec.getString(lvlKey + ".prem-name", "&#FFD700Премиум награда") : "&#FFD700Премиум награда";

            inv.setItem(10 + i, makeItem(
                    freeClaimed ? Material.MINECART : (reached ? Material.CHEST_MINECART : Material.GRAY_DYE),
                    "&f&lFREE Награда &8| &fУр. " + curLvl,
                    List.of("&7Приз: " + freeName, "", freeClaimed ? "&7✔ Получено" : (reached ? "&#00FF66▶ Нажмите, чтобы забрать!" : "&c✖ Достигните " + curLvl + " ур."))));

            inv.setItem(19 + i, makeItem(
                    reached ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    (reached ? "&#00FF66&l★ УРОВЕНЬ " : "&c&l✖ УРОВЕНЬ ") + curLvl,
                    List.of(reached ? "&aУровень открыт!" : "&7Нужно: &f" + getXpPerLevel() + " XP за уровень")));

            inv.setItem(28 + i, makeItem(
                    premClaimed ? Material.MINECART : (prem && reached ? Material.ENDER_CHEST : Material.GOLD_NUGGET),
                    "&#FFD700&l👑 PREMIUM Награда &8| &eУр. " + curLvl,
                    List.of("&7Приз: " + premName, "", !prem ? "&c🔒 Требуется PREMIUM-пропуск!" : (premClaimed ? "&7✔ Получено" : (reached ? "&#00FF66▶ Нажмите, чтобы забрать!" : "&eДостигните " + curLvl + " ур.")))));
        }

        if (safePage > 1) inv.setItem(45, makeItem(Material.ARROW, "&#00E5FF&l◀ Стр. " + (safePage - 1), null));
        if (safePage < maxPages) inv.setItem(53, makeItem(Material.ARROW, "&#00E5FF&lСтр. " + (safePage + 1) + " ▶", null));

        int lvlTok = getConfig().getInt("level-price-tokens", 500);
        int lvlIks = getConfig().getInt("level-price-iks", 2);
        inv.setItem(46, makeItem(Material.EXPERIENCE_BOTTLE, "&#00E5FF&l⚡ КУПИТЬ +1 УРОВЕНЬ", List.of(
                "&7Мгновенная прокачка Боевого Пропуска!",
                "",
                "&#00FF66▶ ЛКМ: &fКупить за &#00FF66" + lvlTok + " Токенов ⛁",
                "&#00E5FF▶ ПКМ: &fКупить за &#00E5FF" + lvlIks + " Икса 🅧"
        )));

        inv.setItem(48, makeItem(Material.GOLD_INGOT, "&#FFD700&l👑 ЧТО ДАЁТ PREMIUM-ПРОПУСК?", List.of(
                "&7Преимущества элитного пропуска на весь сезон:",
                " &#FFD700✦ &fДоступ ко всем &e50 закрытым наградам",
                " &#FFD700✦ &fБустер &#00FF66x1.5 XP &fза ежедневные задания",
                " &#FFD700✦ &fРедкие артефакты &bMirTimeItems &fи киты",
                " &#FFD700✦ &fДесятки &#00E5FFИксов (🅧) &fи тысячи Токенов",
                " &#FFD700✦ &fОтображение статуса в скорборде!"
        )));

        inv.setItem(49, makeItem(Material.NETHER_STAR, sName, List.of(
                "&fВаш уровень: &#00FF66&l" + lvl + " / " + maxLvl + " &7(" + xp + "/" + getXpPerLevel() + " XP)",
                "&fСтатус пропуска: " + (prem ? "&#FFD700&lPREMIUM АКТИВЕН ✔" : "&7Базовый (FREE)"),
                "&fСайт проекта: &#00E5FF" + getConfig().getString("site-link", "don.mir-time.ru")
        )));

        inv.setItem(50, makeItem(Material.HOPPER, "&#00FF66&l🎁 ЗАБРАТЬ ВСЕ НАГРАДЫ", List.of(
                "&7Нажмите, чтобы мгновенно собрать",
                "&7все доступные награды со всех уровней!"
        )));

        int premPrice = getConfig().getInt("token-cost", 10000);
        inv.setItem(52, makeItem(prem ? Material.EMERALD_BLOCK : Material.TRIPWIRE_HOOK,
                prem ? "&#00FF66&l✔ PREMIUM-ПРОПУСК АКТИВЕН" : "&#FFD700&l🔓 КУПИТЬ PREMIUM-ПРОПУСК",
                prem ? List.of("&7Элитная ветка наград разблокирована!") : List.of(
                        "&7Откройте все 50 премиум-наград сезона!",
                        "&7Цена: &#00FF66&l" + premPrice + " Токенов ⛁",
                        "&7Или активируйте ключ с сайта &b" + getConfig().getString("site-link", "don.mir-time.ru"),
                        "",
                        "&#FFD700▶ Нажмите, чтобы купить за Токены"
                )));

        p.openInventory(inv);
    }
    private void giveLevelReward(Player p, int lvl, boolean prem) {
        ConfigurationSection rSec = getSeasonSec().getConfigurationSection("rewards");
        if (rSec == null) return;
        String key = rSec.contains(String.valueOf(lvl)) ? String.valueOf(lvl) : "default";
        String name = p.getName();

        if (!prem) {
            String cmd = rSec.getString(key + ".free-cmd", "");
            if (!cmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", name));
        } else {
            for (String cmd : rSec.getStringList(key + ".prem-cmds")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", name));
            }
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof PassHolder holder)) return;
        e.setCancelled(true);

        int maxLvl = getMaxLevel();
        int maxPages = (int) Math.ceil(maxLvl / 7.0);
        int page = holder.page;


        UUID id = p.getUniqueId();
        String prefix = color(getConfig().getString("prefix"));
        int slot = e.getRawSlot();

        if (slot == 45 && page > 1) { openMenu(p, page - 1); return; }
        if (slot == 53 && page < maxPages) { openMenu(p, page + 1); return; }

        if (slot == 46) {
            if (getLevel(id) >= maxLvl) { p.sendMessage(prefix + color("&eВы уже достигли максимального " + maxLvl + " уровня!")); return; }
            if (e.isRightClick()) {
                int cost = getConfig().getInt("level-price-iks", 2);
                if (getBalance(p, "%gemseconomy_balance_Iksy%") < cost) { p.sendMessage(prefix + color("&cНедостаточно Иксов (нужно " + cost + " 🅧)!")); return; }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "giks take " + p.getName() + " " + cost + " Iksy");
            } else {
                int cost = getConfig().getInt("level-price-tokens", 500);
                if (getBalance(p, "%playerpoints_points%") < cost) { p.sendMessage(prefix + color("&cНедостаточно Токенов (нужно " + cost + " ⛁)!")); return; }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "p take " + p.getName() + " " + cost);
            }
            data.set(id + ".level", getLevel(id) + 1);
            saveData();
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
            p.sendMessage(prefix + color("&#00FF66Вы успешно приобрели +1 уровень Боевого Пропуска!"));
            openMenu(p, page);
            return;
        }

        if (slot == 50) {
            int count = 0;
            int curMax = getLevel(id);
            boolean prem = isPremium(id);
            for (int l = 1; l <= curMax; l++) {
                if (!data.getBoolean(id + ".claimed.free." + l, false)) {
                    data.set(id + ".claimed.free." + l, true);
                    giveLevelReward(p, l, false);
                    count++;
                }
                if (prem && !data.getBoolean(id + ".claimed.prem." + l, false)) {
                    data.set(id + ".claimed.prem." + l, true);
                    giveLevelReward(p, l, true);
                    count++;
                }
            }
            saveData();
            if (count > 0) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.3f);
                p.sendMessage(prefix + color("&#00FF66Вы забрали все доступные награды (" + count + " шт.)!"));
            } else {
                p.sendMessage(prefix + color("&eНет новых доступных наград для получения."));
            }
            openMenu(p, page);
            return;
        }
        if (slot == 52 && !isPremium(id)) {
            int price = getConfig().getInt("token-cost", 10000);
            if (getBalance(p, "%playerpoints_points%") < price) {
                p.sendMessage(prefix + color("&cНедостаточно Токенов! Нужно &#00FF66" + price + " ⛁"));
                return;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "p take " + p.getName() + " " + price);
            data.set(id + ".premium", true);
            saveData();
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
            p.sendMessage(prefix + color("&#FFD700&lПОЗДРАВЛЯЕМ! &fВы активировали &#FFD700&lPREMIUM-ПРОПУСК &fна весь сезон!"));
            openMenu(p, page);
            return;
        }

        int startLvl = (page - 1) * 7 + 1;
        if (slot >= 10 && slot <= 16) {
            int targetLvl = startLvl + (slot - 10);
            if (targetLvl <= getLevel(id) && !data.getBoolean(id + ".claimed.free." + targetLvl, false)) {
                data.set(id + ".claimed.free." + targetLvl, true);
                saveData();
                giveLevelReward(p, targetLvl, false);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
                openMenu(p, page);
            }
        } else if (slot >= 28 && slot <= 34) {
            int targetLvl = startLvl + (slot - 28);
            if (isPremium(id) && targetLvl <= getLevel(id) && !data.getBoolean(id + ".claimed.prem." + targetLvl, false)) {
                data.set(id + ".claimed.prem." + targetLvl, true);
                saveData();
                giveLevelReward(p, targetLvl, true);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
                openMenu(p, page);
            }
        }
    }

    @EventHandler
    public void onKeyUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack is = e.getItem();
        if (is == null || !is.hasItemMeta()) return;
        if (!is.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.BYTE)) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        if (isPremium(p.getUniqueId())) {
            p.sendMessage(color(getConfig().getString("prefix") + "&eУ вас уже активирован PREMIUM-пропуск в этом сезоне!"));
            return;
        }
        is.setAmount(is.getAmount() - 1);
        data.set(p.getUniqueId() + ".premium", true);
        saveData();
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        p.sendMessage(color(getConfig().getString("prefix") + "&#FFD700&lУНИВЕРСАЛЬНЫЙ КЛЮЧ АКТИВИРОВАН! &fДоступ к PREMIUM-ветке открыт!"));
    }

    private void progressQuest(Player p, String type) {
        UUID id = p.getUniqueId();
        ConfigurationSection qSec = getSeasonSec().getConfigurationSection("quests");
        if (qSec == null) return;

        for (String qKey : qSec.getKeys(false)) {
            if (!type.equalsIgnoreCase(qSec.getString(qKey + ".type", ""))) continue;
            if (data.getBoolean(id + ".quests." + qKey + ".done", false)) continue;

            int target = qSec.getInt(qKey + ".target", 10);
            int next = data.getInt(id + ".quests." + qKey + ".prog", 0) + 1;
            data.set(id + ".quests." + qKey + ".prog", next);
            if (next >= target) {
                data.set(id + ".quests." + qKey + ".done", true);
                p.sendMessage(color(getConfig().getString("prefix") + "&#00FF66&lКВЕСТ ВЫПОЛНЕН: " + qSec.getString(qKey + ".name", "")));
                addXp(p, qSec.getInt(qKey + ".xp", 100));
            } else {
                saveData();
            }
        }
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { if (e.getBlock().getType() == Material.OBSIDIAN) progressQuest(e.getPlayer(), "BREAK_OBSIDIAN"); }
    @EventHandler public void onEat(PlayerItemConsumeEvent e) { if (e.getItem().getType() == Material.GOLDEN_APPLE || e.getItem().getType() == Material.ENCHANTED_GOLDEN_APPLE) progressQuest(e.getPlayer(), "EAT_GAPPLE"); }
    @EventHandler public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        if (e.getEntity() instanceof Player) progressQuest(k, "KILL_PLAYER");
        else progressQuest(k, "KILL_MOB");
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("bp")) {
            if (sender instanceof Player p) openMenu(p, 1);
            return true;
        }
        String prefix = color(getConfig().getString("prefix"));
        if (!sender.hasPermission("mirtime.pass.admin")) { sender.sendMessage(prefix + color("&cНет прав!")); return true; }

        List<String> list = List.of(args);
        if (list.isEmpty()) {
            sender.sendMessage(prefix + color("&fКоманды: &b/mtpass setseason <id> &8| &b/mtpass givekey <ник> &8| &b/mtpass setpremium <ник> <true/false> &8| &b/mtpass setlevel <ник> <ур> &8| &b/mtpass addxp <ник> <xp> &8| &b/mtpass resetquests &8| &b/mtpass reload"));
            return true;
        }

        String sub = list.get(0).toLowerCase();
        if (sub.equals("reload")) {
            reloadConfig();
            sender.sendMessage(prefix + color("&#00FF66Конфиг MirTimePass перезагружен!"));
        } else if (sub.equals("setseason") && list.size() >= 2) {
            String sId = list.get(1).toLowerCase();
            if (!getConfig().contains("seasons." + sId)) { sender.sendMessage(prefix + color("&cДоступные сезоны: anarchy, abyss, cyber, inferno, bloodmoon, olympus")); return true; }
            getConfig().set("active-season", sId);
            saveConfig();
            for (String k : data.getKeys(false)) data.set(k, null);
            saveData();
            Bukkit.broadcastMessage(prefix + color("&#00FF66&lНОВЫЙ СЕЗОН БОЕВОГО ПРОПУСКА: " + getSeasonSec().getString("display-name") + "&#00FF66! &fОткройте &b/bp"));
        } else if (sub.equals("givekey") && list.size() >= 2) {
            Player t = Bukkit.getPlayer(list.get(1));
            if (t == null) return true;
            int amt = list.size() >= 3 ? Integer.parseInt(list.get(2)) : 1;
            ItemStack key = makeItem(Material.TRIPWIRE_HOOK, "&#FFD700&l🔑 Универсальный Ключ Пропуска (PREMIUM)", List.of("&7Нажмите ПКМ в руке, чтобы активировать", "&7PREMIUM-ветку на весь текущий сезон!"));
            key.setAmount(amt);
            ItemMeta m = key.getItemMeta();
            m.getPersistentDataContainer().set(keyTag, PersistentDataType.BYTE, (byte) 1);
            key.setItemMeta(m);
            t.getInventory().addItem(key);
            sender.sendMessage(prefix + color("&#00FF66Ключ пропуска выдан игроку &f" + t.getName()));
        } else if (sub.equals("setpremium") && list.size() >= 3) {
            Player t = Bukkit.getPlayer(list.get(1));
            if (t != null) {
                data.set(t.getUniqueId() + ".premium", Boolean.parseBoolean(list.get(2)));
                saveData();
                sender.sendMessage(prefix + color("&#00FF66Статус PREMIUM обновлён для &f" + t.getName()));
            }
        } else if (sub.equals("setlevel") && list.size() >= 3) {
            Player t = Bukkit.getPlayer(list.get(1));
            if (t != null) {
                data.set(t.getUniqueId() + ".level", Math.max(1, Math.min(getMaxLevel(), Integer.parseInt(list.get(2)))));
                saveData();
                sender.sendMessage(prefix + color("&#00FF66Уровень установлен для &f" + t.getName()));
            }
        } else if (sub.equals("addxp") && list.size() >= 3) {
            Player t = Bukkit.getPlayer(list.get(1));
            if (t != null) addXp(t, Integer.parseInt(list.get(2)));
        } else if (sub.equals("resetquests")) {
            questOffset++;
            sender.sendMessage(prefix + color("&#00FF66Ежедневные задания принудительно обновлены!"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return List.of("setseason", "givekey", "setpremium", "setlevel", "addxp", "resetquests", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("setseason")) {
            ConfigurationSection sSec = getConfig().getConfigurationSection("seasons");
            return sSec != null ? new ArrayList<>(sSec.getKeys(false)) : List.of();
        }
        return null;
    }

    private int getBalance(Player p, String placeholder) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method m = papi.getMethod("setPlaceholders", Player.class, String.class);
                String res = (String) m.invoke(null, p, placeholder);
                return Integer.parseInt(res.replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta m = is.getItemMeta();
        if (m != null) {
            m.setDisplayName(color(name));
            if (lore != null) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(color(s));
                m.setLore(l);
            }
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(m);
        }
        return is;
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
