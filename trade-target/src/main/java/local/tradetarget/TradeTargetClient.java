package local.tradetarget;

import de.maxhenkel.tradecycling.TradeCyclingMod;
import de.maxhenkel.tradecycling.mixin.MerchantMenuAccessor;
import java.util.HashMap;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffers;
import net.ramixin.visibletraders.LockedTradeData;
import net.ramixin.visibletraders.ducks.VillagerDuck;
import net.ramixin.visibletraders.ducks.ClientMerchantMenuDuck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local singleplayer only. Uses the existing reroll operation, never edits an offer. */
public final class TradeTargetClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("trade-target-331");
    private Session session;
    private MerchantScreen activeScreen;
    private CompletableFuture<Result> pending;
    private Button button;
    private boolean oldPauseOnLostFocus;
    private TargetConfig config = TargetConfig.defaults();
    private boolean configValid = true;
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("trade-target.properties");

    @Override public void onInitializeClient() {
        try { config = TargetConfig.load(configPath); }
        catch (Exception failure) { configValid = false; LOG.error("Failed to load trade target settings; reroll disabled until settings saved", failure); }
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof MerchantScreen merchant)) return;
            button = Button.builder(Component.literal("筛选：开始"), b -> toggle(client, merchant))
                    .bounds(Math.max(2, width / 2 - 124), Math.max(2, height / 2 - 145), 120, 20).build();
            Screens.getWidgets(screen).add(button);
            Screens.getWidgets(screen).add(Button.builder(Component.literal("筛选设置"), b -> {
                stop(client, "已停止：打开筛选设置");
                ClientMerchantMenuDuck.of(merchant.getMenu()).visibleTraders$enableCombinedOffers();
                int tradeCount = merchant.getMenu().getOffers().size();
                if (client.player != null) client.player.closeContainer();
                client.gui.setScreen(new TargetSettingsScreen(config, tradeCount, value -> {
                    try { value.save(configPath); }
                    catch (java.io.IOException e) { throw new IllegalArgumentException("配置保存失败：" + e.getMessage(), e); }
                    config = value; configValid = true;
                    message(client, "筛选条件已保存；重新打开村民交易后点击开始。");
                }));
            }).bounds(Math.max(126, width / 2 + 4), Math.max(2, height / 2 - 145), 120, 20).build());
            ScreenEvents.remove(screen).register(removed -> {
                if (removed == activeScreen) stop(client, "已停止：交易窗口关闭");
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LOG.info("Trade Target 331 ready (singleplayer; explicit start required)");
    }

    private void toggle(Minecraft client, MerchantScreen screen) {
        if (session != null) { stop(client, "已手动停止"); return; }
        if (client.player == null || client.getSingleplayerServer() == null) {
            message(client, "只支持本机单人存档"); return;
        }
        if (!configValid) { message(client, "配置文件读取失败，请先打开筛选设置并保存"); return; }
        TargetRule.Criteria criteria;
        try { criteria = GameNames.parse(client, config); }
        catch (IllegalArgumentException e) { message(client, "筛选条件错误：" + e.getMessage()); return; }
        activeScreen = screen;
        session = new Session(client.getSingleplayerServer(), client.player.getUUID(), screen.getMenu().containerId, criteria);
        pending = null;
        oldPauseOnLostFocus = client.options.pauseOnLostFocus;
        // Runtime only: restore on stop; never save an options-file change.
        client.options.pauseOnLostFocus = false;
        message(client, "已开始；可切到其他窗口。关闭交易界面或点击停止即可中止。");
        message(client, "条件：" + config.item() + (criteria.potionEffects() ? " | 药水效果：" : " | 附魔：") + config.enchantments() + " | 位置：" + config.position()
                + " | 价格：" + (criteria.basePrice() ? "基础价 " : "当前价 ")
                + (criteria.minPrice() < 0 ? "不限" : criteria.minPrice()) + " 至 "
                + (criteria.maxPrice() < 0 ? "不限" : criteria.maxPrice()));
        button.setMessage(Component.literal("停止 (0)"));
    }

    private void tick(Minecraft client) {
        Session current = session;
        if (current == null) return;
        if (client.gui.screen() != activeScreen || client.player == null
                || client.getSingleplayerServer() != current.server
                || client.player.containerMenu != activeScreen.getMenu()) {
            stop(client, "已停止：交易对象或游戏状态改变"); return;
        }
        if (pending != null) {
            if (!pending.isDone()) return;
            Result result;
            try { result = pending.join(); }
            catch (RuntimeException error) {
                LOG.error("Stopped after reroll failure", error);
                stop(client, "发生错误，已停止；请查看日志"); return;
            }
            pending = null;
            button.setMessage(Component.literal("停止 (" + result.attempts + ")"));
            if (result.stopReason != null) { stop(client, result.stopReason); return; }
        }
        // Exactly one server task outstanding. Server data is never read on the client thread.
        pending = current.server.submit(current::step);
    }

    private void stop(Minecraft client, String reason) {
        if (session == null) return;
        session.cancelled = true;
        session = null;
        pending = null;
        activeScreen = null;
        client.options.pauseOnLostFocus = oldPauseOnLostFocus;
        if (button != null) button.setMessage(Component.literal(reason.startsWith("命中") ? "筛选：已命中" : "筛选：开始"));
        message(client, reason);
    }

    private static void message(Minecraft client, String text) {
        LOG.info(text);
        if (client.player != null) client.player.sendSystemMessage(Component.literal("[331] " + text));
    }

    private record Result(int attempts, String stopReason) {}

    private static final class Session {
        final IntegratedServer server;
        final UUID playerId;
        final int containerId;
        final TargetRule.Criteria criteria;
        volatile boolean cancelled;
        UUID villagerId;
        MerchantMenu lockedMenu;
        LockedTradeData expectedData;
        int attempts;
        long waitingSince = System.nanoTime();
        long lastCycleTick = Long.MIN_VALUE;

        Session(IntegratedServer server, UUID playerId, int containerId, TargetRule.Criteria criteria) {
            this.server = server; this.playerId = playerId; this.containerId = containerId;
            this.criteria = criteria;
        }

        Result end(String reason) { cancelled = true; return new Result(attempts, reason); }
        Result waitResult() {
            if (System.nanoTime() - waitingSince > 15_000_000_000L) return end("交易生成超时，已停止");
            return new Result(attempts, null);
        }

        Result step() {
            if (cancelled) return end("已停止");
            var player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !(player.containerMenu instanceof MerchantMenu menu)
                    || menu.containerId != containerId) return end("交易窗口已改变，已停止");
            if (lockedMenu == null) lockedMenu = menu;
            if (lockedMenu != menu) return end("交易窗口已改变，已停止");
            if (!(((MerchantMenuAccessor) menu).getTrader() instanceof Villager villager))
                return end("当前交易对象不是村民，已停止");
            if (villagerId == null) villagerId = villager.getUUID();
            if (!villagerId.equals(villager.getUUID()) || !villager.isAlive()
                    || villager.getTradingPlayer() != player) return end("村民或交易对象已改变，已停止");
            if (villager.getVillagerXp() > 0) return end("村民已有交易经验，拒绝刷新");
            if (villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty())
                return end("村民没有绑定工作站，无法刷新");
            if (!menu.getSlot(0).getItem().isEmpty() || !menu.getSlot(1).getItem().isEmpty()
                    || !menu.getCarried().isEmpty()) return end("请先清空交易输入格和鼠标持物");

            var duck = VillagerDuck.of(villager);
            LockedTradeData data = duck.visibleTraders$getLockedTradeData().orElse(null);
            if (data == null) return waitResult();
            if (expectedData != null && data != expectedData)
                return end("检测到其他刷新操作，已停止");
            if (data.isGenerating()) return waitResult();
            // Match the UI's order: current unlocked offers, then future-level preview offers.
            MerchantOffers allOffers = new MerchantOffers();
            allOffers.addAll(villager.getOffers());
            var futureOffers = data.condense();
            if (futureOffers.isEmpty() && villager.getVillagerData().level() < 5)
                return end("高级交易预览为空，已停止；请稍后重试");
            allOffers.addAll(futureOffers);
            if (allOffers.isEmpty()) return end("交易列表为空，已停止");
            if (!criteria.positions().all() && !criteria.positions().last()
                    && criteria.positions().numbers().stream().noneMatch(n -> n <= allOffers.size()))
                return end("所选序号均超出当前交易数量，请重新选择位置");
            var candidates = new ArrayList<TargetRule.Offer>();
            for (var offer : allOffers) {
                ItemStack result = offer.getResult();
                var levels = new HashMap<String, Integer>();
                for (var component : java.util.List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS)) {
                    var enchantments = result.getOrDefault(component, ItemEnchantments.EMPTY);
                    for (var holder : enchantments.keySet())
                        levels.merge(holder.getRegisteredName(), enchantments.getLevel(holder), Math::max);
                }
                var effects = new HashMap<String, Integer>();
                var potion = result.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                // Includes the base potion's effects plus custom effects. Minecraft
                // stores a zero-based amplifier; the UI uses levels I=1, II=2, etc.
                for (var effect : potion.getAllEffects())
                    effects.merge(effect.getEffect().getRegisteredName(), effect.getAmplifier() + 1, Math::max);
                ItemStack costA = criteria.basePrice() ? offer.getBaseCostA() : offer.getCostA();
                ItemStack costB = offer.getCostB();
                int emeralds = -1;
                if (costA.is(Items.EMERALD)) emeralds = costA.getCount();
                if (costB.is(Items.EMERALD)) emeralds = Math.max(0, emeralds) + costB.getCount();
                candidates.add(new TargetRule.Offer(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), levels, emeralds, effects));
            }
            int match = criteria.findMatch(candidates);
            if (match >= 0) {
                var hit = candidates.get(match);
                return end("命中第 " + (match + 1) + " 项！" + hit.item()
                        + (criteria.potionEffects() ? " 药水效果 " + hit.effects() : " 附魔 " + hit.enchantments())
                        + "；绿宝石 " + hit.emeralds() + "；刷新 " + attempts + " 次，已停止，未购买。");
            }
            if (attempts >= 10000) return end("已刷新10000次，安全上限停止；可以手动再次开始");
            // Never more than one cycle per server tick, even with an uncapped client.
            long tick = server.getTickCount();
            if (tick == lastCycleTick) return new Result(attempts, null);
            if (cancelled) return end("已停止");
            TradeCyclingMod.onCycleTrades(player);
            attempts++;
            lastCycleTick = tick;
            expectedData = duck.visibleTraders$getLockedTradeData().orElse(null);
            if (expectedData == null || expectedData == data) return end("刷新未生效，已停止");
            waitingSince = System.nanoTime();
            return new Result(attempts, null);
        }
    }
}
