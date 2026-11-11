package com.qg.fastscroll;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 滚轮加速核心：全局监听滚轮事件，按住修饰键时补发一个"带满格数"的同向滚轮事件。
 *
 * <p>实现要点：AWT 的事件监听器在事件派发给目标组件之前回调（见 {@link EventQueue#dispatchEvent}），
 * 因此在原事件到达组件前把"多出来的那几格"作为<b>一个</b>事件补发进队列即可，
 * 完全复用组件自身的滚动逻辑（编辑器、表格、控制台都一样生效），不碰任何滚动条数值。
 *
 * <p>为什么补发的是<b>一个</b>事件而不是 N 个：IDEA 编辑器的滚轮走
 * {@code com.intellij.ui.scroll.MouseWheelSmoothScroll}，它把每次事件变成一段动画
 * （{@code animator.start(bar.getValue(), ...)}）并 {@code consume()} 掉原事件。
 * 短时间内连续塞多个事件时，后一个事件是拿"还没开始移动的滚动条当前位置"重新起动画的，
 * 结果会被前一个覆盖甚至归零——实测表现就是"按住修饰键完全不滚"。
 * 一个事件带满 delta 则只有一段动画，滚动量精确。
 *
 * @author qg
 * @date 2026/11/11
 */
@Service(Service.Level.APP)
public final class WheelAcceleratorService implements Disposable {

    private static final Logger LOG = Logger.getInstance(WheelAcceleratorService.class);

    /** 单次事件最多补发的格数，防止异常输入把事件队列刷爆 */
    private static final int MAX_EXTRA_TICKS = 100;

    /** 启动后前若干次命中打日志，够定位问题又不至于刷屏 */
    private static final int DIAGNOSTIC_LIMIT = 30;

    /** 补发事件要清掉的修饰键：保证它被当成普通的垂直滚动处理 */
    private static final int STRIPPED_MASK = InputEvent.ALT_DOWN_MASK
            | InputEvent.CTRL_DOWN_MASK
            | InputEvent.SHIFT_DOWN_MASK
            | InputEvent.META_DOWN_MASK;

    /**
     * 组件 -> 不足一整格的小数滚动量。
     * 鼠标滚轮是整格事件用不上，但触控板的 preciseWheelRotation 常是 0.3 这种小数，需要累积后再补发。
     * 用 WeakHashMap 跟随组件生命周期，避免插件长期持有已关闭编辑器的引用。
     */
    private final Map<Component, Double> myRemainders = new WeakHashMap<>();

    private final AtomicInteger myHitCount = new AtomicInteger();

    private final AWTEventListener myListener = this::onAwtEvent;

    public WheelAcceleratorService() {
        Toolkit.getDefaultToolkit().addAWTEventListener(myListener, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        logStartup();
    }

    /**
     * 取应用级单例；首次调用会完成事件监听注册。
     *
     * @return 加速服务
     */
    public static WheelAcceleratorService getInstance() {
        return ApplicationManager.getApplication().getService(WheelAcceleratorService.class);
    }

    @Override
    public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(myListener);
        myRemainders.clear();
    }

    /**
     * 全局回调，只关心滚轮事件。
     *
     * @param event AWT 事件
     */
    private void onAwtEvent(AWTEvent event) {
        if (!(event instanceof MouseWheelEvent)) {
            return;
        }
        MouseWheelEvent wheelEvent = (MouseWheelEvent) event;
        // 自己补发的事件直接放行，否则会被再次放大
        if (wheelEvent instanceof SyntheticWheelEvent) {
            return;
        }
        try {
            accelerate(wheelEvent);
        } catch (RuntimeException e) {
            // 加速失败不能让整个事件链断掉，放弃本次加速即可
            LOG.warn("[滚轮加速-事件处理] 计算加速量失败,跳过本次加速, reason:" + e, e);
        }
    }

    /**
     * 计算并补发额外滚轮事件。
     *
     * @param event 原始滚轮事件
     */
    private void accelerate(MouseWheelEvent event) {
        FastScrollSettings settings = FastScrollSettings.getInstance();
        if (!settings.isEnabled()) {
            diagnose("未启用", event, 0, settings);
            return;
        }
        double multiplier = settings.getMultiplier();
        if (multiplier <= 1.0) {
            diagnose("倍数为1", event, 0, settings);
            return;
        }
        if (!settings.getTriggerModifier().isDown(event)) {
            return;
        }
        Component source = event.getComponent();
        if (source == null) {
            return;
        }
        double rotation = event.getPreciseWheelRotation();
        if (rotation == 0.0) {
            rotation = event.getWheelRotation();
        }
        if (rotation == 0.0) {
            diagnose("rotation为0", event, 0, settings);
            return;
        }

        // 统一以「一格滚轮」为单位折算：原事件自己已经滚了 rotation 格，倍数多出来的部分才需要补发
        double pendingTicks = rotation * (multiplier - 1.0) + remainderOf(source);
        int extraTicks = (int) pendingTicks;
        myRemainders.put(source, pendingTicks - extraTicks);
        if (extraTicks == 0) {
            diagnose("零头累积中", event, 0, settings);
            return;
        }
        extraTicks = Math.max(-MAX_EXTRA_TICKS, Math.min(MAX_EXTRA_TICKS, extraTicks));

        // 清掉修饰键：补发事件必须是"普通垂直滚轮"，否则组件会按修饰键语义处理（如 Shift 横向滚动）
        int modifiers = event.getModifiersEx() & ~STRIPPED_MASK;
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        queue.postEvent(new SyntheticWheelEvent(source, event.getID(), event.getWhen(),
                modifiers, event.getX(), event.getY(),
                event.getXOnScreen(), event.getYOnScreen(), event.getClickCount(),
                event.isPopupTrigger(), event.getScrollType(), event.getScrollAmount(),
                extraTicks, extraTicks));
        diagnose("已补发", event, extraTicks, settings);
    }

    private double remainderOf(Component source) {
        Double value = myRemainders.get(source);
        return value == null ? 0.0 : value;
    }

    /**
     * 带诊断日志的启动记录，便于从 idea.log 确认插件是否真的在工作。
     */
    private void logStartup() {
        FastScrollSettings settings = FastScrollSettings.getInstance();
        LOG.info("[滚轮加速-启动] 监听已注册, enabled:" + settings.isEnabled()
                + ", multiplier:" + settings.getMultiplier()
                + ", modifier:" + settings.getTriggerModifier().name());
    }

    /**
     * 只在前若干次命中时打日志，避免正常使用刷屏。
     *
     * @param state    处理结果
     * @param event    滚轮事件
     * @param extraTicks 补发格数
     * @param settings 当前配置
     */
    private void diagnose(String state, MouseWheelEvent event, int extraTicks, FastScrollSettings settings) {
        if (myHitCount.incrementAndGet() > DIAGNOSTIC_LIMIT) {
            return;
        }
        LOG.info("[滚轮加速-命中] " + state
                + ", rotation:" + event.getPreciseWheelRotation()
                + ", scrollAmount:" + event.getScrollAmount()
                + ", scrollType:" + event.getScrollType()
                + ", extraTicks:" + extraTicks
                + ", multiplier:" + settings.getMultiplier()
                + ", modifier:" + settings.getTriggerModifier().name()
                + ", source:" + (event.getComponent() == null ? "null" : event.getComponent().getClass().getName()));
    }
}
