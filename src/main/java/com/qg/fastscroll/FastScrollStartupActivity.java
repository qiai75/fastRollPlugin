package com.qg.fastscroll;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * 启动即实例化加速服务，保证 IDEA 一打开事件监听就已注册。
 *
 * @author qg
 * @date 2026/11/11
 */
public final class FastScrollStartupActivity implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        // 服务是懒加载的，这里主动取一次完成注册；后续每个项目重复调用都命中同一个实例
        WheelAcceleratorService.getInstance();
    }
}
