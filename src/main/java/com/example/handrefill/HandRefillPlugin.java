package com.example.handrefill;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * HandRefill — main plugin entry point.
 *
 * Features:
 *  • Scoreboard sidebar HUD  — shows held item name, slot count, and inventory
 *    total on the right side of the screen while the player holds any item.
 *    Counts over 999 are displayed as "999+".
 *  • Auto-refill — when the held stack is genuinely consumed (placed, eaten,
 *    shot, etc.) the best matching stack (hotbar first, then main inventory,
 *    largest stack, exact material + ItemMeta match) is moved to the hand slot.
 *    Cursor-based inventory moves are intentionally ignored to avoid false refills.
 *
 * Command:
 *  /handrefill toggle  — enable / disable auto-refill for yourself
 *  /handrefill status  — show whether auto-refill is currently on
 */
public class HandRefillPlugin extends JavaPlugin {

    /**
     * UUIDs of players who have disabled auto-refill via /handrefill toggle.
     * In-memory only; resets on server restart (intentional for v1).
     */
    private final Set<UUID> refillDisabled = new HashSet<>();

    private SidebarHud  sidebar;
    private RefillTask  refillTask;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        sidebar    = new SidebarHud();
        refillTask = new RefillTask(this, refillDisabled, sidebar);

        // 1-tick repeating task: handles refill detection + sidebar HUD updates
        refillTask.runTaskTimer(this, 0L, 1L);

        // Quit-event listener: cleans up per-player state to prevent memory leaks
        getServer().getPluginManager().registerEvents(
                new HandRefillListener(refillTask, sidebar), this);

        getLogger().info("HandRefill enabled.");
        getLogger().info("  • Scoreboard sidebar (right of screen) shows item count.");
        getLogger().info("  • Total count is capped at '999+' in the display.");
        getLogger().info("  • Hand auto-refills when a stack is consumed (not cursor-moved).");
        getLogger().info("  • /handrefill toggle — turn auto-refill on/off per-player.");
    }

    @Override
    public void onDisable() {
        if (refillTask != null) {
            refillTask.cancel();
        }
        getLogger().info("HandRefill disabled.");
    }

    // ── Command handling ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String sub = (args.length > 0) ? args[0].toLowerCase() : "toggle";

        switch (sub) {
            case "toggle" -> {
                UUID uuid = player.getUniqueId();
                if (refillDisabled.contains(uuid)) {
                    refillDisabled.remove(uuid);
                    player.sendMessage("§aHandRefill auto-refill §fenabled§a.");
                } else {
                    refillDisabled.add(uuid);
                    player.sendMessage("§cHandRefill auto-refill §fdisabled§c.");
                }
            }
            case "status" -> {
                boolean on = !refillDisabled.contains(player.getUniqueId());
                player.sendMessage("§eHandRefill is currently "
                        + (on ? "§aenabled" : "§cdisabled") + "§e.");
            }
            default -> player.sendMessage("§eUsage: §f/handrefill <toggle|status>");
        }
        return true;
    }
}
