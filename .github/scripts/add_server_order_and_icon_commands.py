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
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return start, i + 1
    raise SystemExit(f"unterminated method: {signature}")


root = Path('.')
minerva_path = root / 'src/main/java/org/server/minerva/Minerva.java'
portal_path = root / 'src/main/java/org/server/minerva/ServerPortalFeature.java'

s = minerva_path.read_text(encoding='utf-8')

# Add command cases immediately before delserver so existing command behavior remains untouched.
if 'case "serverorder":' not in s:
    anchor = '            case "delserver":'
    insertion = '''            case "serverorder":\n            case "servermove":\n               this.handleServerOrderCommand(sender, args);\n               break;\n            case "servericon":\n            case "setservericon":\n               this.handleServerIconCommand(sender, args);\n               break;\n'''
    s = replace_once(s, anchor, insertion + anchor, 'server command switch')

# Add commands to the root help line.
s = s.replace(
    'serverwand|sethub|setserver|delserver|warning',
    'serverwand|sethub|setserver|serverorder|servericon|delserver|warning',
    1,
)

# Add management helpers near other location/config helpers.
if 'private void handleServerOrderCommand(CommandSender sender, String[] args)' not in s:
    anchor = '   private void applyWorldSpawnLocations() {'
    helpers = r'''   private void handleServerOrderCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("minerva.admin")) {
         sender.sendMessage("§c権限がありません。");
         return;
      }
      if (args.length < 3) {
         sender.sendMessage("§e/mva serverorder <server-id> <position>");
         sender.sendMessage("§7例: /mva serverorder survival 1");
         return;
      }

      String key = args[1];
      String path = "servers." + key;
      if (!this.getConfig().isConfigurationSection(path)) {
         sender.sendMessage("§cサーバーが見つかりません: " + key);
         return;
      }

      int requested;
      try {
         requested = Integer.parseInt(args[2]);
      } catch (NumberFormatException e) {
         sender.sendMessage("§cposition は1以上の整数で指定してください。");
         return;
      }

      ConfigurationSection servers = this.getConfig().getConfigurationSection("servers");
      if (servers == null || servers.getKeys(false).isEmpty()) {
         sender.sendMessage("§c登録済みサーバーがありません。");
         return;
      }

      List<String> keys = new ArrayList<>(servers.getKeys(false));
      Map<String, Integer> originalIndex = new HashMap<>();
      for (int i = 0; i < keys.size(); i++) {
         originalIndex.put(keys.get(i), i);
      }
      keys.sort((a, b) -> {
         int ao = this.getConfig().getInt("servers." + a + ".order", originalIndex.get(a) + 1);
         int bo = this.getConfig().getInt("servers." + b + ".order", originalIndex.get(b) + 1);
         int order = Integer.compare(ao, bo);
         return order != 0 ? order : Integer.compare(originalIndex.get(a), originalIndex.get(b));
      });

      keys.remove(key);
      int position = Math.max(1, Math.min(requested, keys.size() + 1));
      keys.add(position - 1, key);
      for (int i = 0; i < keys.size(); i++) {
         this.getConfig().set("servers." + keys.get(i) + ".order", i + 1);
      }
      this.saveConfig();

      sender.sendMessage("§aテレポート先の表示順を変更しました: " + key + " → " + position + "番目");
      sender.sendMessage("§7現在の順番: " + String.join(" → ", keys));
      if (sender instanceof Player player) {
         player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
      }
   }

   private void handleServerIconCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("minerva.admin")) {
         sender.sendMessage("§c権限がありません。");
         return;
      }
      if (args.length < 3) {
         sender.sendMessage("§e/mva servericon <server-id> <material>");
         sender.sendMessage("§7例: /mva servericon survival grass_block");
         return;
      }

      String key = args[1];
      String path = "servers." + key;
      if (!this.getConfig().isConfigurationSection(path)) {
         sender.sendMessage("§cサーバーが見つかりません: " + key);
         return;
      }

      Material icon = Material.matchMaterial(args[2]);
      if (icon == null || !icon.isItem() || icon == Material.AIR) {
         sender.sendMessage("§c有効なアイテムMaterialを指定してください: " + args[2]);
         return;
      }

      this.getConfig().set(path + ".icon", icon.name().toLowerCase(Locale.ROOT));
      this.saveConfig();
      sender.sendMessage("§aテレポート先のアイコンを変更しました: " + key + " → " + icon.name().toLowerCase(Locale.ROOT));
      if (sender instanceof Player player) {
         player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.35F);
      }
   }

'''
    s = replace_once(s, anchor, helpers + anchor, 'server management helpers')

minerva_path.write_text(s, encoding='utf-8')

# Make all teleporter destination consumers use the explicit configured order.
s = portal_path.read_text(encoding='utf-8')
start, end = method_span(s, '   private List<TeleportDestination> teleporterDestinations()')
method = s[start:end]
if 'servers." + destination.key() + ".order' not in method:
    old = '      return destinations;\n'
    new = '''      Map<String, Integer> fallbackOrder = new ConcurrentHashMap<>();\n      int fallbackIndex = 1;\n      for (String key : servers.getKeys(false)) {\n         fallbackOrder.put(key, fallbackIndex++);\n      }\n      destinations.sort((a, b) -> {\n         int ao = this.plugin.getConfig().getInt("servers." + a.key() + ".order", fallbackOrder.getOrDefault(a.key(), Integer.MAX_VALUE));\n         int bo = this.plugin.getConfig().getInt("servers." + b.key() + ".order", fallbackOrder.getOrDefault(b.key(), Integer.MAX_VALUE));\n         int order = Integer.compare(ao, bo);\n         return order != 0 ? order : a.key().compareToIgnoreCase(b.key());\n      });\n      return destinations;\n'''
    if old not in method:
        raise SystemExit('missing return destinations in teleporterDestinations')
    method = method.replace(old, new, 1)
    s = s[:start] + method + s[end:]
portal_path.write_text(s, encoding='utf-8')

print('added server order and icon management commands')
