package com.atari.spritemaker.export;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.TransformSettings;
import java.awt.Color;
import java.util.List;

public class BxlExporter {

    public static String export(SpriteModel model, TransformSettings globalFallback) {
        List<Color[][]> frames = model.getAnimationFrames();
        int N = frames.size();
        int gridSize = model.getGridSize();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"gridSize\": ").append(gridSize).append(",\n");
        sb.append("  \"frameCount\": ").append(N).append(",\n");
        sb.append("  \"loop\": true,\n");

        sb.append("  \"palette\": [");
        Color[] pal = model.getPalette();
        for (int i = 0; i < 5; i++) {
            if (i > 0) sb.append(", ");
            if (pal[i] == null) sb.append("null");
            else sb.append("\"#").append(hex(pal[i])).append("\"");
        }
        sb.append("],\n");

        sb.append("  \"frames\": [\n");
        for (int fi = 0; fi < N; fi++) {
            Color[][] frame = frames.get(fi);
            sb.append("    [\n");
            for (int r = 0; r < gridSize; r++) {
                sb.append("      [");
                for (int c = 0; c < gridSize; c++) {
                    if (c > 0) sb.append(", ");
                    Color col = frame[r][c];
                    if (col == null) sb.append("null");
                    else sb.append("\"#").append(hex(col)).append("\"");
                }
                sb.append("]");
                if (r < gridSize - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("    ]");
            if (fi < N - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"transitions\": [\n");
        for (int i = 0; i < N - 1; i++) {
            if (i > 0) sb.append(",\n");
            TransformSettings ts = model.getTransformForTransition(i);
            if (ts == null) ts = globalFallback;
            sb.append("    {\n");
            sb.append("      \"fromFrame\": ").append(i).append(",\n");
            sb.append("      \"toFrame\": ").append(i + 1).append(",\n");
            sb.append("      \"effectType\": ").append(ts.animEffectType).append(",\n");
            appendEffect(sb, ts);
            sb.append("    }");
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendEffect(StringBuilder sb, TransformSettings ts) {
        switch (ts.animEffectType) {
            case 1:
                sb.append("      \"pop\": {\n");
                sb.append("        \"explodeSpeedMs\": ").append(ts.animExplodeSpeedMs).append(",\n");
                sb.append("        \"explodeStrength\": ").append(ts.animExplodeStrength).append(",\n");
                sb.append("        \"unsplodeSpeedMs\": ").append(ts.animUnsplodeSpeedMs).append(",\n");
                sb.append("        \"unsplodeStrength\": ").append(ts.animUnsplodeStrength).append(",\n");
                sb.append("        \"gravityPush\": ").append(ts.animGravityPush).append(",\n");
                sb.append("        \"gravityPull\": ").append(ts.animGravityPull).append(",\n");
                sb.append("        \"gravityFocalX\": ").append(ts.animGravityFocalX).append(",\n");
                sb.append("        \"gravityFocalY\": ").append(ts.animGravityFocalY).append(",\n");
                sb.append("        \"popHoldMs\": ").append(ts.animPopHoldMs).append(",\n");
                sb.append("        \"extendMs\": ").append(ts.animExtendMs).append(",\n");
                sb.append("        \"easing\": ").append(ts.animEasing).append(",\n");
                sb.append("        \"spread\": ").append(ts.animSpread).append(",\n");
                sb.append("        \"wallDamping\": ").append(ts.animWallDamping).append(",\n");
                sb.append("        \"stayInCanvas\": ").append(ts.animStayInCanvas).append(",\n");
                sb.append("        \"stayAtFocus\": ").append(ts.animPopStayAtFocus).append("\n");
                sb.append("      }\n");
                break;
            case 2:
                sb.append("      \"twist\": {\n");
                sb.append("        \"firstSpeedMs\": ").append(ts.animTwistFirstSpeedMs).append(",\n");
                sb.append("        \"secondSpeedMs\": ").append(ts.animTwistSecondSpeedMs).append(",\n");
                sb.append("        \"firstSmooth\": ").append(ts.animTwistFirstSmooth).append(",\n");
                sb.append("        \"secondSmooth\": ").append(ts.animTwistSecondSmooth).append(",\n");
                sb.append("        \"direction\": ").append(ts.animTwistDirection).append(",\n");
                sb.append("        \"fullSpin\": ").append(ts.animTwistFullSpin).append(",\n");
                sb.append("        \"spreadGap\": ").append(ts.animTwistSpreadGap).append("\n");
                sb.append("      }\n");
                break;
            case 3:
                sb.append("      \"morph\": {\n");
                sb.append("        \"speedMs\": ").append(ts.animMorphSpeedMs).append(",\n");
                sb.append("        \"holdMs\": ").append(ts.animMorphHoldMs).append(",\n");
                sb.append("        \"focalX\": ").append(ts.animFocalX).append(",\n");
                sb.append("        \"focalY\": ").append(ts.animFocalY).append(",\n");
                sb.append("        \"fadeDeaths\": ").append(ts.animMorphFadeDeaths).append("\n");
                sb.append("      }\n");
                break;
            case 4:
                sb.append("      \"spring\": {\n");
                sb.append("        \"stiffness\": ").append(ts.animSpringStiffness).append(",\n");
                sb.append("        \"damping\": ").append(ts.animSpringDamping).append(",\n");
                sb.append("        \"impulse\": ").append(ts.animSpringImpulse).append(",\n");
                sb.append("        \"speedMs\": ").append(ts.animSpringSpeedMs).append(",\n");
                sb.append("        \"holdMs\": ").append(ts.animSpringHoldMs).append("\n");
                sb.append("      }\n");
                break;
            default: // 0 = burst
                sb.append("      \"burst\": {\n");
                sb.append("        \"spread\": ").append(ts.animSpread).append(",\n");
                sb.append("        \"speedMs\": ").append(ts.animSpeedMs).append(",\n");
                sb.append("        \"holdMs\": ").append(ts.animHoldMs).append(",\n");
                sb.append("        \"easing\": ").append(ts.animEasing).append(",\n");
                sb.append("        \"focalX\": ").append(ts.animFocalX).append(",\n");
                sb.append("        \"focalY\": ").append(ts.animFocalY).append(",\n");
                sb.append("        \"spin\": ").append(ts.animSpin).append(",\n");
                sb.append("        \"spinStrength\": ").append(ts.animSpinStrength).append("\n");
                sb.append("      }\n");
                break;
        }
    }

    private static String hex(Color c) {
        return String.format("%06X", c.getRGB() & 0xFFFFFF);
    }
}
