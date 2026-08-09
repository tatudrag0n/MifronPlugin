package org.server.minerva;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
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
   private static final int MAX_STATUS_CONTENT_ITEMS = 18;
   private static final int MAX_STATUS_LORE_LINES = 2;
   private final Minerva plugin;

   BedrockUiFeature(Minerva plugin) {
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

      if (isStatusLikeTitle(title)) {
         return this.showStatusMenu(player, title, inventory, actionable, clickHandler, closeHandler);
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

         bindInventoryResult(form, inventory, buttonSlots, clickHandler);
         bindCloseHandler(form, closeHandler);

         GeyserApi api = GeyserApi.api();
         return api != null && api.sendForm(player.getUniqueId(), form.build());
      } catch (Throwable error) {
         this.plugin.getLogger().warning("Failed to open Bedrock form for " + player.getName() + ": " + error.getMessage());
         return false;
      }
   }

   private boolean showStatusMenu(
      Player player,
      String title,
      Inventory inventory,
      Predicate<ItemStack> actionable,
      BiConsumer<Integer, ItemStack> clickHandler,
      Runnable closeHandler
   ) {
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
               String label = compactButtonText(item);
               if (!label.isBlank()) {
                  form.button(label);
                  buttonSlots.add(slot);
               }
               continue;
            }

            if (contentItems >= MAX_STATUS_CONTENT_ITEMS) {
               continue;
            }

            String line = compactStatusText(item);
            if (line.isBlank()) {
               continue;
            }

            if (!content.isEmpty()) {
               content.append("\n");
            }
            content.append(line);
            contentItems++;
         }

         if (content.isEmpty()) {
            content.append("ステータス情報はありません。");
         }

         form.content(content.toString());
         bindInventoryResult(form, inventory, buttonSlots, clickHandler);
         bindCloseHandler(form, closeHandler);

         GeyserApi api = GeyserApi.api();
         return api != null && api.sendForm(player.getUniqueId(), form.build());
      } catch (Throwable error) {
         this.plugin.getLogger().warning("Failed to open Bedrock status form for " + player.getName() + ": " + error.getMessage());
         return false;
      }
   }

   boolean showButtons(Player player, String title, String content, List<String> buttons, IntConsumer clickHandler) {
      if (!this.isBedrock(player) || buttons == null || buttons.isEmpty()) {
         return false;
      }

      try {
         SimpleForm.Builder form = SimpleForm.builder().title(clean(title)).content(content == null ? "" : clean(content));
         for (String button : buttons) {
            form.button(clean(button));
         }

         form.validResultHandler((SimpleFormResponse response) -> {
            int id = response.clickedButtonId();
            if (id < 0 || id >= buttons.size()) {
               return;
            }
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> clickHandler.accept(id));
         });

         GeyserApi api = GeyserApi.api();
         return api != null && api.sendForm(player.getUniqueId(), form.build());
      } catch (Throwable error) {
         this.plugin.getLogger().warning("Failed to open Bedrock button form for " + player.getName() + ": " + error.getMessage());
         return false;
      }
   }

   private void bindInventoryResult(
      SimpleForm.Builder form,
      Inventory inventory,
      List<Integer> buttonSlots,
      BiConsumer<Integer, ItemStack> clickHandler
   ) {
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
   }

   private void bindCloseHandler(SimpleForm.Builder form, Runnable closeHandler) {
      if (closeHandler != null) {
         form.closedOrInvalidResultHandler(() -> this.plugin.getServer().getScheduler().runTask(this.plugin, closeHandler));
      }
   }

   private static boolean isStatusLikeTitle(String title) {
      String cleaned = clean(title).toLowerCase();
      return cleaned.contains("minerva status") || cleaned.contains("minerva friends") || cleaned.contains("ステータス");
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

   private static String compactButtonText(ItemStack item) {
      String name = itemName(item);
      if (!name.isBlank()) {
         return name;
      }
      List<String> lore = itemLore(item);
      return lore.isEmpty() ? "選択" : lore.get(0);
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

   private static String compactStatusText(ItemStack item) {
      String name = itemName(item);
      List<String> lore = itemLore(item);
      StringBuilder text = new StringBuilder();

      if (!name.isBlank()) {
         text.append("• ").append(name);
      }

      int lines = Math.min(MAX_STATUS_LORE_LINES, lore.size());
      for (int i = 0; i < lines; i++) {
         String line = lore.get(i);
         if (line.isBlank()) {
            continue;
         }
         if (!text.isEmpty()) {
            text.append("  ");
         }
         text.append(line);
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
