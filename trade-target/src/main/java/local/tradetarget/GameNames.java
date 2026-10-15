package local.tradetarget;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class GameNames {
    static TargetRule.Criteria parse(Minecraft client, TargetConfig config) {
        if (client.level == null) throw new IllegalArgumentException("请进入存档后设置筛选条件");
        var itemNames = new HashMap<String, String>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            String id = entry.getKey().identifier().toString();
            itemNames.put(entry.getValue().getName(new ItemStack(entry.getValue())).getString(), id);
        }
        var enchantNames = new HashMap<String, String>();
        var registry = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var entry : registry.entrySet()) enchantNames.put(entry.getValue().description().getString(), entry.getKey().identifier().toString());
        for (var entry : registry.entrySet()) {
            String id = entry.getKey().identifier().toString();
            String key = "enchantment." + id.replace(':', '.');
            enchantNames.put(PresetCatalog.name("zh", key, id), id);
            enchantNames.put(PresetCatalog.name("en", key, id), id);
        }
        var effectNames = new HashMap<String, String>();
        for (var entry : BuiltInRegistries.MOB_EFFECT.entrySet())
            effectNames.put(entry.getValue().getDisplayName().getString(), entry.getKey().identifier().toString());
        for (var entry : BuiltInRegistries.MOB_EFFECT.entrySet()) {
            String id = entry.getKey().identifier().toString();
            String key = "effect." + id.replace(':', '.');
            effectNames.put(PresetCatalog.name("zh", key, id), id);
            effectNames.put(PresetCatalog.name("en", key, id), id);
        }
        itemNames.put("药水箭", "minecraft:tipped_arrow");
        var criteria = config.parse(name -> resolve(name, itemNames, id -> BuiltInRegistries.ITEM.containsKey(id)),
                name -> resolve(name, enchantNames, registry::containsKey),
                name -> resolve(name, effectNames, BuiltInRegistries.MOB_EFFECT::containsKey));
        if (criteria.potionEffects()) return criteria;
        if (criteria.item().equals("minecraft:tipped_arrow"))
            throw new IllegalArgumentException("请点击「药水箭」切换为药水效果筛选");
        for (var wanted : criteria.enchantments().entrySet()) {
            var enchantment = registry.getValue(Identifier.parse(wanted.getKey()));
            if (wanted.getValue() > enchantment.getMaxLevel())
                throw new IllegalArgumentException(enchantment.description().getString() + "最高等级为 " + enchantment.getMaxLevel());
        }
        return criteria;
    }
    private static String resolve(String text, Map<String, String> names, java.util.function.Predicate<Identifier> exists) {
        String id = names.getOrDefault(text, text.contains(":") ? text : "minecraft:" + text);
        if (!names.containsKey(text)) {
            for (var entry : names.entrySet()) if (entry.getKey().equalsIgnoreCase(text)) { id = entry.getValue(); break; }
        }
        Identifier key = Identifier.tryParse(id);
        if (key == null || !exists.test(key)) throw new IllegalArgumentException("未知物品、附魔或效果：" + text);
        return id;
    }
}
