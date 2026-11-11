package com.qg.fastscroll;

import java.awt.Component;
import java.awt.event.MouseWheelEvent;

/**
 * 插件自己补发的滚轮事件。
 *
 * <p>单独建一个子类，是为了让全局监听能一眼认出"这是我发的"，直接放行，
 * 否则补发的事件会再次被放大，形成无限递归。
 *
 * @author qg
 * @date 2026/11/11
 */
public final class SyntheticWheelEvent extends MouseWheelEvent {

    public SyntheticWheelEvent(Component source, int id, long when, int modifiers, int x, int y,
                               int absX, int absY, int clickCount, boolean popupTrigger, int scrollType,
                               int scrollAmount, int wheelRotation, double preciseWheelRotation) {
        // 用带绝对坐标的 14 参构造器：JDK 里只有它能把 preciseWheelRotation 一起带上
        super(source, id, when, modifiers, x, y, absX, absY, clickCount, popupTrigger, scrollType,
                scrollAmount, wheelRotation, preciseWheelRotation);
    }
}
