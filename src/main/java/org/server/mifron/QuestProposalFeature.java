package org.server.mifron;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class QuestProposalFeature implements Listener {
   static final String TYPE_TITLE = "§bクエスト提案 / 種類";
   static final String CONDITION_TITLE = "§bクエスト提案 / 条件";
   private final Mifron plugin;
   private final ProposalManager proposals;
   private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();

   QuestProposalFeature(Mifron plugin, ProposalManager proposals) {
      this.plugin = plugin;
      this.proposals = proposals;
   }

   boolean handleCommand(Player player, String[] args) {
      if (args.length >= 2 && "propose".equalsIgnoreCase(args[1])) {
         this.start(player);
         return true;
      }
      if (args.length >= 2 && "cancel".equalsIgnoreCase(args[1])) {
         this.drafts.remove(player.getUniqueId());
         player.sendMessage(ChatColor.YELLOW + "クエスト提案をキャンセルしました。");
         return true;
      }
      return false;
   }

   void start(Player player) {
      this.drafts.put(player.getUniqueId(), new Draft());
      player.openInventory(this.typeMenu());
      player.sendMessage(ChatColor.AQUA + "クエスト提案を開始しました。種類を選んでください。 /mf quest cancel");
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) return;
      String title = event.getView().getTitle();
      if (!TYPE_TITLE.equals(title) && !CONDITION_TITLE.equals(title)) return;
      event.setCancelled(true);
      Draft draft = this.drafts.get(player.getUniqueId());
      if (draft == null) { player.closeInventory(); return; }
      ItemStack clicked = event.getCurrentItem();
      if (clicked == null || !clicked.hasItemMeta()) return;
      String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
      if (TYPE_TITLE.equals(title)) {
         draft.type = switch (name) {
            case "デイリー" -> "daily";
            case "ウィークリー" -> "weekly";
            case "マンスリー" -> "monthly";
            case "単発" -> "one_shot";
            case "隠し" -> "hidden";
            default -> null;
         };
         if (draft.type == null) return;
         draft.step = Step.NAME;
         player.closeInventory();
         player.sendMessage(ChatColor.AQUA + "クエスト名をチャットに入力してください。");
         return;
      }
      draft.condition = switch (name) {
         case "アイテム入手" -> "obtain_item";
         case "ブロック破壊" -> "break_block";
         case "ブロック設置" -> "place_block";
         case "移動・探索" -> "travel";
         case "MP獲得" -> "mp";
         case "進捗達成" -> "advancement";
         case "累計ログイン" -> "login";
         default -> null;
      };
      if (draft.condition == null) return;
      draft.progressKey = this.progressKey(draft.condition);
      draft.step = Step.AMOUNT;
      player.closeInventory();
      player.sendMessage(ChatColor.AQUA + "必要な回数・個数を数字で入力してください。");
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      Draft draft = this.drafts.get(player.getUniqueId());
      if (draft == null || draft.step == Step.TYPE || draft.step == Step.CONDITION) return;
      event.setCancelled(true);
      String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
      Bukkit.getScheduler().runTask(this.plugin, () -> this.acceptChat(player, draft, text));
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.drafts.remove(event.getPlayer().getUniqueId());
   }

   private void acceptChat(Player player, Draft draft, String text) {
      if ("cancel".equalsIgnoreCase(text)) {
         this.drafts.remove(player.getUniqueId());
         player.sendMessage(ChatColor.YELLOW + "クエスト提案をキャンセルしました。");
         return;
      }
      switch (draft.step) {
         case NAME -> {
            if (text.length() < 2 || text.length() > 32) {
               player.sendMessage(ChatColor.RED + "名前は2〜32文字にしてください。");
               return;
            }
            draft.name = text;
            draft.step = Step.CONDITION;
            player.openInventory(this.conditionMenu());
         }
         case AMOUNT -> {
            try { draft.required = Math.max(1, Integer.parseInt(text)); }
            catch (NumberFormatException ignored) { draft.required = 1; draft.advancement = text; }
            draft.step = Step.REWARD;
            player.sendMessage(ChatColor.AQUA + "報酬MPを数字で入力してください。");
         }
         case REWARD -> {
            try { draft.reward = Math.max(1, Integer.parseInt(text)); }
            catch (NumberFormatException ignored) { player.sendMessage(ChatColor.RED + "数字を入力してください。"); return; }
            draft.step = Step.DEPENDS;
            player.sendMessage(ChatColor.AQUA + "依存クエストIDを入力。不要なら none");
         }
         case DEPENDS -> {
            draft.dependsOn = "none".equalsIgnoreCase(text) ? "" : text;
            draft.step = Step.TITLE;
            player.sendMessage(ChatColor.AQUA + "称号報酬があれば名前を入力。不要なら none");
         }
         case TITLE -> {
            draft.title = "none".equalsIgnoreCase(text) ? "" : text;
            String id = this.proposals.submitQuest(player, draft);
            this.drafts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "クエスト提案を送りました: " + id);
         }
         default -> {}
      }
   }

   private Inventory typeMenu() {
      Inventory inventory = Bukkit.createInventory(null, 9, TYPE_TITLE);
      inventory.setItem(0, icon(Material.SUNFLOWER, "デイリー"));
      inventory.setItem(2, icon(Material.CLOCK, "ウィークリー"));
      inventory.setItem(4, icon(Material.ENDER_PEARL, "マンスリー"));
      inventory.setItem(6, icon(Material.NETHER_STAR, "単発"));
      inventory.setItem(8, icon(Material.ENDER_EYE, "隠し"));
      return inventory;
   }

   private Inventory conditionMenu() {
      Inventory inventory = Bukkit.createInventory(null, 9, CONDITION_TITLE);
      inventory.setItem(0, icon(Material.CHEST, "アイテム入手"));
      inventory.setItem(1, icon(Material.IRON_PICKAXE, "ブロック破壊"));
      inventory.setItem(2, icon(Material.BRICKS, "ブロック設置"));
      inventory.setItem(3, icon(Material.COMPASS, "移動・探索"));
      inventory.setItem(4, icon(Material.EMERALD, "MP獲得"));
      inventory.setItem(5, icon(Material.EXPERIENCE_BOTTLE, "進捗達成"));
      inventory.setItem(6, icon(Material.CLOCK, "累計ログイン"));
      return inventory;
   }

   private ItemStack icon(Material material, String name) {
      ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName("§a" + name);
      item.setItemMeta(meta);
      return item;
   }

   private String progressKey(String condition) {
      return switch (condition) {
         case "obtain_item" -> "items_obtained";
         case "break_block" -> "mining_blocks";
         case "place_block" -> "building_blocks";
         case "travel" -> "exploration_chunks";
         case "mp" -> "mp_gained";
         case "advancement" -> "advancements_total";
         case "login" -> "login_days";
         default -> "manual";
      };
   }

   enum Step { TYPE, NAME, CONDITION, AMOUNT, REWARD, DEPENDS, TITLE }

   static final class Draft {
      Step step = Step.TYPE;
      String type = "one_shot";
      String name = "";
      String condition = "";
      String progressKey = "manual";
      String advancement = "";
      String dependsOn = "";
      String title = "";
      int required = 1;
      int reward = 10;
   }
}
