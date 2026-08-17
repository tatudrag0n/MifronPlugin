from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"missing patch target: {label}")
    return text.replace(old, new, 1)

manager_path = Path('src/main/java/org/server/mifron/FfaManager.java')
manager = manager_path.read_text(encoding='utf-8')

if 'import org.bukkit.event.entity.EntityChangeBlockEvent;' not in manager:
    manager = replace_once(
        manager,
        'import org.bukkit.event.entity.EntityDamageByEntityEvent;\n',
        'import org.bukkit.event.entity.EntityChangeBlockEvent;\nimport org.bukkit.event.entity.EntityDamageByEntityEvent;\n',
        'FfaManager EntityChangeBlockEvent import',
    )

helper = '''   void handleBugSilverfishBlockChange(EntityChangeBlockEvent event) {
      Entity entity = event.getEntity();
      if (entity.getType() != EntityType.SILVERFISH) {
         return;
      }

      String kind = entity.getPersistentDataContainer().get(this.entityKindKey, PersistentDataType.STRING);
      if (!"bug_silverfish".equals(kind)) {
         return;
      }

      // Bug Mania silverfish are combat summons. Never allow them to disappear into
      // stone-family blocks (or alter blocks while attempting the vanilla merge action).
      event.setCancelled(true);
   }

'''
anchor = '   void handleEntityTarget(EntityTargetLivingEntityEvent event) {'
if 'void handleBugSilverfishBlockChange(EntityChangeBlockEvent event)' not in manager:
    if anchor not in manager:
        raise SystemExit('missing FfaManager entity-target anchor')
    manager = manager.replace(anchor, helper + anchor, 1)

manager_path.write_text(manager, encoding='utf-8', newline='\n')

listener_path = Path('src/main/java/org/server/mifron/FfaListener.java')
listener = listener_path.read_text(encoding='utf-8')

if 'import org.bukkit.event.entity.EntityChangeBlockEvent;' not in listener:
    listener = replace_once(
        listener,
        'import org.bukkit.event.entity.EntityDamageByEntityEvent;\n',
        'import org.bukkit.event.entity.EntityChangeBlockEvent;\nimport org.bukkit.event.entity.EntityDamageByEntityEvent;\n',
        'FfaListener EntityChangeBlockEvent import',
    )

handler = '''   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onEntityChangeBlock(EntityChangeBlockEvent event) {
      this.ffa.handleBugSilverfishBlockChange(event);
   }

'''
listener_anchor = '   @EventHandler(ignoreCancelled = true)\n   public void onBlockBreak(BlockBreakEvent event) {'
if 'public void onEntityChangeBlock(EntityChangeBlockEvent event)' not in listener:
    if listener_anchor not in listener:
        raise SystemExit('missing FfaListener block-break anchor')
    listener = listener.replace(listener_anchor, handler + listener_anchor, 1)

listener_path.write_text(listener, encoding='utf-8', newline='\n')

print('Bug Mania silverfish can no longer burrow into blocks; all tagged bug_silverfish use the same rule.')
