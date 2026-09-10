package org.server.mifron;

import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

abstract class MifronPart7 extends MifronPart6x2 {
   protected void handleJumpPadWandClick(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      Block block = event.getClickedBlock();
      if (!player.hasPermission("mifron.admin")) { player.sendMessage("\u00a7c\u6a29\u9650\u304c\u3042\u308a\u307e\u305b\u3093\u3002"); event.setCancelled(true); return; }
      if (block == null) { player.sendMessage("\u00a7c\u30d6\u30ed\u30c3\u30af\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u304f\u3060\u3055\u3044\u3002"); event.setCancelled(true); return; }
      if (event.getAction().isRightClick()) {
         int verticalPower = this.utilityItemsFeature.getJumpPadVerticalPower(event.getItem());
         int horizontalPower = this.utilityItemsFeature.getJumpPadHorizontalPower(event.getItem());
         this.setJumpPad(block, verticalPower, horizontalPower);
         player.sendMessage("\u00a7a\u30b8\u30e3\u30f3\u30d7\u30d1\u30c3\u30c9\u3092\u8a2d\u5b9a\u3057\u307e\u3057\u305f\u3002\u7e26: " + verticalPower + " / \u6a2a: " + horizontalPower);
         event.setCancelled(true);
         return;
      }
      if (event.getAction().isLeftClick()) {
         boolean existed = this.setJumpPad(block, false);
         player.sendMessage((existed ? "\u00a7a\u30b8\u30e3\u30f3\u30d7\u30d1\u30c3\u30c9\u3092\u89e3\u9664\u3057\u307e\u3057\u305f\u3002" : "\u00a7e\u3053\u306e\u30d6\u30ed\u30c3\u30af\u306f\u30b8\u30e3\u30f3\u30d7\u30d1\u30c3\u30c9\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002"));
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
   public void onPlayerMove(PlayerMoveEvent event) {
      Location to = event.getTo();
      Location from = event.getFrom();
      if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) return;
      Player player = event.getPlayer();
      JumpPadPower power = this.jumpPadPower(to.clone().subtract(0.0, 1.0, 0.0).getBlock());
      if (power == null) return;
      long now = System.currentTimeMillis();
      if (now - this.lastJumpPadUse.getOrDefault(player.getUniqueId(), 0L) < 650L) return;
      this.lastJumpPadUse.put(player.getUniqueId(), now);
      this.jumpPadFallProtectionUntil.put(player.getUniqueId(), now + 60000L);
      player.setFallDistance(0.0F);
      Vector direction = player.getLocation().getDirection().setY(0.0);
      if (direction.lengthSquared() > 0.0) direction.normalize().multiply(this.jumpPadHorizontalVelocity(power.horizontal()));
      player.setVelocity(direction.setY(this.jumpPadVerticalVelocity(power.vertical())));
      player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8F, Math.min(2.0F, 1.0F + power.vertical() * 0.01F));
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onJumpPadFallDamage(EntityDamageEvent event) {
      if (!(event.getEntity() instanceof Player player) || event.getCause() != DamageCause.FALL) return;
      Long until = this.jumpPadFallProtectionUntil.remove(player.getUniqueId());
      if (until == null || System.currentTimeMillis() > until) return;
      player.setFallDistance(0.0F);
      event.setCancelled(true);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onJumpPadBlockBreak(BlockBreakEvent event) {
      if (this.jumpPadPower(event.getBlock()) != null) this.setJumpPad(event.getBlock(), false);
   }

   protected void setJumpPad(Block block, int verticalPower, int horizontalPower) {
      String path = this.jumpPadPath(block);
      this.data.set(path + ".verticalPower", this.clampJumpPadPower(verticalPower));
      this.data.set(path + ".horizontalPower", this.clampJumpPadPower(horizontalPower));
      this.data.set(path + ".material", block.getType().name());
      this.mifron().saveData();
   }

   protected boolean setJumpPad(Block block, boolean enabled) {
      boolean existed = this.jumpPadPower(block) != null;
      if (enabled) this.setJumpPad(block, 5, 5);
      else { this.data.set(this.jumpPadPath(block), null); this.mifron().saveData(); }
      return existed;
   }

   protected JumpPadPower jumpPadPower(Block block) {
      if (block == null) return null;
      String path = this.jumpPadPath(block);
      Object raw = this.data.get(path);
      if (raw instanceof Boolean value) { if (!value) return null; this.setJumpPad(block, 5, 5); return new JumpPadPower(5, 5); }
      if (raw instanceof Number value) {
         int power = this.oldJumpPadPowerToNewPower(value.intValue());
         this.setJumpPad(block, power, power);
         return new JumpPadPower(power, power);
      }
      ConfigurationSection section = this.data.getConfigurationSection(path);
      if (section == null) return null;
      if (!block.getType().name().equals(section.getString("material", ""))) return null;
      if (section.contains("power")) {
         int power = this.oldJumpPadPowerToNewPower(section.getInt("power", 3));
         this.setJumpPad(block, power, power);
         return new JumpPadPower(power, power);
      }
      return new JumpPadPower(this.clampJumpPadPower(section.getInt("verticalPower", 5)), this.clampJumpPadPower(section.getInt("horizontalPower", 5)));
   }

   protected String jumpPadPath(Block block) {
      return "jump-pads." + block.getWorld().getUID() + "." + block.getX() + "_" + block.getY() + "_" + block.getZ();
   }
   protected void migrateBarrelShopOfferSlots() {
      if (this.getConfig().getInt("barrel-shop.offer-slots", 27) == 18) { this.getConfig().set("barrel-shop.offer-slots", 27); this.saveConfig(); }
   }
   protected int clampJumpPadPower(int power) { return Math.max(1, Math.min(100, power)); }
   protected int oldJumpPadPowerToNewPower(int power) { return this.clampJumpPadPower(Math.max(1, Math.min(5, power)) * 2); }
   protected double jumpPadHorizontalVelocity(int power) {
      int safe = this.clampJumpPadPower(power);
      return 0.45 + Math.min(10, safe) * 0.18 + Math.max(0, safe - 10) * 0.04;
   }
   protected double jumpPadVerticalVelocity(int power) {
      int safe = this.clampJumpPadPower(power);
      return 0.75 + Math.min(10, safe) * 0.15 + Math.max(0, safe - 10) * 0.05;
   }
   protected MerchantOffer randomBarrelOffer(List<MerchantOffer> pool, Set<Material> used, boolean bargain) {
      List<MerchantOffer> candidates = pool.stream().filter(o -> !used.contains(o.material())).filter(o -> !bargain || this.mifron().isMerchantRarityAtLeast(this.mifron().merchantRarity(o.material()), "rare")).toList();
      if (candidates.isEmpty()) candidates = pool.stream().filter(o -> !used.contains(o.material())).toList();
      if (candidates.isEmpty()) { used.clear(); candidates = pool; }
      MerchantOffer offer = this.mifron().randomWeightedBarrelOffer(candidates);
      used.add(offer.material());
      return new MerchantOffer(offer.material(), offer.amount(), bargain ? "bargain" : "junk", offer.price());
   }
}
