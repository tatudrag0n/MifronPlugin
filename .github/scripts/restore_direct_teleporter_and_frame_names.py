from pathlib import Path


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


def replace_method(text: str, signature: str, replacement: str) -> str:
    start, end = method_span(text, signature)
    return text[:start] + replacement + text[end:]

root = Path('.')

# 1) Keep existing teleporter item, but restore direct-use guidance and update existing copies.
p = root / 'src/main/java/org/server/minerva/UtilityItemsFeature.java'
s = p.read_text(encoding='utf-8')
old = '''      this.giveMinervaItemIfMissing(
         player,
         "teleporter",
         this.createMinervaItem(Material.ENDER_EYE, "teleporter", ChatColor.LIGHT_PURPLE + "テレポーター", List.of(ChatColor.GRAY + "対応するエンドポータルフレームに使用して移動", ChatColor.DARK_GRAY + "投げることはできません"))
      );'''
new = '''      this.updateOrGiveMinervaItem(
         player,
         "teleporter",
         this.createMinervaItem(
            Material.ENDER_EYE,
            "teleporter",
            ChatColor.LIGHT_PURPLE + "テレポーター",
            List.of(
               ChatColor.GRAY + "右クリック: テレポート先を選択",
               ChatColor.GRAY + "エンドポータルフレームに使用: フレームの登録地点へ移動",
               ChatColor.DARK_GRAY + "投げることはできません"
            )
         )
      );'''
s = replace_once(s, old, new, 'teleporter initial item')
p.write_text(s, encoding='utf-8')

# 2) Add a native Bedrock button-list helper. This keeps the teleporter completely free of chest UI on mobile.
p = root / 'src/main/java/org/server/minerva/BedrockUiFeature.java'
s = p.read_text(encoding='utf-8')
if 'import java.util.function.IntConsumer;' not in s:
    s = s.replace('import java.util.function.BiConsumer;\n', 'import java.util.function.BiConsumer;\nimport java.util.function.IntConsumer;\n', 1)
if 'boolean showButtons(Player player, String title, String content, List<String> buttons, IntConsumer clickHandler)' not in s:
    anchor = '   private static boolean isEmptyOrFiller(ItemStack item) {'
    helper = '''   boolean showButtons(Player player, String title, String content, List<String> buttons, IntConsumer clickHandler) {
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

'''
    s = replace_once(s, anchor, helper + anchor, 'Bedrock button helper')
p.write_text(s, encoding='utf-8')

# 3) Expose the already optional Bedrock UI bridge to package features.
p = root / 'src/main/java/org/server/minerva/Minerva.java'
s = p.read_text(encoding='utf-8')
if 'BedrockUiFeature bedrockUiFeature()' not in s:
    anchor = '   void teleportToConfigLocation(Player player, String path) {'
    accessor = '''   BedrockUiFeature bedrockUiFeature() {
      return this.bedrockUiFeature;
   }

'''
    s = replace_once(s, anchor, accessor + anchor, 'Bedrock UI accessor')
p.write_text(s, encoding='utf-8')

# 4) Main teleporter/frame behavior.
p = root / 'src/main/java/org/server/minerva/ServerPortalFeature.java'
s = p.read_text(encoding='utf-8')

# Imports.
if 'io.papermc.paper.event.player.AsyncChatEvent;' not in s:
    s = s.replace('package org.server.minerva;\n\n', 'package org.server.minerva;\n\nimport io.papermc.paper.event.player.AsyncChatEvent;\n', 1)
if 'org.bukkit.Particle;' not in s:
    s = s.replace('import org.bukkit.NamespacedKey;\n', 'import org.bukkit.NamespacedKey;\nimport org.bukkit.Particle;\nimport org.bukkit.Sound;\n', 1)
if 'org.bukkit.configuration.ConfigurationSection;' not in s:
    s = s.replace('import org.bukkit.block.data.type.EndPortalFrame;\n', 'import org.bukkit.block.data.type.EndPortalFrame;\nimport org.bukkit.configuration.ConfigurationSection;\n', 1)
if 'net.kyori.adventure.text.format.NamedTextColor;' not in s:
    s = s.replace('import net.kyori.adventure.text.Component;\n', 'import net.kyori.adventure.text.Component;\nimport net.kyori.adventure.text.format.NamedTextColor;\nimport net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;\n', 1)
if 'org.bukkit.event.player.PlayerQuitEvent;' not in s:
    s = s.replace('import org.bukkit.event.player.PlayerMoveEvent;\n', 'import org.bukkit.event.player.PlayerMoveEvent;\nimport org.bukkit.event.player.PlayerQuitEvent;\n', 1)

# State.
if 'pendingFrameRenameKeys' not in s:
    anchor = '   private final Map<UUID, Location> pendingCoordinateTargets = new ConcurrentHashMap<>();'
    repl = anchor + '''\n   private final Map<UUID, String> pendingFrameRenameKeys = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> teleporterSelections = new ConcurrentHashMap<>();
   private final Map<UUID, Long> teleporterSelectionExpires = new ConcurrentHashMap<>();'''
    s = replace_once(s, anchor, repl, 'teleporter state')

# Wand lore: make rename operation discoverable.
s = s.replace(
'''            Component.text(ChatColor.GRAY + "移動先未記憶でフレーム右クリック: 移動先UI"),
            Component.text(ChatColor.GRAY + "左クリック: 設定解除 / ポータル削除")''',
'''            Component.text(ChatColor.GRAY + "移動先未記憶でフレーム右クリック: 移動先UI"),
            Component.text(ChatColor.GRAY + "Shift+左クリック: フレームの表示名を変更"),
            Component.text(ChatColor.GRAY + "左クリック: 設定解除 / ポータル削除")''',
1,
)

# Frame left-click behavior: Shift+left starts rename; normal left still clears.
old_frame_left = '''            } else if (event.getAction().isLeftClick()) {
               this.clearPortalTarget(block);
               player.sendMessage(ChatColor.GREEN + "テレポーターフレームの移動先設定を解除しました。");
            }'''
new_frame_left = '''            } else if (event.getAction().isLeftClick()) {
               if (player.isSneaking()) {
                  if (!this.hasFrameTarget(block)) {
                     player.sendMessage(ChatColor.YELLOW + "先にこのフレームへ移動先を登録してください。");
                  } else {
                     this.pendingFrameRenameKeys.put(player.getUniqueId(), this.blockKey(block));
                     player.sendMessage(ChatColor.LIGHT_PURPLE + "テレポート先の表示名をチャットに入力してください。");
                     player.sendMessage(ChatColor.GRAY + "reset で自動名に戻す / cancel で中止");
                  }
               } else {
                  this.clearPortalTarget(block);
                  player.sendMessage(ChatColor.GREEN + "テレポーターフレームの移動先設定を解除しました。");
               }
            }'''
s = replace_once(s, old_frame_left, new_frame_left, 'frame rename click')

# Replace direct teleporter right-click logic.
new_on_use = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onTeleporterUse(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
         return;
      }

      ItemStack item = event.getItem();
      if (!this.isTeleporter(item)) {
         return;
      }

      // The Minerva Ender Eye is a UI item, never a throwable vanilla eye.
      event.setCancelled(true);
      Player player = event.getPlayer();
      Block frame = event.getClickedBlock();
      if (frame != null && frame.getType() == Material.END_PORTAL_FRAME) {
         this.useTeleporterFrame(player, frame);
         return;
      }

      this.openDirectTeleporter(player);
   }'''
s = replace_method(s, '   public void onTeleporterUse(PlayerInteractEvent event)', new_on_use)

# Insert all direct-menu, frame-name, and cleanup helpers before isTeleporter.
if 'private void openDirectTeleporter(Player player)' not in s:
    anchor = '   private boolean isTeleporter(ItemStack item) {'
    helper = r'''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onTeleporterLeftClick(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isLeftClick() || !this.isTeleporter(event.getItem())) {
         return;
      }

      // Bedrock users navigate with the native Form opened by right-click.
      BedrockUiFeature bedrock = this.plugin.bedrockUiFeature();
      if (bedrock != null && bedrock.isBedrock(event.getPlayer())) {
         return;
      }

      event.setCancelled(true);
      this.cycleJavaTeleporter(event.getPlayer(), -1);
   }

   private void useTeleporterFrame(Player player, Block frame) {
      Location coordinateTarget = this.coordinateTarget(frame);
      String target = this.serverPortalTarget(frame);
      if (coordinateTarget == null && (target == null || target.isBlank())) {
         player.sendMessage(ChatColor.YELLOW + "このエンドポータルフレームには移動先が設定されていません。");
         return;
      }

      this.clearTeleporterSelection(player);
      this.setFrameEye(frame, true);
      player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8F, 1.1F);
      player.spawnParticle(Particle.PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 22, 0.45, 0.65, 0.45, 0.08);
      if (coordinateTarget != null) {
         player.teleport(coordinateTarget);
         player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.05F);
         player.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 30, 0.55, 0.75, 0.55, 0.05);
      } else {
         this.plugin.teleportToConfigLocation(player, target);
      }

      // The inserted eye is only a short visual cue and never remains in the frame.
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.setFrameEye(frame, false));
   }

   private void openDirectTeleporter(Player player) {
      List<TeleportDestination> destinations = this.teleporterDestinations();
      if (destinations.isEmpty()) {
         player.sendMessage(ChatColor.YELLOW + "テレポーターに利用できる移動先が設定されていません。");
         return;
      }

      BedrockUiFeature bedrock = this.plugin.bedrockUiFeature();
      if (bedrock != null && bedrock.isBedrock(player)) {
         List<String> buttons = new ArrayList<>();
         for (TeleportDestination destination : destinations) {
            buttons.add("▶ " + destination.name() + "\n" + destination.location().getWorld().getName() + "  " + this.formatCoordinates(destination.location().getX(), destination.location().getY(), destination.location().getZ()));
         }
         if (bedrock.showButtons(
            player,
            "Mifron Teleporter",
            "移動先をタップしてください。\nチェスト操作は必要ありません。",
            buttons,
            index -> {
               List<TeleportDestination> current = this.teleporterDestinations();
               if (index >= 0 && index < current.size()) {
                  this.teleportDirect(player, current.get(index));
               }
            }
         )) {
            return;
         }
      }

      long now = System.currentTimeMillis();
      boolean active = this.teleporterSelectionExpires.getOrDefault(player.getUniqueId(), 0L) > now;
      if (player.isSneaking() && active) {
         int index = Math.max(0, Math.min(this.teleporterSelections.getOrDefault(player.getUniqueId(), 0), destinations.size() - 1));
         this.teleportDirect(player, destinations.get(index));
         return;
      }

      if (!active) {
         this.teleporterSelections.put(player.getUniqueId(), 0);
      } else {
         int next = Math.floorMod(this.teleporterSelections.getOrDefault(player.getUniqueId(), 0) + 1, destinations.size());
         this.teleporterSelections.put(player.getUniqueId(), next);
      }
      this.teleporterSelectionExpires.put(player.getUniqueId(), now + 12000L);
      this.showJavaTeleporterSelection(player, destinations);
   }

   private void cycleJavaTeleporter(Player player, int delta) {
      List<TeleportDestination> destinations = this.teleporterDestinations();
      if (destinations.isEmpty()) {
         player.sendMessage(ChatColor.YELLOW + "テレポーターに利用できる移動先が設定されていません。");
         return;
      }

      long now = System.currentTimeMillis();
      boolean active = this.teleporterSelectionExpires.getOrDefault(player.getUniqueId(), 0L) > now;
      int current = active ? this.teleporterSelections.getOrDefault(player.getUniqueId(), 0) : 0;
      int next = Math.floorMod(current + delta, destinations.size());
      this.teleporterSelections.put(player.getUniqueId(), next);
      this.teleporterSelectionExpires.put(player.getUniqueId(), now + 12000L);
      this.showJavaTeleporterSelection(player, destinations);
   }

   private void showJavaTeleporterSelection(Player player, List<TeleportDestination> destinations) {
      int index = Math.max(0, Math.min(this.teleporterSelections.getOrDefault(player.getUniqueId(), 0), destinations.size() - 1));
      TeleportDestination destination = destinations.get(index);
      String subtitle = ChatColor.DARK_GRAY + "◀  " + ChatColor.AQUA + "[" + (index + 1) + "/" + destinations.size() + "] " + ChatColor.WHITE + destination.name() + ChatColor.DARK_GRAY + "  ▶";
      player.sendTitle(ChatColor.LIGHT_PURPLE + "◆ TELEPORTER ◆", subtitle, 0, 38, 6);
      player.sendActionBar(Component.text("◀ 左クリック   |   Shift+右クリック: 移動   |   右クリック ▶", NamedTextColor.GRAY));
      player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55F, 1.25F);
      player.spawnParticle(Particle.PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.45, 0.3, 0.02);
   }

   private List<TeleportDestination> teleporterDestinations() {
      ConfigurationSection servers = this.plugin.getConfig().getConfigurationSection("servers");
      if (servers == null) {
         return List.of();
      }

      List<TeleportDestination> destinations = new ArrayList<>();
      for (String key : servers.getKeys(false)) {
         String path = "servers." + key;
         Location location = this.plugin.readLocation(path);
         if (location == null || location.getWorld() == null) {
            continue;
         }
         String name = this.plugin.getConfig().getString(path + ".display-name", "");
         if (name == null || name.isBlank()) {
            name = this.plugin.getConfig().getString(path + ".name", key);
         }
         if (name == null || name.isBlank()) {
            name = key;
         }
         destinations.add(new TeleportDestination(key, name, location));
      }
      return destinations;
   }

   private void teleportDirect(Player player, TeleportDestination destination) {
      if (destination == null || destination.location() == null || destination.location().getWorld() == null) {
         player.sendMessage(ChatColor.RED + "移動先を読み込めませんでした。");
         return;
      }

      this.clearTeleporterSelection(player);
      Location before = player.getLocation();
      player.spawnParticle(Particle.PORTAL, before.clone().add(0.0, 1.0, 0.0), 28, 0.5, 0.7, 0.5, 0.08);
      player.playSound(before, Sound.BLOCK_PORTAL_TRIGGER, 0.45F, 1.35F);
      if (player.teleport(destination.location())) {
         player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.85F, 1.05F);
         player.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 34, 0.55, 0.75, 0.55, 0.05);
         player.sendActionBar(Component.text("移動先: " + destination.name(), NamedTextColor.LIGHT_PURPLE));
      } else {
         player.sendMessage(ChatColor.RED + "テレポートに失敗しました。");
      }
   }

   private void clearTeleporterSelection(Player player) {
      this.teleporterSelections.remove(player.getUniqueId());
      this.teleporterSelectionExpires.remove(player.getUniqueId());
   }

   private boolean hasFrameTarget(Block frame) {
      return this.coordinateTarget(frame) != null || !this.serverPortalTarget(frame).isBlank();
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onFrameRenameChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      String key = this.pendingFrameRenameKeys.remove(player.getUniqueId());
      if (key == null) {
         return;
      }

      event.setCancelled(true);
      String input = PlainTextComponentSerializer.plainText().serialize(event.message());
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         Block frame = this.blockFromKey(key);
         if (frame == null || frame.getType() != Material.END_PORTAL_FRAME) {
            player.sendMessage(ChatColor.RED + "対象のエンドポータルフレームが見つかりませんでした。");
            return;
         }

         String normalized = input == null ? "" : input.trim();
         if (normalized.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "表示名の変更を中止しました。");
            return;
         }

         if (normalized.equalsIgnoreCase("reset") || normalized.equalsIgnoreCase("auto")) {
            this.setFrameDisplayName(frame, null);
            player.sendMessage(ChatColor.GREEN + "表示名を自動名に戻しました。");
            return;
         }

         String clean = this.sanitizeFrameName(normalized);
         if (clean.isBlank()) {
            player.sendMessage(ChatColor.RED + "表示名が空です。もう一度Shift+左クリックから設定してください。");
            return;
         }
         this.setFrameDisplayName(frame, clean);
         player.sendMessage(ChatColor.GREEN + "テレポート先の表示名を「" + clean + "」に変更しました。");
      });
   }

   @EventHandler
   public void onTeleporterPlayerQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.pendingCoordinateTargets.remove(uuid);
      this.pendingFrameRenameKeys.remove(uuid);
      this.teleporterSelections.remove(uuid);
      this.teleporterSelectionExpires.remove(uuid);
   }

   private String sanitizeFrameName(String raw) {
      if (raw == null) {
         return "";
      }
      String clean = ChatColor.stripColor(raw.replace("§", ""));
      if (clean == null) {
         clean = raw.replace("§", "");
      }
      clean = clean.replaceAll("\\p{Cntrl}", "").trim();
      return clean.length() > 40 ? clean.substring(0, 40) : clean;
   }

'''
    s = replace_once(s, anchor, helper + anchor, 'direct teleporter helpers')

# Store/clear custom frame names.
if 'private String frameDisplayNamePath(String key)' not in s:
    anchor = '   private void clearPortalTarget(Block block) {'
    helper = '''   private String frameDisplayNamePath(String key) {
      return "server-portal-display-names." + key;
   }

   private String frameDisplayName(Block frame) {
      return frame == null ? "" : this.plugin.data().getString(this.frameDisplayNamePath(this.blockKey(frame)), "");
   }

   private void setFrameDisplayName(Block frame, String name) {
      if (frame == null) {
         return;
      }
      String clean = name == null ? "" : this.sanitizeFrameName(name);
      this.plugin.data().set(this.frameDisplayNamePath(this.blockKey(frame)), clean.isBlank() ? null : clean);
      this.plugin.saveData();
      this.refreshFrameLabel(frame);
   }

'''
    s = replace_once(s, anchor, helper + anchor, 'frame display name helpers')

old_clear = '''         this.plugin.data().set(this.serverPortalTargetPath(key), null);
         this.plugin.data().set(this.coordinateTargetPath(key), null);
         this.plugin.saveData();'''
new_clear = '''         this.plugin.data().set(this.serverPortalTargetPath(key), null);
         this.plugin.data().set(this.coordinateTargetPath(key), null);
         this.plugin.data().set(this.frameDisplayNamePath(key), null);
         this.plugin.saveData();'''
s = replace_once(s, old_clear, new_clear, 'clear frame display name')

# Replace label rendering so custom names are prominent and coordinates stay visible underneath.
new_refresh = '''   private void refreshFrameLabel(Block frame) {
      if (frame == null || frame.getWorld() == null) {
         return;
      }

      String key = this.blockKey(frame);
      for (Entity entity : frame.getWorld().getNearbyEntities(frame.getLocation().add(0.5, 1.5, 0.5), 2.0, 2.0, 2.0)) {
         if (entity instanceof TextDisplay display
            && key.equals(display.getPersistentDataContainer().get(this.frameLabelKey, PersistentDataType.STRING))) {
            display.remove();
         }
      }

      Location direct = this.coordinateTarget(frame);
      String named = this.serverPortalTarget(frame);
      if (direct == null && (named == null || named.isBlank())) {
         return;
      }

      String detail = direct != null ? this.formatLocation(direct) : this.namedTargetLabel(named);
      String custom = this.frameDisplayName(frame);
      Component label = Component.text("▶ ", NamedTextColor.LIGHT_PURPLE);
      if (custom != null && !custom.isBlank()) {
         label = label.append(Component.text(custom, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text(detail, NamedTextColor.GRAY));
      } else {
         label = label.append(Component.text(detail, NamedTextColor.WHITE));
      }

      TextDisplay display = (TextDisplay)frame.getWorld().spawnEntity(frame.getLocation().add(0.5, 1.45, 0.5), EntityType.TEXT_DISPLAY);
      display.text(label);
      display.setBillboard(Display.Billboard.CENTER);
      display.setSeeThrough(true);
      display.setShadowed(true);
      display.setPersistent(true);
      display.setLineWidth(240);
      display.getPersistentDataContainer().set(this.frameLabelKey, PersistentDataType.STRING, key);
   }'''
s = replace_method(s, '   private void refreshFrameLabel(Block frame)', new_refresh)

# Add record before the final class brace.
if 'private record TeleportDestination' not in s:
    last = s.rfind('\n}')
    if last < 0:
        raise SystemExit('missing class closing brace')
    s = s[:last] + '''\n\n   private record TeleportDestination(String key, String name, Location location) {
   }''' + s[last:]

p.write_text(s, encoding='utf-8')
