package com.qg.fastscroll;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * 插件配置，持久化在 idea 配置目录的 fast-scroll.xml 中。
 *
 * <p>服务被每次滚轮事件读取，因此设置项改完后无需重启即生效。
 *
 * @author qg
 * @date 2026/11/11
 */
@Service(Service.Level.APP)
// 存储文件名和组件名都带插件前缀：市场上已有的同类插件用的正是 fast-scroll.xml / FastScrollSettings，
// 撞名会导致两个插件互相覆盖配置，甚至读不到彼此的字段
@State(name = "QGFastScrollSettings", storages = @Storage("qg-fast-scroll.xml"))
public final class FastScrollSettings implements PersistentStateComponent<FastScrollSettings.SettingsState> {

    /** 加速倍数下限，1 表示不加速 */
    public static final double MIN_MULTIPLIER = 1.0;
    /** 加速倍数上限，过大一次滚屏会直接跳到文件末尾 */
    public static final double MAX_MULTIPLIER = 20.0;
    /** 默认加速倍数 */
    public static final double DEFAULT_MULTIPLIER = 3.0;

    private SettingsState myState = new SettingsState();

    /**
     * 取应用级单例。
     *
     * @return 配置服务
     */
    public static FastScrollSettings getInstance() {
        return ApplicationManager.getApplication().getService(FastScrollSettings.class);
    }

    @Override
    public @NotNull SettingsState getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull SettingsState state) {
        myState = state;
    }

    /**
     * 加速开关。
     *
     * @return 开启返回 true
     */
    public boolean isEnabled() {
        return myState.enabled;
    }

    /**
     * 设置加速开关。
     *
     * @param enabled 是否开启
     */
    public void setEnabled(boolean enabled) {
        myState.enabled = enabled;
    }

    /**
     * 加速倍数，已做上下限收敛。
     *
     * @return 倍数
     */
    public double getMultiplier() {
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, myState.multiplier));
    }

    /**
     * 设置加速倍数，超出范围会被收敛到边界值。
     *
     * @param multiplier 倍数
     */
    public void setMultiplier(double multiplier) {
        myState.multiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));
    }

    /**
     * 触发加速的修饰键。
     *
     * @return 修饰键
     */
    public TriggerModifier getTriggerModifier() {
        return TriggerModifier.fromName(myState.modifier);
    }

    /**
     * 设置触发加速的修饰键。
     *
     * @param modifier 修饰键
     */
    public void setTriggerModifier(TriggerModifier modifier) {
        myState.modifier = (modifier == null ? TriggerModifier.ALT : modifier).name();
    }

    /**
     * 参与持久化的字段集合，字段名即 XML 属性名。
     */
    public static final class SettingsState {

        /** 是否启用加速 */
        @Attribute
        public boolean enabled = true;

        /** 加速倍数 */
        @Attribute
        public double multiplier = DEFAULT_MULTIPLIER;

        /** 修饰键枚举名 */
        @Attribute
        public String modifier = TriggerModifier.ALT.name();
    }
}
