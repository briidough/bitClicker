package com.atari.spritemaker.ui;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.*;

public class RetroTheme {

    // All colors are ColorUIResource so they can be replaced when L&F switches back
    public static final Color BG_DARKEST  = new ColorUIResource(0x0e, 0x0a, 0x1a);
    public static final Color BG_DARK     = new ColorUIResource(0x1a, 0x12, 0x28);
    public static final Color BG_MID      = new ColorUIResource(0x2a, 0x1e, 0x40);
    public static final Color FG_TEXT     = new ColorUIResource(0xc8, 0xd4, 0xff);
    public static final Color FG_ACCENT   = new ColorUIResource(0x00, 0xe5, 0xff);
    public static final Color FG_SELECTED = new ColorUIResource(0xff, 0x95, 0x00);
    public static final Color BORDER_HI   = new ColorUIResource(0x5a, 0x3a, 0x90);
    public static final Color BORDER_LO   = new ColorUIResource(0x06, 0x03, 0x12);
    public static final Color GRID_LINE   = new ColorUIResource(0x2a, 0x1e, 0x44);
    public static final Color CHECK_A     = new ColorUIResource(0x1e, 0x14, 0x30);
    public static final Color CHECK_B     = new ColorUIResource(0x2a, 0x1e, 0x44);

    // FontUIResource so it is replaceable by L&F switch
    public static final Font RETRO_FONT = new FontUIResource("Monospaced", Font.BOLD, 11);

    public static boolean active = false;

    // ── theme-aware color accessors (used by custom paintComponent code) ──────

    public static Color gridLine()              { return active ? GRID_LINE : Color.LIGHT_GRAY; }
    public static Color checkA()                { return active ? CHECK_A   : new Color(0xcccccc); }
    public static Color checkB()                { return active ? CHECK_B   : Color.WHITE; }
    public static Color paletteSelectBorder()   { return active ? FG_ACCENT : Color.WHITE; }
    public static Color paletteUnselectBorder() { return active ? GRID_LINE : Color.DARK_GRAY; }
    public static Color noFramesTextColor()     { return active ? BORDER_HI : Color.GRAY; }

    // ── keys we override so reset() can clear them ────────────────────────────

    private static final String[] KEYS = {
        "ButtonUI", "ToggleButtonUI",
        "Panel.background", "Panel.foreground",
        "Label.foreground", "Label.font",
        "ComboBox.background", "ComboBox.foreground", "ComboBox.selectionBackground",
        "ComboBox.selectionForeground", "ComboBox.font", "ComboBox.disabledBackground",
        "ComboBox.disabledForeground",
        "Slider.background", "Slider.foreground", "Slider.tickColor", "Slider.font",
        "ScrollBar.background", "ScrollBar.thumb", "ScrollBar.thumbDarkShadow",
        "ScrollBar.thumbHighlight", "ScrollBar.thumbShadow", "ScrollBar.track",
        "ScrollBar.trackHighlight",
        "ScrollPane.background", "Viewport.background",
        "SplitPane.background", "SplitPaneDivider.background",
        "MenuBar.background", "MenuBar.foreground", "MenuBar.font", "MenuBar.border",
        "Menu.background", "Menu.foreground", "Menu.selectionBackground",
        "Menu.selectionForeground", "Menu.font",
        "MenuItem.background", "MenuItem.foreground", "MenuItem.selectionBackground",
        "MenuItem.selectionForeground", "MenuItem.font", "MenuItem.acceleratorForeground",
        "CheckBoxMenuItem.background", "CheckBoxMenuItem.foreground",
        "CheckBoxMenuItem.selectionBackground", "CheckBoxMenuItem.selectionForeground",
        "CheckBoxMenuItem.font",
        "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.border",
        "CheckBox.background", "CheckBox.foreground", "CheckBox.font",
        "RadioButton.background", "RadioButton.foreground", "RadioButton.font",
        "TextField.background", "TextField.foreground", "TextField.caretForeground",
        "TextField.selectionBackground", "TextField.selectionForeground",
        "TextField.font", "TextField.border",
        "OptionPane.background", "OptionPane.messageForeground", "OptionPane.font",
        "FileChooser.background",
        "List.background", "List.foreground", "List.selectionBackground",
        "List.selectionForeground", "List.font",
        "Table.background", "Table.foreground", "Table.selectionBackground",
        "Table.selectionForeground", "Table.gridColor",
        "TableHeader.background", "TableHeader.foreground", "TableHeader.font",
        "TabbedPane.background", "TabbedPane.foreground", "TabbedPane.selected",
        "TabbedPane.selectHighlight", "TabbedPane.tabAreaBackground",
        "ToolTip.background", "ToolTip.foreground", "ToolTip.font", "ToolTip.border",
    };

    public static void apply() {
        active = true;
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception ignored) {}

        UIManager.put("ButtonUI",       RetroButtonUI.class.getName());
        UIManager.put("ToggleButtonUI", RetroToggleButtonUI.class.getName());

        UIManager.put("Panel.background",  BG_DARK);
        UIManager.put("Panel.foreground",  FG_TEXT);

        UIManager.put("Label.foreground",  FG_TEXT);
        UIManager.put("Label.font",        RETRO_FONT);

        UIManager.put("ComboBox.background",          BG_MID);
        UIManager.put("ComboBox.foreground",          FG_ACCENT);
        UIManager.put("ComboBox.selectionBackground", FG_SELECTED);
        UIManager.put("ComboBox.selectionForeground", BG_DARKEST);
        UIManager.put("ComboBox.font",                RETRO_FONT);
        UIManager.put("ComboBox.disabledBackground",  BG_DARK);
        UIManager.put("ComboBox.disabledForeground",  new ColorUIResource(0x3a, 0x3a, 0x6a));

        UIManager.put("Slider.background", BG_DARK);
        UIManager.put("Slider.foreground", FG_ACCENT);
        UIManager.put("Slider.tickColor",  BORDER_HI);
        UIManager.put("Slider.font",       RETRO_FONT);

        UIManager.put("ScrollBar.background",      BG_DARK);
        UIManager.put("ScrollBar.thumb",           BG_MID);
        UIManager.put("ScrollBar.thumbDarkShadow", BORDER_LO);
        UIManager.put("ScrollBar.thumbHighlight",  BORDER_HI);
        UIManager.put("ScrollBar.thumbShadow",     BG_DARKEST);
        UIManager.put("ScrollBar.track",           BG_DARKEST);
        UIManager.put("ScrollBar.trackHighlight",  BG_DARKEST);

        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("Viewport.background",   BG_DARK);

        UIManager.put("SplitPane.background",        BG_DARK);
        UIManager.put("SplitPaneDivider.background", BG_MID);

        UIManager.put("MenuBar.background", BG_DARKEST);
        UIManager.put("MenuBar.foreground", FG_TEXT);
        UIManager.put("MenuBar.font",       RETRO_FONT);
        UIManager.put("MenuBar.border",     new BorderUIResource(new LineBorder(BORDER_LO, 1)));

        UIManager.put("Menu.background",          BG_DARKEST);
        UIManager.put("Menu.foreground",          FG_TEXT);
        UIManager.put("Menu.selectionBackground", BG_MID);
        UIManager.put("Menu.selectionForeground", FG_ACCENT);
        UIManager.put("Menu.font",                RETRO_FONT);

        UIManager.put("MenuItem.background",           BG_DARKEST);
        UIManager.put("MenuItem.foreground",           FG_TEXT);
        UIManager.put("MenuItem.selectionBackground",  BG_MID);
        UIManager.put("MenuItem.selectionForeground",  FG_ACCENT);
        UIManager.put("MenuItem.font",                 RETRO_FONT);
        UIManager.put("MenuItem.acceleratorForeground", BORDER_HI);

        UIManager.put("CheckBoxMenuItem.background",          BG_DARKEST);
        UIManager.put("CheckBoxMenuItem.foreground",          FG_TEXT);
        UIManager.put("CheckBoxMenuItem.selectionBackground", BG_MID);
        UIManager.put("CheckBoxMenuItem.selectionForeground", FG_ACCENT);
        UIManager.put("CheckBoxMenuItem.font",                RETRO_FONT);

        UIManager.put("PopupMenu.background", BG_DARKEST);
        UIManager.put("PopupMenu.foreground", FG_TEXT);
        UIManager.put("PopupMenu.border",     new BorderUIResource(new LineBorder(BORDER_HI, 1)));

        UIManager.put("CheckBox.background",    BG_DARK);
        UIManager.put("CheckBox.foreground",    FG_TEXT);
        UIManager.put("CheckBox.font",          RETRO_FONT);
        UIManager.put("RadioButton.background", BG_DARK);
        UIManager.put("RadioButton.foreground", FG_TEXT);
        UIManager.put("RadioButton.font",       RETRO_FONT);

        UIManager.put("TextField.background",          BG_DARKEST);
        UIManager.put("TextField.foreground",          FG_TEXT);
        UIManager.put("TextField.caretForeground",     FG_ACCENT);
        UIManager.put("TextField.selectionBackground", BG_MID);
        UIManager.put("TextField.selectionForeground", FG_ACCENT);
        UIManager.put("TextField.font",                RETRO_FONT);
        UIManager.put("TextField.border",              new BorderUIResource(new LineBorder(BORDER_HI, 1)));

        UIManager.put("OptionPane.background",        BG_DARK);
        UIManager.put("OptionPane.messageForeground", FG_TEXT);
        UIManager.put("OptionPane.font",              RETRO_FONT);

        UIManager.put("FileChooser.background",       BG_DARK);
        UIManager.put("List.background",              BG_DARKEST);
        UIManager.put("List.foreground",              FG_TEXT);
        UIManager.put("List.selectionBackground",     BG_MID);
        UIManager.put("List.selectionForeground",     FG_ACCENT);
        UIManager.put("List.font",                    RETRO_FONT);

        UIManager.put("Table.background",          BG_DARKEST);
        UIManager.put("Table.foreground",          FG_TEXT);
        UIManager.put("Table.selectionBackground", BG_MID);
        UIManager.put("Table.selectionForeground", FG_ACCENT);
        UIManager.put("Table.gridColor",           GRID_LINE);
        UIManager.put("TableHeader.background",    BG_MID);
        UIManager.put("TableHeader.foreground",    FG_ACCENT);
        UIManager.put("TableHeader.font",          RETRO_FONT);

        UIManager.put("TabbedPane.background",       BG_DARK);
        UIManager.put("TabbedPane.foreground",       FG_TEXT);
        UIManager.put("TabbedPane.selected",         BG_MID);
        UIManager.put("TabbedPane.selectHighlight",  BORDER_HI);
        UIManager.put("TabbedPane.tabAreaBackground",BG_DARKEST);

        UIManager.put("ToolTip.background", BG_MID);
        UIManager.put("ToolTip.foreground", FG_ACCENT);
        UIManager.put("ToolTip.font",       RETRO_FONT);
        UIManager.put("ToolTip.border",     new BorderUIResource(new LineBorder(BORDER_HI, 1)));
    }

    public static void reset() {
        active = false;
        // Null out every user-layer override so Metal L&F defaults take over
        for (String key : KEYS) UIManager.put(key, null);
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception ignored) {}
    }
}
