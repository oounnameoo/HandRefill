package com.example.handrefill;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Pure stateless utility methods for refill logic.
 * No Bukkit scheduling or event dependencies — easy to unit-test.
 */
public final class RefillUtil {

    private RefillUtil() {}

    /**
     * Find the best matching stack to move into the hand slot.
     *
     * Priority order:
     *  1. Other hotbar slots (0–8), largest stack first.
     *  2. Main inventory slots (9–35), largest stack first.
     *
     * "Matching" means exact material AND exact ItemMeta (so an
     * enchanted sword won't refill from a plain sword stack).
     *
     * @param inv      the player's inventory
     * @param handSlot the currently held slot index (0–8), excluded from search
     * @param target   the item that just ran out (used as the match template)
     * @return the inventory slot index containing the best match, or -1 if none
     */
    public static int findBestMatch(PlayerInventory inv, int handSlot, ItemStack target) {
        if (target == null || target.getType() == Material.AIR) return -1;

        int bestHotbarSlot   = -1;
        int bestHotbarAmount = -1;
        int bestMainSlot     = -1;
        int bestMainAmount   = -1;

        for (int i = 0; i <= 35; i++) {
            if (i == handSlot) continue;

            ItemStack candidate = inv.getItem(i);
            if (candidate == null || candidate.getType() == Material.AIR) continue;
            if (!itemMatches(candidate, target)) continue;

            if (i <= 8) {
                // Hotbar
                if (candidate.getAmount() > bestHotbarAmount) {
                    bestHotbarAmount = candidate.getAmount();
                    bestHotbarSlot   = i;
                }
            } else {
                // Main inventory
                if (candidate.getAmount() > bestMainAmount) {
                    bestMainAmount = candidate.getAmount();
                    bestMainSlot   = i;
                }
            }
        }

        // Prefer hotbar over main inventory
        return bestHotbarSlot != -1 ? bestHotbarSlot : bestMainSlot;
    }

    /**
     * Sum the total count of a given item type across all 36 player inventory
     * slots (hotbar + main), including the held slot.
     *
     * @param inv    the player's inventory
     * @param target the item to count (matched by material + ItemMeta)
     * @return total item count, 0 if none found
     */
    public static int computeTotal(PlayerInventory inv, ItemStack target) {
        if (target == null || target.getType() == Material.AIR) return 0;

        int total = 0;
        for (int i = 0; i <= 35; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (itemMatches(slot, target)) {
                total += slot.getAmount();
            }
        }
        return total;
    }

    /**
     * Returns true when two ItemStacks represent the same item:
     * same material AND identical ItemMeta (enchantments, display name, lore, etc.).
     * Amount is intentionally ignored.
     */
    public static boolean itemMatches(ItemStack a, ItemStack b) {
        if (a.getType() != b.getType()) return false;

        boolean aMeta = a.hasItemMeta();
        boolean bMeta = b.hasItemMeta();
        if (aMeta != bMeta) return false;
        if (aMeta && !a.getItemMeta().equals(b.getItemMeta())) return false;

        return true;
    }
}
