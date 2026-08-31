from pathlib import Path

path = Path('src/main/java/org/server/mifron/Mifron.java')
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

path.write_text(text, encoding='utf-8')
print('Integrated Minoru bridge lifecycle only; player linking uses MifronAuth kick codes.')
