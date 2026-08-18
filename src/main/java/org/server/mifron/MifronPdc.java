package org.server.mifron;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Reads plugin-owned persistent data from both the current Mifron namespace
 * and the pre-rename Minerva namespace. The public plugin name changed, but
 * player inventories and loaded entities must remain compatible.
 */
final class MifronPdc {
   private static final String LEGACY_NAMESPACE = "minerva";

   private MifronPdc() {
   }

   static <P, C> C get(PersistentDataContainer container, NamespacedKey currentKey, PersistentDataType<P, C> type) {
      if (container == null || currentKey == null) {
         return null;
      }

      C current = container.get(currentKey, type);
      if (current != null) {
         return current;
      }

      NamespacedKey legacyKey = new NamespacedKey(LEGACY_NAMESPACE, currentKey.getKey());
      return container.get(legacyKey, type);
   }

   static <P, C> boolean has(PersistentDataContainer container, NamespacedKey currentKey, PersistentDataType<P, C> type) {
      if (container == null || currentKey == null) {
         return false;
      }

      return container.has(currentKey, type)
         || container.has(new NamespacedKey(LEGACY_NAMESPACE, currentKey.getKey()), type);
   }
}
