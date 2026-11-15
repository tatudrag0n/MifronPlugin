package org.server.mifron;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * 特殊クラフトのうち「道具系」7種の右クリック効果を受け持つ。
 *
 * <p>携帯作業台・携帯エンダーチェスト・野営セット・収穫の鎌・折り畳みボート・
 * 足場の束・光のしずく。メインハンドの右クリックだけを拾い、作業台や
 * エンダーチェストをそのまま設置されてしまわないように必ずイベントを止める。
 */
final class SpecialCraftToolFeature implements Listener {

    private static final String PORTABLE_CRAFTING_TABLE = "portable_crafting_table";
    private static final String PORTABLE_ENDER_CHEST = "portable_ender_chest";
    private static final String CAMPFIRE_KIT = "campfire_kit";
    private static final String HARVEST_SCYTHE = "harvest_scythe";
    private static final String FOLDING_BOAT = "folding_boat";
    private static final String SCAFFOLD_PACK = "scaffold_pack";
    private static final String LIGHT_DROP = "light_drop";

    /** この Feature が扱うID。ここに無いIDの右クリックは素通しする。 */
    private static final Set<String> TOOL_IDS = Set.of(
        PORTABLE_CRAFTING_TABLE,
        PORTABLE_ENDER_CHEST,
        CAMPFIRE_KIT,
        HARVEST_SCYTHE,
        FOLDING_BOAT,
        SCAFFOLD_PACK,
        LIGHT_DROP);

    /** 光のしずくが消えるまでの時間（35秒）。 */
    private static final long LIGHT_DROP_LIFETIME_TICKS = 35L * 20L;

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
        if (id == null || !TOOL_IDS.contains(id)) {
            return;
        }
        // チェスト・ドアなどは通常操作を優先する（道具は発動させない）。
        if (SpecialCraftKit.opensOwnUi(event)) {
            return;
        }
        // バニラの設置（作業台・エンダーチェスト等）と土の耕しを止める。
        event.setCancelled(true);
        switch (id) {
            case PORTABLE_CRAFTING_TABLE -> this.portableCraftingTable(player);
            case PORTABLE_ENDER_CHEST -> this.portableEnderChest(player);
            case CAMPFIRE_KIT -> this.campfireKit(event, player);
            case HARVEST_SCYTHE -> this.harvestScythe(event, player);
            case FOLDING_BOAT -> this.foldingBoat(event, player);
            case SCAFFOLD_PACK -> this.scaffoldPack(player);
            case LIGHT_DROP -> this.lightDrop(event, player);
            default -> {
                // TOOL_IDS で絞ってあるので到達しない。
            }
        }
    }

    // ------------------------------------------------------------------
    // 携帯作業台・携帯エンダーチェスト
    // ------------------------------------------------------------------

    private void portableCraftingTable(Player player) {
        if (!this.kit.tryUse(player, PORTABLE_CRAFTING_TABLE, 10)) {
            return;
        }
        player.openWorkbench(player.getLocation(), true);
    }

    private void portableEnderChest(Player player) {
        if (!this.kit.tryUse(player, PORTABLE_ENDER_CHEST, 15)) {
            return;
        }
        player.openInventory(player.getEnderChest());
    }

    // ------------------------------------------------------------------
    // 野営セット・光のしずく（面にブロックを置く）
    // ------------------------------------------------------------------

    private void campfireKit(PlayerInteractEvent event, Player player) {
        Block placed = this.placementBlock(event);
        if (placed == null) {
            this.kit.actionBar(player, "§7焚き火を置く面を狙ってください");
            return;
        }
        if (!this.kit.tryUse(player, CAMPFIRE_KIT, 10)) {
            return;
        }
        placed.setType(Material.CAMPFIRE, false);
    }

    private void lightDrop(PlayerInteractEvent event, Player player) {
        Block placed = this.placementBlock(event);
        if (placed == null) {
            this.kit.actionBar(player, "§7光を置く面を狙ってください");
            return;
        }
        if (!this.kit.tryUse(player, LIGHT_DROP, 8)) {
            return;
        }
        placed.setType(Material.LIGHT, false);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (placed.getType() == Material.LIGHT) {
                placed.setType(Material.AIR, false);
            }
        }, LIGHT_DROP_LIFETIME_TICKS);
    }

    /** 右クリックした面の隣で、空気になっているブロック。置けなければ null。 */
    private Block placementBlock(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return null;
        }
        Block target = clicked.getRelative(event.getBlockFace());
        return target.getType().isAir() ? target : null;
    }

    // ------------------------------------------------------------------
    // 収穫の鎌
    // ------------------------------------------------------------------

    private void harvestScythe(PlayerInteractEvent event, Player player) {
        Block center = event.getClickedBlock();
        if (!isMature(center)) {
            this.kit.actionBar(player, "§7実った作物を狙ってください");
            return;
        }
        List<Block> crops = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = center.getRelative(dx, 0, dz);
                if (isMature(block)) {
                    crops.add(block);
                }
            }
        }
        if (crops.isEmpty()) {
            return;
        }
        if (!this.kit.tryUse(player, HARVEST_SCYTHE, 4)) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (Block crop : crops) {
            crop.breakNaturally(tool);
        }
        this.kit.damageHeld(player, 1);
    }

    /** 育ちきった作物（バニラの Ageable）か。 */
    private static boolean isMature(Block block) {
        return block != null
            && block.getBlockData() instanceof Ageable age
            && age.getAge() >= age.getMaximumAge();
    }

    // ------------------------------------------------------------------
    // 折り畳みボート
    // ------------------------------------------------------------------

    private void foldingBoat(PlayerInteractEvent event, Player player) {
        if (player.getVehicle() != null) {
            this.kit.actionBar(player, "§7すでに乗り物に乗っています");
            return;
        }
        if (this.boatNearby(player)) {
            this.kit.actionBar(player, "§7近くにボートがあります");
            return;
        }
        if (!this.kit.tryUse(player, FOLDING_BOAT, 20)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Location target;
        if (clicked != null && clicked.isLiquid()) {
            target = clicked.getLocation().add(0.5, 1.0, 0.5);
        } else {
            target = player.getLocation();
        }
        World world = player.getWorld();
        Entity spawned = world.spawnEntity(target, EntityType.OAK_BOAT);
        if (spawned instanceof Boat boat) {
            boat.addPassenger(player);
            return;
        }
        spawned.remove();
        this.kit.clearCooldown(player, FOLDING_BOAT);
        this.kit.actionBar(player, "§7ボートを出せませんでした");
    }

    private boolean boatNearby(Player player) {
        for (Entity nearby : player.getNearbyEntities(4.0, 3.0, 4.0)) {
            if (nearby instanceof Boat) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 足場の束
    // ------------------------------------------------------------------

    private void scaffoldPack(Player player) {
        Location origin = player.getLocation();
        Vector direction = origin.getDirection();
        direction.setY(0.0);
        if (direction.lengthSquared() < 1.0E-4) {
            double yaw = Math.toRadians(origin.getYaw());
            direction = new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        direction.normalize();
        Block base = origin.getBlock();
        Set<Block> targets = new LinkedHashSet<>();
        for (int step = 1; step <= 5; step++) {
            int dx = (int) Math.round(direction.getX() * step);
            int dz = (int) Math.round(direction.getZ() * step);
            if (dx == 0 && dz == 0) {
                continue;
            }
            Block target = base.getRelative(dx, 0, dz);
            if (target.getType().isAir()) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            this.kit.actionBar(player, "§7足場を敷く場所がありません");
            return;
        }
        if (!this.kit.tryUse(player, SCAFFOLD_PACK, 8)) {
            return;
        }
        int placed = 0;
        for (Block target : targets) {
            if (!target.getType().isAir()) {
                continue;
            }
            target.setType(Material.SCAFFOLDING, false);
            placed++;
        }
        if (placed <= 0) {
            this.kit.clearCooldown(player, SCAFFOLD_PACK);
            return;
        }
        this.consumeOneUse(player);
        this.kit.actionBar(player, "§7足場を " + placed + " 本設置");
    }

    // ------------------------------------------------------------------
    // 消耗
    // ------------------------------------------------------------------

    /**
     * 道具を1回ぶん消耗させる。耐久のある道具は耐久を1減らし、耐久を持たない
     * もの（足場の束）はスタックから1個減らす。
     */
    private void consumeOneUse(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        if (held.getType().getMaxDurability() > 0) {
            this.kit.damageHeld(player, 1);
            return;
        }
        this.kit.consumeHeld(player);
    }
}
