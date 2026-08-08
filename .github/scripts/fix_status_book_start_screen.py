from pathlib import Path

path = Path("src/main/java/org/server/minerva/Minerva.java")
text = path.read_text(encoding="utf-8")
old = '''               if (this.isMinervaItem(item, "friend_book") && event.getAction().isRightClick()) {\n                  this.openStatusUi(player, "progress:0");\n                  event.setCancelled(true);\n'''
new = '''               if (this.isMinervaItem(item, "friend_book") && event.getAction().isRightClick()) {\n                  this.openFriendUi(player);\n                  event.setCancelled(true);\n'''
if old not in text:
    raise SystemExit("status book interaction target not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Status book now opens the Minerva main/friend status screen instead of the progress tab.")
