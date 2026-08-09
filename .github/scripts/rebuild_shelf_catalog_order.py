from pathlib import Path

p = Path('src/main/java/org/server/minerva/Minerva.java')
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

catalog = r'''   private List<Material> shelfShopCatalogMaterials() {
      List<Material> materials = new ArrayList<>();
      for (Material material : Material.values()) {
         if (this.isRandomShopItem(material)) {
            materials.add(material);
         }
      }

      // Keep catalog numbers stable and human-readable: category first, then related
      // material families, then the vanilla material id as a deterministic tie-breaker.
      materials.sort((a, b) -> {
         int category = Integer.compare(this.shelfShopCategoryRank(a), this.shelfShopCategoryRank(b));
         if (category != 0) {
            return category;
         }
         int family = this.shelfShopFamilyKey(a).compareTo(this.shelfShopFamilyKey(b));
         return family != 0 ? family : a.name().compareTo(b.name());
      });
      return materials;
   }'''
s = replace_method(s, '   private List<Material> shelfShopCatalogMaterials()', catalog)

# Insert category helpers immediately before shelfShopCatalogNumber.
anchor = '   private int shelfShopCatalogNumber(Material material) {'
if 'private int shelfShopCategoryRank(Material material)' not in s:
    helpers = r'''   private int shelfShopCategoryRank(Material material) {
      String name = material.name();

      // 0: 基本ブロック・地形
      if (name.contains("STONE") || name.contains("DEEPSLATE") || name.contains("TUFF") || name.contains("CALCITE")
         || name.contains("DRIPSTONE") || name.contains("DIRT") || name.contains("GRASS_BLOCK") || name.contains("PODZOL")
         || name.contains("MYCELIUM") || name.contains("SAND") || name.contains("GRAVEL") || name.contains("CLAY")
         || name.contains("MUD") || name.contains("SNOW") || name.contains("ICE") || name.contains("BLACKSTONE")
         || name.contains("BASALT") || name.equals("NETHERRACK") || name.equals("END_STONE")) {
         return 0;
      }

      // 1: 木材・植物
      if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")
         || name.endsWith("_PLANKS") || name.endsWith("_LEAVES") || name.endsWith("_SAPLING") || name.contains("BAMBOO")
         || name.contains("MANGROVE") || name.contains("CHERRY") || name.contains("FLOWER") || name.contains("TULIP")
         || name.contains("ORCHID") || name.contains("DANDELION") || name.contains("POPPY") || name.contains("AZALEA")
         || name.contains("MUSHROOM") || name.contains("FUNGUS") || name.contains("ROOTS") || name.contains("VINE")
         || name.contains("MOSS") || name.contains("LILY") || name.contains("CACTUS") || name.contains("SUGAR_CANE")) {
         return 1;
      }

      // 2: 建材・装飾ブロック
      if (name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_WALL") || name.endsWith("_FENCE")
         || name.endsWith("_FENCE_GATE") || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_SIGN")
         || name.endsWith("_HANGING_SIGN") || name.contains("GLASS") || name.contains("CONCRETE") || name.contains("TERRACOTTA")
         || name.contains("WOOL") || name.contains("CARPET") || name.contains("BRICK") || name.contains("PRISMARINE")
         || name.contains("PURPUR") || name.contains("CORAL") || name.contains("BANNER") || name.contains("CANDLE")) {
         return 2;
      }

      // 3: 鉱石・素材
      if (name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT") || name.endsWith("_NUGGET")
         || name.equals("COAL") || name.equals("CHARCOAL") || name.contains("IRON") || name.contains("GOLD")
         || name.contains("COPPER") || name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("REDSTONE")
         || name.contains("LAPIS") || name.contains("QUARTZ") || name.contains("AMETHYST") || name.contains("NETHERITE")
         || name.equals("ANCIENT_DEBRIS")) {
         return 3;
      }

      // 4: レッドストーン・機構
      if (name.contains("PISTON") || name.contains("OBSERVER") || name.contains("COMPARATOR") || name.contains("REPEATER")
         || name.contains("HOPPER") || name.contains("DISPENSER") || name.contains("DROPPER") || name.contains("CRAFTER")
         || name.contains("RAIL") || name.contains("LEVER") || name.contains("BUTTON") || name.contains("PRESSURE_PLATE")
         || name.contains("TRIPWIRE") || name.contains("DAYLIGHT_DETECTOR") || name.contains("TARGET") || name.contains("NOTE_BLOCK")) {
         return 4;
      }

      // 5: 食料・農業
      if (material.isEdible() || name.contains("SEEDS") || name.equals("WHEAT") || name.equals("CARROT") || name.equals("POTATO")
         || name.equals("BEETROOT") || name.contains("MELON") || name.contains("PUMPKIN") || name.contains("COCOA")
         || name.contains("SWEET_BERRIES") || name.contains("GLOW_BERRIES") || name.contains("HONEY")) {
         return 5;
      }

      // 6: Mobドロップ・醸造素材
      if (name.contains("ROTTEN_FLESH") || name.equals("BONE") || name.equals("STRING") || name.contains("SPIDER_EYE")
         || name.equals("GUNPOWDER") || name.contains("BLAZE") || name.contains("GHAST") || name.contains("ENDER_PEARL")
         || name.contains("MAGMA_CREAM") || name.contains("SLIME_BALL") || name.contains("PHANTOM_MEMBRANE")
         || name.contains("SHULKER_SHELL") || name.contains("POTION") || name.contains("FERMENTED") || name.contains("RABBIT_FOOT")
         || name.contains("DRAGON_BREATH") || name.contains("GLISTERING_MELON") || name.contains("NETHER_WART")) {
         return 6;
      }

      // 7: 道具・武器
      if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
         || name.endsWith("_HOE") || name.endsWith("_SPEAR") || name.equals("BOW") || name.equals("CROSSBOW")
         || name.equals("TRIDENT") || name.equals("MACE") || name.equals("SHIELD") || name.equals("FISHING_ROD")
         || name.equals("SHEARS") || name.equals("FLINT_AND_STEEL") || name.equals("BRUSH")) {
         return 7;
      }

      // 8: 防具・装備
      if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
         || name.equals("ELYTRA") || name.contains("HORSE_ARMOR") || name.equals("TURTLE_HELMET")) {
         return 8;
      }

      // 9: 収納・移動・生活用品
      if (name.contains("CHEST") || name.contains("BARREL") || name.contains("SHULKER_BOX") || name.contains("BUNDLE")
         || name.contains("MINECART") || name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.equals("SADDLE")
         || name.equals("LEAD") || name.equals("COMPASS") || name.equals("RECOVERY_COMPASS") || name.equals("CLOCK")
         || name.equals("NAME_TAG") || name.equals("LANTERN") || name.equals("SOUL_LANTERN") || name.equals("TORCH")) {
         return 9;
      }

      // 10: レア・特殊
      if (name.contains("SMITHING_TEMPLATE") || name.contains("MUSIC_DISC") || name.contains("POTTERY_SHERD")
         || name.contains("HEAD") || name.contains("SKULL") || name.equals("TOTEM_OF_UNDYING") || name.equals("NETHER_STAR")
         || name.equals("HEART_OF_THE_SEA") || name.equals("CONDUIT") || name.equals("BEACON") || name.equals("DRAGON_EGG")
         || name.equals("ENCHANTED_GOLDEN_APPLE")) {
         return 10;
      }

      return 11;
   }

   private String shelfShopFamilyKey(Material material) {
      String name = material.name();
      // Wood species stay together (OAK_*, SPRUCE_*, ...), while coloured building
      // blocks stay together by block family (WOOL, CONCRETE, TERRACOTTA, etc.).
      for (String family : List.of("WOOL", "CARPET", "CONCRETE_POWDER", "CONCRETE", "TERRACOTTA", "GLASS_PANE", "GLASS", "BANNER", "BED", "CANDLE")) {
         if (name.endsWith("_" + family) || name.equals(family)) {
            return family + ":" + name;
         }
      }
      for (String wood : List.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED")) {
         if (name.startsWith(wood + "_")) {
            return "WOOD:" + String.format("%02d", List.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED").indexOf(wood)) + ":" + name;
         }
      }
      return name;
   }

   private void renumberSequentialShelfShops() {
      ConfigurationSection shops = this.data.getConfigurationSection("shelf-shops");
      if (shops == null) {
         return;
      }

      List<String> paths = new ArrayList<>();
      for (String worldId : shops.getKeys(false)) {
         ConfigurationSection worldShops = shops.getConfigurationSection(worldId);
         if (worldShops == null) {
            continue;
         }
         for (String coordinates : worldShops.getKeys(false)) {
            String relative = worldId + "." + coordinates;
            boolean enabled = worldShops.getBoolean(coordinates, false) || worldShops.getBoolean(coordinates + ".enabled", false);
            String mode = worldShops.getString(coordinates + ".mode", "sequential");
            boolean slotMachine = !this.data.getString("slot-machines." + worldId + "." + coordinates + ".difficulty", "").isBlank();
            if (enabled && !"custom".equalsIgnoreCase(mode) && !slotMachine) {
               paths.add(relative);
            }
         }
      }

      paths.sort((a, b) -> {
         int ao = this.data.getInt("shelf-shops." + a + ".order", Integer.MAX_VALUE);
         int bo = this.data.getInt("shelf-shops." + b + ".order", Integer.MAX_VALUE);
         int order = Integer.compare(ao, bo);
         return order != 0 ? order : a.compareTo(b);
      });

      for (int i = 0; i < paths.size(); i++) {
         this.data.set("shelf-shops." + paths.get(i) + ".order", i + 1);
      }
      this.saveData();
      this.syncShelfShopDisplays();
   }

'''
    s = s.replace(anchor, helpers + anchor, 1)

command = r'''   private void handleShelfShopCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("minerva.shop.admin") && !sender.hasPermission("minerva.admin")) {
         sender.sendMessage("§c権限がありません。");
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
         sender.sendMessage("§e/mva shelfshop reorder §7- 順番配置の棚番号を1から振り直す");
         sender.sendMessage("§e/mva shelfshop resetstock §7- 共有在庫を0にする");
      }
   }'''
s = replace_method(s, '   private void handleShelfShopCommand(CommandSender sender, String[] args)', command)

p.write_text(s, encoding='utf-8')
print('rebuilt shelf catalog ordering and added renumber command')
