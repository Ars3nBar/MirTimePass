package ru.mirtime.pass;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class PassMenu implements Listener {

    private final MirTimePass plugin;
    private final Map<UUID, Integer> playerPage = new HashMap<>();

    public PassMenu(MirTimePass plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player p, int page) {
        playerPage.put(p.getUniqueId(), page);
        String seasonId = plugin.getActiveSeason();
        String seasonTitle = plugin.getConfig().getString("seasons." + seasonId + ".display-name", seasonId);

        int lvl = plugin.getLevel(p.getUniqueId());
        int xp = plugin.getXp(p.getUniqueId());
        int need = plugin.getConfig().getInt("xp-per-level", 100);
        int maxLvl = plugin.getConfig().getInt("max-level", 50);
        boolean prem = plugin.hasPremium(p.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, MirTimePass.color("&#0055ff&l⚡ &#00E5FF&lБОЕВОЙ ПРОПУСК &8[Стр. " + (page + 1) + "]"));

        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff&l▪", null);
        ItemStack cyan = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫", null);
        ItemStack dark = item(Material.BLACK_STAINED_GLASS_PANE, " ", null);

        for (int s : new int[]{0, 8, 45, 53}) inv.setItem(s, blue);
        for (int s : new int[]{1, 2, 3, 5, 6, 7, 9, 17, 18, 26, 27, 35, 36, 44, 46, 52}) inv.setItem(s, cyan);
        for (int s : new int[]{37, 38, 39, 40, 41, 42, 43}) inv.setItem(s, dark);

        inv.setItem(4, item(Material.NETHER_STAR, "&#00E5FF&l✦ СЕЗОН: " + seasonTitle + " &#00E5FF&l✦", List.of(
                " &#0055ff╚════════════════════════════════════╝",
                "  &fВаш уровень: &#00FF66&l" + lvl + " / " + maxLvl,
                "  &fПрогресс опыта: &#00E5FF" + xp + " / " + need + " XP",
                "  &fСтатус доступа: " + (prem ? "&#FFD700&lPREMIUM АКТИВЕН ✔ &7(до вайпа)" : "&7Бесплатная ветка"),
                " &#0055ff╔════════════════════════════════════╝"
        )));
        int startLvl = page * 7 + 1;

        for (int col = 0; col < 7; col++) {
            int curLvl = startLvl + col;
            if (curLvl > maxLvl) break;

            boolean unlocked = lvl >= curLvl;
            boolean freeClaimed = plugin.isClaimed(p.getUniqueId(), curLvl, false);
            boolean premClaimed = plugin.isClaimed(p.getUniqueId(), curLvl, true);

            String baseSec = "seasons." + seasonId + ".rewards.";
            String sec = plugin.getConfig().contains(baseSec + curLvl) ? baseSec + curLvl : baseSec + "default";
            String freeName = plugin.getConfig().getString(sec + ".free-name", "Награда");
            String premName = plugin.getConfig().getString(sec + ".prem-name", "Премиум награда");

            Material fMat = freeClaimed ? Material.GRAY_DYE : (unlocked ? Material.LIME_DYE : Material.LIGHT_GRAY_DYE);
            String fStatus = freeClaimed ? "&8[✔ Награда получена]" : (unlocked ? "&#00FF66[▶ Кликните, чтобы забрать]" : "&c[🔒 Требуется " + curLvl + " ур.]");
            inv.setItem(10 + col, item(fMat, "&f&lУровень " + curLvl + " &8| &7Бесплатно", List.of(
                    " &7Награда: " + freeName, "", " " + fStatus
            )));

            Material barMat = unlocked ? Material.LIME_STAINED_GLASS_PANE : (curLvl == lvl + 1 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            String barTitle = unlocked ? "&#00FF66&l✔ УРОВЕНЬ " + curLvl + " ОТКРЫТ" : (curLvl == lvl + 1 ? "&e&l⚡ СЛЕДУЮЩИЙ: " + curLvl + " УР." : "&c&l✖ УРОВЕНЬ " + curLvl + " ЗАКРЫТ");
            inv.setItem(19 + col, item(barMat, barTitle, List.of("&7Выполняйте квесты для открытия.")));

            Material pMat = premClaimed ? Material.GRAY_DYE : (unlocked && prem ? Material.GOLD_INGOT : Material.ORANGE_DYE);
            String pStatus = premClaimed ? "&8[✔ Награда получена]" : (!prem ? "&#FFD700[🗝 Нужен PREMIUM]" : (unlocked ? "&#00FF66[▶ Кликните, чтобы забрать]" : "&c[🔒 Требуется " + curLvl + " ур.]"));
            inv.setItem(28 + col, item(pMat, "&#FFD700&lУровень " + curLvl + " &8| &#FFD700PREMIUM", List.of(
                    " &7Награда: " + premName, "", " " + pStatus
            )));
        }

        if (page > 0) inv.setItem(47, item(Material.ARROW, "&#00E5FF&l[◀ Предыдущая страница]", null));

        inv.setItem(48, item(Material.WRITABLE_BOOK, "&#00FF66&l📜 Задания сезона", List.of(
                "&7Выполняйте задания анархии,",
                "&7чтобы зарабатывать XP и качать уровни.",
                "",
                "&#00FF66▶ Нажмите, чтобы открыть квесты"
        )));

        if (prem) {
            inv.setItem(50, item(Material.EMERALD, "&#00FF66&l✔ PREMIUM ДОСТУП АКТИВЕН", List.of(
                    "&7Вы активировали платный Боевой Пропуск!",
                    "&7Доступ открыт до следующего вайпа."
            )));
        } else {
            boolean hasKey = plugin.hasUniversalKeyInInv(p);
            int cost = plugin.getConfig().getInt("token-cost", 10000);
            String site = plugin.getConfig().getString("site-link", "don.mir-time.ru");
            inv.setItem(50, item(Material.TRIPWIRE_HOOK, "&#FFD700&l👑 ОТКРЫТЬ PREMIUM ПРОПУСК", List.of(
                    "&7Открывает нижнюю ветку наград на &#00E5FF1 вайп&7!",
                    "",
                    "&#00FF66Способ 1 (За токены):",
                    " &7Стоимость: &#00FF66&l" + cost + " Токенов &a⛁",
                    "",
                    "&#00E5FFСпособ 2 (Универсальный Ключ):",
                    " &7Купить на сайте: &#00E5FF&n" + site,
                    hasKey ? " &#00FF66✔ У вас есть Ключ в инвентаре!" : " &8(Или нажмите ПКМ ключом в руке)",
                    "",
                    hasKey ? "&#00FF66▶ Нажмите, чтобы активировать Ключ!" : "&#FFD700▶ Нажмите, чтобы купить за " + cost + " Токенов!"
            )));
        }

        if ((page + 1) * 7 < maxLvl) inv.setItem(51, item(Material.ARROW, "&#00FF66&l[Следующая страница ▶]", null));

        p.openInventory(inv);
    }
    public void openQuests(Player p) {
        String seasonId = plugin.getActiveSeason();
        Inventory inv = Bukkit.createInventory(null, 27, MirTimePass.color("&#0055ff&l⚡ &#00FF66&lЗАДАНИЯ ТЕКУЩЕГО СЕЗОНА"));
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack dark = item(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, (i == 0 || i == 8 || i == 18 || i == 26) ? blue : dark);

        int[] slots = {10, 12, 14, 16};
        String qPath = "seasons." + seasonId + ".quests";
        if (plugin.getConfig().getConfigurationSection(qPath) != null) {
            List<String> keys = new ArrayList<>(plugin.getConfig().getConfigurationSection(qPath).getKeys(false));
            for (int i = 0; i < Math.min(4, keys.size()); i++) {
                String k = keys.get(i);
                String name = plugin.getConfig().getString(qPath + "." + k + ".name", k);
                String desc = plugin.getConfig().getString(qPath + "." + k + ".desc", "");
                int target = plugin.getConfig().getInt(qPath + "." + k + ".target", 1);
                int xp = plugin.getConfig().getInt(qPath + "." + k + ".xp", 50);
                int prog = plugin.getQuestProgress(p.getUniqueId(), k);
                boolean done = prog >= target;

                inv.setItem(slots[i], item(done ? Material.EMERALD_BLOCK : Material.PAPER, name, List.of(
                        "&7" + desc,
                        "",
                        "&fПрогресс: " + (done ? "&#00FF66&lВЫПОЛНЕНО ✔" : "&#00E5FF" + prog + " / " + target),
                        "&fНаграда: &#00FF66+" + xp + " XP"
                )));
            }
        }
        inv.setItem(22, item(Material.ARROW, "&#00E5FF&l[◀ Вернуться к наградам]", null));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (!title.contains("БОЕВОЙ ПРОПУСК") && !title.contains("ЗАДАНИЯ ТЕКУЩЕГО СЕЗОНА")) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (title.contains("ЗАДАНИЯ ТЕКУЩЕГО СЕЗОНА")) {
            if (slot == 22) openMain(p, playerPage.getOrDefault(p.getUniqueId(), 0));
            return;
        }

        int page = playerPage.getOrDefault(p.getUniqueId(), 0);
        if (slot == 47 && page > 0) { openMain(p, page - 1); return; }
        if (slot == 51 && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { openMain(p, page + 1); return; }
        if (slot == 48) { openQuests(p); return; }

        if (slot == 50 && !plugin.hasPremium(p.getUniqueId())) {
            if (plugin.consumeUniversalKeyFromInv(p)) {
                openMain(p, page);
                return;
            }
            int cost = plugin.getConfig().getInt("token-cost", 10000);
            int bal = plugin.getTokensBalance(p);
            if (bal < cost) {
                String site = plugin.getConfig().getString("site-link", "don.mir-time.ru");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.sendMessage(MirTimePass.color(plugin.getConfig().getString("prefix") + "&cНедостаточно токенов! Нужно &#00FF66" + cost + " ⛁ &cили Ключ с сайта &#00E5FF&n" + site));
                return;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "p take " + p.getName() + " " + cost);
            plugin.activatePremiumForWipe(p);
            openMain(p, page);
            return;
        }

        if ((slot >= 10 && slot <= 16) || (slot >= 28 && slot <= 34)) {
            boolean isPrem = slot >= 28;
            int col = isPrem ? slot - 28 : slot - 10;
            int targetLvl = page * 7 + 1 + col;
            plugin.claimReward(p, targetLvl, isPrem);
            openMain(p, page);
        }
    }

    public static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MirTimePass.color(name));
            if (lore != null) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(MirTimePass.color(s));
                meta.setLore(l);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(meta);
        }
        return is;
    }
}
