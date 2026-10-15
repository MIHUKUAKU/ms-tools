package local.tradetarget;

import java.util.Map;
import java.util.List;

public final class TargetRule {
    private TargetRule() {}
    public static boolean matches(String item, Map<String, Integer> enchants) {
        return Criteria.defaults().matches(new Offer(item, enchants, 0));
    }

    public record Offer(String item, Map<String, Integer> enchantments, int emeralds, Map<String, Integer> effects) {
        public Offer { enchantments = Map.copyOf(enchantments); effects = Map.copyOf(effects); }
        public Offer(String item, Map<String, Integer> enchantments, int emeralds) {
            this(item, enchantments, emeralds, Map.of());
        }
    }

    /** Any one selected position may satisfy the target. */
    public record Criteria(String item, Map<String, Integer> enchantments, boolean minimumLevels,
                           boolean allowExtras, int minPrice, int maxPrice, TradePositions positions, boolean basePrice, boolean potionEffects) {
        public Criteria(String item, Map<String, Integer> enchantments, boolean minimumLevels,
                        boolean allowExtras, int minPrice, int maxPrice, int position, boolean basePrice, boolean potionEffects) {
            this(item, enchantments, minimumLevels, allowExtras, minPrice, maxPrice, TradePositions.legacy(position), basePrice, potionEffects);
        }
        public Criteria(String item, Map<String, Integer> enchantments, boolean minimumLevels,
                        boolean allowExtras, int minPrice, int maxPrice, int position, boolean basePrice) {
            this(item, enchantments, minimumLevels, allowExtras, minPrice, maxPrice, position, basePrice, false);
        }
        public Criteria {
            if (item == null || (!item.equals("*") && !item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
                throw new IllegalArgumentException("物品 ID 格式错误");
            enchantments = Map.copyOf(enchantments);
            for (var entry : enchantments.entrySet()) {
                if (!entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || entry.getValue() < 1 || entry.getValue() > (potionEffects ? 256 : 255))
                    throw new IllegalArgumentException("附魔或效果 ID/等级错误");
            }
            if (potionEffects && !item.equals("minecraft:tipped_arrow"))
                throw new IllegalArgumentException("药水箭模式的物品必须是 minecraft:tipped_arrow");
            if (minPrice < -1 || maxPrice < -1 || minPrice > 64 || maxPrice > 64
                    || (minPrice >= 0 && maxPrice >= 0 && minPrice > maxPrice))
                throw new IllegalArgumentException("价格应为 0–64，且最低价不能高于最高价");
            if (positions == null || positions.isEmpty()) throw new IllegalArgumentException("请至少选择一个交易序号");
            if (item.equals("*") && enchantments.isEmpty() && minPrice < 0 && maxPrice < 0)
                throw new IllegalArgumentException("请至少指定物品、附魔或价格条件");
        }
        public static Criteria defaults() {
            return new Criteria("minecraft:diamond_pickaxe", Map.of("minecraft:unbreaking", 3,
                    "minecraft:efficiency", 3, "minecraft:silk_touch", 1), false, false, -1, -1, -1, true);
        }
        public boolean matches(Offer offer) {
            if (!item.equals("*") && !item.equals(offer.item)) return false;
            if ((minPrice >= 0 || maxPrice >= 0) && offer.emeralds < 0) return false;
            if (minPrice >= 0 && offer.emeralds < minPrice) return false;
            if (maxPrice >= 0 && offer.emeralds > maxPrice) return false;
            // Potion effects and enchantments are independent data sources. Never
            // satisfy a potion condition using enchantments, or the reverse.
            Map<String, Integer> actualLevels = potionEffects ? offer.effects : offer.enchantments;
            for (var wanted : enchantments.entrySet()) {
                int actual = actualLevels.getOrDefault(wanted.getKey(), 0);
                if (minimumLevels ? actual < wanted.getValue() : actual != wanted.getValue()) return false;
            }
            // An empty enchantment field means no enchantment filter.
            return enchantments.isEmpty() || allowExtras || actualLevels.keySet().equals(enchantments.keySet());
        }
        public int findMatch(List<Offer> offers) {
            if (offers.isEmpty()) return -1;
            for (int index = 0; index < offers.size(); index++)
                if (positions.includes(index + 1, offers.size()) && matches(offers.get(index))) return index;
            return -1;
        }
    }
}
