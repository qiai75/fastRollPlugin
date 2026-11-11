package com.qg.fastscroll;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * 触发加速的修饰键。
 *
 * @author qg
 * @date 2026/11/11
 */
public enum TriggerModifier {

    /** Alt，在 macOS 上即 Option 键 */
    ALT("Alt / Option (⌥)", InputEvent.ALT_DOWN_MASK),
    /** Ctrl，在 macOS 上即 Control 键 */
    CTRL("Ctrl / Control (⌃)", InputEvent.CTRL_DOWN_MASK),
    /** Shift */
    SHIFT("Shift (⇧)", InputEvent.SHIFT_DOWN_MASK),
    /** Meta，在 macOS 上即 Command 键 */
    META("Meta / Command (⌘)", InputEvent.META_DOWN_MASK);

    private final String myLabel;
    private final int myMask;

    TriggerModifier(String label, int mask) {
        myLabel = label;
        myMask = mask;
    }

    /**
     * 判断该修饰键是否正处于按下状态。
     *
     * @param event 鼠标事件
     * @return 按下返回 true
     */
    public boolean isDown(MouseEvent event) {
        return (event.getModifiersEx() & myMask) != 0;
    }

    /**
     * 按枚举名反查，未知值回落到 ALT。
     *
     * @param name 枚举名
     * @return 匹配到的修饰键
     */
    public static TriggerModifier fromName(String name) {
        if (name != null) {
            for (TriggerModifier value : values()) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
        }
        return ALT;
    }

    // 下拉框直接调用 toString 渲染
    @Override
    public String toString() {
        return myLabel;
    }
}
