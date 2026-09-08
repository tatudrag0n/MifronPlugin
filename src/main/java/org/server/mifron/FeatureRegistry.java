package org.server.mifron;

import org.bukkit.plugin.java.JavaPlugin;

public class FeatureRegistry {

    public static void registerAll(JavaPlugin plugin) {
        plugin.getLogger().info("[Mifron] Registering V2 Overhaul Features...");

        // 1. 商人買取専用UI
        new MerchantBuybackFeature(plugin).register();

        // 2. 特殊アイテム7種
        new SpecialItemsFeature(plugin).register();

        // 3. エリートモブ
        new EliteMobFeature(plugin).register();

        // 4. 個人チャット禁止
        new PrivateChatBlocker(plugin).register();

        // 5. Survivalリスポーン固定 (0, 101, 0)
        new SurvivalSpawnFeature(plugin).register();

        // 6. Survivalワールド以外でのモブスポーン禁止
        new MobSpawnRestrictionFeature(plugin).register();

        // 7. BAN時ロールバック連携
        new BanRollbackFeature(plugin).register();

        // 8. ONLINE SHOP & クールダウン
        OnlineShopFeature onlineShop = new OnlineShopFeature(plugin);
        onlineShop.register();

        // 9. 棚ショップ
        new ShelfShopFeature(plugin).register();

        // 10. 樽ショップ
        new BarrelShopFeature(plugin).register();

        // 11. 再生成システム除外制化
        new RegenerationExclusionFeature(plugin);

        // 12. Tebex Storeアイテム
        TebexStoreItem tebexItem = new TebexStoreItem(plugin);
        tebexItem.register();

        // 13. 初期アイテム自動配布
        new StartItemFeature(plugin, tebexItem).register();

        // 14. WorldEdit制限 (Creativeワールド化)
        new WorldEditHook(plugin).register();

        plugin.getLogger().info("[Mifron] All V2 Overhaul Features registered successfully!");
    }
}
