package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import javax.swing.*;
import java.awt.*;

public class PixelTwistPanel extends JPanel {

    private final SpriteModel model;

    private final JSlider sliderFirstSpeed, sliderSecondSpeed;
    private final JSlider sliderFirstSmooth, sliderSecondSmooth;
    private final JComboBox<String> comboDirection;
    private final JCheckBox chkFullSpin, chkSpread;

    private boolean updating = false;

    public PixelTwistPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel dirLbl = new JLabel("Direction:");
        dirLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(dirLbl);
        add(Box.createVerticalStrut(2));
        comboDirection = new JComboBox<>(new String[]{"Clockwise", "Counter-clockwise"});
        comboDirection.setSelectedIndex(model.getAnimTwistDirection());
        comboDirection.setAlignmentX(Component.LEFT_ALIGNMENT);
        comboDirection.setMaximumSize(new Dimension(Integer.MAX_VALUE, comboDirection.getPreferredSize().height));
        comboDirection.addActionListener(e -> { if (!updating) model.setAnimTwistDirection(comboDirection.getSelectedIndex()); });
        add(comboDirection);

        add(Box.createVerticalStrut(8));
        chkFullSpin = new JCheckBox("Full Spin (360°)");
        chkFullSpin.setSelected(model.isAnimTwistFullSpin());
        chkFullSpin.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkFullSpin.setMaximumSize(new Dimension(Integer.MAX_VALUE, chkFullSpin.getPreferredSize().height));
        chkFullSpin.addActionListener(e -> { if (!updating) model.setAnimTwistFullSpin(chkFullSpin.isSelected()); });
        add(chkFullSpin);

        add(Box.createVerticalStrut(10));
        JLabel firstLbl = new JLabel("First Half (0 → 180°):");
        firstLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(firstLbl);
        add(Box.createVerticalStrut(4));

        sliderFirstSpeed = new JSlider(AnimConfig.TWIST_FIRST_SPEED_MIN, AnimConfig.TWIST_FIRST_SPEED_MAX,
                model.getAnimTwistFirstSpeedMs() / AnimConfig.TWIST_SPEED_SCALE);
        sliderFirstSpeed.addChangeListener(e -> {
            if (!updating) model.setAnimTwistFirstSpeedMs(sliderFirstSpeed.getValue() * AnimConfig.TWIST_SPEED_SCALE);
        });
        add(wrapSlider("Speed (×10 ms)", sliderFirstSpeed, AnimConfig.TWIST_SPEED_SCALE));

        add(Box.createVerticalStrut(4));
        sliderFirstSmooth = new JSlider(AnimConfig.TWIST_FIRST_SMOOTH_MIN, AnimConfig.TWIST_FIRST_SMOOTH_MAX,
                model.getAnimTwistFirstSmooth());
        sliderFirstSmooth.addChangeListener(e -> { if (!updating) model.setAnimTwistFirstSmooth(sliderFirstSmooth.getValue()); });
        add(wrapSlider("Smooth", sliderFirstSmooth));

        add(Box.createVerticalStrut(10));
        JLabel secondLbl = new JLabel("Second Half (180° → end):");
        secondLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(secondLbl);
        add(Box.createVerticalStrut(4));

        sliderSecondSpeed = new JSlider(AnimConfig.TWIST_SECOND_SPEED_MIN, AnimConfig.TWIST_SECOND_SPEED_MAX,
                model.getAnimTwistSecondSpeedMs() / AnimConfig.TWIST_SPEED_SCALE);
        sliderSecondSpeed.addChangeListener(e -> {
            if (!updating) model.setAnimTwistSecondSpeedMs(sliderSecondSpeed.getValue() * AnimConfig.TWIST_SPEED_SCALE);
        });
        add(wrapSlider("Speed (×10 ms)", sliderSecondSpeed, AnimConfig.TWIST_SPEED_SCALE));

        add(Box.createVerticalStrut(4));
        sliderSecondSmooth = new JSlider(AnimConfig.TWIST_SECOND_SMOOTH_MIN, AnimConfig.TWIST_SECOND_SMOOTH_MAX,
                model.getAnimTwistSecondSmooth());
        sliderSecondSmooth.addChangeListener(e -> { if (!updating) model.setAnimTwistSecondSmooth(sliderSecondSmooth.getValue()); });
        add(wrapSlider("Smooth", sliderSecondSmooth));

        add(Box.createVerticalStrut(10));
        chkSpread = new JCheckBox("Spread");
        chkSpread.setSelected(model.isAnimTwistSpreadGap());
        chkSpread.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkSpread.setMaximumSize(new Dimension(Integer.MAX_VALUE, chkSpread.getPreferredSize().height));
        chkSpread.addActionListener(e -> { if (!updating) model.setAnimTwistSpreadGap(chkSpread.isSelected()); });
        add(chkSpread);

        add(Box.createVerticalStrut(12));
        JButton resetBtn = new JButton("Reset Defaults");
        resetBtn.addActionListener(e -> resetDefaults());
        resetBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, resetBtn.getPreferredSize().height));
        add(resetBtn);
    }

    public void refresh() {
        updating = true;
        comboDirection.setSelectedIndex(model.getAnimTwistDirection());
        chkFullSpin.setSelected(model.isAnimTwistFullSpin());
        sliderFirstSpeed.setValue(model.getAnimTwistFirstSpeedMs() / AnimConfig.TWIST_SPEED_SCALE);
        sliderFirstSmooth.setValue(model.getAnimTwistFirstSmooth());
        sliderSecondSpeed.setValue(model.getAnimTwistSecondSpeedMs() / AnimConfig.TWIST_SPEED_SCALE);
        sliderSecondSmooth.setValue(model.getAnimTwistSecondSmooth());
        chkSpread.setSelected(model.isAnimTwistSpreadGap());
        updating = false;
    }

    public void resetDefaults() {
        model.setAnimTwistFirstSpeedMs(AnimConfig.TWIST_FIRST_SPEED_DEF * AnimConfig.TWIST_SPEED_SCALE);
        sliderFirstSpeed.setValue(AnimConfig.TWIST_FIRST_SPEED_DEF);
        model.setAnimTwistSecondSpeedMs(AnimConfig.TWIST_SECOND_SPEED_DEF * AnimConfig.TWIST_SPEED_SCALE);
        sliderSecondSpeed.setValue(AnimConfig.TWIST_SECOND_SPEED_DEF);
        model.setAnimTwistFirstSmooth(AnimConfig.TWIST_FIRST_SMOOTH_DEF);
        sliderFirstSmooth.setValue(AnimConfig.TWIST_FIRST_SMOOTH_DEF);
        model.setAnimTwistSecondSmooth(AnimConfig.TWIST_SECOND_SMOOTH_DEF);
        sliderSecondSmooth.setValue(AnimConfig.TWIST_SECOND_SMOOTH_DEF);
        model.setAnimTwistDirection(0);
        comboDirection.setSelectedIndex(0);
        model.setAnimTwistFullSpin(true);
        chkFullSpin.setSelected(true);
        model.setAnimTwistSpreadGap(false);
        chkSpread.setSelected(false);
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
