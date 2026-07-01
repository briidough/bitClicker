package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import javax.swing.*;
import java.awt.*;

public class PixelSpringPanel extends JPanel {

    private final SpriteModel model;

    private final JSlider sliderStiffness;
    private final JSlider sliderDamping;
    private final JSlider sliderImpulse;
    private final JSlider sliderSpeed;
    private final JSlider sliderHold;

    private boolean updating = false;

    public PixelSpringPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        sliderStiffness = new JSlider(AnimConfig.SPRING_STIFFNESS_MIN, AnimConfig.SPRING_STIFFNESS_MAX, model.getAnimSpringStiffness());
        sliderStiffness.addChangeListener(e -> { if (!updating) model.setAnimSpringStiffness(sliderStiffness.getValue()); });
        add(wrapSlider("Stiffness", sliderStiffness));

        add(Box.createVerticalStrut(6));
        sliderDamping = new JSlider(AnimConfig.SPRING_DAMPING_MIN, AnimConfig.SPRING_DAMPING_MAX, model.getAnimSpringDamping());
        sliderDamping.addChangeListener(e -> { if (!updating) model.setAnimSpringDamping(sliderDamping.getValue()); });
        add(wrapSlider("Damping (% critical)", sliderDamping));

        add(Box.createVerticalStrut(6));
        sliderImpulse = new JSlider(AnimConfig.SPRING_IMPULSE_MIN, AnimConfig.SPRING_IMPULSE_MAX, model.getAnimSpringImpulse());
        sliderImpulse.addChangeListener(e -> { if (!updating) model.setAnimSpringImpulse(sliderImpulse.getValue()); });
        add(wrapSlider("Impulse", sliderImpulse));

        add(Box.createVerticalStrut(6));
        sliderSpeed = new JSlider(AnimConfig.SPRING_SPEED_MIN, AnimConfig.SPRING_SPEED_MAX, model.getAnimSpringSpeedMs());
        sliderSpeed.addChangeListener(e -> { if (!updating) model.setAnimSpringSpeedMs(sliderSpeed.getValue()); });
        add(wrapSlider("Duration (ms)", sliderSpeed));

        add(Box.createVerticalStrut(6));
        sliderHold = new JSlider(AnimConfig.SPRING_HOLD_MIN, AnimConfig.SPRING_HOLD_MAX, model.getAnimSpringHoldMs());
        sliderHold.addChangeListener(e -> { if (!updating) model.setAnimSpringHoldMs(sliderHold.getValue()); });
        add(wrapSlider("Hold (ms)", sliderHold));

        add(Box.createVerticalStrut(12));
        JButton resetBtn = new JButton("Reset Defaults");
        resetBtn.addActionListener(e -> resetDefaults());
        resetBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, resetBtn.getPreferredSize().height));
        add(resetBtn);
    }

    public void refresh() {
        updating = true;
        sliderStiffness.setValue(model.getAnimSpringStiffness());
        sliderDamping.setValue(model.getAnimSpringDamping());
        sliderImpulse.setValue(model.getAnimSpringImpulse());
        sliderSpeed.setValue(model.getAnimSpringSpeedMs());
        sliderHold.setValue(model.getAnimSpringHoldMs());
        updating = false;
    }

    public void resetDefaults() {
        model.setAnimSpringStiffness(AnimConfig.SPRING_STIFFNESS_DEF); sliderStiffness.setValue(AnimConfig.SPRING_STIFFNESS_DEF);
        model.setAnimSpringDamping(AnimConfig.SPRING_DAMPING_DEF);     sliderDamping.setValue(AnimConfig.SPRING_DAMPING_DEF);
        model.setAnimSpringImpulse(AnimConfig.SPRING_IMPULSE_DEF);     sliderImpulse.setValue(AnimConfig.SPRING_IMPULSE_DEF);
        model.setAnimSpringSpeedMs(AnimConfig.SPRING_SPEED_DEF);       sliderSpeed.setValue(AnimConfig.SPRING_SPEED_DEF);
        model.setAnimSpringHoldMs(AnimConfig.SPRING_HOLD_DEF);         sliderHold.setValue(AnimConfig.SPRING_HOLD_DEF);
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
