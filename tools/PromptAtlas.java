import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Empaqueta los iconos de "Free Input Prompts" (JulioCacko, CC0; ver CREDITS) en un atlas por estilo y genera la hoja de
 * comparacion docs/prompts-styles.png.
 *
 * Uso (desde la raiz del proyecto): java tools/PromptAtlas.java
 *
 * Atlas: rejilla de COLS columnas de CELL px; el icono i de la familia f (orden XBOX, PLAYSTATION, NINTENDO = PadType.ordinal())
 * va en la celda f * ICONS.length + i. El orden de ICONS debe coincidir con PadIcons.Icon. Una celda vacia = el boton no existe
 * en ese set (PadIcons no dibuja nada).
 */
public final class PromptAtlas {
    static final String[] ICONS = {"A", "B", "X", "Y", "LB", "RB", "LT", "RT", "BACK", "START", "L3", "R3",
            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD", "L", "R"};
    static final String[] STYLES = {"default_color", "default_white", "retro_color", "retro_white", "stylized"};
    static final int CELL = 32, COLS = 8;

    // Nombres de archivo (sin prefijo ni sufijo de estilo) por familia, en el orden de ICONS. {c} = variante de color o blanca.
    static final String[] XBOX = {"A_{c}", "B_{c}", "X_{c}", "Y_{c}", "LB", "RB", "LT", "RT", "Share", "X",
            "Left_Stick_Click", "Right_Stick_Click", "Dpad_Up", "Dpad_Down", "Dpad_Left", "Dpad_Right", "Dpad", "L", "R"};
    static final String[] PS = {"Cross{c}", "Circle{c}", "Square{c}", "Triangle{c}", "L1", "R1", "L2", "R2", "Share", "Options",
            "L3", "R3", "Dpad_Up", "Dpad_Down", "Dpad_Left", "Dpad_Right", "Dpad", "L", "R"};
    static final String[] SWITCH = {"B", "A", "Y", "X", "LB", "RB", "LT", "RT", "Minus", "Plus",
            "L", "R", "Dpad_Up", "Dpad_Down", "Dpad_Left", "Dpad_Right", "Dpad", "L", "R"};

    public static void main(String[] args) throws Exception {
        File root = new File("assets-src/input-prompts");
        File outDir = new File("src/main/resources/assets/consolepad/textures/gui/prompts");
        outDir.mkdirs();
        BufferedImage[][][] all = new BufferedImage[STYLES.length][3][];
        for (int s = 0; s < STYLES.length; s++) {
            String style = STYLES[s];
            boolean color = !style.endsWith("white");
            boolean retro = style.startsWith("retro");
            boolean stylized = style.equals("stylized");
            String[][] defs = {XBOX, PS, SWITCH};
            BufferedImage atlas = new BufferedImage(COLS * CELL, 8 * CELL, BufferedImage.TYPE_INT_ARGB);
            for (int f = 0; f < 3; f++) {
                String dir, prefix, suffix;
                String variant = retro ? "Retro" : "Default";
                if (f == 0) { dir = "XGamepad/" + variant; prefix = "T_X_"; suffix = retro ? "_Retro" : ""; }
                else if (f == 1 && stylized) { dir = "P4Gamepad/Stylized"; prefix = "T_P4_"; suffix = "_Stylized"; }
                else if (f == 1) { dir = "P5Gamepad/" + variant; prefix = "T_P5_"; suffix = retro ? "_Retro" : ""; }
                else { dir = "SGamepad/" + variant; prefix = "T_S_"; suffix = retro ? "_Retro" : ""; }
                Map<String, File> files = new HashMap<>();
                for (File file : new File(root, dir).listFiles()) files.put(file.getName().toLowerCase(), file);
                all[s][f] = new BufferedImage[ICONS.length];
                for (int i = 0; i < ICONS.length; i++) {
                    String token = defs[f][i];
                    String c = f == 0 ? (color ? "Color" : "White") : (color ? "_Color" : "");
                    token = token.replace("{c}", c);
                    File file = files.get((prefix + token + suffix + ".png").toLowerCase());
                    if (file == null) {
                        System.out.println("FALTA " + style + " familia " + f + " icono " + ICONS[i] + " (" + prefix + token + suffix + ")");
                        continue;
                    }
                    BufferedImage small = shrink(ImageIO.read(file), CELL);
                    all[s][f][i] = small;
                    int idx = f * ICONS.length + i;
                    atlas.createGraphics().drawImage(small, (idx % COLS) * CELL, (idx / COLS) * CELL, null);
                }
            }
            File png = new File(outDir, style + ".png");
            ImageIO.write(atlas, "png", png);
            // Filtro lineal: los iconos se dibujan reducidos o ampliados segun la escala de interfaz.
            Files.writeString(new File(outDir, style + ".png.mcmeta").toPath(), "{\"texture\":{\"blur\":true}}\n");
            System.out.println(png + " " + png.length() + " bytes");
        }
        contactSheet(all, new File("docs/prompts-styles.png"));
    }

    static BufferedImage shrink(BufferedImage src, int size) {
        BufferedImage cur = src;
        while (cur.getWidth() > size) {
            int w = Math.max(size, cur.getWidth() / 2);
            BufferedImage next = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cur, 0, 0, w, w, null);
            g.dispose();
            cur = next;
        }
        return cur;
    }

    static void contactSheet(BufferedImage[][][] all, File out) throws Exception {
        int cell = 44, labelW = 150, rowH = cell + 2;
        int w = labelW + ICONS.length * cell, h = STYLES.length * (3 * rowH + 22);
        BufferedImage sheet = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x3C3C3C));
        g.fillRect(0, 0, w, h);
        String[] fam = {"Xbox", "PlayStation", "Nintendo"};
        for (int s = 0; s < STYLES.length; s++) {
            int y0 = s * (3 * rowH + 22);
            g.setColor(Color.YELLOW);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString((s + 1) + ". " + STYLES[s], 6, y0 + 15);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            for (int f = 0; f < 3; f++) {
                int y = y0 + 22 + f * rowH;
                g.setColor(Color.WHITE);
                g.drawString(fam[f], 10, y + cell / 2 + 4);
                for (int i = 0; i < ICONS.length; i++) {
                    if (all[s][f][i] != null) g.drawImage(all[s][f][i], labelW + i * cell, y, cell, cell, null);
                }
            }
        }
        g.dispose();
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
        System.out.println(out + " " + out.length() + " bytes");
    }
}
