import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Icono del mod para Modrinth y CurseForge en pixel art de 16 bits: una cuadricula de 64x64 con cuatro cuadrantes (los colores de jugador: azul, rojo, verde y
 * amarillo, con luz arriba y sombra abajo), una cruz negra de division y un mando blanco en el centro, ampliada 8 veces sin suavizar a 512x512. Dibujo propio, sin
 * recursos de Mojang. Uso (desde la raiz, con Java 21): java tools/MakeIcon.java docs/publicar/webs/icono-512.png
 */
public class MakeIcon {
    static final int N = 64, SCALE = 8;
    static final int OUTLINE = 0xFF1A1A24;

    // Mitad izquierda del mando (12 columnas); la derecha es el espejo. '#' contorno, 'W' cuerpo, '.' nada.
    static final String[] HALF = {
        "..##########",
        ".#WWWWWWWWWW",
        "#WWWWWWWWWWW",
        "#WWWWWWWWWWW",
        "#WWWWWWWWWWW",
        "#WWWWWWWWWWW",
        "#WWWWWWWWWWW",
        "#WWWWWWWWWWW",
        "#WWWWWWW####",
        "#WWWWWWW#...",
        "#WWWWWW#....",
        ".#WWWW#.....",
        "..####......",
    };

    static int[] shade(int rgb, double k) {
        return new int[] {(int) (((rgb >> 16) & 255) * k), (int) (((rgb >> 8) & 255) * k), (int) ((rgb & 255) * k)};
    }

    static int pack(int[] c) {
        return 0xFF000000 | (Math.min(255, c[0]) << 16) | (Math.min(255, c[1]) << 8) | Math.min(255, c[2]);
    }

    public static void main(String[] args) throws Exception {
        int[][] base = {{0x6C8CFF, 0xA3B6FF, 0x4A64D0}, {0xFF5555, 0xFF8E8E, 0xCC3A3A}, {0x55FF55, 0x92FF92, 0x35C435}, {0xFFD84A, 0xFFEA90, 0xCFAE2C}}; // medio, luz, sombra
        int[][] px = new int[N][N];
        // Cuadrantes con una fila de luz arriba y otra de sombra abajo.
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int q = (x < N / 2 ? 0 : 1) + (y < N / 2 ? 0 : 2);
                int row = y % (N / 2);
                int tone = row == 0 || row == 1 ? 1 : row >= N / 2 - 3 ? 2 : 0;
                px[y][x] = 0xFF000000 | base[q][tone];
            }
        }
        for (int i = 0; i < N; i++) { // cruz de division, 2 pixeles de ancho
            for (int k = N / 2 - 1; k <= N / 2; k++) {
                px[i][k] = OUTLINE;
                px[k][i] = OUTLINE;
            }
        }

        // Mando: 24 x 13 pixeles de arte, cada uno de 2 x 2 de la cuadricula.
        int aw = HALF[0].length() * 2, ah = HALF.length;
        char[][] art = new char[ah][aw];
        for (int r = 0; r < ah; r++) {
            for (int c = 0; c < HALF[r].length(); c++) {
                art[r][c] = HALF[r].charAt(c);
                art[r][aw - 1 - c] = HALF[r].charAt(c);
            }
        }
        int[][] col = new int[ah][aw];
        for (int r = 0; r < ah; r++) {
            for (int c = 0; c < aw; c++) {
                char ch = art[r][c];
                if (ch == '#') col[r][c] = OUTLINE;
                else if (ch == 'W') {
                    boolean top = r > 0 && art[r - 1][c] == '#', bottom = r + 1 < ah && art[r + 1][c] == '#';
                    col[r][c] = top ? 0xFFFFFFFF : bottom ? 0xFFA6A6BA : 0xFFE4E4F0;
                }
            }
        }
        for (int d = -2; d <= 2; d++) { // cruceta
            col[4 + d][5] = OUTLINE;
            col[4][5 + d] = OUTLINE;
        }
        int[][] btn = {{2, 18}, {4, 16}, {4, 20}, {6, 18}};
        int[] btnColor = {0xFFFFD84A, 0xFF6C8CFF, 0xFFFF5555, 0xFF55FF55}; // Y, X, B, A
        for (int i = 0; i < 4; i++) col[btn[i][0]][btn[i][1]] = btnColor[i];
        int ox = (N - aw * 2) / 2, oy = (N - ah * 2) / 2;
        for (int r = 0; r < ah; r++) { // sombra: el cuadrante de debajo, mas oscuro
            for (int c = 0; c < aw; c++) {
                if (art[r][c] == '.') continue;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int x = ox + c * 2 + dx, y = oy + r * 2 + dy + 3;
                        if (y < N) px[y][x] = pack(shade(px[y][x], 0.55));
                    }
                }
            }
        }
        for (int r = 0; r < ah; r++) {
            for (int c = 0; c < aw; c++) {
                if (art[r][c] == '.') continue;
                for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) px[oy + r * 2 + dy][ox + c * 2 + dx] = col[r][c];
            }
        }

        // Esquinas redondeadas a pixel y un borde oscuro de 1 pixel.
        boolean[][] in = new boolean[N][N];
        int R = 9;
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double cx = x < R ? R : x >= N - R ? N - R : x + 0.5, cy = y < R ? R : y >= N - R ? N - R : y + 0.5;
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                in[y][x] = dx * dx + dy * dy <= R * R;
            }
        }
        BufferedImage img = new BufferedImage(N * SCALE, N * SCALE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                if (!in[y][x]) continue;
                boolean edge = x == 0 || y == 0 || x == N - 1 || y == N - 1 || !in[y - 1][x] || !in[y + 1][x] || !in[y][x - 1] || !in[y][x + 1];
                int argb = edge ? OUTLINE : px[y][x];
                for (int dy = 0; dy < SCALE; dy++) for (int dx = 0; dx < SCALE; dx++) img.setRGB(x * SCALE + dx, y * SCALE + dy, argb);
            }
        }
        File out = new File(args.length > 0 ? args[0] : "icono-512.png");
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("icono pixel art: " + out + " (" + N * SCALE + "x" + N * SCALE + ", cuadricula " + N + "x" + N + ")");
    }
}
