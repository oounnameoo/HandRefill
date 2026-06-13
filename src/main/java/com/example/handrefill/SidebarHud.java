package com.example.handrefill;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages a per-player scoreboard sidebar (right side of screen) showing:
 *
 *   ┌─────────────────┐
 *   │  Stone Slab     │  ← item name  (gold, bold) — objective title
 *   │  ▣ Total: 192   │  ← inventory total  ("999+" when > 999)
 *   └─────────────────┘
 */
public class SidebarHud {

    /*
     * Single dummy entry name — a colour-reset sequence that renders as
     * zero-width text in vanilla, giving us a unique scoreboard key that
     * won't appear as a visible label alongside the team prefix.
     */
    private static final String ENTRY_TOTAL = "\u00a7r"; // §r

    // ── Per-player state ──────────────────────────────────────────────────────
    private final Map<UUID, Scoreboard> boards     = new HashMap<>();
    private final Map<UUID, Objective>  objectives = new HashMap<>();
    private final Map<UUID, Team>       totTeams   = new HashMap<>();
    private final Set<UUID>             shown      = new HashSet<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Show or refresh the sidebar.
     *
     * @param player the player to update
     * @param held   the item currently in the main hand (non-null, non-AIR)
     * @param total  total count of this item across the whole inventory
     */
    public void update(Player player, ItemStack held, int total) {
        UUID uuid = player.getUniqueId();

        if (!boards.containsKey(uuid)) {
            init(player);
        }

        Objective obj    = objectives.get(uuid);
        Team      totTeam = totTeams.get(uuid);

        // Update sidebar title to the current item name
        obj.displayName(buildItemName(held));

        // Make sidebar visible if it was hidden
        if (!shown.contains(uuid)) {
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            shown.add(uuid);
        }

        // Update total line — cap display at 999+
        String totalStr = total > 999 ? "999+" : String.valueOf(total);
        totTeam.prefix(
            Component.text("▣ Total: ", NamedTextColor.GRAY)
                .append(Component.text(totalStr, NamedTextColor.AQUA))
        );
    }

    /** Hide the sidebar when the player's hand is empty. */
    public void hide(Player player) {
        UUID uuid = player.getUniqueId();
        if (shown.remove(uuid)) {
            Objective obj = objectives.get(uuid);
            if (obj != null) {
                obj.setDisplaySlot(null);
            }
        }
    }

    /** Release all resources for a player (call on PlayerQuitEvent). */
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        shown.remove(uuid);
        boards.remove(uuid);
        objectives.remove(uuid);
        totTeams.remove(uuid);
        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void init(Player player) {
        UUID uuid = player.getUniqueId();

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(uuid, board);

        Objective obj = board.registerNewObjective(
            "handrefill",
            Criteria.DUMMY,
            Component.text("HandRefill", NamedTextColor.GOLD, TextDecoration.BOLD)
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        objectives.put(uuid, obj);

        // Single "Total" line with score number hidden
        Team totTeam = board.registerNewTeam("total");
        totTeam.addEntry(ENTRY_TOTAL);
        Score totScore = obj.getScore(ENTRY_TOTAL);
        totScore.setScore(1);
        totScore.numberFormat(NumberFormat.blank()); // hides the numeric score
        totTeams.put(uuid, totTeam);

        shown.add(uuid);
        player.setScoreboard(board);
    }

    private Component buildItemName(ItemStack item) {
        String name;
        if (item.hasItemMeta()) {
            var meta = item.getItemMeta();
            if (meta.hasDisplayName() && meta.displayName() != null) {
                name = PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
            } else {
                name = toTitleCase(item.getType().name().replace('_', ' '));
            }
        } else {
            name = toTitleCase(item.getType().name().replace('_', ' '));
        }
        return Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (String word : s.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }
}
