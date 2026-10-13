package org.server.mifron;

import java.util.Locale;
import java.util.Set;

/**
 * Pure chat policy rules: decides whether a command line is a player-to-player
 * private message that must be blocked. Mifron only provides public global
 * chat ("場" provision); directed messaging would make the operator a
 * telecommunications carrier under the 電気通信事業法 and require a filing,
 * so every private-message path must stay closed.
 */
final class ChatPolicyRules {
   private ChatPolicyRules() {
   }

   /** Commands that deliver a directed message to a specific player. */
   static final Set<String> DEFAULT_BLOCKED_COMMANDS = Set.of(
      "tell", "msg", "w", "whisper", "message", "m", "t",
      "r", "reply", "mail", "em", "email"
   );

   /**
    * True when {@code rawCommandLine} is a private-message command.
    * {@code extraBlocked} holds additional labels from config.
    */
   static boolean isPrivateMessageCommand(String rawCommandLine, Set<String> extraBlocked) {
      if (rawCommandLine == null) return false;
      String trimmed = rawCommandLine.trim();
      if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
      if (trimmed.isBlank()) return false;
      String[] parts = trimmed.split("\\s+");
      String label = normalizeLabel(parts[0]);
      if (DEFAULT_BLOCKED_COMMANDS.contains(label)) return true;
      if (extraBlocked != null && extraBlocked.contains(label)) return true;
      // /team msg <text> is a directed message to team members.
      return "team".equals(label) && parts.length >= 2 && "msg".equals(parts[1].toLowerCase(Locale.ROOT));
   }

   /** Strips the plugin namespace ("essentials:msg" -> "msg") and lowercases. */
   static String normalizeLabel(String label) {
      if (label == null) return "";
      String normalized = label.toLowerCase(Locale.ROOT);
      int colon = normalized.indexOf(':');
      return colon >= 0 ? normalized.substring(colon + 1) : normalized;
   }
}
