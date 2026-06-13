package com.example.handrefill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Repeating task (every 1 tick) responsible for:
 *
 *  1. Detecting when the held stack was naturally depleted and triggering
 *     an auto-refill.
 *  2. Driving the per-player scoreboard sidebar HUD via {@link SidebarHud}.
 *
 * ── Refill guards ────────────────────────────────────────────────────────────
 *  • Cursor guard     — skips refill when the cursor holds the item that just
 *    left the slot (player dragged it away manually, not consumed it).
 *  • Inventory guard  — skips refill entirely while any inventory screen is open
 *    (player inventory, chest, furnace, etc.). Tracked via
 *    {@link #onInventoryOpen} / {@link #onInventoryClose}.
 *  • Manual-vacate guard — skips the ONE tick that follows a shift-click, drop,
 *    or off-hand swap on the hand slot.  These actions move the item out of the
 *    slot without leaving anything on the cursor AND may race the InventoryClose
 *    event, so we need an explicit per-event suppression flag. Set by
 *    HandRefillListener via {@link #markHandSlotVacated}.
 */
public class RefillTask extends BukkitRunnable {

    /** Sidebar is refreshed every N ticks (4 ≈ 200 ms). */
    private static final int SIDEBAR_INTERVAL = 4;

    private final HandRefillPlugin plugin;
    private final Set<UUID>        refillDisabled;
    private final SidebarHud       sidebar;

    // ── Per-player tracking ───────────────────────────────────────────────────

    /** Hotbar slot index held on the previous tick. */
    private final Map<UUID, Integer>   lastSlot      = new HashMap<>();

    /**
     * Clone of the item held on the previous tick.
     * {@code null} means the hand was empty that tick.
     */
    private final Map<UUID, ItemStack> lastItem      = new HashMap<>();

    /**
     * Players who currently have any inventory screen open.
     * Populated by HandRefillListener via {@link #onInventoryOpen} /
     * {@link #onInventoryClose}.
     */
    private final Set<UUID>            inventoryOpen    = new HashSet<>();

    /**
     * Players whose hand slot was manually vacated this tick via shift-click,
     * drop, or off-hand swap.  The next depletion check for these players is
     * skipped unconditionally, regardless of whether the inventory appears closed.
     * Entries are consumed (removed) as soon as they are checked.
     */
    private final Set<UUID>            manuallyVacated  = new HashSet<>();

    private int tickCounter = 0;

    // ─────────────────────────────────────────────────────────────────────────

    public RefillTask(HandRefillPlugin plugin, Set<UUID> refillDisabled, SidebarHud sidebar) {
        this.plugin         = plugin;
        this.refillDisabled = refillDisabled;
        this.sidebar        = sidebar;
    }

    // ── Inventory-screen tracking (called by HandRefillListener) ──────────────

    public void onInventoryOpen(UUID uuid) {
        inventoryOpen.add(uuid);
    }

    public void onInventoryClose(UUID uuid) {
        inventoryOpen.remove(uuid);
    }

    /**
     * Called by HandRefillListener when a shift-click, drop key, or off-hand
     * swap is detected on the player's currently held hand slot.
     * Suppresses the next depletion-refill check for this player.
     */
    public void markHandSlotVacated(UUID uuid) {
        manuallyVacated.add(uuid);
    }

    // ── Main loop ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        tickCounter++;
        boolean doSidebar = (tickCounter % SIDEBAR_INTERVAL == 0);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID            uuid         = player.getUniqueId();
            PlayerInventory inv          = player.getInventory();
            int             currentSlot  = inv.getHeldItemSlot();
            ItemStack       currentItem  = inv.getItemInMainHand(); // never null in Paper
            boolean         currentEmpty = currentItem.getType() == Material.AIR;

            // ── 1. Refill check ───────────────────────────────────────────────
            if (!refillDisabled.contains(uuid)) {
                Integer   prevSlot = lastSlot.get(uuid);
                ItemStack prevItem = lastItem.get(uuid);

                boolean slotUnchanged  = prevSlot != null && prevSlot == currentSlot;
                boolean wasHoldingItem = prevItem != null && prevItem.getType() != Material.AIR;
                boolean cursorEmpty    = player.getItemOnCursor().getType() == Material.AIR;
                boolean invClosed      = !inventoryOpen.contains(uuid);

                /*
                 * Refill fires only when ALL conditions are true:
                 *  (a) same hotbar slot as last tick (not a manual slot switch)
                 *  (b) slot held a real item last tick
                 *  (c) slot is now empty (item was consumed / used)
                 *  (d) cursor is empty — non-empty cursor means the player moved
                 *      the item away with their mouse, not actually consumed it
                 *  (e) no inventory screen is open
                 *  (f) the hand slot was NOT manually vacated this tick via
                 *      shift-click / drop / off-hand swap (beats the race where
                 *      InventoryCloseEvent clears the guard before we run)
                 */
                boolean notManuallyVacated = !manuallyVacated.remove(uuid); // consume the flag
                if (slotUnchanged && wasHoldingItem && currentEmpty && cursorEmpty && invClosed && notManuallyVacated) {
                    int refillSlot = RefillUtil.findBestMatch(inv, currentSlot, prevItem);
                    if (refillSlot != -1) {
                        ItemStack replacement = inv.getItem(refillSlot).clone();
                        inv.setItemInMainHand(replacement);
                        inv.setItem(refillSlot, null);

                        // Subtle audio cue
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);

                        // Brief action-bar confirmation flash
                        Component flash = Component.text("✔ Refilled ", NamedTextColor.GREEN)
                                .append(Component.text(friendlyName(replacement),
                                        NamedTextColor.GOLD, TextDecoration.BOLD))
                                .append(Component.text(" ×" + replacement.getAmount(),
                                        NamedTextColor.WHITE));
                        player.sendActionBar(flash);

                        currentItem  = replacement;
                        currentEmpty = false;
                    }
                }
            }

            // ── 2. Update per-player snapshot ─────────────────────────────────
            lastSlot.put(uuid, currentSlot);
            lastItem.put(uuid, currentEmpty ? null : currentItem.clone());

            // ── 3. Sidebar HUD ────────────────────────────────────────────────
            if (doSidebar) {
                if (currentEmpty) {
                    sidebar.hide(player);
                } else {
                    int total = RefillUtil.computeTotal(inv, currentItem);
                    sidebar.update(player, currentItem, total);
                }
            }
        }
    }

    /**
     * Remove all stored state for a player (called on quit to avoid memory leaks).
     */
    public void forgetPlayer(UUID uuid) {
        lastSlot.remove(uuid);
        lastItem.remove(uuid);
        inventoryOpen.remove(uuid);
        manuallyVacated.remove(uuid);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String friendlyName(ItemStack item) {
        if (item.hasItemMeta()) {
            var meta = item.getItemMeta();
            if (meta.hasDisplayName() && meta.displayName() != null) {
                return PlainTextComponentSerializer.plainText()
                        .serialize(meta.displayName());
            }
        }
        return toTitleCase(item.getType().name().replace('_', ' '));
    }

    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder(input.length());
        for (String word : input.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }
}
