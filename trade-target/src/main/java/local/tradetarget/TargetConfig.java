package local.tradetarget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.function.UnaryOperator;

public record TargetConfig(String item, String enchantments, String minPrice, String maxPrice,
                           String position, boolean minimumLevels, boolean allowExtras, boolean basePrice, boolean potionEffects) {
    public TargetConfig(String item, String enchantments, String minPrice, String maxPrice,
                        String position, boolean minimumLevels, boolean allowExtras, boolean basePrice) {
        this(item, enchantments, minPrice, maxPrice, position, minimumLevels, allowExtras, basePrice, false);
    }
    public static TargetConfig defaults() {
        return new TargetConfig("minecraft:diamond_pickaxe", "unbreaking=3,efficiency=3,silk_touch=1", "", "", "最后", false, false, true);
    }
    public static TargetConfig bookPreset() {
        return new TargetConfig("minecraft:enchanted_book", "mending=1", "", "", "任意", false, true, true, false);
    }
    public static TargetConfig arrowPreset() {
        return new TargetConfig("minecraft:tipped_arrow", "slowness=1", "", "", "任意", false, true, true, true);
    }
    public TargetRule.Criteria parse(UnaryOperator<String> itemName, UnaryOperator<String> enchantName) {
        if (potionEffects) throw new IllegalArgumentException("药水箭必须使用药水效果解析器");
        return parse(itemName, enchantName, name -> { throw new IllegalArgumentException("药水效果解析器不可用"); });
    }
    public TargetRule.Criteria parse(UnaryOperator<String> itemName, UnaryOperator<String> enchantName, UnaryOperator<String> effectName) {
        String label = potionEffects ? "药水效果" : "附魔";
        var levels = new LinkedHashMap<String, Integer>();
        if (!enchantments.isBlank()) {
            for (String part : enchantments.replace('，', ',').replace('；', ',').replace(';', ',').split(",", -1)) {
                String[] pair = part.trim().replace('＝', '=').split("=", -1);
                if (pair.length != 2) throw new IllegalArgumentException(label + "格式：名称=等级，多项用逗号分隔");
                String id = (potionEffects ? effectName : enchantName).apply(pair[0].trim());
                int level = number(pair[1].trim(), label + "等级");
                if (levels.putIfAbsent(id, level) != null) throw new IllegalArgumentException(label + "重复：" + id);
            }
        }
        return new TargetRule.Criteria(item.isBlank() || item.trim().equals("*") ? "*" : itemName.apply(item.trim()), levels,
                minimumLevels, allowExtras, price(minPrice), price(maxPrice), TradePositions.parse(position), basePrice, potionEffects);
    }
    private static int price(String text) { return text.isBlank() ? -1 : number(text.trim(), "价格"); }
    private static int number(String text, String label) {
        try { return Integer.parseInt(text); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(label + "必须是整数"); }
    }
    public static TargetConfig load(Path path) throws IOException {
        if (!Files.exists(path)) return defaults();
        var p = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(reader); }
        var d = defaults();
        return new TargetConfig(p.getProperty("item", d.item), p.getProperty("enchantments", d.enchantments),
                p.getProperty("minPrice", ""), p.getProperty("maxPrice", ""), p.getProperty("position", d.position),
                bool(p, "minimumLevels", false), bool(p, "allowExtras", false), bool(p, "basePrice", true), bool(p, "potionEffects", false));
    }
    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key, Boolean.toString(fallback));
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("无效配置：" + key);
        return Boolean.parseBoolean(value);
    }
    public void save(Path path) throws IOException {
        var p = new Properties();
        p.setProperty("item", item); p.setProperty("enchantments", enchantments);
        p.setProperty("minPrice", minPrice); p.setProperty("maxPrice", maxPrice); p.setProperty("position", position);
        p.setProperty("minimumLevels", Boolean.toString(minimumLevels));
        p.setProperty("allowExtras", Boolean.toString(allowExtras)); p.setProperty("basePrice", Boolean.toString(basePrice));
        p.setProperty("potionEffects", Boolean.toString(potionEffects));
        Files.createDirectories(path.getParent());
        // CREATE_NEW prevents overwriting an existing file; this non-secret local
        // settings file needs collision safety, not a cryptographic random source.
        Path temporary = Files.createFile(path.resolveSibling(path.getFileName() + "." + Long.toUnsignedString(System.nanoTime()) + ".tmp"));
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { p.store(writer, "Trade Target settings"); }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
