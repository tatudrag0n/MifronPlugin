package org.server.mifron;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 特殊クラフトの道具系（高度な作業台専用）。
 *
 * <ul>
 *   <li>収穫の鎌 — 3x3の実った作物をまとめて収穫する。</li>
 *   <li>光のしずく — 一時的な光源を置く（35秒で消える）。</li>
 * </ul>
 */
final class SpecialCraftToolFeature implements Listener {

    private static final String HARVEST_SCYTHE = "harvest_scythe";
    private static final String LIGHT_DROP = "light_drop";

    private static final int SCYTHE_COOLDOWN_SECONDS = 4;
    private static final int LIGHT_COOLDOWN_SECONDS = 8;
    private static final int LIGHT_TICKS = 35 * 20;

    private final Mifron plugin;
    private final SpecialCraftKit kit;

    SpecialCraftToolFeature(Mifron plugin, SpecialCraftKit kit) {
        this.plugin = plugin;
        this.kit = kit;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        String id = this.kit.heldId(player);
        if (id == null) {
            return;
        }
        // チェスト・ドアなどは通常操作を優先する（道具は発動させない）。
        if (SpecialCraftKit.opensOwnUi(event)) {
            return;
        }
        if (HARVEST_SCYTHE.equals(id)) {
            this.harvestScythe(event, player);
        } else if (LIGHT_DROP.equals(id)) {
            this.lightDrop(event, player);
        }
    }

    /** 収穫の鎌: クリックした作物を中心に3x3の実った作物を収穫する。 */
    private void harvestScythe(PlayerInteractEvent event, Player player) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            this.kit.actionBar(player, "§7作物を右クリックで収穫");
            return;
        }
        Block center = event.getClickedBlock();
        if (center == null) {
            return;
        }
        event.setCancelled(true);

        int harvested = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = center.getRelative(dx, 0, dz);
                if (!(block.getBlockData() instanceof Ageable age)) {
                    continue;
                }
                if (age.getAge() < age.getMaximumAge()) {
                    continue;
                }
                block.breakNaturally(player.getInventory().getItemInMainHand());
                harvested++;
            }
        }
        if (harvested == 0) {
            this.kit.actionBar(player, "§7実った作物がない");
            return;
        }
        if (!this.kit.tryUse(player, HARVEST_SCYTHE, SCYTHE_COOLDOWN_SECONDS)) {
            return;
        }
        this.kit.damageHeld(player, 1);
    }

    /** 光のしずく: クリックした面に光ブロックを置き、35秒後に消す。 */
    private void lightDrop(PlayerInteractEvent event, Player player) {
        Block clicked = event.getAction() == Action.RIGHT_CLICK_BLOCK ? event.getClickedBlock() : null;
        Block target = clicked == null
            ? player.getLocation().getBlock()
            : clicked.getRelative(event.getBlockFace());
        if (target.getType() != Material.AIR) {
            this.kit.actionBar(player, "§7そこには灯せない");
            return;
        }
        if (!this.kit.tryUse(player, LIGHT_DROP, LIGHT_COOLDOWN_SECONDS)) {
            return;
        }
        event.setCancelled(true);
        target.setType(Material.LIGHT, false);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (target.getType() == Material.LIGHT) {
                target.setType(Material.AIR, false);
            }
        }, LIGHT_TICKS);
        this.kit.actionBar(player, "§e光を灯した §7(35秒)");
    }
}
