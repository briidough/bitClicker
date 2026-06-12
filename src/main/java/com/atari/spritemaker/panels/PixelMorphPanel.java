package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import javax.swing.*;
import java.awt.*;

public class PixelMorphPanel extends JPanel {

    private final SpriteModel model;

    private final JSlider sliderMorphSpeed;
    private final JSlider sliderMorphHold;
    private final JSlider sliderFocalX;
    private final JSlider sliderFocalY;
    private final JCheckBox chkFadeDeaths;

    private boolean updating = false;

    public PixelMorphPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        sliderMorphSpeed = new JSlider(AnimConfig.MORPH_SPEED_MIN, AnimConfig.MORPH_SPEED_MAX, model.getAnimMorphSpeedMs());
        sliderMorphSpeed.addChangeListener(e -> { if (!updating) model.setAnimMorphSpeedMs(sliderMorphSpeed.getValue()); });
        add(wrapSlider("Speed (ms)", sliderMorphSpeed));

        add(Box.createVerticalStrut(6));
        sliderMorphHold = new JSlider(AnimConfig.MORPH_HOLD_MIN, AnimConfig.MORPH_HOLD_MAX, model.getAnimMorphHoldMs());
        sliderMorphHold.addChangeListener(e -> { if (!updating) model.setAnimMorphHoldMs(sliderMorphHold.getValue()); });
        add(wrapSlider("Hold (ms)", sliderMorphHold));

        add(Box.createVerticalStrut(12));
        JLabel focalLbl = new JLabel("Focal Point:");
        focalLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(focalLbl);
        add(Box.createVerticalStrut(4));

        sliderFocalX = new JSlider(AnimConfig.BURST_FOCAL_X_MIN, AnimConfig.BURST_FOCAL_X_MAX, model.getAnimFocalX());
        sliderFocalX.addChangeListener(e -> { if (!updating) model.setAnimFocalX(sliderFocalX.getValue()); });
        add(wrapSlider("X %", sliderFocalX));

        add(Box.createVerticalStrut(4));
        sliderFocalY = new JSlider(AnimConfig.BURST_FOCAL_Y_MIN, AnimConfig.BURST_FOCAL_Y_MAX, model.getAnimFocalY());
        sliderFocalY.addChangeListener(e -> { if (!updating) model.setAnimFocalY(sliderFocalY.getValue()); });
        add(wrapSlider("Y %", sliderFocalY));

        add(Box.createVerticalStrut(6));
        chkFadeDeaths = new JCheckBox("Fade Dying Pixels");
        chkFadeDeaths.setSelected(model.isAnimMorphFadeDeaths());
        chkFadeDeaths.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkFadeDeaths.setMaximumSize(new Dimension(Integer.MAX_VALUE, chkFadeDeaths.getPreferredSize().height));
        chkFadeDeaths.addActionListener(e -> { if (!updating) model.setAnimMorphFadeDeaths(chkFadeDeaths.isSelected()); });
        add(chkFadeDeaths);

        add(Box.createVerticalStrut(12));
        JButton resetBtn = new JButton("Reset Defaults");
        resetBtn.addActionListener(e -> resetDefaults());
        resetBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, resetBtn.getPreferredSize().height));
        add(resetBtn);
    }

    public void refresh() {
        updating = true;
        sliderMorphSpeed.setValue(model.getAnimMorphSpeedMs());
        sliderMorphHold.setValue(model.getAnimMorphHoldMs());
        sliderFocalX.setValue(model.getAnimFocalX());
        sliderFocalY.setValue(model.getAnimFocalY());
        chkFadeDeaths.setSelected(model.isAnimMorphFadeDeaths());
        updating = false;
    }

    public void resetDefaults() {
        model.setAnimMorphSpeedMs(AnimConfig.MORPH_SPEED_DEF); sliderMorphSpeed.setValue(AnimConfig.MORPH_SPEED_DEF);
        model.setAnimMorphHoldMs(AnimConfig.MORPH_HOLD_DEF);   sliderMorphHold.setValue(AnimConfig.MORPH_HOLD_DEF);
        model.setAnimFocalX(AnimConfig.BURST_FOCAL_X_DEF);     sliderFocalX.setValue(AnimConfig.BURST_FOCAL_X_DEF);
        model.setAnimFocalY(AnimConfig.BURST_FOCAL_Y_DEF);     sliderFocalY.setValue(AnimConfig.BURST_FOCAL_Y_DEF);
    }

    private JPanel wrapSlider(String name, JSlider slider) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(name + ": " + slider.getValue());
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, slider.getPreferredSize().height));
        slider.addChangeListener(e -> lbl.setText(name + ": " + slider.getValue()));
        row.add(lbl);
        row.add(slider);
        return row;
    }

    private JPanel wrapSlider(String name, JSlider slider, int scale) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(name + ": " + (slider.getValue() * scale));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, slider.getPreferredSize().height));
        slider.addChangeListener(e -> lbl.setText(name + ": " + (slider.getValue() * scale)));
        row.add(lbl);
        row.add(slider);
        return row;
    }
}
