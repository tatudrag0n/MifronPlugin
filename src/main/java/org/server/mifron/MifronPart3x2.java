package org.server.mifron;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

abstract class MifronPart3x2 extends MifronPart3x1 {
   protected void tryConvertProposedItem(EntityPickupItemEvent event, ItemStack source) {
      ConfigurationSection proposals = this.getConfig().getConfigurationSection("custom-items");
      if (proposals == null || source == null || source.getAmount() <= 0) return;
      for (String id : proposals.getKeys(false)) {
         String path = "custom-items." + id;
         if (!this.getConfig().getBoolean(path + ".conversion-enabled", true)) continue;
         Material base = Material.matchMaterial(this.getConfig().getString(path + ".base-material", ""));
         Material result = Material.matchMaterial(this.getConfig().getString(path + ".material", ""));
         double chance = Math.max(0.0, Math.min(0.01, this.getConfig().getDouble(path + ".conversion-chance", 0.001)));
         if (base == null || result == null || base == result || source.getType() != base || chance <= 0.0 || this.random.nextDouble() >= chance) continue;
         ItemStack converted = this.mifron().named(result, this.getConfig().getString(path + ".display-name", id), this.getConfig().getStringList(path + ".lore"));
         ItemMeta meta = converted.getItemMeta();
         meta.setEnchantmentGlintOverride(this.getConfig().getBoolean(path + ".glint", false));
         converted.setItemMeta(meta);
         source.setAmount(source.getAmount() - 1);
         event.getItem().getWorld().dropItem(event.getItem().getLocation(), converted);
         event.getItem().setItemStack(source);
         break;
      }
   }

   @EventHandler
   public void onMobDeath(EntityDeathEvent event) {
      LivingEntity entity = event.getEntity();
      Player killer = entity.getKiller();
      if (killer == null || entity instanceof Player || this.mifron().isMifronMerchant(entity)) return;
      if (this.isFfaSummonedMob(entity) || !this.isDirectPlayerKill(entity, killer)) return;
      int baseReward = this.mobKillReward(entity.getType());
      if (baseReward <= 0) return;
      int reward = this.mifron().applyIncomeBonus(killer.getUniqueId(), this.adjustedMobKillReward(killer.getUniqueId(), entity.getType(), baseReward));
      this.mifron().addPlayerStat(killer.getUniqueId(), "total-mob-kills", 1);
      boolean firstKill = this.addKilledMob(killer.getUniqueId(), entity.getType());
      this.mifron().depositEmeralds(killer.getUniqueId(), reward);
      killer.sendMessage("\u00a7a\u8a0e\u4f10\u5831\u916c: +" + this.mifron().formatNumber(reward) + "MP");
      if (firstKill) {
         int firstKillBonus = this.mifron().applyIncomeBonus(killer.getUniqueId(), this.firstMobKillBonus(baseReward));
         if (firstKillBonus > 0) {
            this.mifron().depositEmeralds(killer.getUniqueId(), firstKillBonus);
            killer.sendMessage("\u00a76\u521d\u8a0e\u4f10\u30dc\u30fc\u30ca\u30b9: +" + this.mifron().formatNumber(firstKillBonus) + "MP");
         }
      }
   }

   protected int mobKillReward(EntityType type) {
      return type == null ? 0 : MOB_KILL_REWARDS.getOrDefault(type.name(), 0);
   }

   protected int firstMobKillBonus(int baseReward) {
      double multiplier = Math.max(0.0D, Math.min(100.0D, this.getConfig().getDouble("mob-kill-rewards.first-kill-bonus-multiplier", 5.0D)));
      int minimum = Math.max(0, this.getConfig().getInt("mob-kill-rewards.first-kill-bonus-minimum", 25));
      return (int) Math.min(2000000000L, Math.max((long) minimum, Math.round(baseReward * multiplier)));
   }

   protected boolean isFfaSummonedMob(LivingEntity entity) {
      return "summon".equals(entity.getPersistentDataContainer().get(this.ffaEntityKindKey, PersistentDataType.STRING));
   }

   protected int adjustedMobKillReward(UUID uuid, EntityType type, int baseReward) {
      if (baseReward <= 0 || type == null) return 0;
      long now = System.currentTimeMillis();
      Map<String, KillRewardWindow> playerWindows = this.mobRewardWindows.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
      KillRewardWindow window = playerWindows.computeIfAbsent(type.name(), ignored -> new KillRewardWindow(now));
      if (now - window.startedAtMillis >= 3600000L) {
         window.startedAtMillis = now;
         window.count = 0;
      }
      window.count++;
      return window.count > 100 && this.isFarmAdjustedMob(type) ? Math.max(1, baseReward / 2) : baseReward;
   }

   protected boolean isFarmAdjustedMob(EntityType type) {
      return switch (type) {
         case BEE, CAVE_SPIDER, DOLPHIN, ENDERMITE, GOAT, GUARDIAN, LLAMA, MAGMA_CUBE, POLAR_BEAR, SILVERFISH, SLIME, TRADER_LLAMA, ZOMBIFIED_PIGLIN -> true;
         default -> false;
      };
   }

   protected boolean isDirectPlayerKill(LivingEntity entity, Player killer) {
      if (!(entity.getLastDamageCause() instanceof EntityDamageByEntityEvent entityDamage)) return false;
      Entity damager = entityDamage.getDamager();
      if (damager instanceof Player player) return player.getUniqueId().equals(killer.getUniqueId());
      return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player && player.getUniqueId().equals(killer.getUniqueId());
   }

   protected boolean addKilledMob(UUID uuid, EntityType type) {
      Set<String> killed = new HashSet<>(this.mifron().getPlayerSection(uuid).getStringList("killed-mobs"));
      if (!killed.add(type.name())) return false;
      this.mifron().getPlayerSection(uuid).set("killed-mobs", new ArrayList<>(killed));
      this.queueDataSave();
      return true;
   }
}
