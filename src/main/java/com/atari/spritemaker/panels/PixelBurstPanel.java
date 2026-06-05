package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class PixelBurstPanel extends JPanel {

    private final SpriteModel model;

    private final JSlider sliderSpread, sliderSpeed, sliderHold;
    private final JSlider sliderFocalX, sliderFocalY, sliderSpinStrength;
    private final JComboBox<String> comboEasing, comboSpin;

    public PixelBurstPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        sliderSpread = new JSlider(AnimConfig.BURST_SPREAD_MIN, AnimConfig.BURST_SPREAD_MAX, model.getAnimSpread());
        sliderSpread.addChangeListener(e -> model.setAnimSpread(sliderSpread.getValue()));
        add(wrapSlider("Spread", sliderSpread));

        add(Box.createVerticalStrut(6));
        sliderSpeed = new JSlider(AnimConfig.BURST_SPEED_MIN, AnimConfig.BURST_SPEED_MAX, model.getAnimSpeedMs());
        sliderSpeed.addChangeListener(e -> model.setAnimSpeedMs(sliderSpeed.getValue()));
        add(wrapSlider("Speed (ms)", sliderSpeed));

        add(Box.createVerticalStrut(6));
        sliderHold = new JSlider(AnimConfig.BURST_HOLD_MIN, AnimConfig.BURST_HOLD_MAX, model.getAnimHoldMs());
        sliderHold.addChangeListener(e -> model.setAnimHoldMs(sliderHold.getValue()));
        add(wrapSlider("Hold (ms)", sliderHold));

        add(Box.createVerticalStrut(8));
        JLabel easingLbl = new JLabel("Easing:");
        easingLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(easingLbl);
        add(Box.createVerticalStrut(2));
        comboEasing = new JComboBox<>(new String[]{"Smooth", "Sharp", "Snappy"});
        comboEasing.setSelectedIndex(model.getAnimEasing());
        comboEasing.setAlignmentX(Component.LEFT_ALIGNMENT);
        comboEasing.setMaximumSize(new Dimension(Integer.MAX_VALUE, comboEasing.getPreferredSize().height));
        comboEasing.addActionListener(e -> model.setAnimEasing(comboEasing.getSelectedIndex()));
        add(comboEasing);

        add(Box.createVerticalStrut(12));
        JLabel focalLbl = new JLabel("Focal Point:");
        focalLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(focalLbl);
        add(Box.createVerticalStrut(4));

        sliderFocalX = new JSlider(AnimConfig.BURST_FOCAL_X_MIN, AnimConfig.BURST_FOCAL_X_MAX, model.getAnimFocalX());
        sliderFocalX.addChangeListener(e -> model.setAnimFocalX(sliderFocalX.getValue()));
        MouseAdapter focalDragTracker = new MouseAdapter() {
            public void mousePressed(MouseEvent e)  { model.setFocalActive(true); }
            public void mouseReleased(MouseEvent e) { model.setFocalActive(false); }
        };
        sliderFocalX.addMouseListener(focalDragTracker);
        add(wrapSlider("X %", sliderFocalX));

        add(Box.createVerticalStrut(4));
        sliderFocalY = new JSlider(AnimConfig.BURST_FOCAL_Y_MIN, AnimConfig.BURST_FOCAL_Y_MAX, model.getAnimFocalY());
        sliderFocalY.addChangeListener(e -> model.setAnimFocalY(sliderFocalY.getValue()));
        sliderFocalY.addMouseListener(focalDragTracker);
        add(wrapSlider("Y %", sliderFocalY));

        add(Box.createVerticalStrut(8));
        JLabel spinLbl = new JLabel("Spin:");
        spinLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(spinLbl);
        add(Box.createVerticalStrut(2));
        comboSpin = new JComboBox<>(new String[]{"None", "Clockwise", "Counter-clockwise"});
        comboSpin.setSelectedIndex(model.getAnimSpin());
        comboSpin.setAlignmentX(Component.LEFT_ALIGNMENT);
        comboSpin.setMaximumSize(new Dimension(Integer.MAX_VALUE, comboSpin.getPreferredSize().height));
        add(comboSpin);

        sliderSpinStrength = new JSlider(AnimConfig.BURST_SPIN_STRENGTH_MIN, AnimConfig.BURST_SPIN_STRENGTH_MAX, model.getAnimSpinStrength());
        sliderSpinStrength.addChangeListener(e -> model.setAnimSpinStrength(sliderSpinStrength.getValue()));
        JPanel spinStrengthSection = new JPanel();
        spinStrengthSection.setLayout(new BoxLayout(spinStrengthSection, BoxLayout.Y_AXIS));
        spinStrengthSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        spinStrengthSection.add(Box.createVerticalStrut(6));
        spinStrengthSection.add(wrapSlider("Spin Strength", sliderSpinStrength));
        spinStrengthSection.setVisible(model.getAnimSpin() != 0);
        add(spinStrengthSection);

        comboSpin.addActionListener(e -> {
            model.setAnimSpin(comboSpin.getSelectedIndex());
            spinStrengthSection.setVisible(comboSpin.getSelectedIndex() != 0);
            revalidate();
            repaint();
        });

        add(Box.createVerticalStrut(12));
        JButton resetBtn = new JButton("Reset Defaults");
        resetBtn.addActionListener(e -> resetDefaults());
        resetBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, resetBtn.getPreferredSize().height));
        add(resetBtn);
    }

    public void resetDefaults() {
        model.setAnimSpread(AnimConfig.BURST_SPREAD_DEF);         sliderSpread.setValue(AnimConfig.BURST_SPREAD_DEF);
        model.setAnimSpeedMs(AnimConfig.BURST_SPEED_DEF);         sliderSpeed.setValue(AnimConfig.BURST_SPEED_DEF);
        model.setAnimHoldMs(AnimConfig.BURST_HOLD_DEF);           sliderHold.setValue(AnimConfig.BURST_HOLD_DEF);
        model.setAnimEasing(AnimConfig.BURST_EASING_DEF);         comboEasing.setSelectedIndex(AnimConfig.BURST_EASING_DEF);
        model.setAnimFocalX(AnimConfig.BURST_FOCAL_X_DEF);        sliderFocalX.setValue(AnimConfig.BURST_FOCAL_X_DEF);
        model.setAnimFocalY(AnimConfig.BURST_FOCAL_Y_DEF);        sliderFocalY.setValue(AnimConfig.BURST_FOCAL_Y_DEF);
        model.setAnimSpin(AnimConfig.BURST_SPIN_DEF);             comboSpin.setSelectedIndex(AnimConfig.BURST_SPIN_DEF);
        model.setAnimSpinStrength(AnimConfig.BURST_SPIN_STRENGTH_DEF); sliderSpinStrength.setValue(AnimConfig.BURST_SPIN_STRENGTH_DEF);
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
}
