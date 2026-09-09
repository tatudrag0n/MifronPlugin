package org.server.mifron;

import java.util.List;
import org.bukkit.advancement.Advancement;

abstract class MifronPart12x2 extends MifronPart12x1 {
   protected int advancementPathOrder(Advancement advancement) {
      String key = advancement.getKey().getKey();
      String category = key.contains("/") ? key.substring(0, key.indexOf('/')) : key;
      String path = key.contains("/") ? key.substring(key.indexOf('/') + 1) : key;
      List<String> ordered = switch (category) {
         case "story" -> List.of("root", "mine_stone", "upgrade_tools", "smelt_iron", "obtain_armor", "lava_bucket",
            "iron_tools", "deflect_arrow", "form_obsidian", "mine_diamond", "enter_the_nether", "shiny_gear",
            "enchant_item", "cure_zombie_villager", "follow_ender_eye", "enter_the_end");
         case "nether" -> List.of("root", "return_to_sender", "find_bastion", "obtain_ancient_debris", "fast_travel",
            "find_fortress", "obtain_crying_obsidian", "distract_piglin", "ride_strider", "uneasy_alliance",
            "loot_bastion", "use_lodestone", "netherite_armor", "get_wither_skull", "obtain_blaze_rod",
            "charge_respawn_anchor", "ride_strider_in_overworld_lava", "explore_nether", "summon_wither",
            "brew_potion", "create_beacon", "all_potions", "create_full_beacon", "all_effects");
         case "end" -> List.of("root", "kill_dragon", "dragon_egg", "enter_end_gateway", "respawn_dragon",
            "dragon_breath", "find_end_city", "elytra", "levitate");
         case "adventure" -> List.of("root", "voluntary_exile", "spyglass_at_parrot", "kill_a_mob", "trade",
            "trim_with_any_armor_pattern", "honey_block_slide", "ol_betsy", "lightning_rod_with_villager_no_fire",
            "fall_from_world_height", "avoid_vibration", "sleep_in_bed", "hero_of_the_village", "spyglass_at_ghast",
            "throw_trident", "shoot_arrow", "kill_all_mobs", "totem_of_undying", "summon_iron_golem",
            "trade_at_world_height", "two_birds_one_arrow", "whos_the_pillager_now", "arbalistic", "adventuring_time",
            "play_jukebox_in_meadows", "walk_on_powder_snow_with_leather_boots", "spyglass_at_dragon",
            "very_very_frightening", "sniper_duel", "bullseye");
         case "husbandry" -> List.of("root", "safely_harvest_honey", "breed_an_animal", "ride_a_boat_with_a_goat",
            "tame_an_animal", "make_a_sign_glow", "fishy_business", "silk_touch_nest", "plant_seed", "wax_on",
            "bred_all_animals", "allay_deliver_item_to_player", "complete_catalogue", "tactical_fishing",
            "balanced_diet", "obtain_netherite_hoe", "axolotl_in_a_bucket", "wax_off", "kill_axolotl_target",
            "frogspawn", "froglights", "allay_deliver_cake_to_note_block", "leash_all_frog_variants",
            "feed_snifflet", "plant_any_sniffer_seed");
         default -> List.of("root");
      };
      int index = ordered.indexOf(path);
      return index >= 0 ? index : (path.equals("root") ? 0 : 1000 + this.advancementDifficulty(advancement));
   }
}
