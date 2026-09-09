package org.server.mifron;

import java.util.regex.Pattern;

abstract class MifronPart1 extends MifronBase {
   protected static final Pattern SAFE_CONFIG_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
}
