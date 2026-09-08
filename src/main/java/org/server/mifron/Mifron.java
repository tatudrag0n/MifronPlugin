// Mifron.java の一部を修正
// 既存の createOnlineShopProduct メソッドの近くに以下を追加

public ItemStack createOnlineShopProduct(String id) {
    // 既存の OnlineShop 商品処理
    if (id.equals("sell-shelf")) {
        return createShopBlockItem("SELL_SHELF", Material.OAK_SHELF, ChatColor.GREEN + "販売棚ショップ");
    } else if (id.equals("buy-shelf")) {
        return createShopBlockItem("BUY_SHELF", Material.SPRUCE_SHELF, ChatColor.GOLD + "買取棚ショップ");
    } else if (id.equals("sell-barrel")) {
        return createShopBlockItem("SELL_BARREL", Material.BARREL, ChatColor.BLUE + "販売樽ショップ");
    } else if (id.equals("buy-barrel")) {
        return createShopBlockItem("BUY_BARREL", Material.HOPPER, ChatColor.RED + "買取樽ショップ");
    }
    // 他の既存商品処理はそのまま...
    return null;
}

private ItemStack createShopBlockItem(String shopType, Material material, String displayName) {
    ItemStack item = new ItemStack(material);
    var meta = item.getItemMeta();
    if (meta != null) {
        meta.setDisplayName(displayName);
        var pdc = meta.getPersistentDataContainer();
        NamespacedKey keyShopType = new NamespacedKey(this, "shop_type");
        pdc.set(keyShopType, PersistentDataType.STRING, shopType);
        item.setItemMeta(meta);
    }
    return item;
}

public boolean canPlaceShopBlock(Player player) {
    // ShopBlockFeature に委譲
    if (shopBlockFeature == null) return true;
    return shopBlockFeature.canPlaceShopBlock(player);
}

public void recordShopBlockPlacement(Player player) {
    if (shopBlockFeature == null) return;
    shopBlockFeature.recordShopBlockPlacement(player);
}
