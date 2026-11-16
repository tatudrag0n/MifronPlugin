package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * 特殊クラフト品の共通処理（生成・識別・クールダウン・耐久）。
 *
 * <p>効果そのものはテーマ別の Feature に分かれており、このクラスは
 * それらが共有する最小限の道具箱に徹する。
 */
final class SpecialCraftKit {

    /** 特殊クラフトを識別する PDC キー（既存の狙撃弓・連射弩・暗視鏡と共通）。 */
    private static final String ID_KEY_NAME = "special_craft";

    private final Mifron plugin;
    private final NamespacedKey idKey;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    SpecialCraftKit(Mifron plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, ID_KEY_NAME);
    }

    Mifron plugin() {
        return this.plugin;
    }

    // ------------------------------------------------------------------
    // 生成・識別
    // ------------------------------------------------------------------

    /** カタログのIDから特殊クラフト品を生成する。未知のIDなら null。 */
    ItemStack create(String id) {
        SpecialCraftCatalog.Def def = SpecialCraftCatalog.byId(id);
        if (def == null) {
            return null;
        }
        ItemStack item = new ItemStack(def.material(), Math.max(1, def.amount()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(def.displayName());
            List<String> lore = new ArrayList<>(def.lore());
            lore.add("§8特殊クラフト");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(this.idKey, PersistentDataType.STRING, def.id());
            String itemModel = SpecialCraftCatalog.itemModel(def.id());
            if (itemModel != null) {
                meta.setItemModel(new NamespacedKey(this.plugin, itemModel));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 特殊クラフトIDを返す（特殊クラフトでなければ null）。 */
    String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(this.idKey, PersistentDataType.STRING);
    }

    /** 指定IDの特殊クラフトか。 */
    boolean is(ItemStack item, String id) {
        return id != null && id.equals(this.idOf(item));
    }

    /** メインハンドの特殊クラフトID（無ければ null）。 */
    String heldId(Player player) {
        return player == null ? null : this.idOf(player.getInventory().getItemInMainHand());
    }

    /** オフハンドの特殊クラフトID（無ければ null）。 */
    String offhandId(Player player) {
        return player == null ? null : this.idOf(player.getInventory().getItemInOffHand());
    }

    /** メインハンドかオフハンドに持っているか。 */
    boolean isHeld(Player player, String id) {
        return this.is(player == null ? null : player.getInventory().getItemInMainHand(), id)
            || this.is(player == null ? null : player.getInventory().getItemInOffHand(), id);
    }

    /** 防具4部位のいずれかに装着しているか。 */
    boolean isWearing(Player player, String id) {
        if (player == null || id == null) {
            return false;
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (this.is(armor, id)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // クールダウン
    // ------------------------------------------------------------------

    private String cooldownKey(Player player, String id) {
        UUID uuid = player.getUniqueId();
        return uuid + "|" + id;
    }

    /** 残りクールダウン（秒）。0 なら使用可能。 */
    int remaining(Player player, String id) {
        if (player == null || id == null) {
            return 0;
        }
        Long until = this.cooldowns.get(this.cooldownKey(player, id));
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0L) {
            this.cooldowns.remove(this.cooldownKey(player, id));
            return 0;
        }
        return (int) Math.ceil(left / 1000.0);
    }

    /**
     * クールダウンを確認して開始する。使えたら true。
     * まだ残っているときはアクションバーに残り秒数を出して false を返す。
     */
    boolean tryUse(Player player, String id, int cooldownSeconds) {
        if (player == null || id == null) {
            return false;
        }
        int left = this.remaining(player, id);
        if (left > 0) {
            this.actionBar(player, "§cクールダウン中 §7残り " + left + "秒");
            return false;
        }
        if (cooldownSeconds > 0) {
            this.cooldowns.put(this.cooldownKey(player, id),
                System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
        return true;
    }

    void clearCooldown(Player player, String id) {
        if (player != null && id != null) {
            this.cooldowns.remove(this.cooldownKey(player, id));
        }
    }

    void actionBar(Player player, String text) {
        if (player != null) {
            player.sendActionBar(text);
        }
    }

    // ------------------------------------------------------------------
    // 耐久・消費
    // ------------------------------------------------------------------

    /**
     * メインハンドのアイテムの耐久を減らす。壊れた場合は true（アイテムは消える）。
     * 耐久値を持たないアイテムは何もせず false。
     */
    boolean damageHeld(Player player, int amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return false;
        }
        int max = held.getType().getMaxDurability();
        if (max <= 0) {
            return false;
        }
        if (!(held.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        int next = damageable.getDamage() + amount;
        if (next >= max) {
            player.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.AIR));
            return true;
        }
        damageable.setDamage(next);
        held.setItemMeta(damageable);
        return false;
    }

    /** メインハンドのアイテムを1つ消費する。 */
    void consumeHeld(Player player) {
        if (player == null) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        int amount = held.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.AIR));
        } else {
            held.setAmount(amount - 1);
        }
    }

    /** 座標から当たり判定に使う距離（二乗）へ。 */
    static double squared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    /**
     * 右クリックしたブロックが「プレイヤー自身のUIを開くブロック」なら true。
     *
     * <p>特殊クラフト品を持ったままチェストやドアを操作したときに、その操作を
     * 邪魔しないためのガード。true のときは発動もキャンセルもせず素通しする。
     */
    static boolean opensOwnUi(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event == null || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null) {
            return false;
        }
        if (block.getState() instanceof org.bukkit.inventory.InventoryHolder) {
            return true;
        }
        return block.getType().isInteractable();
    }
}
