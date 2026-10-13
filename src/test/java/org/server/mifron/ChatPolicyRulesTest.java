package org.server.mifron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatPolicyRulesTest {
   @Test
   void blocksVanillaAndEssentialsPrivateMessages() {
      for (String line : new String[] {
            "/tell alice hi", "/msg alice hi", "/w alice hi", "/whisper alice hi",
            "/message alice hi", "/m alice hi", "/t alice hi", "/r hi", "/reply hi",
            "/mail send alice hi", "/em alice hi", "/email alice hi",
            "tell alice", "/TELL alice hi"}) {
         assertTrue(ChatPolicyRules.isPrivateMessageCommand(line, Set.of()), line);
      }
   }

   @Test
   void stripsPluginNamespace() {
      assertTrue(ChatPolicyRules.isPrivateMessageCommand("/essentials:msg alice hi", Set.of()));
      assertTrue(ChatPolicyRules.isPrivateMessageCommand("/minecraft:tell alice hi", Set.of()));
      assertFalse(ChatPolicyRules.isPrivateMessageCommand("/mifron:status alice reset", Set.of()));
   }

   @Test
   void blocksTeamMsgOnly() {
      assertTrue(ChatPolicyRules.isPrivateMessageCommand("/team msg hi team", Set.of()));
      assertTrue(ChatPolicyRules.isPrivateMessageCommand("/team MSG hello", Set.of()));
      assertFalse(ChatPolicyRules.isPrivateMessageCommand("/team join red", Set.of()));
   }

   @Test
   void allowsPublicAndAdminCommands() {
      for (String line : new String[] {
            "hello everyone", "/me waves", "/say hi", "/mifron vote", "/shop", "/trade alice", null, "   ", "/"}) {
         assertFalse(ChatPolicyRules.isPrivateMessageCommand(line, Set.of()), String.valueOf(line));
      }
   }

   @Test
   void configExtrasExtendTheBlockList() {
      assertFalse(ChatPolicyRules.isPrivateMessageCommand("/pm alice hi", Set.of()));
      assertTrue(ChatPolicyRules.isPrivateMessageCommand("/pm alice hi", Set.of("pm")));
      assertTrue(ChatPolicyRules.isPrivateMessageCommand("/essentials:pm alice hi", Set.of("pm")));
   }

   @Test
   void normalizesLabels() {
      assertEquals("msg", ChatPolicyRules.normalizeLabel("ESSENTIALS:MSG"));
      assertEquals("tell", ChatPolicyRules.normalizeLabel("tell"));
      assertEquals("", ChatPolicyRules.normalizeLabel(null));
   }
}
