from pathlib import Path

path = Path('src/main/java/org/server/minerva/Minerva.java')
text = path.read_text(encoding='utf-8')

field_anchor = '   private final AthleticManager athleticManager = new AthleticManager(this);\n'
if 'MinoruBridgeFeature minoruBridgeFeature' not in text:
    text = text.replace(field_anchor, field_anchor + '   private final MinoruBridgeFeature minoruBridgeFeature = new MinoruBridgeFeature(this);\n', 1)

enable_anchor = '      this.loadData();\n'
if 'start Minoru bridge API' not in text:
    text = text.replace(enable_anchor, enable_anchor + '      this.runStartupStep("start Minoru bridge API", this.minoruBridgeFeature::start);\n', 1)

disable_anchor = '   public void onDisable() {\n      this.cancelScheduledAutoShutdown("the plugin is disabling");\n'
if 'this.minoruBridgeFeature.stop();' not in text:
    text = text.replace(disable_anchor, disable_anchor + '      this.minoruBridgeFeature.stop();\n', 1)

command_anchor = '   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {\n      try {\n'
command_insert = '''   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {\n      try {\n         if (("minerva".equalsIgnoreCase(command.getName()) || "mva".equalsIgnoreCase(command.getName()))\n            && args.length > 0 && ("link".equalsIgnoreCase(args[0]) || "discord".equalsIgnoreCase(args[0]))) {\n            if (!(sender instanceof Player player)) {\n               sender.sendMessage("§cこのコマンドはゲーム内プレイヤー専用です。");\n               return true;\n            }\n            return this.minoruBridgeFeature.handleCommand(player, args);\n         }\n'''
if 'this.minoruBridgeFeature.handleCommand(player, args)' not in text:
    if command_anchor not in text:
        raise SystemExit('onCommand anchor not found')
    text = text.replace(command_anchor, command_insert, 1)

path.write_text(text, encoding='utf-8')
print('Integrated Minoru bridge into Minerva lifecycle and /mva link command.')
