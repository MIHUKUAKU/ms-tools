package local.tradetarget;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** One-based positions. Legacy 'any' and 'last' remain readable. */
public record TradePositions(boolean all, boolean last, Set<Integer> numbers) {
    public TradePositions {
        numbers = Set.copyOf(numbers);
        if (numbers.stream().anyMatch(n -> n < 1 || n > 1000)) throw new IllegalArgumentException("交易序号必须为 1–1000");
        if (all && (last || !numbers.isEmpty())) throw new IllegalArgumentException("全部位置不能混合指定序号");
    }
    public static TradePositions legacy(int value) {
        if (value == 0) return new TradePositions(true, false, Set.of());
        if (value == -1) return new TradePositions(false, true, Set.of());
        return new TradePositions(false, false, Set.of(value));
    }
    public static TradePositions parse(String value) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (List.of("", "0", "any", "all", "任意", "全部").contains(text)) return legacy(0);
        if (List.of("-1", "last", "最后").contains(text)) return legacy(-1);
        if (text.equals("none")) return new TradePositions(false, false, Set.of());
        var result = new TreeSet<Integer>();
        for (String token : text.replace('，', ',').split(",", -1)) {
            try { result.add(Integer.parseInt(token.trim())); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("无效交易序号，请在界面中重新选择"); }
        }
        return new TradePositions(false, false, result);
    }
    public boolean isEmpty() { return !all && !last && numbers.isEmpty(); }
    public boolean includes(int number, int count) {
        return number >= 1 && number <= count && (all || numbers.contains(number) || (last && number == count));
    }
    public Set<Integer> resolve(int count) {
        if (count < 1 || count > 1000) throw new IllegalArgumentException("交易数量尚未加载或超出支持范围");
        if (all) return IntStream.rangeClosed(1, count).boxed().collect(Collectors.toSet());
        var result = new TreeSet<>(numbers);
        if (last) result.add(count);
        return result;
    }
    public String serialize() {
        if (all) return "any";
        if (last && numbers.isEmpty()) return "last";
        if (last) throw new IllegalArgumentException("请将最后一项转换为具体序号");
        return numbers.isEmpty() ? "none" : numbers.stream().sorted().map(Object::toString).collect(Collectors.joining(","));
    }
}
