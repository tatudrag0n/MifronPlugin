from pathlib import Path

p = Path('src/main/java/org/server/minerva/Minerva.java')
s = p.read_text(encoding='utf-8')

# 1) Add a short duplicate-input guard for shelf shop interactions.
anchor = '   private final Map<UUID, Long> portalUseCooldowns = new ConcurrentHashMap<>();'
if anchor in s and 'shelfShopTransactionUntil' not in s:
    s = s.replace(anchor, anchor + '\n   private final Map<UUID, Long> shelfShopTransactionUntil = new ConcurrentHashMap<>();', 1)
else:
    # Fallback: place near other maps in Minerva if ServerPortal fields are elsewhere.
    marker = '   private final Map<UUID, Long> temporaryActionBarUntil = new ConcurrentHashMap<>();'
    if marker in s and 'shelfShopTransactionUntil' not in s:
        s = s.replace(marker, marker + '\n   private final Map<UUID, Long> shelfShopTransactionUntil = new ConcurrentHashMap<>();', 1)

# 2) Cancel native shelf interaction before transaction work and collapse duplicate packets.
old = '''            } else if (event.getAction().isRightClick()\n               && event.getClickedBlock() != null\n               && this.isShelf(event.getClickedBlock().getType())\n               && this.isShelfShop(event.getClickedBlock())) {\n               if (!this.slotMachineManager.isMachine(event.getClickedBlock())) {\n                  if (this.isMinervaItem(item, "emerald_bundle")) {\n                     this.tryShopPayment(player, event.getClickedBlock());\n                  } else {\n                     this.tryShopSell(player, event.getClickedBlock(), item);\n                  }\n\n                  event.setCancelled(true);\n               }\n'''
new = '''            } else if (event.getAction().isRightClick()\n               && event.getClickedBlock() != null\n               && this.isShelf(event.getClickedBlock().getType())\n               && this.isShelfShop(event.getClickedBlock())) {\n               if (!this.slotMachineManager.isMachine(event.getClickedBlock())) {\n                  // Cancel the vanilla shelf interaction immediately so its use animation/inventory\n                  // handling cannot overlap the Minerva transaction. Bedrock clients can also emit\n                  // duplicate interact packets for one tap, so collapse only near-identical packets.\n                  event.setCancelled(true);\n                  long now = System.currentTimeMillis();\n                  long blockedUntil = this.shelfShopTransactionUntil.getOrDefault(player.getUniqueId(), 0L);\n                  if (now < blockedUntil) {\n                     return;\n                  }\n                  this.shelfShopTransactionUntil.put(player.getUniqueId(), now + 90L);\n\n                  if (this.isMinervaItem(item, "emerald_bundle")) {\n                     this.tryShopPayment(player, event.getClickedBlock());\n                  } else {\n                     this.tryShopSell(player, event.getClickedBlock(), item);\n                  }\n               }\n'''
if old not in s:
    raise SystemExit('shelf interaction block not found')
s = s.replace(old, new, 1)

# 3) Make purchase persistence match the already-optimized sell path: one queued save.
old = '''         } else if (!this.withdrawEmeralds(player.getUniqueId(), discountedPrice)) {\n            this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(discountedPrice) + "MP");\n            return true;\n         } else {\n            this.changeShelfShopStock(offer.material(), -offer.amount());\n            this.giveShopPurchasedItems(player, offer.material(), offer.amount());\n            this.addPlayerStat(player.getUniqueId(), "total-trades", offer.amount());\n            this.playPurchaseSound(player);'''
new = '''         } else if (!this.withdrawEmeralds(player.getUniqueId(), discountedPrice, false)) {\n            this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(discountedPrice) + "MP");\n            return true;\n         } else {\n            this.changeShelfShopStock(offer.material(), -offer.amount());\n            this.giveShopPurchasedItems(player, offer.material(), offer.amount());\n            this.addPlayerStat(player.getUniqueId(), "total-trades", offer.amount(), false);\n            this.queueDataSave();\n            this.playPurchaseSound(player);'''
if old not in s:
    raise SystemExit('purchase persistence block not found')
s = s.replace(old, new, 1)

# 4) Add persist-aware withdrawal overload while preserving existing callers.
old = '''   boolean withdrawEmeralds(UUID uuid, int amount) {\n      if (amount <= 0) {\n         return false;\n      }\n\n      ConfigurationSection section = this.getPlayerSection(uuid);'''
new = '''   boolean withdrawEmeralds(UUID uuid, int amount) {\n      return this.withdrawEmeralds(uuid, amount, true);\n   }\n\n   private boolean withdrawEmeralds(UUID uuid, int amount, boolean persist) {\n      if (amount <= 0) {\n         return false;\n      }\n\n      ConfigurationSection section = this.getPlayerSection(uuid);'''
if old not in s:
    raise SystemExit('withdraw method start not found')
s = s.replace(old, new, 1)

# Change only the save in the withdrawal method to respect persist. Locate bounded method body.
start = s.index('   private boolean withdrawEmeralds(UUID uuid, int amount, boolean persist) {')
end = s.find('\n   }\n', start)
if end == -1:
    raise SystemExit('withdraw method end not found')
body = s[start:end+5]
if 'this.saveData();' in body:
    body = body.replace('this.saveData();', 'if (persist) {\n            this.saveData();\n         }', 1)
else:
    raise SystemExit('withdraw saveData call not found')
s = s[:start] + body + s[end+5:]

p.write_text(s, encoding='utf-8')
print('optimized shelf shop purchase/sell transaction path')
