package com.qg.fastscroll;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 菜单入口：Tools → Fast Scroll Settings…，同时用作"插件是否加载成功"的可见信号。
 *
 * @author qg
 * @date 2026/11/11
 */
public final class OpenSettingsAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        ShowSettingsUtil.getInstance().showSettingsDialog(project, FastScrollConfigurable.class);
    }
}
