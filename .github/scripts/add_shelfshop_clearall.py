from pathlib import Path

p = Path('src/main/java/org/server/mifron/Mifron.java')
s = p.read_text(encoding='utf-8')


def method_span(text, signature):
    start = text.find(signature)
    if start < 0:
        raise SystemExit(f'missing method: {signature}')
    brace = text.find('{', start)
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise SystemExit(f'unterminated method: {signature}')


def replace_method(text, signature, replacement):
    a, b = method_span(text, signature)
    return text[:a] + replacement + text[b:]


command = r'''   private void handleShelfShopCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("mifron.shop.admin") && !sender.hasPermission("mifron.admin")) {
         sender.sendMessage("§c権限がありません。");
      } else if (args.length >= 2 && ("clearall".equalsIgnoreCase(args[1]) || "removeall".equalsIgnoreCase(args[1]) || "disableall".equalsIgnoreCase(args[1]))) {
         ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
         List<Block> registeredShelves = new ArrayList<>();
         int registered = 0;

         if (shops != null) {
            for (String worldId : new ArrayList<>(shops.getKeys(false))) {
               ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
               if (worldShops == null) {
                  continue;
               }

               World world = null;
               try {
                  world = Bukkit.getWorld(UUID.fromString(worldId));
               } catch (IllegalArgumentException ignored) {
               }

               for (String coordinates : new ArrayList<>(worldShops.getKeys(false))) {
                  boolean enabled = worldShops.getBoolean(coordinates, false) || worldShops.getBoolean(coordinates + ".enabled", false);
                  if (!enabled) {
                     continue;
                  }

                  registered++;
                  if (world == null) {
                     continue;
                  }

                  String[] parts = coordinates.split("_", 3);
                  if (parts.length != 3) {
                     continue;
                  }

                  try {
                     int x = Integer.parseInt(parts[0]);
                     int y = Integer.parseInt(parts[1]);
                     int z = Integer.parseInt(parts[2]);
                     registeredShelves.add(world.getBlockAt(x, y, z));
                  } catch (NumberFormatException ignored) {
                  }
               }
            }
         }

         for (Block shelf : registeredShelves) {
            this.setShelfShop(shelf, false);
         }

         // Remove stale entries that could not be resolved to a currently loaded world.
         this.data.set("shelf-shops", null);
         this.data.set("shelf-shop-offers", null);
         this.saveData();
         sender.sendMessage("§a棚ショップを全解除しました: " + registered + "棚");
         sender.sendMessage("§7棚ブロック自体とスロットマシン登録、共有在庫は維持されます。");
         if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.0F);
         }
      } else if (args.length >= 2 && ("reorder".equalsIgnoreCase(args[1]) || "renumber".equalsIgnoreCase(args[1]) || "resetorder".equalsIgnoreCase(args[1]))) {
         ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
         int before = 0;
         if (shops != null) {
            for (String worldId : shops.getKeys(false)) {
               ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
               if (worldShops == null) continue;
               for (String coordinates : worldShops.getKeys(false)) {
                  boolean enabled = worldShops.getBoolean(coordinates, false) || worldShops.getBoolean(coordinates + ".enabled", false);
                  String mode = worldShops.getString(coordinates + ".mode", "sequential");
                  boolean slotMachine = !this.data.getString("slot-machines." + worldId + "." + coordinates + ".difficulty", "").isBlank();
                  if (enabled && !"custom".equalsIgnoreCase(mode) && !slotMachine) before++;
               }
            }
         }
         this.renumberSequentialShelfShops();
         sender.sendMessage("§a順番配置の棚をNo.001から振り直しました: " + before + "棚");
         sender.sendMessage("§7商品カタログもジャンル別の固定順に再構成されています。");
         if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.2F);
         }
      } else if (args.length >= 2 && ("reset".equalsIgnoreCase(args[1]) || "resetstock".equalsIgnoreCase(args[1]))) {
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
      } else {
         sender.sendMessage("§e/mf shelfshop clearall §7- 登録済みの棚ショップをすべて解除する");
         sender.sendMessage("§e/mf shelfshop reorder §7- 順番配置の棚番号を1から振り直す");
         sender.sendMessage("§e/mf shelfshop resetstock §7- 共有在庫を0にする");
      }
   }'''

s = replace_method(s, '   private void handleShelfShopCommand(CommandSender sender, String[] args)', command)
p.write_text(s, encoding='utf-8')
print('added shelfshop clearall command')
