package local.tradetarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One row per effect/enchantment, not one row per level. */
public record PresetOption(String id, String chinese, String english, List<Integer> levels) {
    public PresetOption {
        levels = levels.stream().distinct().sorted().toList();
        if (levels.isEmpty() || levels.getFirst() < 1) throw new IllegalArgumentException("Missing preset levels");
    }
    public String label() { return chinese + " / " + english + " (" + compactLevels(levels) + ")"; }
    public boolean contains(String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        return chinese.toLowerCase(Locale.ROOT).contains(q) || english.toLowerCase(Locale.ROOT).contains(q) || id.contains(q);
    }
    public int nextLevel(int current, int direction) {
        int index = levels.indexOf(current);
        if (index < 0) return levels.getFirst();
        return levels.get(Math.max(0, Math.min(levels.size() - 1, index + direction)));
    }
    public String condition(int level) {
        if (!levels.contains(level)) throw new IllegalArgumentException("该等级不在预设可选范围内");
        return id + "=" + level;
    }
    public static String compactLevels(List<Integer> input) {
        var values = input.stream().distinct().sorted().toList();
        var parts = new ArrayList<String>();
        for (int start = 0; start < values.size();) {
            int end = start;
            while (end + 1 < values.size() && values.get(end + 1) == values.get(end) + 1) end++;
            parts.add(start == end ? roman(values.get(start)) : roman(values.get(start)) + "–" + roman(values.get(end)));
            start = end + 1;
        }
        return String.join(", ", parts);
    }
    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> Integer.toString(n);
        };
    }
}
