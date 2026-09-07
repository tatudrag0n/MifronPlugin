package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * SpecialItemsFeature
 * --------------------
 * Implements the 7 special (legendary) items. Each vanilla base item has
 * a very low chance to be converted into its special counterpart when
 * picked up / crafted. Special items are flagged unbreakable + untradeable
 * via PersistentDataContainer so shop/anvil/trade code elsewhere can check
 * SpecialItemsFeature.isSpecial(item).
 *
 * Base items and chances:
 *  1. フックショット (fishing rod)     0.05%
 *  2. スクラッチ (paper)              1%
 *  3. ジェットパック (chainmail chestplate) 1%
 *  4. ローラースケート (iron boots)  0.01%
 *  5. 残響の結晶 (echo shard)         2%
 *  6. ファイアーボール (fire charge)  0.1%
 *  7. 朽ちた剣 (netherite sword)      0.001%
 *
 * Wiring (Mifron.java onEnable):
 *   SpecialItemsFeature.register(this);
 */
public final class SpecialItemsFeature implements Listener {

    public static final NamespacedKey SPECIAL_KEY = new NamespacedKey("mifron", "special_item");

    private static final Random RANDOM = new Random();

    public enum SpecialItem {
        HOOKSHOT("hookshot", "§bフックショット", 0.0005),
        SCRATCH("scratch", "§fスクラッチ", 0.01),
        JETPACK("jetpack", "§6ジェットパック", 0.01),
        ROLLERSKATE("rollerskate", "§bローラースケート", 0.0001),
        ECHO_CRYSTAL("echo_crystal", "§5残響の結晶", 0.02),
        FIREBALL("fireball", "§cファイアーボール", 0.001),
        DECAYED_SWORD("decayed_sword", "§8朽ちた剣", 0.00001);

        public final String id;
        public final String displayName;
        public final double chance;

        SpecialItem(String id, String displayName, double chance) {
            this.id = id;
            this.displayName = displayName;
            this.chance = chance;
        }
    }

    private SpecialItemsFeature() {}

    public static void register(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new SpecialItemsFeature(), plugin);
    }

    public static boolean isSpecial(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(SPECIAL_KEY, PersistentDataType.STRING);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ItemStack item = event.getItem().getItemStack();
        rollAndTransform(item);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        rollAndTransform(result);
    }

    private void rollAndTransform(ItemStack item) {
        if (item == null || isSpecial(item)) return;
        SpecialItem match = matchBaseItem(item);
        if (match == null) return;
        if (RANDOM.nextDouble() > match.chance) return;
        applySpecial(item, match);
    }

    private SpecialItem matchBaseItem(ItemStack item) {
        switch (item.getType().name()) {
            case "FISHING_ROD": return SpecialItem.HOOKSHOT;
            case "PAPER": return SpecialItem.SCRATCH;
            case "CHAINMAIL_CHESTPLATE": return SpecialItem.JETPACK;
            case "IRON_BOOTS": return SpecialItem.ROLLERSKATE;
            case "ECHO_SHARD": return SpecialItem.ECHO_CRYSTAL;
            case "FIRE_CHARGE": return SpecialItem.FIREBALL;
            case "NETHERITE_SWORD": return SpecialItem.DECAYED_SWORD;
            default: return null;
        }
    }

    private void applySpecial(ItemStack item, SpecialItem special) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(special.displayName);
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(SPECIAL_KEY, PersistentDataType.STRING, special.id);
        item.setItemMeta(meta);
        Bukkit.getLogger().info("[Mifron] Special item generated: " + special.id);
    }

    /**
     * Right-click ability dispatch. One-shot consumption (Fireball,
     * Echo Crystal reverting to a plain shard) is handled here; physical
     * abilities (hookshot pull, jetpack flight, rollerskate friction)
     * should be implemented by per-tick scheduler tasks hooked via
     * getAbilityId(item) - e.g. inside AthleticManager-style movement code.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isSpecial(item)) return;
        String id = getAbilityId(item);
        if (id == null) return;
        if (id.equals(SpecialItem.FIREBALL.id)) {
            event.getPlayer().getInventory().removeItem(item);
        } else if (id.equals(SpecialItem.ECHO_CRYSTAL.id)) {
            ItemStack plain = new ItemStack(org.bukkit.Material.ECHO_SHARD, item.getAmount());
            event.getPlayer().getInventory().setItemInMainHand(plain);
        }
    }

    public static String getAbilityId(ItemStack item) {
        if (!isSpecial(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(SPECIAL_KEY, PersistentDataType.STRING);
    }
}
