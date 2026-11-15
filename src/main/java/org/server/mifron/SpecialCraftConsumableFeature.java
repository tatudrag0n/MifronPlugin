package org.server.mifron;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 特殊クラフトの「消費アイテム」3種（応急包帯・毒消しの蜂蜜・名工の金槌）を扱う。
 *
 * <p>すべてメインハンドの右クリックで発動する。発動判定は
 * {@link SpecialCraftKit#heldId(Player)} / {@link SpecialCraftKit#tryUse(Player, String, int)} に任せ、
 * このクラスは効果の適用だけを受け持つ。</p>
 */
final class SpecialCraftConsumableFeature implements Listener {
    private static final String ID_FIELD_BANDAGE = "field_bandage";
    private static final String ID_ANTIDOTE_HONEY = "antidote_honey";
    private static final String ID_REPAIR_HAMMER = "repair_hammer";

    private static final int BANDAGE_COOLDOWN_SECONDS = 25;
    private static final int ANTIDOTE_COOLDOWN_SECONDS = 30;
    private static final int REPAIR_HAMMER_COOLDOWN_SECONDS = 60;

    /** 応急包帯の回復量。 */
    private static final double BANDAGE_HEAL_AMOUNT = 4.0;
    /** 名工の金槌が1部位あたりに戻す耐久値。 */
    private static final int REPAIR_HAMMER_DAMAGE_RESTORE = 15;

    private final Mifron plugin;
    private final SpecialCraftKit kit;

    SpecialCraftConsumableFeature(Mifron plugin, SpecialCraftKit kit) {
        this.plugin = plugin;
        this.kit = kit;
    }

    // ------------------------------------------------------------------
    // 純粋関数（単体テスト可能）
    // ------------------------------------------------------------------

    /** 現在体力 {@code current} から応急包帯を使ったあとの体力（最大値 {@code max} で頭打ち）。 */
    static double healedHealth(double current, double max) {
        return Math.min(max, current + BANDAGE_HEAL_AMOUNT);
    }

    /**
     * 現在の損傷値 {@code currentDamage} から金槌で15戻したあとの損傷値。
     * 損傷値は 0 未満にならない。
     */
    static int repairedDamage(int currentDamage) {
        return Math.max(0, currentDamage - REPAIR_HAMMER_DAMAGE_RESTORE);
    }

    // ------------------------------------------------------------------
    // イベント
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
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
        // チェスト・ドアなどは通常操作を優先する（持ち物の都合で操作を止めない）。
        if (SpecialCraftKit.opensOwnUi(event)) {
            return;
        }
        // 飲食物の自動飲食・設置を止める。キャンセルしないと蜂蜜が二重に発動する。
        switch (id) {
            case ID_FIELD_BANDAGE -> {
                event.setCancelled(true);
                this.useFieldBandage(player);
            }
            case ID_ANTIDOTE_HONEY -> {
                event.setCancelled(true);
                this.useAntidoteHoney(player);
            }
            case ID_REPAIR_HAMMER -> {
                event.setCancelled(true);
                this.useRepairHammer(player);
            }
            default -> {
                // 他の特殊クラフトは他Featureの担当。
            }
        }
    }

    // ------------------------------------------------------------------
    // 応急包帯
    // ------------------------------------------------------------------

    private void useFieldBandage(Player player) {
        double current = player.getHealth();
        double max = player.getMaxHealth();
        if (current >= max) {
            // 満タン時はクールダウンを消費しない（tryUse を呼ばない）。
            this.kit.actionBar(player, "§b体力は満タン");
            return;
        }
        if (!this.kit.tryUse(player, ID_FIELD_BANDAGE, BANDAGE_COOLDOWN_SECONDS)) {
            return;
        }
        player.setHealth(healedHealth(current, max));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
        this.kit.actionBar(player, "§a応急包帯で体力を4回復した");
    }

    // ------------------------------------------------------------------
    // 毒消しの蜂蜜
    // ------------------------------------------------------------------

    private void useAntidoteHoney(Player player) {
        if (!this.kit.tryUse(player, ID_ANTIDOTE_HONEY, ANTIDOTE_COOLDOWN_SECONDS)) {
            return;
        }
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, true, false));
        this.kit.consumeHeld(player);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        this.kit.actionBar(player, "§a毒と衰弱を浄化した §7(再生I 5秒)");
    }

    // ------------------------------------------------------------------
    // 名工の金槌
    // ------------------------------------------------------------------

    private void useRepairHammer(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] armor = inventory.getArmorContents();
        if (countRepairable(armor) == 0) {
            // 直す対象が無いときはクールダウンを消費しない（tryUse を呼ばない）。
            this.kit.actionBar(player, "§7直す装備がない");
            return;
        }
        if (!this.kit.tryUse(player, ID_REPAIR_HAMMER, REPAIR_HAMMER_COOLDOWN_SECONDS)) {
            return;
        }
        int repaired = repairArmor(armor);
        if (repaired > 0) {
            inventory.setArmorContents(armor);
            this.kit.damageHeld(player, 1);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
            this.kit.actionBar(player, "§e防具" + repaired + "部位の耐久を15回復した");
        }
    }

    /** 損傷している（=直せる）防具の部位数。 */
    private static int countRepairable(ItemStack[] armor) {
        if (armor == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack piece : armor) {
            Damageable damageable = damageableOf(piece);
            if (damageable != null && damageable.getDamage() > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 防具配列の各部位を {@link #repairedDamage(int)} だけ修復する。
     * 修復した部位数を返す（配列と各 ItemStack は直接書き換える）。
     */
    private static int repairArmor(ItemStack[] armor) {
        if (armor == null) {
            return 0;
        }
        int repaired = 0;
        for (ItemStack piece : armor) {
            Damageable damageable = damageableOf(piece);
            if (damageable == null || damageable.getDamage() <= 0) {
                continue;
            }
            int next = repairedDamage(damageable.getDamage());
            damageable.setDamage(next);
            piece.setItemMeta((ItemMeta) damageable);
            repaired++;
        }
        return repaired;
    }

    /** 損傷値を持てるアイテムならそのメタを返す（空気・非ダメージアイテムは null）。 */
    private static Damageable damageableOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return damageable;
        }
        return null;
    }
}
