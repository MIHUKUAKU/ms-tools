package local.tradetarget;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class TargetSettingsScreen extends Screen {
    private TargetConfig draft;
    private final Consumer<TargetConfig> save;
    private EditBox item, enchants, min, max;
    private String position;
    private final int tradeCount;
    private boolean minimum, extras, base, potionEffects;
    private String error = "";
    private int left, top, formWidth;

    TargetSettingsScreen(TargetConfig initial, int tradeCount, Consumer<TargetConfig> save) {
        super(Component.literal("交易筛选设置")); this.draft = initial; this.save = save; this.tradeCount = tradeCount;
    }
    @Override protected void init() {
        if (item != null) draft = collect();
        formWidth = Math.min(460, width - 24); left = (width - formWidth) / 2; top = Math.max(4, (height - 238) / 2);
        minimum = draft.minimumLevels(); extras = draft.allowExtras(); base = draft.basePrice();
        potionEffects = draft.potionEffects();
        item = field(left, top + 28, formWidth, "物品", draft.item(), 128);
        enchants = field(left, top + 63, formWidth - 100, potionEffects ? "药水效果及等级" : "附魔及等级", draft.enchantments(), 1024);
        var presetList = addRenderableWidget(Button.builder(Component.literal("预设 / List"), b -> openPresetList())
                .bounds(left + formWidth - 96, top + 63, 96, 20).build());
        presetList.active = isBookOrArrow();
        item.setResponder(value -> presetList.active = isBookOrArrow());
        min = field(left, top + 99, 70, "最低价格", draft.minPrice(), 3);
        max = field(left + 80, top + 99, 70, "最高价格", draft.maxPrice(), 3);
        position = draft.position();
        try {
            var positions = TradePositions.parse(position);
            if (tradeCount > 0 && (positions.all() || positions.last()))
                position = new TradePositions(false, false, positions.resolve(tradeCount)).serialize();
        } catch (IllegalArgumentException e) { position = "none"; error = "旧位置设置无效，请重新选择序号"; }
        String positionLabel = "序号 " + (position.equals("none") ? "未选" : position);
        var positionButton = addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(positionLabel, formWidth - 180)), b -> openPositions())
                .bounds(left + 164, top + 99, formWidth - 164, 20).build());
        positionButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("选择序号（多选） / Select positions\n" + position)));
        int third = (formWidth - 8) / 3;
        addRenderableWidget(Button.builder(levelText(), b -> { minimum = !minimum; b.setMessage(levelText()); })
                .bounds(left, top + 124, third, 20).build());
        addRenderableWidget(Button.builder(extraText(), b -> { extras = !extras; b.setMessage(extraText()); })
                .bounds(left + third + 4, top + 124, third, 20).build());
        addRenderableWidget(Button.builder(priceText(), b -> { base = !base; b.setMessage(priceText()); })
                .bounds(left + (third + 4) * 2, top + 124, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("331 钻石镐"), b -> preset(TargetConfig.defaults()))
                .bounds(left, top + 149, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("附魔书"), b -> { preset(TargetConfig.bookPreset()); openPresetList(); })
                .bounds(left + third + 4, top + 149, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("药水箭"), b -> { preset(TargetConfig.arrowPreset()); openPresetList(); })
                .bounds(left + (third + 4) * 2, top + 149, third, 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存并返回"), b -> saveAndClose())
                .bounds(left, top + 174, formWidth / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(left + formWidth / 2 + 2, top + 174, formWidth / 2 - 2, 20).build());
    }
    private void preset(TargetConfig config) { item = null; draft = config; error = ""; rebuildWidgets(); }
    private void openPositions() {
        try {
            if (tradeCount < 1) throw new IllegalArgumentException("请等待完整交易列表加载后，重新打开设置");
            draft = collect();
            minecraft.gui.setScreen(new PositionPickerScreen(this, tradeCount, TradePositions.parse(position), value -> {
                draft = new TargetConfig(draft.item(), draft.enchantments(), draft.minPrice(), draft.maxPrice(), value,
                        draft.minimumLevels(), draft.allowExtras(), draft.basePrice(), draft.potionEffects());
                item = null; error = "";
            }));
        } catch (IllegalArgumentException e) { error = e.getMessage(); }
    }
    private boolean isBookOrArrow() {
        String value = item.getValue().trim();
        return potionEffects || value.equals("minecraft:enchanted_book") || value.equals("enchanted_book") || value.equals("附魔书");
    }
    private void openPresetList() {
        try {
            draft = collect();
            var options = PresetCatalog.list(minecraft, potionEffects);
            minecraft.gui.setScreen(new PresetPickerScreen(this, options, potionEffects, (option, level, append) -> {
                String text = option.condition(level);
                if (append && !draft.enchantments().isBlank()) {
                    var existing = new java.util.TreeMap<>(GameNames.parse(minecraft, draft).enchantments());
                    existing.put(option.id(), level);
                    text = existing.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining(","));
                }
                draft = new TargetConfig(draft.item(), text, draft.minPrice(), draft.maxPrice(), draft.position(),
                        draft.minimumLevels(), draft.allowExtras(), draft.basePrice(), draft.potionEffects());
                item = null; error = ""; // Keep the selected draft when the parent screen initializes again.
            }));
        } catch (IllegalArgumentException e) { error = e.getMessage(); }
    }
    private EditBox field(int x, int y, int w, String label, String value, int limit) {
        var box = new EditBox(font, x, y, w, 20, Component.literal(label));
        box.setMaxLength(limit); box.setValue(value); return addRenderableWidget(box);
    }
    private Component levelText() { return Component.literal(minimum ? "等级：至少" : "等级：恰好"); }
    private Component extraText() { return Component.literal((potionEffects ? "额外效果：" : "额外附魔：") + (extras ? "允许" : "禁止")); }
    private Component priceText() { return Component.literal(base ? "价格：基础价" : "价格：当前价"); }
    private TargetConfig collect() {
        return new TargetConfig(item.getValue(), enchants.getValue(), min.getValue(), max.getValue(), position, minimum, extras, base, potionEffects);
    }
    private void saveAndClose() {
        try {
            TargetConfig value = collect();
            GameNames.parse(minecraft, value);
            save.accept(value);
            minecraft.gui.setScreen(null);
        } catch (RuntimeException failure) { error = failure.getMessage() == null ? "保存失败，请检查日志" : failure.getMessage(); }
    }
    @Override public void onClose() { minecraft.gui.setScreen(null); }
    @Override public boolean isPauseScreen() { return true; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, potionEffects ? "交易筛选设置 · 药水效果" : "交易筛选设置 · 附魔", width / 2, top, 0xFFFFFFFF);
        g.text(font, "物品：中文名或 ID；留空为任意", left, top + 18, 0xFFFFFFFF);
        g.text(font, potionEffects ? "药水效果：slowness=1；留空不限" : "附魔：耐久=3,效率=3；留空不限制", left, top + 53, 0xFFFFFFFF);
        g.text(font, "最低价格", left, top + 89, 0xFFFFFFFF);
        g.text(font, "最高价格", left + 80, top + 89, 0xFFFFFFFF);
        g.text(font, "序号 / Positions", left + 164, top + 89, 0xFFFFFFFF);
        g.text(font, font.plainSubstrByWidth(error.isEmpty() ? "价格单位：绿宝石；留空不限，上下限相同即指定价格" : error, formWidth), left, top + 201,
                error.isEmpty() ? 0xFFBBBBBB : 0xFFFF6666);
        g.text(font, potionEffects ? "效果等级填 1/2 等数字；暂不区分持续时间。" : "高级交易预览价可能不含未来折扣。", left, top + 214, 0xFFBBBBBB);
        g.text(font, "保存后重新打开村民，点击开始；不会自动购买。", left, top + 227, 0xFFBBBBBB);
    }
}
