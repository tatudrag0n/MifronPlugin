from pathlib import Path


def method_span(text: str, signature: str):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f"missing method: {signature}")
    brace = text.find('{', start)
    if brace < 0:
        raise SystemExit(f"missing opening brace: {signature}")
    depth = 0
    for i in range(brace, len(text)):
        c = text[i]
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise SystemExit(f"unterminated method: {signature}")


def replace_method(text: str, signature: str, replacement: str) -> str:
    start, end = method_span(text, signature)
    return text[:start] + replacement + text[end:]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing patch target: {label}")
    return text.replace(old, new, 1)

root = Path('.')
p = root / 'src/main/java/org/server/mifron/Mifron.java'
s = p.read_text(encoding='utf-8')

s = replace_method(s, '   private void handleShopWandClick(PlayerInteractEvent event)', r'''   private void handleShopWandClick(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      Block block = event.getClickedBlock();
      ItemStack wand = event.getItem();
      ShopWandType type = this.shopWandType(wand);
      if (type == null) {
         player.sendMessage("§cこのワンドの種類を判別できません。再発行してください。");
         event.setCancelled(true);
      } else if (type.isSlotWand()) {
         this.handleSlotWandClick(event, player, block, type);
      } else if (type == ShopWandType.FRAME) {
         player.sendMessage("§c額縁ショップは未実装です。額縁は既存のオークション機能を使用してください。");
         event.setCancelled(true);
      } else if (block == null || !this.isValidShopWandTarget(block, type)) {
         player.sendMessage("§c" + this.shopWandTargetMessage(type));
         event.setCancelled(true);
      } else if (event.getAction().isRightClick() && !this.canCreateShop(player)) {
         player.sendMessage("§c権限がありません。");
         event.setCancelled(true);
      } else if (event.getAction().isLeftClick() && !this.canManageShop(player, block)) {
         player.sendMessage("§cこのショップを解除できるのは作成者または管理者のみです。");
         event.setCancelled(true);
      } else if (block.getType() == Material.BARREL) {
         this.handleBarrelShopWandClick(player, block, event, type);
      } else if (event.getAction().isRightClick()) {
         if (player.isSneaking()) {
            ItemStack specified = player.getInventory().getItemInOffHand();
            Material material = specified == null ? Material.AIR : specified.getType();
            if (material == Material.AIR || !this.isRandomShopItem(material) || this.utilityItemsFeature.getMifronItemId(specified) != null) {
               player.sendMessage("§e指定配置: オフハンドに販売したい通常アイテムを持ち、設定したい棚の枠をShift+右クリックしてください。");
               event.setCancelled(true);
               return;
            }

            int selectedSlot = this.selectedShelfSlot(player, block);
            this.assignCustomShelfShopSlot(block, selectedSlot, material);
            this.setShopOwner(block, player.getUniqueId());
            player.sendMessage(
               "§a指定配置に設定: 枠" + (selectedSlot + 1) + " → No." + String.format("%03d", this.shelfShopCatalogNumber(material)) + " " + this.japaneseItemName(material)
            );
         } else {
            this.configureSequentialShelfShop(block);
            this.setShopOwner(block, player.getUniqueId());
            List<Material> materials = this.shelfShopRandomOffers(block);
            if (materials.isEmpty()) {
               player.sendMessage("§e順番配置の対象商品がありません。");
            } else {
               int first = this.shelfShopCatalogNumber(materials.get(0));
               int last = this.shelfShopCatalogNumber(materials.get(materials.size() - 1));
               player.sendMessage("§a順番配置に設定しました: No." + String.format("%03d", first) + "～No." + String.format("%03d", last));
            }
         }
         event.setCancelled(true);
      } else if (event.getAction().isLeftClick()) {
         if (player.isSneaking() && "custom".equals(this.shelfShopMode(block))) {
            int selectedSlot = this.selectedShelfSlot(player, block);
            this.clearCustomShelfShopSlot(block, selectedSlot);
            player.sendMessage("§a指定配置の枠" + (selectedSlot + 1) + "を空欄にしました。");
         } else if (this.setShelfShop(block, false)) {
            player.sendMessage("§a棚のショップ化を解除しました。");
         } else {
            player.sendMessage("§eこの棚はショップ化されていません。");
         }
         event.setCancelled(true);
      }
   }''')

s = replace_method(s, '   private Mifron.ShelfShopOffer readShelfShopOffer(Player player, Block block)', r'''   private Mifron.ShelfShopOffer readShelfShopOffer(Player player, Block block) {
      if (block == null || !this.isShelf(block.getType()) || !this.isShelfShop(block)) {
         return null;
      }

      List<Material> configuredMaterials = this.shelfShopRandomOffers(block);
      if (configuredMaterials.isEmpty()) {
         return null;
      }

      Material configuredMaterial = this.materialForShelfSlot(configuredMaterials, this.selectedShelfSlot(player, block));
      if (configuredMaterial == null || configuredMaterial == Material.AIR || !this.isPricedShopItem(configuredMaterial)) {
         return null;
      }

      int price = this.materialPrice(configuredMaterial);
      return price <= 0 ? null : new Mifron.ShelfShopOffer(configuredMaterial, 1, price);
   }''')

s = replace_method(s, '   private List<Material> shelfShopRandomOffers(Block block)', r'''   private List<Material> shelfShopRandomOffers(Block block) {
      if (block == null || !this.isShelfShop(block)) {
         return List.of();
      }

      if ("custom".equals(this.shelfShopMode(block))) {
         return this.customShelfShopMaterials(block);
      }

      int order = this.data.getInt(this.shelfShopPath(block) + ".order", 0);
      if (order <= 0) {
         return List.of();
      }

      List<Material> catalog = this.shelfShopCatalogMaterials();
      int start = (order - 1) * SHELF_SHOP_OFFER_SLOTS;
      return start >= catalog.size() ? List.of() : catalog.subList(start, Math.min(catalog.size(), start + SHELF_SHOP_OFFER_SLOTS));
   }''')

# Add helpers before catalog builder.
anchor = '   private List<Material> shelfShopCatalogMaterials() {'
if 'private String shelfShopMode(Block block)' not in s:
    helper = r'''   private String shelfShopMode(Block block) {
      if (block == null) {
         return "sequential";
      }
      String mode = this.data.getString(this.shelfShopPath(block) + ".mode", "sequential");
      return "custom".equalsIgnoreCase(mode) ? "custom" : "sequential";
   }

   private void configureSequentialShelfShop(Block block) {
      if (block == null) {
         return;
      }
      this.slotMachineManager.unregisterMachine(block);
      String path = this.shelfShopPath(block);
      int order = "sequential".equals(this.shelfShopMode(block)) ? this.data.getInt(path + ".order", 0) : 0;
      if (order <= 0) {
         order = this.nextSequentialShelfOrder();
      }
      this.data.set(path + ".enabled", true);
      this.data.set(path + ".mode", "sequential");
      this.data.set(path + ".order", order);
      this.data.set(path + ".custom", null);
      this.clearShelfShopRandomOffer(block);
      this.displayShelfShopOffers(block, this.shelfShopRandomOffers(block));
      this.saveData();
   }

   private int nextSequentialShelfOrder() {
      int nextOrder = 1;
      ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
      if (shops != null) {
         for (String worldId : shops.getKeys(false)) {
            ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
            if (worldShops == null) {
               continue;
            }
            for (String coordinates : worldShops.getKeys(false)) {
               String base = coordinates;
               String mode = worldShops.getString(base + ".mode", "sequential");
               if (!"custom".equalsIgnoreCase(mode) && worldShops.getBoolean(base + ".enabled", worldShops.getBoolean(base, false))) {
                  nextOrder = Math.max(nextOrder, worldShops.getInt(base + ".order", 0) + 1);
               }
            }
         }
      }
      return nextOrder;
   }

   private List<Material> customShelfShopMaterials(Block block) {
      List<Material> materials = new ArrayList<>();
      String base = this.shelfShopPath(block) + ".custom";
      for (int slot = 0; slot < SHELF_SHOP_OFFER_SLOTS; slot++) {
         String raw = this.data.getString(base + "." + slot, "");
         Material material = raw.isBlank() ? Material.AIR : Material.matchMaterial(raw);
         materials.add(material == null ? Material.AIR : material);
      }
      return materials;
   }

   private void assignCustomShelfShopSlot(Block block, int selectedSlot, Material material) {
      if (block == null || material == null || selectedSlot < 0 || selectedSlot >= SHELF_SHOP_OFFER_SLOTS) {
         return;
      }
      this.slotMachineManager.unregisterMachine(block);
      String path = this.shelfShopPath(block);
      if (!"custom".equals(this.shelfShopMode(block))) {
         this.data.set(path + ".custom", null);
      }
      this.data.set(path + ".enabled", true);
      this.data.set(path + ".mode", "custom");
      this.data.set(path + ".order", null);
      this.data.set(path + ".custom." + selectedSlot, material.name());
      this.clearShelfShopRandomOffer(block);
      this.displayShelfShopOffers(block, this.customShelfShopMaterials(block));
      this.saveData();
   }

   private void clearCustomShelfShopSlot(Block block, int selectedSlot) {
      if (block == null || selectedSlot < 0 || selectedSlot >= SHELF_SHOP_OFFER_SLOTS) {
         return;
      }
      this.data.set(this.shelfShopPath(block) + ".custom." + selectedSlot, null);
      this.displayShelfShopOffers(block, this.customShelfShopMaterials(block));
      this.saveData();
   }

'''
    s = replace_once(s, anchor, helper + anchor, 'dual shelf helpers')

s = replace_method(s, '   private boolean displayShelfShopOffers(Block block, List<Material> materials)', r'''   private boolean displayShelfShopOffers(Block block, List<Material> materials) {
      if (block != null && materials != null && block.getState() instanceof Shelf shelfState) {
         Inventory inventory = shelfState.getInventory();
         inventory.clear();
         int slots = Math.min(SHELF_SHOP_OFFER_SLOTS, Math.min(inventory.getSize(), materials.size()));

         for (int slot = 0; slot < slots; slot++) {
            Material material = materials.get(slot);
            if (material != null && material != Material.AIR) {
               // This is a product sample only. Global stock is stored separately and never represented by stack size.
               inventory.setItem(slot, new ItemStack(material, 1));
            }
         }

         return true;
      } else {
         return false;
      }
   }''')

s = replace_method(s, '   boolean setShelfShop(Block block, boolean enabled)', r'''   boolean setShelfShop(Block block, boolean enabled) {
      String path = this.shelfShopPath(block);
      boolean existed = this.isShelfShop(block);
      if (enabled) {
         this.configureSequentialShelfShop(block);
         return existed;
      }

      this.data.set(path, null);
      this.clearShelfShopDisplay(block);
      this.clearShopOwner(block);
      this.clearShelfShopRandomOffer(block);
      this.saveData();
      return existed;
   }''')

# Keep sync working for both modes and remove duplicate condition.
s = s.replace('if (block != null && this.isShelfShop(block) && this.isShelfShop(block)) {', 'if (block != null && this.isShelfShop(block)) {', 1)

p.write_text(s, encoding='utf-8')

# Update wand lore so both placement modes are discoverable.
p = root / 'src/main/java/org/server/mifron/UtilityItemsFeature.java'
s = p.read_text(encoding='utf-8')
old = 'List.of(ChatColor.GRAY + "右クリック: 棚・樽をショップ化", ChatColor.GRAY + "左クリック: ショップ化を解除", ChatColor.DARK_GRAY + "樽ショップの商品はショップ化時に生成されます。")'
new = '''List.of(
            ChatColor.GRAY + "棚を右クリック: 番号順の順番配置",
            ChatColor.GRAY + "棚をShift+右クリック: オフハンドの商品を指定枠へ配置",
            ChatColor.GRAY + "指定配置でShift+左クリック: 選択枠を空欄化",
            ChatColor.GRAY + "左クリック: ショップ化を解除",
            ChatColor.DARK_GRAY + "棚商品の在庫は全棚で共有されます。"
         )'''
if old in s:
    s = s.replace(old, new, 1)

# Also update typed SHELF wand generic lore when present.
old2 = 'List.of(ChatColor.GRAY + "種類: " + type.key(), ChatColor.GRAY + "右クリック: 対応ブロックをショップ化", ChatColor.GRAY + "左クリック: ショップ化を解除")'
new2 = '''type == ShopWandType.SHELF
               ? List.of(
                  ChatColor.GRAY + "右クリック: 番号順の順番配置",
                  ChatColor.GRAY + "Shift+右クリック: オフハンドの商品を指定枠へ配置",
                  ChatColor.GRAY + "Shift+左クリック: 指定枠を空欄化",
                  ChatColor.GRAY + "左クリック: ショップ化を解除"
               )
               : List.of(ChatColor.GRAY + "種類: " + type.key(), ChatColor.GRAY + "右クリック: 対応ブロックをショップ化", ChatColor.GRAY + "左クリック: ショップ化を解除")'''
if old2 in s:
    s = s.replace(old2, new2, 1)
p.write_text(s, encoding='utf-8')
