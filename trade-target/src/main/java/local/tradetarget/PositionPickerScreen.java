package local.tradetarget;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class PositionPickerScreen extends Screen {
    private final Screen parent;
    private final int tradeCount;
    private final int displayCount;
    private final TreeSet<Integer> selected;
    private final Consumer<String> accept;
    private int page, pages, left, formWidth, footerY;
    PositionPickerScreen(Screen parent, int tradeCount, TradePositions initial, Consumer<String> accept) {
        super(Component.literal("交易序号（多选） / Trade positions"));
        this.parent = parent; this.tradeCount = tradeCount; this.accept = accept;
        selected = new TreeSet<>(initial.resolve(tradeCount));
        displayCount = Math.max(tradeCount, selected.isEmpty() ? 0 : selected.last());
    }
    @Override protected void init() {
        formWidth = Math.min(560, width - 24); left = (width - formWidth) / 2;
        int columns = Math.max(3, Math.min(8, formWidth / 60));
        int rows = Math.max(1, (height - 158) / 26);
        int perPage = columns * rows;
        pages = Math.max(1, (displayCount + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int cellWidth = formWidth / columns;
        for (int i = 0; i < perPage && page * perPage + i < displayCount; i++) {
            int number = page * perPage + i + 1;
            String label = (selected.contains(number) ? "[✓] " : "[ ] ") + number + (number > tradeCount ? "*" : "");
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                if (!selected.remove(number)) selected.add(number);
                rebuildWidgets();
            }).bounds(left + (i % columns) * cellWidth, 50 + (i / columns) * 26, cellWidth - 4, 22).build());
        }
        footerY = 50 + rows * 26;
        var prev = addRenderableWidget(Button.builder(Component.literal("上一页 / Prev"), b -> { page--; rebuildWidgets(); })
                .bounds(left, footerY, 100, 20).build());
        prev.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal("下一页 / Next"), b -> { page++; rebuildWidgets(); })
                .bounds(left + formWidth - 100, footerY, 100, 20).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal("全选 / All"), b -> {
            selected.clear(); for (int n = 1; n <= tradeCount; n++) selected.add(n); rebuildWidgets();
        }).bounds(left, footerY + 25, formWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("清空 / Clear"), b -> { selected.clear(); rebuildWidgets(); })
                .bounds(left + formWidth / 2 + 2, footerY + 25, formWidth / 2 - 2, 20).build());
        var confirm = addRenderableWidget(Button.builder(Component.literal("确认 / Confirm"), b -> {
            accept.accept(new TradePositions(false, false, selected).serialize()); minecraft.gui.setScreen(parent);
        }).bounds(left, footerY + 50, formWidth / 2 - 2, 20).build());
        confirm.active = !selected.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("取消 / Cancel"), b -> onClose())
                .bounds(left + formWidth / 2 + 2, footerY + 50, formWidth / 2 - 2, 20).build());
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractRenderState(g, x, y, delta);
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        g.text(font, "从上往下编号，包含高级预览 / Top to bottom", left, 25, 0xFFBBBBBB);
        g.centeredText(font, (page + 1) + " / " + pages, width / 2, footerY + 6, 0xFFFFFFFF);
        g.text(font, "已选 / Selected: " + selected.size() + "   当前交易 / Trades: " + tradeCount, left, footerY + 77, 0xFFFFFFFF);
        g.text(font, "任一所选项命中即停止；* 为当前不存在的旧序号", left, footerY + 90, 0xFFBBBBBB);
    }
}
