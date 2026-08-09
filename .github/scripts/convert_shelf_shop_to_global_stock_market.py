from pathlib import Path

p = Path('src/main/java/org/server/minerva/Minerva.java')
s = p.read_text(encoding='utf-8')

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'missing patch target: {label}')
    s = s.replace(old, new, 1)

# Purchase: require global stock and decrement only after payment succeeds.
old = '''      if (offer.material() != null && offer.price() > 0) {
         int discountedPrice = this.applyShopDiscount(player, offer.price());
         int currentEmeralds = this.getEmeralds(player.getUniqueId());
         if (currentEmeralds < discountedPrice) {
            this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(discountedPrice - currentEmeralds) + "MP");
            return true;
         } else if (this.inventorySpaceFor(player, offer.material()) < offer.amount()) {
            this.showTemporaryActionBar(player, "インベントリに空きがありません。");
            return true;
         } else if (!this.withdrawEmeralds(player.getUniqueId(), discountedPrice)) {
            this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(discountedPrice) + "MP");
            return true;
         } else {
            this.giveShopPurchasedItems(player, offer.material(), offer.amount());
            this.addPlayerStat(player.getUniqueId(), "total-trades", offer.amount());
            this.playPurchaseSound(player);
            this.sendItemMessage(
               player, NamedTextColor.GREEN, "購入しました: ", offer.material(), " x" + offer.amount() + " (" + this.formatNumber(discountedPrice) + "MP)"
            );
            return true;
         }
      } else {'''
new = '''      if (offer.material() != null && offer.price() > 0) {
         int discountedPrice = this.applyShopDiscount(player, offer.price());
         int currentEmeralds = this.getEmeralds(player.getUniqueId());
         int stock = this.shelfShopStock(offer.material());
         if (stock < offer.amount()) {
            this.showTemporaryActionBar(player, "在庫切れです。プレイヤーが売却すると再入荷します。");
            return true;
         } else if (currentEmeralds < discountedPrice) {
            this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(discountedPrice - currentEmeralds) + "MP");
            return true;
         } else if (this.inventorySpaceFor(player, offer.material()) < offer.amount()) {
            this.showTemporaryActionBar(player, "インベントリに空きがありません。");
            return true;
         } else if (!this.withdrawEmeralds(player.getUniqueId(), discountedPrice)) {
            this.showTemporaryActionBar(player, "MPが不足しています：" + this.formatNumber(discountedPrice) + "MP");
            return true;
         } else {
            this.changeShelfShopStock(offer.material(), -offer.amount());
            this.giveShopPurchasedItems(player, offer.material(), offer.amount());
            this.addPlayerStat(player.getUniqueId(), "total-trades", offer.amount());
            this.playPurchaseSound(player);
            this.sendItemMessage(
               player,
               NamedTextColor.GREEN,
               "購入しました: ",
               offer.material(),
               " x" + offer.amount() + " (" + this.formatNumber(discountedPrice) + "MP / 在庫 " + this.formatNumber(this.shelfShopStock(offer.material())) + ")"
            );
            return true;
         }
      } else {'''
rep(old, new, 'purchase stock flow')

# Sale: strictly lower buyback than the player's current selling price and increase global stock.
old = '''      if (offer != null && held != null && held.getType() == offer.material()) {
         int price = this.materialBuyPrice(offer.material());
         if (price <= 0) {
            this.showTemporaryActionBar(player, "このアイテムは買い取り対象外です。");
            return true;
         } else if (this.utilityItemsFeature.getMinervaItemId(held) == null && !this.isShopWand(held)) {
            held.setAmount(held.getAmount() - 1);
            this.depositEmeralds(player.getUniqueId(), price);
            this.addPlayerStat(player.getUniqueId(), "total-trades", 1);
            this.playPurchaseSound(player);
            this.sendItemMessage(player, NamedTextColor.GREEN, "買い取りました: ", offer.material(), " (" + this.formatNumber(price) + "MP)");
            return true;
         } else {'''
new = '''      if (offer != null && held != null && held.getType() == offer.material()) {
         int currentSellPrice = this.applyShopDiscount(player, offer.price());
         int price = Math.max(0, Math.min(this.materialBuyPrice(offer.material()), currentSellPrice - 1));
         if (price <= 0) {
            this.showTemporaryActionBar(player, "このアイテムは買い取り対象外です。");
            return true;
         } else if (this.utilityItemsFeature.getMinervaItemId(held) == null && !this.isShopWand(held)) {
            held.setAmount(held.getAmount() - 1);
            this.changeShelfShopStock(offer.material(), 1);
            this.depositEmeralds(player.getUniqueId(), price);
            this.addPlayerStat(player.getUniqueId(), "total-trades", 1);
            this.playPurchaseSound(player);
            this.sendItemMessage(
               player,
               NamedTextColor.GREEN,
               "買い取りました: ",
               offer.material(),
               " (" + this.formatNumber(price) + "MP / 在庫 " + this.formatNumber(this.shelfShopStock(offer.material())) + ")"
            );
            return true;
         } else {'''
rep(old, new, 'sale stock flow')

rep('player.sendMessage("§a棚をショップ化しました。取得済みアイテムが順番に追加されます。");',
    'player.sendMessage("§a棚をショップ化しました。商品は固定カタログの番号順に表示され、在庫は全棚で共有されます。");',
    'shop wand message')

# Action bar includes item number, sale/buyback prices and global stock.
old = '''            if (offer != null) {
               player.sendActionBar(
                  ((TranslatableComponent)Component.translatable(offer.material().translationKey())
                        .color(this.rarityTextColor(this.merchantRarity(offer.material()))))
                     .append(Component.text("：" + this.formatNumber(this.applyShopDiscount(player, offer.price())) + "MP", NamedTextColor.GOLD))
               );
            }'''
new = '''            if (offer != null) {
               int catalogNumber = this.shelfShopCatalogNumber(offer.material());
               int sellPrice = this.applyShopDiscount(player, offer.price());
               int buyPrice = Math.max(0, Math.min(this.materialBuyPrice(offer.material()), sellPrice - 1));
               int stock = this.shelfShopStock(offer.material());
               Component prefix = Component.text(String.format("No.%03d ", Math.max(0, catalogNumber)), NamedTextColor.GRAY);
               player.sendActionBar(
                  prefix.append(
                     ((TranslatableComponent)Component.translatable(offer.material().translationKey())
                           .color(this.rarityTextColor(this.merchantRarity(offer.material()))))
                        .append(Component.text(
                           "  販売:" + this.formatNumber(sellPrice) + "MP / 買取:" + this.formatNumber(buyPrice) + "MP / 在庫:" + this.formatNumber(stock),
                           stock > 0 ? NamedTextColor.GOLD : NamedTextColor.RED
                        ))
                  )
               );
            }'''
rep(old, new, 'action bar stock')

# Shelf offers no longer depend on acquired/unlocked items. Every shelf gets the same fixed catalog sequence based on its assigned order.
start = s.find('   private List<Material> shelfShopRandomOffers(Block block) {')
end = s.find('   private List<Material> shelfShopUnlockedMaterials() {', start)
if start < 0 or end < 0:
    raise SystemExit('missing shelfShopRandomOffers span')
new_method = '''   private List<Material> shelfShopRandomOffers(Block block) {
      int order = this.data.getInt(this.shelfShopPath(block) + ".order", 0);
      if (order <= 0) {
         return List.of();
      }

      List<Material> catalog = this.shelfShopCatalogMaterials();
      int start = (order - 1) * SHELF_SHOP_OFFER_SLOTS;
      return start >= catalog.size() ? List.of() : catalog.subList(start, Math.min(catalog.size(), start + SHELF_SHOP_OFFER_SLOTS));
   }

'''
s = s[:start] + new_method + s[end:]

# Replace unlock/acquisition block with catalog + global stock helpers; retain no-op acquisition hooks for compatibility with callers.
start = s.find('   private List<Material> shelfShopUnlockedMaterials() {')
end = s.find('   private void handleShelfShopCommand(CommandSender sender, String[] args) {', start)
if start < 0 or end < 0:
    raise SystemExit('missing acquisition block')
helpers = '''   private List<Material> shelfShopCatalogMaterials() {
      List<Material> materials = new ArrayList<>();
      for (Material material : Material.values()) {
         if (this.isRandomShopItem(material)) {
            materials.add(material);
         }
      }
      materials.sort((a, b) -> a.name().compareTo(b.name()));
      return materials;
   }

   private int shelfShopCatalogNumber(Material material) {
      if (material == null) {
         return 0;
      }
      List<Material> catalog = this.shelfShopCatalogMaterials();
      int index = catalog.indexOf(material);
      return index < 0 ? 0 : index + 1;
   }

   private String shelfShopStockPath(Material material) {
      return "shelf-shop-stock." + material.name();
   }

   private int shelfShopStock(Material material) {
      return material == null ? 0 : Math.max(0, this.data.getInt(this.shelfShopStockPath(material), 0));
   }

   private void changeShelfShopStock(Material material, int delta) {
      if (material == null || delta == 0) {
         return;
      }
      int next = Math.max(0, this.shelfShopStock(material) + delta);
      this.data.set(this.shelfShopStockPath(material), next);
      this.saveData();
   }

   private void recordAcquiredItem(Player player, Material material) {
      // Shelf shops now use a fixed global catalog; acquiring an item no longer unlocks shop entries.
   }

   private void recordInventoryAcquisitions(Player player) {
      // Kept as a compatibility no-op. Global stock changes only through shelf-shop buy/sell transactions.
   }

   void recordShelfShopAcquisition(Player player, Material material) {
      // No-op: catalog availability is independent of player acquisition history.
   }

'''
s = s[:start] + helpers + s[end:]

# Admin reset now resets shared market stock instead of obsolete acquisition history.
old = '''      } else if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
         int unlockedCount = this.shelfShopUnlockedMaterials().size();
         this.data.set("shelf-shop-unlocked", List.of());
         this.saveData();
         this.syncShelfShopDisplays();
         sender.sendMessage("§a棚ショップの取得履歴をリセットしました: " + unlockedCount + "種類");
         if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.8F);
         }
      } else {'''
new = '''      } else if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
         ConfigurationSection stock = this.data.getConfigurationSection("shelf-shop-stock");
         int stockedTypes = stock == null ? 0 : stock.getKeys(false).size();
         this.data.set("shelf-shop-stock", null);
         this.data.set("shelf-shop-unlocked", null);
         this.saveData();
         this.syncShelfShopDisplays();
         sender.sendMessage("§a棚ショップの共有在庫を0にリセットしました: " + stockedTypes + "種類");
         if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 0.8F);
         }
      } else {'''
rep(old, new, 'admin reset')

# Shelf display stacks reflect stock visually while preserving the catalog item even at stock 0.
old = '''               inventory.setItem(slot, new ItemStack(material));'''
new = '''               ItemStack displayItem = new ItemStack(material);
               int stock = this.shelfShopStock(material);
               if (stock > 0 && displayItem.getMaxStackSize() > 1) {
                  displayItem.setAmount(Math.min(stock, displayItem.getMaxStackSize()));
               }
               inventory.setItem(slot, displayItem);'''
rep(old, new, 'display stock amount')

# Remove legacy offer data whenever a shelf is enabled: order alone determines its contents.
old = '''      if (enabled && !existed) {
         int nextOrder = 1;'''
new = '''      if (enabled) {
         this.clearShelfShopRandomOffer(block);
      }

      if (enabled && !existed) {
         int nextOrder = 1;'''
rep(old, new, 'legacy offer cleanup')

p.write_text(s, encoding='utf-8')
print('patched Minerva.java for global-stock shelf market')
