package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import javax.swing.*;
import java.awt.*;

public class PixelPopPanel extends JPanel {

    private final SpriteModel model;

    private final JSlider sliderExplodeSpeed, sliderExplodeStrength;
    private final JSlider sliderExtendMs;
    private final JSlider sliderUnsplodeSpeed, sliderUnsplodeStrength;
    private final JSlider sliderGravPush, sliderGravPull;
    private final JSlider sliderGravFocalX, sliderGravFocalY;
    private final JSlider sliderPopHold;

    public PixelPopPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        sliderExplodeSpeed = new JSlider(AnimConfig.POP_EXPLODE_SPEED_MIN, AnimConfig.POP_EXPLODE_SPEED_MAX, model.getAnimExplodeSpeedMs());
        sliderExplodeSpeed.addChangeListener(e -> model.setAnimExplodeSpeedMs(sliderExplodeSpeed.getValue()));
        add(wrapSlider("Explode Speed (ms)", sliderExplodeSpeed));

        add(Box.createVerticalStrut(6));
        sliderExplodeStrength = new JSlider(AnimConfig.POP_EXPLODE_STRENGTH_MIN, AnimConfig.POP_EXPLODE_STRENGTH_MAX, model.getAnimExplodeStrength());
        sliderExplodeStrength.addChangeListener(e -> model.setAnimExplodeStrength(sliderExplodeStrength.getValue()));
        add(wrapSlider("Explode Strength", sliderExplodeStrength));

        add(Box.createVerticalStrut(6));
        sliderExtendMs = new JSlider(AnimConfig.POP_EXTEND_MS_MIN, AnimConfig.POP_EXTEND_MS_MAX, model.getAnimExtendMs());
        sliderExtendMs.addChangeListener(e -> model.setAnimExtendMs(sliderExtendMs.getValue()));
        add(wrapSlider("Extend Duration (ms)", sliderExtendMs));

        add(Box.createVerticalStrut(6));
        sliderUnsplodeSpeed = new JSlider(AnimConfig.POP_UNSPLODE_SPEED_MIN, AnimConfig.POP_UNSPLODE_SPEED_MAX, model.getAnimUnsplodeSpeedMs());
        sliderUnsplodeSpeed.addChangeListener(e -> model.setAnimUnsplodeSpeedMs(sliderUnsplodeSpeed.getValue()));
        add(wrapSlider("Unsplode Speed (ms)", sliderUnsplodeSpeed));

        add(Box.createVerticalStrut(6));
        sliderUnsplodeStrength = new JSlider(AnimConfig.POP_UNSPLODE_STRENGTH_MIN, AnimConfig.POP_UNSPLODE_STRENGTH_MAX, model.getAnimUnsplodeStrength());
        sliderUnsplodeStrength.addChangeListener(e -> model.setAnimUnsplodeStrength(sliderUnsplodeStrength.getValue()));
        add(wrapSlider("Unsplode Strength", sliderUnsplodeStrength));

        add(Box.createVerticalStrut(6));
        sliderGravPush = new JSlider(AnimConfig.POP_GRAV_PUSH_MIN, AnimConfig.POP_GRAV_PUSH_MAX, model.getAnimGravityPush());
        sliderGravPush.addChangeListener(e -> model.setAnimGravityPush(sliderGravPush.getValue()));
        add(wrapSlider("Gravity Push", sliderGravPush));

        add(Box.createVerticalStrut(6));
        sliderGravPull = new JSlider(AnimConfig.POP_GRAV_PULL_MIN, AnimConfig.POP_GRAV_PULL_MAX, model.getAnimGravityPull());
        sliderGravPull.addChangeListener(e -> model.setAnimGravityPull(sliderGravPull.getValue()));
        add(wrapSlider("Gravity Pull", sliderGravPull));

        add(Box.createVerticalStrut(12));
        JLabel gravFocalLbl = new JLabel("Gravity Target:");
        gravFocalLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(gravFocalLbl);
        add(Box.createVerticalStrut(4));

        sliderGravFocalX = new JSlider(AnimConfig.POP_GRAV_FOCAL_X_MIN, AnimConfig.POP_GRAV_FOCAL_X_MAX, model.getAnimGravityFocalX());
        sliderGravFocalX.addChangeListener(e -> model.setAnimGravityFocalX(sliderGravFocalX.getValue()));
        add(wrapSlider("X %", sliderGravFocalX));
        add(Box.createVerticalStrut(4));

        sliderGravFocalY = new JSlider(AnimConfig.POP_GRAV_FOCAL_Y_MIN, AnimConfig.POP_GRAV_FOCAL_Y_MAX, model.getAnimGravityFocalY());
        sliderGravFocalY.addChangeListener(e -> model.setAnimGravityFocalY(sliderGravFocalY.getValue()));
        add(wrapSlider("Y %", sliderGravFocalY));

        add(Box.createVerticalStrut(10));
        JCheckBox chkStayInCanvas = new JCheckBox("Stay in Canvas");
        chkStayInCanvas.setSelected(model.isAnimStayInCanvas());
        chkStayInCanvas.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkStayInCanvas.setMaximumSize(new Dimension(Integer.MAX_VALUE, chkStayInCanvas.getPreferredSize().height));
        chkStayInCanvas.addActionListener(e -> model.setAnimStayInCanvas(chkStayInCanvas.isSelected()));
        add(chkStayInCanvas);

        add(Box.createVerticalStrut(6));
        sliderPopHold = new JSlider(AnimConfig.POP_HOLD_MIN, AnimConfig.POP_HOLD_MAX, AnimConfig.POP_HOLD_DEF);
        sliderPopHold.addChangeListener(e -> model.setAnimPopHoldMs(sliderPopHold.getValue() * AnimConfig.POP_HOLD_SCALE));
        add(wrapSlider("Delay (ms)", sliderPopHold, AnimConfig.POP_HOLD_SCALE));

        add(Box.createVerticalStrut(12));
        JButton resetBtn = new JButton("Reset Defaults");
        resetBtn.addActionListener(e -> resetDefaults());
        resetBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, resetBtn.getPreferredSize().height));
        add(resetBtn);
    }

    public void resetDefaults() {
        model.setAnimExplodeSpeedMs(AnimConfig.POP_EXPLODE_SPEED_DEF);         sliderExplodeSpeed.setValue(AnimConfig.POP_EXPLODE_SPEED_DEF);
        model.setAnimExplodeStrength(AnimConfig.POP_EXPLODE_STRENGTH_DEF);     sliderExplodeStrength.setValue(AnimConfig.POP_EXPLODE_STRENGTH_DEF);
        model.setAnimUnsplodeSpeedMs(AnimConfig.POP_UNSPLODE_SPEED_DEF);       sliderUnsplodeSpeed.setValue(AnimConfig.POP_UNSPLODE_SPEED_DEF);
        model.setAnimUnsplodeStrength(AnimConfig.POP_UNSPLODE_STRENGTH_DEF);   sliderUnsplodeStrength.setValue(AnimConfig.POP_UNSPLODE_STRENGTH_DEF);
        model.setAnimGravityPush(AnimConfig.POP_GRAV_PUSH_DEF);                sliderGravPush.setValue(AnimConfig.POP_GRAV_PUSH_DEF);
        model.setAnimGravityPull(AnimConfig.POP_GRAV_PULL_DEF);                sliderGravPull.setValue(AnimConfig.POP_GRAV_PULL_DEF);
        model.setAnimGravityFocalX(AnimConfig.POP_GRAV_FOCAL_X_DEF);          sliderGravFocalX.setValue(AnimConfig.POP_GRAV_FOCAL_X_DEF);
        model.setAnimGravityFocalY(AnimConfig.POP_GRAV_FOCAL_Y_DEF);          sliderGravFocalY.setValue(AnimConfig.POP_GRAV_FOCAL_Y_DEF);
        model.setAnimPopHoldMs(AnimConfig.POP_HOLD_DEF);                       sliderPopHold.setValue(AnimConfig.POP_HOLD_DEF);
        model.setAnimExtendMs(AnimConfig.POP_EXTEND_MS_DEF);                   sliderExtendMs.setValue(AnimConfig.POP_EXTEND_MS_DEF);
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
