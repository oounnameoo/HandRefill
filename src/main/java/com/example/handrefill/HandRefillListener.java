package com.example.handrefill;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;

/**
 * Event listener for HandRefill.
 *
 * Responsibilities:
 *  • Detect manual hand-slot vacations (shift-click, drop key, off-hand swap)
 *    and flag RefillTask to skip the next depletion check for that player.
 *    This fixes the race condition where Shift+Click empties the hand slot,
 *    InventoryCloseEvent clears the inventory-open guard, and the next polling
 *    tick falsely triggers a refill.
 *  • Track when a player opens / closes any inventory screen so that
 *    RefillTask can suppress auto-refill while the inventory is open.
 *  • Clean up per-player state (RefillTask snapshots + SidebarHud scoreboard)
 *    when a player disconnects.
 */
public class HandRefillListener implements Listener {

    private final RefillTask refillTask;
    private final SidebarHud sidebar;

    public HandRefillListener(RefillTask refillTask, SidebarHud sidebar) {
        this.refillTask = refillTask;
        this.sidebar    = sidebar;
    }

    /** Any inventory screen opened — pause auto-refill. */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        HumanEntity entity = event.getPlayer();
        if (entity instanceof Player player) {
            refillTask.onInventoryOpen(player.getUniqueId());
        }
    }

    /** Inventory screen closed — resume auto-refill. */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity entity = event.getPlayer();
        if (entity instanceof Player player) {
            refillTask.onInventoryClose(player.getUniqueId());
        }
    }

    /**
     * Detect when the player manually vacates their held hand slot via
     * shift-click, drop key (Q / Ctrl+Q), off-hand swap (F), or number-key
     * hotbar assignment (1–9).
     *
     * NUMBER_KEY has two distinct scenarios that can both empty the hand slot:
     *
     *  Case A — hovering over the hand slot, pressing any number key N:
     *    Swaps hand slot ↔ hotbar[N].  If hotbar[N] is empty, hand goes empty.
     *    Detected by: clickedSlot == handSlot AND type == NUMBER_KEY.
     *
     *  Case B — hovering over ANY other slot, pressing the number key for handSlot:
     *    Swaps hovered slot ↔ hand slot.  If hovered slot is empty, hand goes empty.
     *    Detected by: type == NUMBER_KEY AND getHotbarButton() == handSlot
     *    (clickedSlot may not equal handSlot here, so the early-return guard is
     *    bypassed by checking this condition first, before the guard).
     *
     * Both leave the cursor empty and can race the InventoryCloseEvent.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;

        int       handSlot    = player.getInventory().getHeldItemSlot();
        int       clickedSlot = event.getSlot();
        ClickType type        = event.getClick();

        // Case B: hovering over any slot, pressing the number key for the hand slot
        if (type == ClickType.NUMBER_KEY && event.getHotbarButton() == handSlot) {
            refillTask.markHandSlotVacated(player.getUniqueId());
            return;
        }

        // All remaining checks only apply when the click lands on the hand slot itself
        if (clickedSlot != handSlot) return;

        // Case A + existing cases: action directly on the hand slot that moves
        // the item away without the cursor picking it up
        boolean isManualVacation =
            type == ClickType.SHIFT_LEFT    ||
            type == ClickType.SHIFT_RIGHT   ||
            type == ClickType.DROP          ||
            type == ClickType.CONTROL_DROP  ||
            type == ClickType.SWAP_OFFHAND  ||
            type == ClickType.NUMBER_KEY;   // Case A: hover hand slot, press any number key

        if (isManualVacation) {
            refillTask.markHandSlotVacated(player.getUniqueId());
        }
    }

    /** Player disconnected — free all per-player resources. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        refillTask.forgetPlayer(player.getUniqueId());
        sidebar.remove(player);
    }
}
