package com.qg.fastscroll;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Dimension;

/**
 * 设置页：Settings / Preferences → Tools → Fast Scroll。
 *
 * @author qg
 * @date 2026/11/11
 */
public final class FastScrollConfigurable implements SearchableConfigurable {

    private static final double SPINNER_STEP = 0.5;

    private JBCheckBox myEnabledBox;
    private ComboBox<TriggerModifier> myModifierBox;
    private JSpinner myMultiplierSpinner;
    private JLabel myHintLabel;

    @Override
    public @NotNull String getId() {
        return "com.qg.fastscroll.settings";
    }

    @Override
    public @Nls String getDisplayName() {
        return "Fast Scroll";
    }

    @Override
    public @Nullable JComponent createComponent() {
        // 插件是"可动态加载"的，装完不重启也能被加载，但那时 postStartupActivity 不会执行；
        // 打开设置页时顺手把服务取一次，事件监听立刻可用，不用等到下次重启
        WheelAcceleratorService.getInstance();

        myEnabledBox = new JBCheckBox("按住修饰键滚动时加速（不按时保持原速）");
        myModifierBox = new ComboBox<>(TriggerModifier.values());
        myMultiplierSpinner = new JSpinner(new SpinnerNumberModel(FastScrollSettings.DEFAULT_MULTIPLIER,
                FastScrollSettings.MIN_MULTIPLIER, FastScrollSettings.MAX_MULTIPLIER, SPINNER_STEP));
        myMultiplierSpinner.setPreferredSize(new Dimension(120, myMultiplierSpinner.getPreferredSize().height));
        myHintLabel = new JLabel("<html>倍数 3 表示按住修饰键滚一格等于滚三格；改完立即生效，无需重启。</html>");

        myEnabledBox.addActionListener(e -> updateControlsEnabled());
        reset();

        JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(myEnabledBox)
                .addLabeledComponent("修饰键:", myModifierBox)
                .addLabeledComponent("加速倍数:", myMultiplierSpinner)
                .addComponent(myHintLabel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        updateControlsEnabled();
        return panel;
    }

    @Override
    public boolean isModified() {
        FastScrollSettings settings = FastScrollSettings.getInstance();
        return settings.isEnabled() != myEnabledBox.isSelected()
                || settings.getTriggerModifier() != myModifierBox.getSelectedItem()
                || Math.abs(settings.getMultiplier() - multiplierOfSpinner()) > 1e-6;
    }

    @Override
    public void apply() {
        FastScrollSettings settings = FastScrollSettings.getInstance();
        settings.setEnabled(myEnabledBox.isSelected());
        Object selected = myModifierBox.getSelectedItem();
        settings.setTriggerModifier(selected instanceof TriggerModifier ? (TriggerModifier) selected : TriggerModifier.ALT);
        settings.setMultiplier(multiplierOfSpinner());
        // 倍数可能被收敛到边界，回写一次让界面显示真实生效值
        myMultiplierSpinner.setValue(settings.getMultiplier());
    }

    @Override
    public void reset() {
        FastScrollSettings settings = FastScrollSettings.getInstance();
        myEnabledBox.setSelected(settings.isEnabled());
        myModifierBox.setSelectedItem(settings.getTriggerModifier());
        myMultiplierSpinner.setValue(settings.getMultiplier());
    }

    @Override
    public void disposeUIResources() {
        myEnabledBox = null;
        myModifierBox = null;
        myMultiplierSpinner = null;
        myHintLabel = null;
    }

    private void updateControlsEnabled() {
        boolean enabled = myEnabledBox.isSelected();
        myModifierBox.setEnabled(enabled);
        myMultiplierSpinner.setEnabled(enabled);
        myHintLabel.setEnabled(enabled);
    }

    private double multiplierOfSpinner() {
        Object value = myMultiplierSpinner.getValue();
        return value instanceof Number ? ((Number) value).doubleValue() : FastScrollSettings.DEFAULT_MULTIPLIER;
    }
}
