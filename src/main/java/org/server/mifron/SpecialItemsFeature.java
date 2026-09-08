package org.server.mifron;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpecialItemsFeature implements Listener {
    private final Mifron plugin;
    private final NamespacedKey keySpecial;
    private final Random random = new Random();

    public SpecialItemsFeature(Mifron plugin) {
        this.plugin = plugin;
        this.keySpecial = new NamespacedKey(plugin, "special_item_id");
    }

    public enum SpecialType {
        HOOKSHOT("hookshot", "§6§lフックショット", Material.FISHING_ROD, 0.0005, "§7ウキを引っ掛けた所へ急接近する。"),
        SCRATCH("scratch", "§e§lスクラッチ", Material.PAPER, 0.01, "§7右クリックで運試し！MPを獲得できる。"),
        JETPACK("jetpack", "§b§lジェットパック", Material.CHAINMAIL_CHESTPLATE, 0.01, "§7ブレイズパウダーを燃料に空を飛ぶ。"),
        ROLLER_SKATES("skates", "§f§lローラースケート", Material.IRON_BOOTS, 0.0001, "§7陸上を氷の上のように高速滑走する。"),
        RESONANCE_CRYSTAL("resonance", "§3§l残響の結晶", Material.ECHO_SHARD, 0.02, "§7ウォーデンのソニックビームを放つ。(使い捨て)"),
        FIREBALL("fireball", "§c§lファイアーボール", Material.FIRE_CHARGE, 0.001, "§7ガストの火の玉を撃ち出す。(使い捨て)"),
        DECAYED_SWORD("decayed_sword", "§8§l朽ちた剣", Material.NETHERITE_SWORD, 0.00001, "§7いつかの勇者の剣。全エンチャントMAXで真価を発揮する。");

        public final String id;
        public final String displayName;
        public final Material baseMaterial;
        public final double chance;
        public final String description;

        SpecialType(String id, String displayName, Material baseMaterial, double chance, String description) {
            this.id = id;
            this.displayName = displayName;
            this.baseMaterial = baseMaterial;
            this.chance = chance;
            this.description = description;
        }
    }

    public ItemStack createSpecialItem(SpecialType type) {
        ItemStack item = new ItemStack(type.baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(type.displayName);
            meta.setUnbreakable(true);
            List<String> lore = new ArrayList<>();
            lore.add(type.description);
            lore.add("§8[特殊不可壊アイテム / 売買不可]");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(keySpecial, PersistentDataType.STRING, type.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isSpecialItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keySpecial, PersistentDataType.STRING);
    }

    public String getSpecialId(ItemStack item) {
        if (!isSpecialItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keySpecial, PersistentDataType.STRING);
    }

    private void tryTransform(ItemStack item, Player player) {
        if (item == null || isSpecialItem(item)) return;
        for (SpecialType type : SpecialType.values()) {
            if (item.getType() == type.baseMaterial) {
                if (random.nextDouble() < type.chance) {
                    ItemStack special = createSpecialItem(type);
                    item.setType(special.getType());
                    item.setItemMeta(special.getItemMeta());
                    player.sendMessage(ChatColor.GOLD + "✨ 奇跡が起きた！ 手に入れたアイテムが " + type.displayName + ChatColor.GOLD + " に変化した！");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            tryTransform(event.getItem().getItemStack(), (Player) event.getEntity());
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            ItemStack result = event.getCurrentItem();
            if (result != null) {
                tryTransform(result, (Player) event.getWhoClicked());
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !isSpecialItem(item)) return;

        String id = getSpecialId(item);
        if (id == null) return;

        if (id.equals("scratch") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            int winMp = (random.nextInt(100) < 10) ? 500 : 50;
            plugin.depositEmeralds(player.getUniqueId(), winMp);
            player.sendMessage(ChatColor.YELLOW + "🎰 スクラッチを削った！ " + winMp + " MP を手に入れた！");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } else if (id.equals("resonance") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            player.getWorld().playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f);
            player.getWorld().spawnParticle(org.bukkit.Particle.SONIC_BOOM, eye.add(dir.clone().multiply(2)), 1);

            eye.getWorld().getNearbyEntities(eye, 15, 15, 15).forEach(e -> {
                if (e != player && e instanceof org.bukkit.entity.LivingEntity) {
                    ((org.bukkit.entity.LivingEntity) e).damage(20.0, player);
                }
            });

            item.setAmount(item.getAmount() - 1);
            player.getInventory().addItem(new ItemStack(Material.ECHO_SHARD));
            player.sendMessage(ChatColor.DARK_AQUA + "残響の結晶の力が解放され、通常の欠片に戻った。");
        } else if (id.equals("fireball") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            Location eye = player.getEyeLocation();
            Fireball fireball = player.launchProjectile(Fireball.class, eye.getDirection().multiply(1.5));
            fireball.setYield(2.0f);
            player.getWorld().playSound(eye, Sound.ENTITY_GHAST_SHOOT, 1.0f, 1.0f);
        } else if (id.equals("jetpack") && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (player.getInventory().contains(Material.BLAZE_POWDER)) {
                player.getInventory().removeItem(new ItemStack(Material.BLAZE_POWDER, 1));
                player.setVelocity(player.getVelocity().setY(1.0).add(player.getLocation().getDirection().multiply(0.8)));
                player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, player.getLocation(), 15, 0.2, 0.2, 0.2, 0.05);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            } else {
                player.sendMessage(ChatColor.RED + "ジェットパックの燃料(ブレイズパウダー)がありません！");
            }
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!isSpecialItem(item) || !"hookshot".equals(getSpecialId(item))) return;

        if (event.getState() == PlayerFishEvent.State.IN_GROUND || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            Player player = event.getPlayer();
            Location hookLoc = event.getHook().getLocation();
            Vector pull = hookLoc.toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.8);
            pull.setY(Math.min(pull.getY() + 0.5, 1.5));
            player.setVelocity(pull);
            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.5f);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null && isSpecialItem(boots) && "skates".equals(getSpecialId(boots))) {
            if (player.isSprinting() && player.isOnGround()) {
                Vector v = player.getLocation().getDirection().normalize().multiply(0.35);
                v.setY(player.getVelocity().getY());
                player.setVelocity(v);
                player.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE, player.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }
}
