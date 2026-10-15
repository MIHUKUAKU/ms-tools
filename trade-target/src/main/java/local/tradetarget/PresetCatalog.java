package local.tradetarget;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.PotionTags;

final class PresetCatalog {
    private static final Properties NAMES = readNames();
    private static Properties readNames() {
        var p = new Properties();
        try (var stream = PresetCatalog.class.getResourceAsStream("/trade-target-names.properties")) {
            if (stream != null) p.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) { throw new IllegalStateException("无法读取双语预设名称", e); }
        return p;
    }
    static String name(String lang, String key, String fallback) { return NAMES.getProperty(lang + "." + key, fallback); }
    static List<PresetOption> list(Minecraft client, boolean effects) {
        if (client.level == null) throw new IllegalArgumentException("请先进入存档");
        var result = new ArrayList<PresetOption>();
        if (!effects) {
            var registry = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var tag = registry.get(EnchantmentTags.TRADEABLE).orElseThrow(() -> new IllegalArgumentException("未同步可交易附魔标签"));
            tag.stream().forEach(holder -> {
                String id = holder.getRegisteredName();
                String key = "enchantment." + id.replace(':', '.');
                var enchant = holder.value();
                result.add(new PresetOption(id, name("zh", key, enchant.description().getString()), name("en", key, id),
                        IntStream.rangeClosed(enchant.getMinLevel(), enchant.getMaxLevel()).boxed().toList()));
            });
        } else {
            var levels = new HashMap<String, TreeSet<Integer>>();
            var displayNames = new HashMap<String, String>();
            // Aggregate levels from the world's actual tradeable potion tag. This
            // avoids inventing nonexistent intermediate levels (e.g. slowness).
            var tag = BuiltInRegistries.POTION.get(PotionTags.TRADEABLE)
                    .orElseThrow(() -> new IllegalArgumentException("未同步可交易药水标签"));
            tag.stream().forEach(holder -> {
                for (var effect : holder.value().getEffects()) {
                    String id = effect.getEffect().getRegisteredName();
                    levels.computeIfAbsent(id, ignored -> new TreeSet<>()).add(effect.getAmplifier() + 1);
                    displayNames.put(id, effect.getEffect().value().getDisplayName().getString());
                }
            });
            for (var entry : levels.entrySet()) {
                String key = "effect." + entry.getKey().replace(':', '.');
                result.add(new PresetOption(entry.getKey(), name("zh", key, displayNames.get(entry.getKey())),
                        name("en", key, entry.getKey()), List.copyOf(entry.getValue())));
            }
        }
        result.sort(Comparator.comparing(PresetOption::english, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }
}
