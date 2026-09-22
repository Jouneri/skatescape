package com.skatescape;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class SkateScapeConfigPanelController {
    private SkateScapeConfig config;
    private SkateScapePlugin plugin;
    private int maxSafeAnimationId;

    /*
     * Reflection-free live RuneLite config-panel machinery for the hidden
     * developer editors.
     */
    private volatile boolean perTrickEditorWritesSuppressed;

    SkateScapeConfigPanelController(
            SkateScapeConfig config,
            SkateScapePlugin plugin,
            int maxSafeAnimationId) {

        this.config = config;
        this.plugin = plugin;
        this.maxSafeAnimationId = maxSafeAnimationId;
    }

    void installListeners() {
        KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(configSpinnerArrowGuard);

        Toolkit.getDefaultToolkit()
                .addAWTEventListener(
                        configPanelVisibilityListener,
                        AWTEvent.HIERARCHY_EVENT_MASK
                );
    }

    void uninstallListeners() {
        KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(configSpinnerArrowGuard);

        Toolkit.getDefaultToolkit()
                .removeAWTEventListener(configPanelVisibilityListener);
    }

    void suppressPerTrickEditorWrites() {
        perTrickEditorWritesSuppressed = true;
    }

    void clearPerTrickEditorWriteSuppression() {
        perTrickEditorWritesSuppressed = false;
    }

    boolean isSuppressingPerTrickEditorWrites() {
        return perTrickEditorWritesSuppressed;
    }

    /*
     * REFLECTION-FREE LIVE CONFIG PANEL.
     *
     * ConfigPanel.rebuild() is package-private, and Plugin Hub rules forbid
     * reflection. Refresh the existing Swing controls directly instead.
     *
     * Trick Tuning removes rows that do not apply to the selected trick. Keep
     * references so those rows can be restored on the next selection. The
     * always-present duration row acts as the rebuild sentinel.
     */
    private boolean applyingLiveConfigPanel;
    private Component liveConfigPanel;
    private Component liveConfigSentinelRow;
    private Container liveTuningSection;

    private Component livePopHeightRow;
    private Component livePopStartRow;
    private Component livePrimaryStartRow;
    private Component livePrimaryEndRow;
    private Component liveCatchRow;
    private Component liveTouchdownRow;

    private Component liveKickDegreesRow;
    private Component liveVarialShoveStartRow;
    private Component liveVarialShoveEndRow;
    private Component liveSecondaryTuningRow;
    private Component liveImpossibleDegreesRow;

    private Component liveTrick5BoardFrameSlotRow;
    private Component liveTrick5BoardFrameLockRow;
    private Component liveTrick5BoardXRow;
    private Component liveTrick5BoardYRow;
    private Component liveTrick5BoardZRow;
    private Component liveTrick5BoardPitchRow;
    private Component liveTrick5BoardYawRow;
    private Component liveTrick5BoardRollRow;

    private int livePopHeightIndex = -1;
    private int livePopStartIndex = -1;
    private int livePrimaryStartIndex = -1;
    private int livePrimaryEndIndex = -1;
    private int liveCatchIndex = -1;
    private int liveTouchdownIndex = -1;

    private int liveKickDegreesIndex = -1;
    private int liveVarialShoveStartIndex = -1;
    private int liveVarialShoveEndIndex = -1;
    private int liveSecondaryTuningIndex = -1;
    private int liveImpossibleDegreesIndex = -1;

    private int liveTrick5BoardFrameSlotIndex = -1;
    private int liveTrick5BoardFrameLockIndex = -1;
    private int liveTrick5BoardXIndex = -1;
    private int liveTrick5BoardYIndex = -1;
    private int liveTrick5BoardZIndex = -1;
    private int liveTrick5BoardPitchIndex = -1;
    private int liveTrick5BoardYawIndex = -1;
    private int liveTrick5BoardRollIndex = -1;

    /*
     * Post-process the SkateScape config panel when it first becomes visible.
     * RuneLite may restore a saved trick selection before any dropdown event
     * has a chance to refresh the dynamic rows.
     */
    private final AWTEventListener configPanelVisibilityListener =
            event -> {
                if (!(event instanceof HierarchyEvent)
                        || applyingLiveConfigPanel) {
                    return;
                }

                final HierarchyEvent hierarchyEvent =
                        (HierarchyEvent) event;

                if ((hierarchyEvent.getChangeFlags()
                        & HierarchyEvent.SHOWING_CHANGED) == 0) {
                    return;
                }

                final Component changed =
                        hierarchyEvent.getChanged();

                final Component configPanel =
                        "net.runelite.client.plugins.config.ConfigPanel"
                                .equals(changed.getClass().getName())
                                ? changed
                                : findAncestorByClassName(
                                        changed,
                                        "net.runelite.client.plugins.config.ConfigPanel"
                                );

                if (configPanel == null
                        || !configPanel.isShowing()
                        || !isSkateScapeConfigPanel(configPanel)) {
                    return;
                }

                /*
                 * Ignore hierarchy events caused by row movement. A real
                 * RuneLite rebuild replaces the duration row, making the cached
                 * sentinel stale so the new panel contents can be captured.
                 */
                if (changed != configPanel
                        && configPanel == liveConfigPanel
                        && isDescendantOf(
                                liveConfigSentinelRow,
                                configPanel
                        )) {
                    return;
                }

                SwingUtilities.invokeLater(
                        () -> refreshVisibleConfigPanel(configPanel)
                );
            };

    /*
     * RUNE LITE CONFIG SPINNER ARROW GUARD.
     *
     * RuneLite's spinner key bindings can hand an Up/Down key to the
     * surrounding UI when the spinner is already at its boundary. That made
     * the plugin-list/sidebar start scrolling. Consume only those boundary
     * presses, and only while a SkateScape config spinner owns focus.
     *
     * Pose sequence length has a per-trick maximum: 12 for Tricks 1-3,
     * 19 for Trick 4 and 9 for Trick 5. The stock @Range stays broad enough
     * for every slot; this guard supplies the selected trick's actual max.
     */
    private final KeyEventDispatcher configSpinnerArrowGuard =
            event -> {
                if (event.getID() != KeyEvent.KEY_PRESSED
                        || (event.getKeyCode() != KeyEvent.VK_UP
                                && event.getKeyCode() != KeyEvent.VK_DOWN)
                        || event.isControlDown()
                        || event.isAltDown()
                        || event.isShiftDown()
                        || event.isMetaDown()) {
                    return false;
                }

                final Component focusOwner =
                        KeyboardFocusManager
                                .getCurrentKeyboardFocusManager()
                                .getFocusOwner();

                final JSpinner spinner =
                        findAncestorSpinner(focusOwner);

                if (spinner == null) {
                    return false;
                }

                final Component configPanel =
                        findAncestorByClassName(
                                spinner,
                                "net.runelite.client.plugins.config.ConfigPanel"
                        );

                if (configPanel == null
                        || !isSkateScapeConfigPanel(configPanel)
                        || !(spinner.getModel() instanceof SpinnerNumberModel)
                        || !(spinner.getValue() instanceof Number)) {
                    return false;
                }

                final SpinnerNumberModel model =
                        (SpinnerNumberModel) spinner.getModel();

                final double currentValue =
                        ((Number) spinner.getValue()).doubleValue();

                double minimum =
                        model.getMinimum() instanceof Number
                                ? ((Number) model.getMinimum()).doubleValue()
                                : -Double.MAX_VALUE;

                double maximum =
                        model.getMaximum() instanceof Number
                                ? ((Number) model.getMaximum()).doubleValue()
                                : Double.MAX_VALUE;

                final String label =
                        getSpinnerLabel(spinner);

                /*
                 * While Animation Inspector is ON, RuneLite can also react to
                 * Up/Down after the Animation ID spinner changes. Handle that
                 * spinner here so the ID still moves by one but the same
                 * keypress never reaches the plugin sidebar.
                 */
                if (config.animationTest()
                        && "Animation ID".equals(label)) {

                    final int currentId =
                            ((Number) spinner.getValue()).intValue();

                    final int nextId =
                            Math.max(
                                    0,
                                    Math.min(
                                            maxSafeAnimationId,
                                            currentId
                                                    + (event.getKeyCode()
                                                            == KeyEvent.VK_UP
                                                            ? 1
                                                            : -1)
                                    )
                            );

                    if (nextId != currentId) {
                        spinner.setValue(nextId);
                    }

                    event.consume();
                    return true;
                }

                if ("Pose sequence length".equals(label)) {
                    maximum =
                            plugin.maxPoseSequenceLength(
                                    config.poseSelectedTrick().getSlot()
                            );
                } else if ("Trick position (cycle)".equals(label)) {
                    maximum = getLiveInspectorMaxCycle();
                } else if ("Animation frame".equals(label)
                        && config.animationTest()
                        && plugin.getAnimationBrowseMaxFrame() >= 0) {
                    maximum = plugin.getAnimationBrowseMaxFrame();
                } else if ("Animation ID".equals(label)) {
                    maximum = maxSafeAnimationId;
                }

                final boolean atUpperBoundary =
                        event.getKeyCode() == KeyEvent.VK_UP
                                && currentValue >= maximum;

                final boolean atLowerBoundary =
                        event.getKeyCode() == KeyEvent.VK_DOWN
                                && currentValue <= minimum;

                if (atUpperBoundary || atLowerBoundary) {
                    event.consume();
                    return true;
                }

                return false;
            };

    private JSpinner findAncestorSpinner(Component component) {
        Component current = component;

        while (current != null) {
            if (current instanceof JSpinner) {
                return (JSpinner) current;
            }

            current = current.getParent();
        }

        return null;
    }

    private Component findAncestorByClassName(
            Component component,
            String className) {

        Component current = component;

        while (current != null) {
            if (className.equals(current.getClass().getName())) {
                return current;
            }

            current = current.getParent();
        }

        return null;
    }

    private boolean containsLabelText(
            Component component,
            String text) {

        if (component instanceof JLabel
                && text.equals(((JLabel) component).getText())) {
            return true;
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (containsLabelText(child, text)) {
                    return true;
                }
            }
        }

        return false;
    }

    private JLabel findLabel(
            Component component,
            String... acceptedTexts) {

        if (component instanceof JLabel) {
            final String text = ((JLabel) component).getText();

            for (String acceptedText : acceptedTexts) {
                if (acceptedText.equals(text)) {
                    return (JLabel) component;
                }
            }
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                final JLabel found =
                        findLabel(
                                child,
                                acceptedTexts
                        );

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private boolean isDescendantOf(
            Component component,
            Component ancestor) {

        if (component == null || ancestor == null) {
            return false;
        }

        Component current = component;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }

        return false;
    }

    private <T extends Component> T findChildComponent(
            Component component,
            Class<T> type) {

        if (component == null || type == null) {
            return null;
        }

        if (type.isInstance(component)) {
            return type.cast(component);
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                final T found = findChildComponent(child, type);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private Component getConfigRow(
            Component configPanel,
            String... acceptedLabelTexts) {

        final JLabel label =
                findLabel(
                        configPanel,
                        acceptedLabelTexts
                );

        return label == null ? null : label.getParent();
    }

    private int getComponentIndex(
            Container parent,
            Component component) {

        if (parent == null || component == null) {
            return -1;
        }

        return parent.getComponentZOrder(component);
    }

    private void captureLiveConfigRows(Component configPanel) {
        final Component durationRow =
                getConfigRow(
                        configPanel,
                        "Trick duration (ms)"
                );

        if (durationRow == null || durationRow.getParent() == null) {
            return;
        }

        final Container tuningSection = durationRow.getParent();

        liveConfigPanel = configPanel;
        liveConfigSentinelRow = durationRow;
        liveTuningSection = tuningSection;

        livePopHeightRow =
                getConfigRow(configPanel, "Board pop height");
        livePopStartRow =
                getConfigRow(configPanel, "Pop starts (%)");
        livePrimaryStartRow =
                getConfigRow(
                        configPanel,
                        "Kickflip starts (%)",
                        "Shove-it starts (%)"
                );
        livePrimaryEndRow =
                getConfigRow(
                        configPanel,
                        "Kickflip ends (%)",
                        "Shove-it ends (%)"
                );
        liveCatchRow =
                getConfigRow(configPanel, "Board catch (%)");
        liveTouchdownRow =
                getConfigRow(configPanel, "Board touchdown (%)");

        liveKickDegreesRow =
                getConfigRow(
                        configPanel,
                        "Kickflip rotation (degrees)",
                        "Christ Air Kickflip rotation (degrees)"
                );
        liveVarialShoveStartRow =
                getConfigRow(
                        configPanel,
                        "Varial Shove-it starts (%)",
                        "Shove-it starts (%)"
                );
        liveVarialShoveEndRow =
                getConfigRow(
                        configPanel,
                        "Varial Shove-it ends (%)",
                        "Shove-it ends (%)"
                );
        liveSecondaryTuningRow =
                getConfigRow(
                        configPanel,
                        "360 Shove-it starts (%)",
                        "Shove-it rotation (degrees)"
                );
        liveImpossibleDegreesRow =
                getConfigRow(
                        configPanel,
                        "360 Shove-it rotation (degrees)"
                );

        liveTrick5BoardFrameSlotRow =
                getConfigRow(configPanel, "Board frame");
        liveTrick5BoardFrameLockRow =
                getConfigRow(configPanel, "Keyframe lock");
        liveTrick5BoardXRow =
                getConfigRow(configPanel, "X - right / left");
        liveTrick5BoardYRow =
                getConfigRow(configPanel, "Y - up / down");
        liveTrick5BoardZRow =
                getConfigRow(configPanel, "Z - forward / back");
        liveTrick5BoardPitchRow =
                getConfigRow(configPanel, "Pitch");
        liveTrick5BoardYawRow =
                getConfigRow(configPanel, "Yaw");
        liveTrick5BoardRollRow =
                getConfigRow(configPanel, "Roll");

        livePopHeightIndex =
                getComponentIndex(tuningSection, livePopHeightRow);
        livePopStartIndex =
                getComponentIndex(tuningSection, livePopStartRow);
        livePrimaryStartIndex =
                getComponentIndex(tuningSection, livePrimaryStartRow);
        livePrimaryEndIndex =
                getComponentIndex(tuningSection, livePrimaryEndRow);
        liveKickDegreesIndex =
                getComponentIndex(tuningSection, liveKickDegreesRow);
        liveVarialShoveStartIndex =
                getComponentIndex(tuningSection, liveVarialShoveStartRow);
        liveVarialShoveEndIndex =
                getComponentIndex(tuningSection, liveVarialShoveEndRow);
        liveSecondaryTuningIndex =
                getComponentIndex(tuningSection, liveSecondaryTuningRow);
        liveCatchIndex =
                getComponentIndex(tuningSection, liveCatchRow);
        liveTouchdownIndex =
                getComponentIndex(tuningSection, liveTouchdownRow);
        liveImpossibleDegreesIndex =
                getComponentIndex(tuningSection, liveImpossibleDegreesRow);

        liveTrick5BoardFrameSlotIndex =
                getComponentIndex(tuningSection, liveTrick5BoardFrameSlotRow);
        liveTrick5BoardFrameLockIndex =
                getComponentIndex(tuningSection, liveTrick5BoardFrameLockRow);
        liveTrick5BoardXIndex =
                getComponentIndex(tuningSection, liveTrick5BoardXRow);
        liveTrick5BoardYIndex =
                getComponentIndex(tuningSection, liveTrick5BoardYRow);
        liveTrick5BoardZIndex =
                getComponentIndex(tuningSection, liveTrick5BoardZRow);
        liveTrick5BoardPitchIndex =
                getComponentIndex(tuningSection, liveTrick5BoardPitchRow);
        liveTrick5BoardYawIndex =
                getComponentIndex(tuningSection, liveTrick5BoardYawRow);
        liveTrick5BoardRollIndex =
                getComponentIndex(tuningSection, liveTrick5BoardRollRow);
    }

    private void detachCachedConfigRow(Component row) {
        if (row == null) {
            return;
        }

        final Container parent = row.getParent();
        if (parent != null) {
            parent.remove(row);
        }
    }

    private void attachCachedConfigRow(
            Component row,
            int index) {

        if (row == null
                || liveTuningSection == null
                || index < 0) {
            return;
        }

        final int safeIndex =
                Math.max(
                        0,
                        Math.min(
                                index,
                                liveTuningSection.getComponentCount()
                        )
                );

        liveTuningSection.add(row, safeIndex);
    }

    private void restoreCachedTuningRows(Component configPanel) {
        final boolean cacheIsCurrent =
                configPanel == liveConfigPanel
                        && isDescendantOf(
                                liveConfigSentinelRow,
                                configPanel
                        );

        if (!cacheIsCurrent) {
            captureLiveConfigRows(configPanel);
        }

        if (liveTuningSection == null) {
            return;
        }

        /*
         * The live-row cache includes Christ Air's dedicated frame-by-frame
         * board editor. Restore every row that may be dynamically removed, in
         * static ConfigItem order, before applying the selected trick's
         * presentation rules.
         */
        final Component[] rows = {
                livePopHeightRow,
                livePopStartRow,
                livePrimaryStartRow,
                livePrimaryEndRow,
                liveKickDegreesRow,
                liveVarialShoveStartRow,
                liveVarialShoveEndRow,
                liveSecondaryTuningRow,
                liveCatchRow,
                liveTouchdownRow,
                liveImpossibleDegreesRow,
                liveTrick5BoardFrameSlotRow,
                liveTrick5BoardFrameLockRow,
                liveTrick5BoardXRow,
                liveTrick5BoardYRow,
                liveTrick5BoardZRow,
                liveTrick5BoardPitchRow,
                liveTrick5BoardYawRow,
                liveTrick5BoardRollRow
        };

        final int[] indices = {
                livePopHeightIndex,
                livePopStartIndex,
                livePrimaryStartIndex,
                livePrimaryEndIndex,
                liveKickDegreesIndex,
                liveVarialShoveStartIndex,
                liveVarialShoveEndIndex,
                liveSecondaryTuningIndex,
                liveCatchIndex,
                liveTouchdownIndex,
                liveImpossibleDegreesIndex,
                liveTrick5BoardFrameSlotIndex,
                liveTrick5BoardFrameLockIndex,
                liveTrick5BoardXIndex,
                liveTrick5BoardYIndex,
                liveTrick5BoardZIndex,
                liveTrick5BoardPitchIndex,
                liveTrick5BoardYawIndex,
                liveTrick5BoardRollIndex
        };

        for (Component row : rows) {
            detachCachedConfigRow(row);
        }

        for (int i = 0; i < rows.length; i++) {
            attachCachedConfigRow(rows[i], indices[i]);
        }

        resetCachedTuningRowLabels();
    }

    private void resetCachedTuningRowLabels() {
        final JLabel kickDegreesLabel =
                findChildComponent(
                        liveKickDegreesRow,
                        JLabel.class
                );
        if (kickDegreesLabel != null) {
            kickDegreesLabel.setText("Kickflip rotation (degrees)");
        }

        final JLabel varialStartLabel =
                findChildComponent(
                        liveVarialShoveStartRow,
                        JLabel.class
                );
        if (varialStartLabel != null) {
            varialStartLabel.setText("Varial Shove-it starts (%)");
        }

        final JLabel varialEndLabel =
                findChildComponent(
                        liveVarialShoveEndRow,
                        JLabel.class
                );
        if (varialEndLabel != null) {
            varialEndLabel.setText("Varial Shove-it ends (%)");
        }

        final JLabel secondaryLabel =
                findChildComponent(
                        liveSecondaryTuningRow,
                        JLabel.class
                );
        if (secondaryLabel != null) {
            secondaryLabel.setText("360 Shove-it starts (%)");
        }

        final JLabel impossibleDegreesLabel =
                findChildComponent(
                        liveImpossibleDegreesRow,
                        JLabel.class
                );
        if (impossibleDegreesLabel != null) {
            impossibleDegreesLabel.setText("360 Shove-it rotation (degrees)");
        }
    }

    private void setSpinnerValue(
            JSpinner spinner,
            Number value) {

        if (spinner == null
                || value == null
                || !(spinner.getValue() instanceof Number)) {
            return;
        }

        final Number currentValue = (Number) spinner.getValue();
        final boolean floatingModel =
                currentValue instanceof Double
                        || currentValue instanceof Float;
        final Number modelValue;
        if (floatingModel) {
            modelValue = Double.valueOf(value.doubleValue());
        } else {
            modelValue = Integer.valueOf(value.intValue());
        }

        if (floatingModel
                ? Double.compare(
                                currentValue.doubleValue(),
                                modelValue.doubleValue()
                        ) == 0
                : currentValue.intValue() == modelValue.intValue()) {
            return;
        }

        final ChangeListener[] listeners =
                spinner.getChangeListeners();

        /*
         * keep Swing's own JSpinner editor listener attached while the
         * value changes. Removing it leaves RuneLite showing stale text even
         * though the spinner model itself changed.
         */
        final Component spinnerEditor = spinner.getEditor();

        for (ChangeListener listener : listeners) {
            if (listener != spinnerEditor) {
                spinner.removeChangeListener(listener);
            }
        }

        try {
            /*
             * The previously selected trick may have narrowed this shared
             * model. Widen only far enough to accept the incoming saved value;
             * immediately restores the selected trick's real bounds.
             */
            if (spinner.getModel() instanceof SpinnerNumberModel) {
                final SpinnerNumberModel model =
                        (SpinnerNumberModel) spinner.getModel();

                if (model.getMinimum() instanceof Number
                        && modelValue.doubleValue()
                                < ((Number) model.getMinimum()).doubleValue()) {
                    if (floatingModel) {
                        model.setMinimum(Double.valueOf(modelValue.doubleValue()));
                    } else {
                        model.setMinimum(Integer.valueOf(modelValue.intValue()));
                    }
                }

                if (model.getMaximum() instanceof Number
                        && modelValue.doubleValue()
                                > ((Number) model.getMaximum()).doubleValue()) {
                    if (floatingModel) {
                        model.setMaximum(Double.valueOf(modelValue.doubleValue()));
                    } else {
                        model.setMaximum(Integer.valueOf(modelValue.intValue()));
                    }
                }
            }

            spinner.setValue(modelValue);
        } finally {
            for (ChangeListener listener : listeners) {
                if (listener != spinnerEditor) {
                    spinner.addChangeListener(listener);
                }
            }
        }
    }

    private void setTextValue(
            Component row,
            String value) {

        final JTextComponent textComponent =
                findChildComponent(
                        row,
                        JTextComponent.class
                );

        if (textComponent == null) {
            return;
        }

        final String safeValue = value == null ? "" : value;
        if (!safeValue.equals(textComponent.getText())) {
            textComponent.setText(safeValue);
        }
    }

    private void setCheckBoxValue(
            Component row,
            boolean value) {

        final JCheckBox checkBox =
                findChildComponent(
                        row,
                        JCheckBox.class
                );

        if (checkBox != null && checkBox.isSelected() != value) {
            checkBox.setSelected(value);
        }
    }

    private void setSpinnerEnabled(
            Component row,
            boolean enabled) {

        final JSpinner spinner =
                findChildComponent(
                        row,
                        JSpinner.class
                );

        if (spinner != null) {
            spinner.setEnabled(enabled);
        }
    }

    private void setSectionComboValue(
            Component configPanel,
            String sectionName,
            Object value) {

        final JLabel sectionLabel =
                findLabel(
                        configPanel,
                        sectionName
                );

        if (sectionLabel == null
                || sectionLabel.getParent() == null
                || sectionLabel.getParent().getParent() == null) {
            return;
        }

        final JComboBox<?> comboBox =
                findChildComponent(
                        sectionLabel.getParent().getParent(),
                        JComboBox.class
                );

        if (comboBox == null
                || comboBox.getSelectedItem() == value) {
            return;
        }

        final ItemListener[] listeners =
                comboBox.getItemListeners();

        for (ItemListener listener : listeners) {
            comboBox.removeItemListener(listener);
        }

        try {
            comboBox.setSelectedItem(value);
        } finally {
            for (ItemListener listener : listeners) {
                comboBox.addItemListener(listener);
            }
        }
    }

    private void setComboValue(
            Component row,
            Object value) {

        final JComboBox<?> comboBox =
                findChildComponent(
                        row,
                        JComboBox.class
                );

        if (comboBox == null
                || comboBox.getSelectedItem() == value) {
            return;
        }

        final ItemListener[] listeners =
                comboBox.getItemListeners();

        for (ItemListener listener : listeners) {
            comboBox.removeItemListener(listener);
        }

        try {
            comboBox.setSelectedItem(value);
        } finally {
            for (ItemListener listener : listeners) {
                comboBox.addItemListener(listener);
            }
        }
    }

    private void syncVisibleConfigEditorValues(Component configPanel) {
        setSectionComboValue(
                configPanel,
                "Trick Tuning",
                config.tuningSelectedTrick()
        );
        setSectionComboValue(
                configPanel,
                "Trick Inspector",
                config.inspectorSelectedTrick()
        );
        setSectionComboValue(
                configPanel,
                "Pose Tuning",
                config.poseSelectedTrick()
        );
        setSectionComboValue(
                configPanel,
                "Advanced Pose Timing",
                config.poseTimingSelectedTrick()
        );

        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Trick duration (ms)"
                ),
                config.tuningDurationMs()
        );
        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Board pop height"
                ),
                config.tuningPopHeight()
        );
        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Pop starts (%)"
                ),
                config.tuningPopStartPercent()
        );

        final JLabel primaryStartLabel =
                findLabel(
                        configPanel,
                        "Kickflip starts (%)",
                        "Shove-it starts (%)"
                );
        setSpinnerValue(
                findChildComponent(
                        primaryStartLabel == null
                                ? null
                                : primaryStartLabel.getParent(),
                        JSpinner.class
                ),
                config.tuningKickflipStartPercent()
        );

        final JLabel primaryEndLabel =
                findLabel(
                        configPanel,
                        "Kickflip ends (%)",
                        "Shove-it ends (%)"
                );
        setSpinnerValue(
                findChildComponent(
                        primaryEndLabel == null
                                ? null
                                : primaryEndLabel.getParent(),
                        JSpinner.class
                ),
                config.tuningKickflipEndPercent()
        );

        setSpinnerValue(
                findChildComponent(
                        liveKickDegreesRow,
                        JSpinner.class
                ),
                config.tuningTrick5KickflipDegrees()
        );
        setSpinnerValue(
                findChildComponent(
                        liveImpossibleDegreesRow,
                        JSpinner.class
                ),
                config.tuningTrick4ImpossibleDegrees()
        );
        setSpinnerValue(
                findChildComponent(
                        liveSecondaryTuningRow,
                        JSpinner.class
                ),
                config.tuningSecondaryValue()
        );
        setSpinnerValue(
                findChildComponent(
                        liveVarialShoveStartRow,
                        JSpinner.class
                ),
                config.tuningVarialShoveStartPercent()
        );
        setSpinnerValue(
                findChildComponent(
                        liveVarialShoveEndRow,
                        JSpinner.class
                ),
                config.tuningVarialShoveEndPercent()
        );
        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Board catch (%)"
                ),
                config.tuningCatchPercent()
        );
        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Board touchdown (%)"
                ),
                config.tuningTouchdownPercent()
        );
        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Queued exit cycle"
                ),
                config.tuningQueuedExitCycle()
        );
        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Queued start cycle"
                ),
                config.tuningQueuedStartCycle()
        );

        setComboValue(
                liveTrick5BoardFrameSlotRow,
                config.trick5BoardFrameSlot()
        );
        setCheckBoxValue(
                liveTrick5BoardFrameLockRow,
                config.trick5BoardFrameLock()
        );
        setSpinnerValue(
                findChildComponent(liveTrick5BoardXRow, JSpinner.class),
                config.trick5BoardFrameX()
        );
        setSpinnerValue(
                findChildComponent(liveTrick5BoardYRow, JSpinner.class),
                config.trick5BoardFrameY()
        );
        setSpinnerValue(
                findChildComponent(liveTrick5BoardZRow, JSpinner.class),
                config.trick5BoardFrameZ()
        );
        setSpinnerValue(
                findChildComponent(liveTrick5BoardPitchRow, JSpinner.class),
                config.trick5BoardFramePitch()
        );
        setSpinnerValue(
                findChildComponent(liveTrick5BoardYawRow, JSpinner.class),
                config.trick5BoardFrameYaw()
        );
        setSpinnerValue(
                findChildComponent(liveTrick5BoardRollRow, JSpinner.class),
                config.trick5BoardFrameRoll()
        );

        final boolean trick5BoardFrameEditable =
                !config.trick5BoardFrameLock();

        setSpinnerEnabled(liveTrick5BoardXRow, trick5BoardFrameEditable);
        setSpinnerEnabled(liveTrick5BoardYRow, trick5BoardFrameEditable);
        setSpinnerEnabled(liveTrick5BoardZRow, trick5BoardFrameEditable);
        setSpinnerEnabled(liveTrick5BoardPitchRow, trick5BoardFrameEditable);
        setSpinnerEnabled(liveTrick5BoardYawRow, trick5BoardFrameEditable);
        setSpinnerEnabled(liveTrick5BoardRollRow, trick5BoardFrameEditable);

        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Trick position (cycle)"
                ),
                config.trickInspectorCycle()
        );
        setCheckBoxValue(
                getConfigRow(
                        configPanel,
                        "Enable trick inspector"
                ),
                config.trickInspectorEnabled()
        );

        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Pose sequence length"
                ),
                config.poseSequenceLength()
        );
        setTextValue(
                getConfigRow(
                        configPanel,
                        "Player animation IDs"
                ),
                config.poseAnimationIds()
        );
        setTextValue(
                getConfigRow(
                        configPanel,
                        "Player frames"
                ),
                config.poseFrames()
        );

        setCheckBoxValue(
                getConfigRow(
                        configPanel,
                        "Advanced pose timing"
                ),
                config.advancedPoseTiming()
        );
        setTextValue(
                getConfigRow(
                        configPanel,
                        "Pose timing (%)"
                ),
                config.poseTimingPercentages()
        );

        setCheckBoxValue(
                getConfigRow(
                        configPanel,
                        "Enable animation inspector"
                ),
                config.animationTest()
        );
        setCheckBoxValue(
                getConfigRow(
                        configPanel,
                        "Freeze frame"
                ),
                config.freezeFrame()
        );
        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Animation ID"
                ),
                config.animationId()
        );
        setSpinnerValue(
                findLabeledSpinner(
                        configPanel,
                        "Animation frame"
                ),
                config.animationFrame()
        );
    }

    private void hideTrickSelectorLabels(Component component) {
        if (component instanceof JLabel) {
            final JLabel label = (JLabel) component;
            if ("Trick".equals(label.getText())) {
                final Container row = label.getParent();
                final JComboBox<?> comboBox =
                        findChildComponent(
                                row,
                                JComboBox.class
                        );

                if (comboBox != null) {
                    // the long trick names leave RuneLite too little
                    // room for this label and it collapses into a stray dot.
                    // The dropdown already makes the row's purpose clear, so
                    // hide only the selector label and give the combo the row.
                    final Dimension zero = new Dimension(0, 0);
                    label.setVisible(false);
                    label.setMinimumSize(zero);
                    label.setPreferredSize(zero);
                    label.setMaximumSize(zero);

                    final Dimension minimum = comboBox.getMinimumSize();
                    comboBox.setMinimumSize(
                            new Dimension(0, minimum.height)
                    );
                }
            }
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                hideTrickSelectorLabels(child);
            }
        }
    }

    private void refreshVisibleConfigPanel(Component configPanel) {
        if (configPanel == null
                || !configPanel.isShowing()
                || !isSkateScapeConfigPanel(configPanel)) {
            return;
        }

        final boolean previousSuppress =
                perTrickEditorWritesSuppressed;

        applyingLiveConfigPanel = true;
        perTrickEditorWritesSuppressed = true;
        try {
            restoreCachedTuningRows(configPanel);
            syncVisibleConfigEditorValues(configPanel);
            hideTrickSelectorLabels(configPanel);
            applyRuneLiteConfigSpinnerFixes(configPanel);
        } finally {
            perTrickEditorWritesSuppressed = previousSuppress;
            applyingLiveConfigPanel = false;
        }
    }

    private void setConfigRowVisible(
            JLabel label,
            boolean visible) {

        if (label == null) {
            return;
        }

        final Container row = label.getParent();
        if (row != null) {
            row.setVisible(visible);
        }
    }

    /*
     * DynamicGridLayout still leaves its inter-row gap around an invisible
     * config item. For controls that do not apply to the selected trick,
     * remove the complete RuneLite item row. Cache removed rows so they can be
     * restored on the next live refresh without rebuilding ConfigPanel.
     */
    private void configureRotationDegreesSpinner(
            JLabel label,
            JSpinner spinner,
            String labelText,
            int degrees,
            int maximumDegrees,
            String tooltip) {

        if (label != null) {
            label.setText(labelText);
            label.setToolTipText(tooltip);
            setConfigRowVisible(label, true);
        }

        if (spinner != null
                && spinner.getModel() instanceof SpinnerNumberModel) {

            final SpinnerNumberModel model =
                    (SpinnerNumberModel) spinner.getModel();
            final boolean floatingModel =
                    spinner.getValue() instanceof Double
                            || spinner.getValue() instanceof Float;

            if (floatingModel) {
                model.setMinimum(180.0);
                model.setMaximum((double) maximumDegrees);
                model.setStepSize(180.0);
            } else {
                model.setMinimum(180);
                model.setMaximum(maximumDegrees);
                model.setStepSize(180);
            }

            final int safeDegrees =
                    maximumDegrees <= 360
                            ? plugin.normalizeShoveItDegrees(degrees)
                            : plugin.normalizeTrick5RotationDegrees(degrees);

            setSpinnerValue(spinner, safeDegrees);

            if (spinner.getEditor() instanceof JSpinner.NumberEditor) {
                ((JSpinner.NumberEditor) spinner.getEditor())
                        .getFormat()
                        .applyPattern("0.######");
            }
        }
    }

    private void configurePercentageSpinner(JSpinner spinner) {
        if (spinner == null
                || !(spinner.getModel() instanceof SpinnerNumberModel)) {
            return;
        }

        final SpinnerNumberModel model =
                (SpinnerNumberModel) spinner.getModel();

        model.setMinimum(0.0);
        model.setMaximum(100.0);
        model.setStepSize(0.01);

        final double current =
                spinner.getValue() instanceof Number
                        ? ((Number) spinner.getValue()).doubleValue()
                        : 0.0;

        if (current < 0.0 || current > 100.0) {
            setSpinnerValue(
                    spinner,
                    Math.max(0.0, Math.min(100.0, current))
            );
        }

        if (spinner.getEditor() instanceof JSpinner.NumberEditor) {
            ((JSpinner.NumberEditor) spinner.getEditor())
                    .getFormat()
                    .applyPattern("0.##");
        }
    }

    private void removeConfigRow(JLabel label) {
        if (label == null) {
            return;
        }

        final Container row = label.getParent();
        if (row == null) {
            return;
        }

        final Container section = row.getParent();
        if (section != null) {
            section.remove(row);
        }
    }

    private void removeCachedConfigRow(Component row) {
        if (row == null) {
            return;
        }

        final Container section = row.getParent();
        if (section != null) {
            section.remove(row);
        }
    }

    /*
     * Some shared Trick Tuning rows mean different things for different
     * tricks, so one static @ConfigItem position cannot express every layout.
     * Move the complete RuneLite row immediately before the selected trick's
     * corresponding start control after a live refresh.
     */
    private void moveConfigRowBefore(
            JLabel movingLabel,
            JLabel anchorLabel) {

        if (movingLabel == null || anchorLabel == null) {
            return;
        }

        final Container movingRow = movingLabel.getParent();
        final Container anchorRow = anchorLabel.getParent();

        if (movingRow == null
                || anchorRow == null
                || movingRow == anchorRow) {
            return;
        }

        final Container section = movingRow.getParent();

        if (section == null
                || section != anchorRow.getParent()) {
            return;
        }

        final int movingIndex =
                section.getComponentZOrder(movingRow);

        final int anchorIndex =
                section.getComponentZOrder(anchorRow);

        if (movingIndex < 0
                || anchorIndex < 0) {
            return;
        }

        // Avoid reordering a row that is already in the requested
        // position. Repeated remove/add operations fire HierarchyEvents and
        // can recursively queue more config-panel fixes on Swing's EDT,
        // eventually making RuneLite stop responding to mouse input.
        if (movingIndex == anchorIndex - 1) {
            return;
        }

        final int insertIndex =
                movingIndex < anchorIndex
                        ? Math.max(0, anchorIndex - 1)
                        : anchorIndex;

        section.remove(movingRow);
        section.add(movingRow, insertIndex);
    }

    private int getLiveInspectorMaxCycle() {
        return plugin.getLiveInspectorMaxCycleForConfigPanel();
    }

    private boolean isSkateScapeConfigPanel(Component configPanel) {
        return containsLabelText(configPanel, "Trick Tuning")
                && containsLabelText(configPanel, "Animation Inspector");
    }

    private String getSpinnerLabel(JSpinner spinner) {
        Component current = spinner;

        while (current != null
                && !"net.runelite.client.plugins.config.ConfigPanel"
                        .equals(current.getClass().getName())) {

            final Container parent = current.getParent();
            if (parent == null) {
                break;
            }

            for (Component sibling : parent.getComponents()) {
                if (sibling instanceof JLabel) {
                    final String text =
                            ((JLabel) sibling).getText();

                    if (text != null && !text.trim().isEmpty()) {
                        return text;
                    }
                }
            }

            current = parent;
        }

        return "";
    }

    private JSpinner findLabeledSpinner(
            Component component,
            String labelText) {

        if (!(component instanceof Container)) {
            return null;
        }

        final Container container =
                (Container) component;

        boolean hasMatchingLabel = false;
        JSpinner directSpinner = null;

        for (Component child : container.getComponents()) {
            if (child instanceof JLabel
                    && labelText.equals(((JLabel) child).getText())) {
                hasMatchingLabel = true;
            } else if (child instanceof JSpinner) {
                directSpinner = (JSpinner) child;
            }
        }

        if (hasMatchingLabel && directSpinner != null) {
            return directSpinner;
        }

        for (Component child : container.getComponents()) {
            final JSpinner found =
                    findLabeledSpinner(
                            child,
                            labelText
                    );

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private void applyRuneLiteConfigSpinnerFixes(Component configPanel) {
        if (!isSkateScapeConfigPanel(configPanel)) {
            return;
        }

        /*
         * Pose sequence length has a real per-trick UI maximum.
         */
        final JSpinner poseLengthSpinner =
                findLabeledSpinner(
                        configPanel,
                        "Pose sequence length"
                );

        if (poseLengthSpinner != null
                && poseLengthSpinner.getModel()
                        instanceof SpinnerNumberModel) {

            final SpinnerNumberModel poseLengthModel =
                    (SpinnerNumberModel) poseLengthSpinner.getModel();

            poseLengthModel.setMaximum(
                    plugin.maxPoseSequenceLength(
                            config.poseSelectedTrick().getSlot()
                    )
            );
        }

        /*
         * Trick Tuning presentation. Trick order is:
         *   1 Kickflip
         *   2 360 Shove-it
         *   3 Varial Kickflip
         *   4 Kickflip 360 Shove-it Body Varial
         *   5 Christ Air
         *
         * Shared controls are relabelled/removed so each selected trick only
         * exposes settings that actually belong to that trick.
         */
        final int tuningSlot =
                config.tuningSelectedTrick().getSlot();

        /*
         * Christ Air uses frame-by-frame skateboard authoring. Generic
         * pop/flip/shove/catch controls do not drive that board choreography,
         * so they are removed from the live editor. The board-frame controls,
         * including the per-frame lock, appear only for Christ Air.
         */
        if (tuningSlot == 5) {
            removeCachedConfigRow(livePopHeightRow);
            removeCachedConfigRow(livePopStartRow);
            removeCachedConfigRow(livePrimaryStartRow);
            removeCachedConfigRow(livePrimaryEndRow);
            removeCachedConfigRow(liveKickDegreesRow);
            removeCachedConfigRow(liveVarialShoveStartRow);
            removeCachedConfigRow(liveVarialShoveEndRow);
            removeCachedConfigRow(liveSecondaryTuningRow);
            removeCachedConfigRow(liveCatchRow);
            removeCachedConfigRow(liveTouchdownRow);
            removeCachedConfigRow(liveImpossibleDegreesRow);
        } else {
            removeCachedConfigRow(liveTrick5BoardFrameSlotRow);
            removeCachedConfigRow(liveTrick5BoardFrameLockRow);
            removeCachedConfigRow(liveTrick5BoardXRow);
            removeCachedConfigRow(liveTrick5BoardYRow);
            removeCachedConfigRow(liveTrick5BoardZRow);
            removeCachedConfigRow(liveTrick5BoardPitchRow);
            removeCachedConfigRow(liveTrick5BoardYawRow);
            removeCachedConfigRow(liveTrick5BoardRollRow);
        }

        final JLabel primaryStartLabel =
                findLabel(
                        configPanel,
                        "Kickflip starts (%)",
                        "Shove-it starts (%)"
                );

        final JLabel primaryEndLabel =
                findLabel(
                        configPanel,
                        "Kickflip ends (%)",
                        "Shove-it ends (%)"
                );

        final JLabel trick5KickDegreesLabel =
                findLabel(
                        configPanel,
                        "Christ Air Kickflip rotation (degrees)",
                        "Kickflip rotation (degrees)"
                );

        JSpinner trick5KickDegreesSpinner =
                findLabeledSpinner(
                        configPanel,
                        "Christ Air Kickflip rotation (degrees)"
                );

        if (trick5KickDegreesSpinner == null) {
            trick5KickDegreesSpinner =
                    findLabeledSpinner(
                            configPanel,
                            "Kickflip rotation (degrees)"
                    );
        }

        final JLabel secondaryLabel =
                findLabel(
                        configPanel,
                        "360 Shove-it starts (%)",
                        "Shove-it rotation (degrees)"
                );

        JSpinner secondarySpinner =
                findLabeledSpinner(
                        configPanel,
                        "360 Shove-it starts (%)"
                );

        if (secondarySpinner == null) {
            secondarySpinner =
                    findLabeledSpinner(
                            configPanel,
                            "Shove-it rotation (degrees)"
                    );
        }

        final JLabel varialShoveStartLabel =
                findLabel(
                        configPanel,
                        "Varial Shove-it starts (%)"
                );

        final JLabel varialShoveEndLabel =
                findLabel(
                        configPanel,
                        "Varial Shove-it ends (%)"
                );

        final JLabel trick4ImpossibleDegreesLabel =
                findLabel(
                        configPanel,
                        "360 Shove-it rotation (degrees)"
                );

        final JSpinner trick4ImpossibleDegreesSpinner =
                findLabeledSpinner(
                        configPanel,
                        "360 Shove-it rotation (degrees)"
                );

        final JLabel catchLabel =
                findLabel(
                        configPanel,
                        "Board catch (%)"
                );

        configurePercentageSpinner(
                findLabeledSpinner(configPanel, "Pop starts (%)")
        );
        configurePercentageSpinner(
                findChildComponent(
                        primaryStartLabel == null
                                ? null
                                : primaryStartLabel.getParent(),
                        JSpinner.class
                )
        );
        configurePercentageSpinner(
                findChildComponent(
                        primaryEndLabel == null
                                ? null
                                : primaryEndLabel.getParent(),
                        JSpinner.class
                )
        );
        configurePercentageSpinner(
                findChildComponent(
                        liveVarialShoveStartRow,
                        JSpinner.class
                )
        );
        configurePercentageSpinner(
                findChildComponent(
                        liveVarialShoveEndRow,
                        JSpinner.class
                )
        );
        configurePercentageSpinner(
                findLabeledSpinner(configPanel, "Board catch (%)")
        );
        configurePercentageSpinner(
                findLabeledSpinner(configPanel, "Board touchdown (%)")
        );

        /*
         * The shared Kickflip-degree row has independent per-trick backing
         * values. Christ Air removes this generic row from the live editor
         * above because its board choreography is frame-authored.
         */
        if (tuningSlot == 1
                || tuningSlot == 3
                || tuningSlot == 4
                || tuningSlot == 5) {

            final int kickDegrees =
                    tuningSlot == 1
                            ? plugin.getKickflipDegrees()
                            : tuningSlot == 3
                                    ? plugin.getVarialKickflipDegrees()
                                    : tuningSlot == 4
                                            ? plugin.getTrick4KickflipDegrees()
                                            : plugin.getTrick5KickflipDegrees();

            final int kickMaximum =
                    tuningSlot == 5
                            ? 720
                            : 360;

            final String kickTooltip =
                    tuningSlot == 5
                            ? "Choose a 180, 360, 540 or 720 degree Kickflip rotation for Christ Air"
                            : "Choose a 180 or 360 degree Kickflip rotation";

            configureRotationDegreesSpinner(
                    trick5KickDegreesLabel,
                    trick5KickDegreesSpinner,
                    "Kickflip rotation (degrees)",
                    kickDegrees,
                    kickMaximum,
                    kickTooltip
            );
        } else {
            removeConfigRow(trick5KickDegreesLabel);
        }

        if (tuningSlot == 4) {
            configureRotationDegreesSpinner(
                    trick4ImpossibleDegreesLabel,
                    trick4ImpossibleDegreesSpinner,
                    "360 Shove-it rotation (degrees)",
                    plugin.getTrick4ImpossibleDegrees(),
                    360,
                    "Choose a 180 or 360 degree Shove-it rotation"
            );
        } else {
            removeConfigRow(trick4ImpossibleDegreesLabel);
        }

        if (catchLabel != null) {
            catchLabel.setToolTipText(
                    tuningSlot == 4
                            ? "Percentage through the MAIN timeline where the board is caught. This also ends the Shove-it rotation."
                            : "Percentage through the MAIN timeline where the board is caught. Fully independent from the other timing points"
            );
        }

        /*
         * Varial Kickflip uses the extra Shove-it start/end rows. Christ Air
         * keeps separate backing values, but its generic timing rows are
         * removed from the live editor in favor of frame authoring.
         */
        if (tuningSlot == 3 || tuningSlot == 5) {
            if (varialShoveStartLabel != null) {
                varialShoveStartLabel.setText("Shove-it starts (%)");
                varialShoveStartLabel.setToolTipText(
                        tuningSlot == 3
                                ? "Percentage through the MAIN timeline where the Varial Kickflip's Shove-it rotation starts"
                                : "Percentage through the MAIN timeline where Christ Air's Shove-it rotation starts"
                );
            }

            if (varialShoveEndLabel != null) {
                varialShoveEndLabel.setText("Shove-it ends (%)");
                varialShoveEndLabel.setToolTipText(
                        tuningSlot == 3
                                ? "Percentage through the MAIN timeline where the Varial Kickflip's Shove-it rotation ends"
                                : "Percentage through the MAIN timeline where Christ Air's Shove-it rotation ends"
                );
            }

            setConfigRowVisible(varialShoveStartLabel, true);
            setConfigRowVisible(varialShoveEndLabel, true);
        } else {
            removeConfigRow(varialShoveStartLabel);
            removeConfigRow(varialShoveEndLabel);
        }

        if (tuningSlot == 2) {
            if (primaryStartLabel != null) {
                primaryStartLabel.setText("Shove-it starts (%)");
                primaryStartLabel.setToolTipText(
                        "Percentage through the MAIN timeline where the Shove-it rotation starts"
                );
            }

            if (primaryEndLabel != null) {
                primaryEndLabel.setText("Shove-it ends (%)");
                primaryEndLabel.setToolTipText(
                        "Percentage through the MAIN timeline where the Shove-it rotation ends"
                );
            }

            configureRotationDegreesSpinner(
                    secondaryLabel,
                    secondarySpinner,
                    "Shove-it rotation (degrees)",
                    plugin.getShoveItDegrees(),
                    360,
                    "Choose a 180 or 360 degree Shove-it rotation"
            );
        } else if (tuningSlot == 3) {
            if (primaryStartLabel != null) {
                primaryStartLabel.setText("Kickflip starts (%)");
                primaryStartLabel.setToolTipText(
                        "Percentage through the MAIN timeline where the Varial Kickflip's Kickflip rotation starts"
                );
            }

            if (primaryEndLabel != null) {
                primaryEndLabel.setText("Kickflip ends (%)");
                primaryEndLabel.setToolTipText(
                        "Percentage through the MAIN timeline where the Varial Kickflip's Kickflip rotation ends"
                );
            }

            configureRotationDegreesSpinner(
                    secondaryLabel,
                    secondarySpinner,
                    "Shove-it rotation (degrees)",
                    plugin.getVarialShoveDegrees(),
                    360,
                    "Choose a 180 or 360 degree Shove-it rotation for the Varial Kickflip"
            );
        } else if (tuningSlot == 5) {
            if (primaryStartLabel != null) {
                primaryStartLabel.setText("Kickflip starts (%)");
                primaryStartLabel.setToolTipText(
                        "Percentage through the MAIN timeline where Christ Air's Kickflip rotation starts"
                );
            }

            if (primaryEndLabel != null) {
                primaryEndLabel.setText("Kickflip ends (%)");
                primaryEndLabel.setToolTipText(
                        "Percentage through the MAIN timeline where Christ Air's Kickflip rotation ends"
                );
            }

            configureRotationDegreesSpinner(
                    secondaryLabel,
                    secondarySpinner,
                    "Shove-it rotation (degrees)",
                    plugin.getTrick5ShoveDegrees(),
                    720,
                    "Choose a 180, 360, 540 or 720 degree Shove-it rotation for Christ Air"
            );
        } else {
            if (primaryStartLabel != null) {
                primaryStartLabel.setText("Kickflip starts (%)");
                primaryStartLabel.setToolTipText(
                        "Percentage through the MAIN timeline where the primary Kickflip rotation starts"
                );
            }

            if (primaryEndLabel != null) {
                primaryEndLabel.setText("Kickflip ends (%)");
                primaryEndLabel.setToolTipText(
                        "Percentage through the MAIN timeline where the primary Kickflip rotation ends"
                );
            }

            if (secondaryLabel != null) {
                secondaryLabel.setText("360 Shove-it starts (%)");
                secondaryLabel.setToolTipText(
                        "Kickflip 360 Shove-it Body Varial secondary Shove-it start. Independent from Kickflip end, catch and touchdown"
                );
            }

            if (tuningSlot == 4) {
                setConfigRowVisible(secondaryLabel, true);

                configurePercentageSpinner(secondarySpinner);
            } else {
                removeConfigRow(secondaryLabel);
            }
        }

        /*
         * Keep each visible rotation selector immediately above the timing
         * control it modifies.
         */
        if (tuningSlot == 1) {
            moveConfigRowBefore(
                    trick5KickDegreesLabel,
                    primaryStartLabel
            );
        } else if (tuningSlot == 2) {
            moveConfigRowBefore(
                    secondaryLabel,
                    primaryStartLabel
            );
        } else if (tuningSlot == 3) {
            moveConfigRowBefore(
                    trick5KickDegreesLabel,
                    primaryStartLabel
            );
            moveConfigRowBefore(
                    secondaryLabel,
                    varialShoveStartLabel
            );
        } else if (tuningSlot == 4) {
            moveConfigRowBefore(
                    trick5KickDegreesLabel,
                    primaryStartLabel
            );
            moveConfigRowBefore(
                    trick4ImpossibleDegreesLabel,
                    secondaryLabel
            );
        } else if (tuningSlot == 5) {
            moveConfigRowBefore(
                    trick5KickDegreesLabel,
                    primaryStartLabel
            );
            moveConfigRowBefore(
                    secondaryLabel,
                    varialShoveStartLabel
            );
        }

        /*
         * Trick Inspector's declared range is intentionally broad, but while
         * an inspector timeline is live, make the spinner stop at the real
         * final cycle as well. The global key guard below is the fallback if
         * the timeline changes after this panel was built.
         */
        final JSpinner inspectorCycleSpinner =
                findLabeledSpinner(
                        configPanel,
                        "Trick position (cycle)"
                );

        if (inspectorCycleSpinner != null
                && inspectorCycleSpinner.getModel()
                        instanceof SpinnerNumberModel) {

            ((SpinnerNumberModel) inspectorCycleSpinner.getModel())
                    .setMaximum(getLiveInspectorMaxCycle());
        }

        /*
         * Animation Inspector bounds. Keep the normal, clean
         * "Animation frame" label, but make the actual spinners obey the
         * real limits instead of only clamping after the fact.
         */
        final JSpinner animationIdSpinner =
                findLabeledSpinner(
                        configPanel,
                        "Animation ID"
                );

        if (animationIdSpinner != null
                && animationIdSpinner.getModel()
                        instanceof SpinnerNumberModel) {

            final SpinnerNumberModel animationIdModel =
                    (SpinnerNumberModel) animationIdSpinner.getModel();

            final int currentAnimationId =
                    ((Number) animationIdSpinner.getValue()).intValue();

            if (currentAnimationId > maxSafeAnimationId) {
                animationIdSpinner.setValue(maxSafeAnimationId);
            } else if (currentAnimationId < 0) {
                animationIdSpinner.setValue(0);
            }

            animationIdModel.setMinimum(0);
            animationIdModel.setMaximum(maxSafeAnimationId);
        }

        final JSpinner animationFrameSpinner =
                findLabeledSpinner(
                        configPanel,
                        "Animation frame"
                );

        if (animationFrameSpinner != null
                && animationFrameSpinner.getModel()
                        instanceof SpinnerNumberModel
                && config.animationTest()
                && plugin.getAnimationBrowseMaxFrame() >= 0) {

            final SpinnerNumberModel animationFrameModel =
                    (SpinnerNumberModel) animationFrameSpinner.getModel();

            final int currentFrame =
                    ((Number) animationFrameSpinner.getValue()).intValue();

            if (currentFrame > plugin.getAnimationBrowseMaxFrame()) {
                animationFrameSpinner.setValue(plugin.getAnimationBrowseMaxFrame());
            } else if (currentFrame < 0) {
                animationFrameSpinner.setValue(0);
            }

            animationFrameModel.setMinimum(0);
            animationFrameModel.setMaximum(plugin.getAnimationBrowseMaxFrame());
        }

        configPanel.revalidate();
        configPanel.repaint();
    }

    /*
     * REFLECTION-FREE CONFIG REFRESH.
     *
     * ConfigPanel.rebuild() is package-private and Plugin Hub rules forbid
     * reflection. Refresh the visible Swing controls in place instead of
     * reaching into RuneLite's package-private ConfigPanel internals.
     */
    void refreshOpenRuneLiteConfigPanel() {
        SwingUtilities.invokeLater(() -> {
            try {
                for (Window window : Window.getWindows()) {
                    if (refreshVisibleConfigPanelInTree(window)) {
                        break;
                    }
                }
            } catch (Exception ex) {
                log.debug(
                        "Could not refresh live SkateScape config panel",
                        ex
                );
            } finally {
                /*
                 * A dropdown change intentionally suppresses stale focus-loss
                 * writes while the shared controls are being repopulated.
                 */
                perTrickEditorWritesSuppressed = false;
            }
        });
    }

    private boolean refreshVisibleConfigPanelInTree(Component component) {
        if (component == null) {
            return false;
        }

        if ("net.runelite.client.plugins.config.ConfigPanel"
                .equals(component.getClass().getName())
                && component.isShowing()
                && isSkateScapeConfigPanel(component)) {

            refreshVisibleConfigPanel(component);
            return true;
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (refreshVisibleConfigPanelInTree(child)) {
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * Apply dynamic spinner limits without rebuilding ConfigPanel. Rebuilding
     * after every Animation ID change would drop focus just to update the frame
     * maximum.
     */
    void applyOpenRuneLiteConfigPanelFixes() {
        SwingUtilities.invokeLater(() -> {
            try {
                for (Window window : Window.getWindows()) {
                    if (applyVisibleConfigPanelFixesInTree(window)) {
                        break;
                    }
                }
            } catch (Exception ex) {
                log.debug(
                        "Could not update live SkateScape ConfigPanel limits",
                        ex
                );
            }
        });
    }

    private boolean applyVisibleConfigPanelFixesInTree(Component component) {
        if (component == null) {
            return false;
        }

        if ("net.runelite.client.plugins.config.ConfigPanel"
                .equals(component.getClass().getName())
                && component.isShowing()
                && isSkateScapeConfigPanel(component)) {

            applyRuneLiteConfigSpinnerFixes(component);
            return true;
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (applyVisibleConfigPanelFixesInTree(child)) {
                    return true;
                }
            }
        }

        return false;
    }
}
