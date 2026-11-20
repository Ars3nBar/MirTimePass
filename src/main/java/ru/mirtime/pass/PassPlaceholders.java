package ru.mirtime.pass;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PassPlaceholders extends PlaceholderExpansion {

    private final MirTimePass plugin;

    public PassPlaceholders(MirTimePass plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mtpass";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MirTime";
    }

    @Override
    public @NotNull String getVersion() {
        return "4.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        boolean prem = plugin.isPremium(player.getUniqueId());
        String sName = plugin.getSeasonSec() != null ? plugin.getSeasonSec().getString("display-name", "") : "";

        return switch (params.toLowerCase()) {
            case "status" -> MirTimePass.color(prem ? "&#FFD700&lPREMIUM ✔" : "&7Базовый");
            case "status_short" -> MirTimePass.color(prem ? "&#00FF66&l✔" : "&c&l✖");
            case "level" -> String.valueOf(plugin.getLevel(player.getUniqueId()));
            case "xp" -> String.valueOf(plugin.getXp(player.getUniqueId()));
            case "season" -> MirTimePass.color(sName);
            default -> "";
        };
    }
}
