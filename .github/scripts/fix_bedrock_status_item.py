from pathlib import Path

path = Path("src/main/java/org/server/minerva/UtilityItemsFeature.java")
text = path.read_text(encoding="utf-8")

old_update = '''      for (ItemStack item : player.getInventory().getContents()) {\n         if (this.isMinervaItem(item, id)) {\n            ItemMeta meta = item.getItemMeta();\n            ItemMeta templateMeta = template.getItemMeta();\n'''
new_update = '''      for (ItemStack item : player.getInventory().getContents()) {\n         if (this.isMinervaItem(item, id)) {\n            if (item.getType() != template.getType()) {\n               item.setType(template.getType());\n            }\n            ItemMeta meta = item.getItemMeta();\n            ItemMeta templateMeta = template.getItemMeta();\n'''
if old_update not in text:
    raise SystemExit("updateOrGiveMinervaItem target not found")
text = text.replace(old_update, new_update, 1)

old_book = '''   private ItemStack createStatusBook() {\n      ItemStack item = new ItemStack(Material.WRITTEN_BOOK);\n      BookMeta meta = (BookMeta)item.getItemMeta();\n      meta.setTitle("ステータス");\n      meta.setAuthor("Minerva");\n      meta.addPages(new Component[]{Component.text("Minerva Status UI\\n右クリックで開きます。")});\n      meta.displayName(Component.text(ChatColor.GOLD + "ステータス"));\n      meta.lore(List.of(Component.text(ChatColor.GRAY + "右クリック: ステータス UI")));\n      meta.getPersistentDataContainer().set(this.minervaItemKey, PersistentDataType.STRING, "friend_book");\n      item.setItemMeta(meta);\n      return item;\n   }\n'''
new_book = '''   private ItemStack createStatusBook() {\n      return this.createMinervaItem(\n         Material.NETHER_STAR,\n         "friend_book",\n         ChatColor.GOLD + "ステータス",\n         List.of(ChatColor.GRAY + "右クリック: ステータス UI")\n      );\n   }\n'''
if old_book not in text:
    raise SystemExit("createStatusBook target not found")
text = text.replace(old_book, new_book, 1)

path.write_text(text, encoding="utf-8")
print("Replaced the native written-book status item with a neutral Nether Star and added material migration for existing fixed items.")
