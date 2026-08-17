from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing patch target: {label}")
    return text.replace(old, new, 1)


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


def replace_in_method(text: str, signature: str, old: str, new: str, label: str) -> str:
    start, end = method_span(text, signature)
    body = text[start:end]
    if old not in body:
        raise SystemExit(f"missing method patch target: {label}")
    body = body.replace(old, new, 1)
    return text[:start] + body + text[end:]

root = Path('.')

# Maven: compile against Geyser API. It is provided by Geyser-Spigot at runtime.
p = root / 'pom.xml'
s = p.read_text(encoding='utf-8')
if 'repo.opencollab.dev/main' not in s:
    s = replace_once(
        s,
        '''        <repository>\n            <id>papermc-repo</id>\n            <url>https://repo.papermc.io/repository/maven-public/</url>\n        </repository>''',
        '''        <repository>\n            <id>papermc-repo</id>\n            <url>https://repo.papermc.io/repository/maven-public/</url>\n        </repository>\n        <repository>\n            <id>opencollab-main</id>\n            <url>https://repo.opencollab.dev/main/</url>\n        </repository>''',
        'OpenCollab repository',
    )
if '<artifactId>api</artifactId>\n            <version>2.10.0-SNAPSHOT</version>' not in s:
    s = replace_once(
        s,
        '''        <dependency>\n            <groupId>io.papermc.paper</groupId>\n            <artifactId>paper-api</artifactId>\n            <version>26.1.2.build.12-alpha</version>\n            <scope>provided</scope>\n        </dependency>''',
        '''        <dependency>\n            <groupId>io.papermc.paper</groupId>\n            <artifactId>paper-api</artifactId>\n            <version>26.1.2.build.12-alpha</version>\n            <scope>provided</scope>\n        </dependency>\n        <dependency>\n            <groupId>org.geysermc.geyser</groupId>\n            <artifactId>api</artifactId>\n            <version>2.10.0-SNAPSHOT</version>\n            <scope>provided</scope>\n        </dependency>''',
        'Geyser API dependency',
    )
p.write_text(s, encoding='utf-8')

# plugin.yml: Geyser remains optional; Java players still use the existing inventory UI.
p = root / 'src/main/resources/plugin.yml'
s = p.read_text(encoding='utf-8')
if '  - Geyser-Spigot' not in s:
    s = replace_once(s, 'softdepend:\n  - WorldEdit', 'softdepend:\n  - WorldEdit\n  - Geyser-Spigot', 'Geyser softdepend')
p.write_text(s, encoding='utf-8')

# New mobile UI bridge. It converts the plugin's existing inventory-menu models to native Bedrock Forms,
# so the same action items and server logic are reused rather than duplicated.
p = root / 'src/main/java/org/server/mifron/BedrockUiFeature.java'
p.write_text(r'''package org.server.mifron;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.geyser.api.GeyserApi;

final class BedrockUiFeature {
   private static final int MAX_CONTENT_ITEMS = 36;
   private final Mifron plugin;

   BedrockUiFeature(Mifron plugin) {
      this.plugin = plugin;
   }

   boolean isBedrock(Player player) {
      if (player == null) {
         return false;
      }

      try {
         GeyserApi api = GeyserApi.api();
         return api != null && api.connectionByUuid(player.getUniqueId()) != null;
      } catch (Throwable ignored) {
         return false;
      }
   }

   boolean showMenu(
      Player player,
      String title,
      Inventory inventory,
      Predicate<ItemStack> actionable,
      BiConsumer<Integer, ItemStack> clickHandler
   ) {
      return this.showMenu(player, title, inventory, actionable, clickHandler, null);
   }

   boolean showMenu(
      Player player,
      String title,
      Inventory inventory,
      Predicate<ItemStack> actionable,
      BiConsumer<Integer, ItemStack> clickHandler,
      Runnable closeHandler
   ) {
      if (!this.isBedrock(player) || inventory == null) {
         return false;
      }

      try {
         List<Integer> buttonSlots = new ArrayList<>();
         StringBuilder content = new StringBuilder();
         int contentItems = 0;
         SimpleForm.Builder form = SimpleForm.builder().title(clean(title));

         for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isEmptyOrFiller(item)) {
               continue;
            }

            if (actionable != null && actionable.test(item)) {
               String buttonText = buttonText(item);
               if (!buttonText.isBlank()) {
                  form.button(buttonText);
                  buttonSlots.add(slot);
               }
            } else if (contentItems < MAX_CONTENT_ITEMS) {
               String line = contentText(item);
               if (!line.isBlank()) {
                  if (!content.isEmpty()) {
                     content.append("\n\n");
                  }
                  content.append(line);
                  contentItems++;
               }
            }
         }

         if (content.isEmpty()) {
            content.append("項目を選択してください。");
         }
         form.content(content.toString());

         form.validResultHandler((SimpleFormResponse response) -> {
            int id = response.clickedButtonId();
            if (id < 0 || id >= buttonSlots.size()) {
               return;
            }
            int slot = buttonSlots.get(id);
            ItemStack selected = inventory.getItem(slot);
            if (selected == null) {
               return;
            }
            ItemStack safeCopy = selected.clone();
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> clickHandler.accept(slot, safeCopy));
         });

         if (closeHandler != null) {
            form.closedOrInvalidResultHandler(() -> this.plugin.getServer().getScheduler().runTask(this.plugin, closeHandler));
         }

         GeyserApi api = GeyserApi.api();
         return api != null && api.sendForm(player.getUniqueId(), form.build());
      } catch (Throwable error) {
         this.plugin.getLogger().warning("Failed to open Bedrock form for " + player.getName() + ": " + error.getMessage());
         return false;
      }
   }

   private static boolean isEmptyOrFiller(ItemStack item) {
      if (item == null || item.getType() == Material.AIR) {
         return true;
      }
      String name = itemName(item);
      return item.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE && name.isBlank();
   }

   private static String buttonText(ItemStack item) {
      StringBuilder text = new StringBuilder(itemName(item));
      List<String> lore = itemLore(item);
      int lines = Math.min(3, lore.size());
      for (int i = 0; i < lines; i++) {
         String line = lore.get(i);
         if (!line.isBlank()) {
            text.append("\n").append(line);
         }
      }
      return text.toString().trim();
   }

   private static String contentText(ItemStack item) {
      StringBuilder text = new StringBuilder(itemName(item));
      for (String line : itemLore(item)) {
         if (!line.isBlank()) {
            text.append("\n").append(line);
         }
      }
      return text.toString().trim();
   }

   private static String itemName(ItemStack item) {
      if (item == null) {
         return "";
      }
      ItemMeta meta = item.getItemMeta();
      if (meta != null && meta.displayName() != null) {
         return clean(PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
      }
      return clean(item.getType().name().toLowerCase().replace('_', ' '));
   }

   private static List<String> itemLore(ItemStack item) {
      ItemMeta meta = item == null ? null : item.getItemMeta();
      if (meta == null || meta.lore() == null) {
         return List.of();
      }
      return meta.lore().stream().map(PlainTextComponentSerializer.plainText()::serialize).map(BedrockUiFeature::clean).toList();
   }

   private static String clean(String text) {
      if (text == null) {
         return "";
      }
      String stripped = ChatColor.stripColor(text);
      return stripped == null ? text.replace("§", "") : stripped;
   }
}
''', encoding='utf-8')

# Mifron integration.
p = root / 'src/main/java/org/server/mifron/Mifron.java'
s = p.read_text(encoding='utf-8')

# Field (nullable when Geyser is not installed).
if 'private BedrockUiFeature bedrockUiFeature;' not in s:
    s = replace_once(
        s,
        '   private final UtilityItemsFeature utilityItemsFeature = new UtilityItemsFeature(this);',
        '   private final UtilityItemsFeature utilityItemsFeature = new UtilityItemsFeature(this);\n   private BedrockUiFeature bedrockUiFeature;',
        'Bedrock UI field',
    )

# Initialize after listeners/features are available, only when Geyser-Spigot exists.
if 'this.bedrockUiFeature = new BedrockUiFeature(this);' not in s:
    anchor = '      this.runStartupStep("register utility item events", () -> Bukkit.getPluginManager().registerEvents(this.utilityItemsFeature, this));'
    s = replace_once(
        s,
        anchor,
        anchor + '\n      if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {\n         this.bedrockUiFeature = new BedrockUiFeature(this);\n         this.getLogger().info("Bedrock mobile Forms UI enabled through Geyser.");\n      }',
        'Bedrock UI initialization',
    )

# Friend/status menus: build exactly the same inventory model, but render it as a Bedrock native Form when applicable.
s = replace_in_method(
    s,
    '   private void openFriendUi(Player player)',
    '      player.openInventory(inventory);',
    '''      if (this.bedrockUiFeature != null
         && this.bedrockUiFeature.showMenu(player, "Mifron Friends / Status", inventory, item -> this.getUiAction(item) != null, (slot, item) -> this.handleFriendUiClick(player, item))) {
         return;
      }
      player.openInventory(inventory);''',
    'friend mobile form',
)

s = replace_in_method(
    s,
    '   private void openStatusUi(Player player, String tab)',
    '      player.openInventory(inventory);',
    '''      if (this.bedrockUiFeature != null
         && this.bedrockUiFeature.showMenu(player, "Mifron Status", inventory, item -> this.getUiAction(item) != null, (slot, item) -> this.handleStatusUiClick(player, item))) {
         return;
      }
      player.openInventory(inventory);''',
    'status mobile form',
)

# Teleporter destination picker: same destinations/actions, native Bedrock buttons.
s = replace_in_method(
    s,
    '   void openServerPortalTargetUi(Player player, String portalKey)',
    '      player.openInventory(inventory);',
    '''      if (this.bedrockUiFeature != null
         && this.bedrockUiFeature.showMenu(player, "Mifron Teleporter", inventory, item -> this.getUiAction(item) != null, (slot, item) -> this.handleTeleporterUiItem(player, item))) {
         return;
      }
      player.openInventory(inventory);''',
    'teleporter mobile form',
)

# Extract teleporter inventory click behavior into a reusable method so Bedrock Forms and Java inventory UI share the same logic.
if 'private void handleTeleporterUiItem(Player player, ItemStack clicked)' not in s:
    insert_before = '   @EventHandler\n   public void onInventoryClick(InventoryClickEvent event) {'
    helper = '''   private void handleTeleporterUiItem(Player player, ItemStack clicked) {
      String action = this.getUiAction(clicked);
      String target = this.getUiTargetString(clicked);
      if ("teleport".equals(action) && target != null) {
         this.playUiClickSound(player);
         this.teleportToConfigLocation(player, target);
         player.closeInventory();
      } else if ("server_portal_bind".equals(action) && target != null) {
         String[] parts = target.split("\\\\|", 2);
         if (parts.length == 2) {
            this.serverPortalFeature.setServerPortalTarget(parts[0], parts[1]);
            this.playUiClickSound(player);
            player.sendMessage("§aサーバーポータルの移動先を設定しました。");
            player.closeInventory();
         }
      }
   }

'''
    s = replace_once(s, insert_before, helper + insert_before, 'teleporter shared click helper')

old_teleport_dispatch = '''               String action = this.getUiAction(event.getCurrentItem());
               String target = this.getUiTargetString(event.getCurrentItem());
               if ("teleport".equals(action) && target != null) {
                  this.playUiClickSound(player);
                  this.teleportToConfigLocation(player, target);
                  player.closeInventory();
               } else if ("server_portal_bind".equals(action) && target != null) {
                  String[] parts = target.split("\\\\|", 2);
                  if (parts.length == 2) {
                     this.serverPortalFeature.setServerPortalTarget(parts[0], parts[1]);
                     this.playUiClickSound(player);
                     player.sendMessage("§aサーバーポータルの移動先を設定しました。");
                     player.closeInventory();
                  }
               }'''
if old_teleport_dispatch in s:
    s = s.replace(old_teleport_dispatch, '               this.handleTeleporterUiItem(player, event.getCurrentItem());', 1)

# Barrel shop: for Bedrock, show large native buttons instead of opening the chest inventory.
old_barrel = '''            } else if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.isBarrelShop(event.getClickedBlock())) {
               if (event.getClickedBlock().getState() instanceof Barrel barrel) {
                  player.openInventory(barrel.getInventory());
               }

               event.setCancelled(true);'''
new_barrel = '''            } else if (event.getAction().isRightClick() && event.getClickedBlock() != null && this.isBarrelShop(event.getClickedBlock())) {
               if (event.getClickedBlock().getState() instanceof Barrel barrel) {
                  Inventory barrelInventory = barrel.getInventory();
                  if (this.bedrockUiFeature == null
                     || !this.bedrockUiFeature.showMenu(
                        player,
                        "Mifron Barrel Shop",
                        barrelInventory,
                        itemx -> itemx != null && itemx.getType() != Material.AIR,
                        (slot, itemx) -> {
                           this.buyBarrelOffer(player, itemx, slot, barrelInventory);
                           if (player.isOnline() && this.bedrockUiFeature != null) {
                              this.bedrockUiFeature.showMenu(
                                 player,
                                 "Mifron Barrel Shop",
                                 barrelInventory,
                                 next -> next != null && next.getType() != Material.AIR,
                                 (nextSlot, nextItem) -> this.buyBarrelOffer(player, nextItem, nextSlot, barrelInventory)
                              );
                           }
                        }
                     )) {
                     player.openInventory(barrelInventory);
                  }
               }

               event.setCancelled(true);'''
s = replace_once(s, old_barrel, new_barrel, 'barrel shop mobile form')

# Merchant: render the generated merchant inventory as a native form for Bedrock. Java keeps chest GUI.
s = replace_in_method(
    s,
    '   private void openMerchantUi(Player player, AbstractVillager villager)',
    '      player.openInventory(inventory);',
    '''      if (this.bedrockUiFeature != null
         && this.bedrockUiFeature.showMenu(
            player,
            "Mifron Merchant",
            inventory,
            this::isMerchantOffer,
            (slot, item) -> {
               if (this.isMerchantOffer(item)) {
                  this.buyMerchantOffer(player, item, false);
               }
            },
            () -> {
               UUID merchantId = this.activeMerchantViews.remove(player.getUniqueId());
               if (merchantId != null && !this.activeMerchantViews.containsValue(merchantId)) {
                  Entity entity = this.findEntity(merchantId);
                  if (entity instanceof AbstractVillager merchant && this.isMifronMerchant(entity)) {
                     merchant.setAI(true);
                     merchant.setInvulnerable(false);
                  }
               }
            }
         )) {
         return;
      }
      player.openInventory(inventory);''',
    'merchant mobile form',
)

p.write_text(s, encoding='utf-8')
print('Applied Bedrock/mobile native Forms UI integration.')
