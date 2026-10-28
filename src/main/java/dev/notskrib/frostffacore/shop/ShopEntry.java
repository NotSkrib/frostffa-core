package dev.notskrib.frostffacore.shop;

import org.bukkit.inventory.ItemStack;

/**
 * One purchasable catalog line: the exact item (material, amount, name, lore, enchants —
 * whatever was held when an admin added it) and its price in the PlayerPoints-backed
 * currency. No stock/limit tracking — every purchase gives a fresh copy of {@code display}.
 */
public record ShopEntry(String id, ItemStack display, int price) {}
