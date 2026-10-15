package local.tradetarget;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class PresetPickerScreen extends Screen {
    interface Selection { void apply(PresetOption option, int level, boolean append); }
    private final Screen parent;
    private final List<PresetOption> options;
    private final Selection selection;
    private final List<Button> rows = new ArrayList<>();
    private EditBox search;
    private Button previous, next, minus, plus, levelButton, apply;
    private PresetOption selected;
    private int level, page, pages = 1, pageSize, left, formWidth, navY;
    private boolean append;
    private String query = "", error = "";

    PresetPickerScreen(Screen parent, List<PresetOption> options, boolean effects, Selection selection) {
        super(Component.literal(effects ? "药水效果 / Potion effects" : "附魔书 / Enchanted books"));
        this.parent = parent; this.options = options; this.selection = selection;
    }
    @Override protected void init() {
        rows.clear();
        formWidth = Math.min(620, width - 24); left = (width - formWidth) / 2;
        pageSize = Math.max(1, Math.min(12, (height - 174) / 22));
        navY = 60 + pageSize * 22 + 4;
        search = addRenderableWidget(new EditBox(font, left, 32, formWidth, 20, Component.literal("搜索 / Search")));
        search.setMaxLength(120); search.setValue(query);
        search.setHint(Component.literal("搜索中文 / English / ID"));
        previous = addRenderableWidget(Button.builder(Component.literal("上一页 / Prev"), b -> { page--; refresh(); })
                .bounds(left, navY, 100, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("下一页 / Next"), b -> { page++; refresh(); })
                .bounds(left + formWidth - 100, navY, 100, 20).build());
        minus = addRenderableWidget(Button.builder(Component.literal("−"), b -> { level = selected.nextLevel(level, -1); updateSelection(); })
                .bounds(left, navY + 45, 26, 20).build());
        levelButton = addRenderableWidget(Button.builder(Component.literal("等级 / Lv"), b -> {})
                .bounds(left + 28, navY + 45, 66, 20).build());
        levelButton.active = false;
        plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> { level = selected.nextLevel(level, 1); updateSelection(); })
                .bounds(left + 96, navY + 45, 26, 20).build());
        addRenderableWidget(Button.builder(modeText(), b -> { append = !append; b.setMessage(modeText()); })
                .bounds(left + 130, navY + 45, formWidth - 130, 20).build());
        apply = addRenderableWidget(Button.builder(Component.literal("使用 / Use"), b -> {
            try { selection.apply(selected, level, append); minecraft.gui.setScreen(parent); }
            catch (IllegalArgumentException e) { error = e.getMessage(); }
        }).bounds(left, navY + 70, formWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回 / Back"), b -> onClose())
                .bounds(left + formWidth / 2 + 2, navY + 70, formWidth / 2 - 2, 20).build());
        search.setResponder(value -> { query = value; page = 0; refresh(); });
        refresh();
    }
    private Component modeText() { return Component.literal(append ? "追加 / Add" : "替换 / Replace"); }
    private void refresh() {
        for (var row : rows) removeWidget(row);
        rows.clear();
        List<PresetOption> filtered = options.stream().filter(option -> option.contains(query)).toList();
        pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pages - 1));
        previous.active = page > 0; next.active = page < pages - 1;
        int from = page * pageSize;
        for (int index = from; index < Math.min(from + pageSize, filtered.size()); index++) {
            PresetOption option = filtered.get(index);
            String label = (option.equals(selected) ? "✓ " : "") + option.label();
            var row = Button.builder(Component.literal(font.plainSubstrByWidth(label, formWidth - 18)), b -> {
                selected = option; level = option.levels().getLast(); error = ""; refresh();
            }).bounds(left, 60 + (index - from) * 22, formWidth, 20).build();
            row.setTooltip(Tooltip.create(Component.literal(option.label() + "\n" + option.id())));
            rows.add(addRenderableWidget(row));
        }
        updateSelection();
    }
    private void updateSelection() {
        minus.active = selected != null && level > selected.levels().getFirst();
        plus.active = selected != null && level < selected.levels().getLast();
        apply.active = selected != null;
        levelButton.setMessage(Component.literal(selected == null ? "等级 / Lv" : "Lv " + PresetOption.roman(level)));
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        g.text(font, "每种一项，括号为可选等级 / One row per type", left, 21, 0xFFBBBBBB);
        g.centeredText(font, (page + 1) + " / " + pages, width / 2, navY + 6, 0xFFFFFFFF);
        String status = !error.isEmpty() ? error : selected == null ? "请选择 / Select an option" : selected.label();
        g.text(font, font.plainSubstrByWidth(status, formWidth), left, navY + 29, error.isEmpty() ? 0xFFFFFFFF : 0xFFFF7777);
        g.text(font, font.plainSubstrByWidth("等级来自可交易标签；实际取决于村民 / Trade rules still apply", formWidth), left, navY + 95, 0xFFBBBBBB);
    }
}
