from pathlib import Path

p = Path('src/main/java/org/server/minerva/Minerva.java')
s = p.read_text(encoding='utf-8')

old = '''   boolean isShelfShop(Block block) {
      if (block == null) {
         return false;
      }

      String path = this.shelfShopPath(block);
      return this.data.getBoolean(path, false) || this.data.getBoolean(path + ".enabled", false);
   }'''
new = '''   boolean isShelfShop(Block block) {
      if (block == null) {
         return false;
      }

      // Slot machines also use shelf blocks, but must never participate in
      // shelf-shop catalog numbering or stock/shop handling.
      if (this.slotMachineManager != null && this.slotMachineManager.isMachine(block)) {
         return false;
      }

      String path = this.shelfShopPath(block);
      return this.data.getBoolean(path, false) || this.data.getBoolean(path + ".enabled", false);
   }'''
if old not in s:
    raise SystemExit('missing isShelfShop target')
s = s.replace(old, new, 1)

old = '''                  for (String coordinates : worldShops.getKeys(false)) {
                     nextOrder = Math.max(nextOrder, worldShops.getInt(coordinates + ".order", 0) + 1);
                  }'''
new = '''                  for (String coordinates : worldShops.getKeys(false)) {
                     World world = this.worldFromId(worldId);
                     Block candidate = world == null ? null : this.blockFromCoordinates(world, coordinates);
                     boolean enabledShop = worldShops.getBoolean(coordinates, false)
                        || worldShops.getBoolean(coordinates + ".enabled", false);
                     if (candidate != null
                        && enabledShop
                        && (this.slotMachineManager == null || !this.slotMachineManager.isMachine(candidate))) {
                        nextOrder = Math.max(nextOrder, worldShops.getInt(coordinates + ".order", 0) + 1);
                     }
                  }'''
if old not in s:
    raise SystemExit('missing nextOrder target')
s = s.replace(old, new, 1)

old = '''            this.setShelfShop(block, false);
            if (!manager.registerMachine(block, difficulty)) {'''
new = '''            // A slot machine occupies the same physical shelf block type as a shelf shop.
            // Remove every shelf-shop trace before registering it so it cannot consume
            // a catalog/order number or be picked up by shelf-shop synchronisation.
            this.setShelfShop(block, false);
            this.data.set(this.shelfShopPath(block), null);
            this.data.set(this.shelfShopOfferPath(block), null);
            this.clearShopOwner(block);
            this.saveData();
            if (!manager.registerMachine(block, difficulty)) {'''
if old not in s:
    raise SystemExit('missing slot conversion target')
s = s.replace(old, new, 1)

p.write_text(s, encoding='utf-8')
