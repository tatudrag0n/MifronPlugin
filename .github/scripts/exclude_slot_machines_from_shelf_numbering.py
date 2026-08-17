from pathlib import Path

p = Path('src/main/java/org/server/mifron/Mifron.java')
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

      // Slot machines also use shelf blocks, but they are never shelf shops.
      // This prevents slot shelves from receiving shelf-shop numbering,
      // stock handling, action bars, or display synchronisation.
      if (this.slotMachineManager != null && this.slotMachineManager.isMachine(block)) {
         return false;
      }

      String path = this.shelfShopPath(block);
      return this.data.getBoolean(path, false) || this.data.getBoolean(path + ".enabled", false);
   }'''
if old not in s:
    raise SystemExit('missing isShelfShop target')
s = s.replace(old, new, 1)

old = '''            this.setShelfShop(block, false);
            if (!manager.registerMachine(block, difficulty)) {'''
new = '''            // Slot machines and shelf shops share the same physical shelf block type.
            // Purge every shelf-shop marker before registering the slot machine so the
            // machine cannot be treated as a numbered shop shelf later.
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
