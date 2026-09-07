package org.server.mifron;

/**
 * EnchantDisplayUtil
 * --------------------
 * Formats enchantment display names so that max-level enchantments show
 * a "-MAX" suffix, e.g. "鋭さ X -MAX" / "拡散 V -MAX".
 *
 * Wiring: wherever the plugin builds enchantment lore/display text
 * (likely inside AdvancedAnvilFeature.java), replace the numeral
 * formatting call with:
 *   EnchantDisplayUtil.format(japaneseName, level, maxLevel)
 */
public final class EnchantDisplayUtil {

    private static final String[] ROMAN = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private EnchantDisplayUtil() {}

    public static String toRoman(int level) {
        if (level <= 0) return "";
        if (level <= ROMAN.length) return ROMAN[level - 1];
        return String.valueOf(level);
    }

    /**
     * @param japaneseName localized enchantment name, e.g. "鋭さ" or "拡散"
     * @param level        current enchantment level
     * @param maxLevel     the enchantment's maximum allowed level
     * @return formatted string, appending " -MAX" when level == maxLevel
     */
    public static String format(String japaneseName, int level, int maxLevel) {
        String roman = toRoman(level);
        if (level >= maxLevel) {
            return japaneseName + " " + roman + " -MAX";
        }
        return japaneseName + " " + roman;
    }
}
