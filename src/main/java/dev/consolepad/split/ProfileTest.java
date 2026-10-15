package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Perfiles locales (dentro de la casilla 49, con P2 dentro): crea dos perfiles con skins distintas; el id es una cuenta valida cuya skin por defecto es la pedida;
 * un jugador extra con perfil entra con esa cuenta (el servidor lo conoce con ese nombre y UUID) y su inventario es del perfil: con el perfil B no tiene lo que
 * guardo con el A, y al volver al A lo recupera; los ajustes del jugador se guardan en su perfil; no se puede cambiar de perfil con el jugador dentro.
 */
public final class ProfileTest {
    private static int t, phase;
    private static Profiles.Profile a, b, c;
    private static final String SKIN_FILE = "zz_autotest_skin.png";

    private ProfileTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    private static ServerPlayer p2(Minecraft mc, String account) {
        return mc.getSingleplayerServer().getPlayerList().getPlayerByName(account);
    }

    private static String skinPath(Minecraft mc, ServerPlayer sp) {
        var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(sp.getUUID());
        return info == null ? "(sin informacion)" : info.getSkin().texture().getPath();
    }

    private static void next() {
        phase++;
        t = 0;
    }

    /** Una vuelta por tick; true al terminar. */
    public static boolean run(Minecraft mc) {
        t++;
        if (t > 2400) {
            check("perfiles: la prueba se quedo esperando en la fase " + phase, false);
            phase = 7;
            t = 0;
        }
        switch (phase) {
            case 0 -> { // crear perfiles
                Profiles.reload();
                a = Profiles.create("Ana", 3, 0, ConsolePad.PADS.slot(1).settings);
                b = Profiles.create("Bruno", 11, 0, ConsolePad.PADS.slot(1).settings);
                check("perfiles: se crean dos perfiles con ids de cuenta validos y distintos (" + (a == null ? "-" : a.id) + ", " + (b == null ? "-" : b.id) + ")",
                        a != null && b != null && a.id.matches("[A-Za-z0-9_]{3,16}") && b.id.matches("[A-Za-z0-9_]{3,16}") && !a.id.equals(b.id));
                if (a == null || b == null) {
                    phase = 8;
                    return false;
                }
                check("perfiles: la skin del id coincide con la elegida (" + Profiles.skinOf(a.id) + " = 3, " + Profiles.skinOf(b.id) + " = 11)", Profiles.skinOf(a.id) == 3 && Profiles.skinOf(b.id) == 11);
                // Pantallas: la lista de perfiles y el perfil nuevo (crear desde la pantalla con nombre y skin)
                var list = new ProfilesScreen(0, null);
                list.init(mc, 427, 240);
                check("perfiles: la lista de perfiles muestra Ninguno, los perfiles y Nuevo (" + list.rowCount() + " filas)", list.rowCount() >= 4);
                var screenBefore = mc.screen;
                var form = new NewProfileScreen(0, null);
                form.init(mc, 427, 240);
                form.setName("Carla");
                form.setSkin(5);
                form.pressCreate();
                var carla = Profiles.all().stream().filter(p -> p.name.equals("Carla")).findFirst().orElse(null);
                check("perfiles: crear desde la pantalla de perfil nuevo respeta nombre y skin (" + (carla == null ? "no creado" : carla.id + " skin " + Profiles.skinOf(carla.id)) + ")",
                        carla != null && Profiles.skinOf(carla.id) == 5 && mc.screen instanceof ProfileScreen);
                if (carla != null) Profiles.delete(carla);
                mc.setScreen(screenBefore);
                next();
            }
            case 1 -> { // P2 sale y se le pone el perfil A
                if (AutoTest.leaveTo(mc, 1) && t > 10) {
                    String err = Profiles.use(1, a);
                    var s = ConsolePad.PADS.slot(1).settings;
                    check("perfiles: usar el perfil A en P2 le pone nombre, perfil y la cuenta del perfil (error=" + err + ", nombre '" + s.playerName + "', cuenta '" + SecondaryPlayers.secondaryName(mc, 1) + "')",
                            err == null && s.playerName.equals("Ana") && s.profileId.equals(a.id) && SecondaryPlayers.secondaryName(mc, 1).equals(a.id));
                    check("perfiles: un perfil no se puede usar en dos jugadores a la vez (" + Profiles.use(2, a) + ")", Profiles.use(2, a) != null);
                    next();
                }
            }
            case 2 -> { // P2 entra como el perfil A
                if (AutoTest.joinTo(mc, 2) && t > 20) {
                    ServerPlayer sp = p2(mc, a.id);
                    check("perfiles: P2 entra con la cuenta del perfil A (servidor: " + (sp == null ? "no esta" : sp.getGameProfile().getName() + " " + sp.getUUID()) + ")",
                            sp != null && sp.getUUID().equals(UUIDUtil.createOfflinePlayerUUID(a.id)));
                    String path = sp == null ? "" : skinPath(mc, sp);
                    check("perfiles: la skin que ve P1 en P2 es la del perfil A, Kai de brazos finos ('" + path + "')", path.contains("slim/kai"));
                    if (sp != null) mc.getSingleplayerServer().execute(() -> sp.getInventory().setItem(20, new ItemStack(Items.DIAMOND, 5)));
                    next();
                }
            }
            case 3 -> { // guardar y cambiar al perfil B
                if (t == 20) {
                    ServerPlayer sp = p2(mc, a.id);
                    check("perfiles: P2 tiene lo que se le dio (5 diamantes en el perfil A)", sp != null && sp.getInventory().countItem(Items.DIAMOND) == 5);
                    ConsolePad.PADS.slot(1).settings.fov = 77;
                    ConsolePad.PADS.saveSettings();
                    check("perfiles: los ajustes del jugador se guardan en su perfil (campo de vision del perfil A = " + Profiles.byId(a.id).settings.fov + ")", Profiles.byId(a.id).settings.fov == 77);
                    String err = Profiles.use(1, b);
                    check("perfiles: no se cambia de perfil con el jugador dentro (" + err + ")", err != null && ConsolePad.PADS.slot(1).settings.profileId.equals(a.id));
                }
                if (t > 25 && AutoTest.leaveTo(mc, 1) && t > 40) {
                    String err = Profiles.use(1, b);
                    check("perfiles: con P2 fuera se cambia al perfil B (" + err + ", nombre '" + ConsolePad.PADS.slot(1).settings.playerName + "', campo de vision " + ConsolePad.PADS.slot(1).settings.fov + ")",
                            err == null && ConsolePad.PADS.slot(1).settings.playerName.equals("Bruno") && ConsolePad.PADS.slot(1).settings.fov != 77);
                    next();
                }
            }
            case 4 -> { // P2 entra como B
                if (AutoTest.joinTo(mc, 2) && t > 20) {
                    ServerPlayer sp = p2(mc, b.id);
                    int diamonds = sp == null ? -1 : sp.getInventory().countItem(Items.DIAMOND);
                    check("perfiles: P2 entra con el perfil B y su inventario es el suyo, no el del A (cuenta " + b.id + ", diamantes " + diamonds + ")", sp != null && diamonds == 0);
                    String path = sp == null ? "" : skinPath(mc, sp);
                    check("perfiles: la skin que ve P1 en P2 es la del perfil B, Efe de brazos anchos ('" + path + "')", path.contains("wide/efe"));
                    next();
                }
            }
            case 5 -> { // de vuelta al perfil A
                if (t > 5 && AutoTest.leaveTo(mc, 1) && t > 30) {
                    Profiles.use(1, a);
                    next();
                }
            }
            case 6 -> {
                if (AutoTest.joinTo(mc, 2) && t > 20) {
                    ServerPlayer sp = p2(mc, a.id);
                    int diamonds = sp == null ? -1 : sp.getInventory().countItem(Items.DIAMOND);
                    check("perfiles: al volver al perfil A recupera su inventario (diamantes " + diamonds + ") y sus ajustes (campo de vision " + ConsolePad.PADS.slot(1).settings.fov + ")",
                            diamonds == 5 && ConsolePad.PADS.slot(1).settings.fov == 77);
                    if (sp != null) mc.getSingleplayerServer().execute(() -> sp.getInventory().clearContent()); // que el inventario del perfil no sobreviva a la prueba
                    next();
                }
            }
            case 7 -> { // limpieza: se sueltan y se borran los perfiles
                if (t > 15 && AutoTest.leaveTo(mc, 1) && t > 40) {
                    Profiles.clear(1);
                    ConsolePad.PADS.slot(1).settings.fov = 0;
                    ConsolePad.PADS.slot(1).settings.playerName = "";
                    ConsolePad.PADS.slot(1).settings.labelColor = 0;
                    if (a != null) Profiles.delete(a);
                    if (b != null) Profiles.delete(b);
                    check("perfiles: se borran y P2 vuelve a su cuenta de siempre (" + SecondaryPlayers.secondaryName(mc, 1) + ")",
                            Profiles.byId(a == null ? "x" : a.id) == null && SecondaryPlayers.secondaryName(mc, 1).endsWith("_2"));
                    phase = 9;
                    t = 0;
                }
            }
            case 9 -> { // skin propia: un PNG de 64x64 de la carpeta de skins, brazos finos
                if (t == 1) {
                    try {
                        var img = new com.mojang.blaze3d.platform.NativeImage(64, 64, true);
                        for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) img.setPixelRGBA(x, y, 0xFF3366CC);
                        java.nio.file.Files.createDirectories(CustomSkins.dir());
                        img.writeToFile(CustomSkins.dir().resolve(SKIN_FILE));
                        img.close();
                    } catch (java.io.IOException e) {
                        check("perfiles: no se pudo escribir el PNG de prueba (" + e + ")", false);
                    }
                    CustomSkins.clearCache();
                    var files = CustomSkins.files();
                    int idx = files.indexOf(SKIN_FILE);
                    check("perfiles: la carpeta de skins lista el PNG de prueba (" + files.size() + " archivos) y ofrece una opcion por tipo de brazos", idx >= 0 && Profiles.skinChoices().size() == 18 + 2 * files.size());
                    c = idx < 0 ? null : Profiles.create("Cora", 18 + 2 * idx + 1, 0, ConsolePad.PADS.slot(1).settings);
                    check("perfiles: un perfil con skin propia guarda el archivo y los brazos finos (" + (c == null ? "no creado" : c.customSkin + " finos=" + c.slim) + ")", c != null && SKIN_FILE.equals(c.customSkin) && c.slim);
                    if (c == null) {
                        phase = 8;
                        t = 0;
                        return false;
                    }
                    Profiles.use(1, c);
                }
                if (t > 5 && AutoTest.joinTo(mc, 2) && t > 25) {
                    ServerPlayer sp = p2(mc, c.id);
                    String path = sp == null ? "" : skinPath(mc, sp);
                    var info = sp == null || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(sp.getUUID());
                    boolean slim = info != null && info.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
                    check("perfiles: P1 ve a P2 con la skin propia del PNG ('" + path + "', finos=" + slim + ")", path.contains("custom_skin") && slim);
                    phase = 10;
                    t = 0;
                }
            }
            case 10 -> { // limpieza de la skin propia
                if (t > 15 && AutoTest.leaveTo(mc, 1) && t > 40) {
                    Profiles.clear(1);
                    ConsolePad.PADS.slot(1).settings.playerName = "";
                    ConsolePad.PADS.slot(1).settings.labelColor = 0;
                    if (c != null) Profiles.delete(c);
                    try {
                        java.nio.file.Files.deleteIfExists(CustomSkins.dir().resolve(SKIN_FILE));
                    } catch (java.io.IOException ignored) {
                    }
                    CustomSkins.clearCache();
                    phase = 8;
                    t = 0;
                }
            }
            case 8 -> { // P2 vuelve a entrar para las pruebas siguientes
                if (AutoTest.joinTo(mc, 2) && t > 10) {
                    ConsolePad.LOGGER.info("[autotest] RESULTADO perfiles locales -> fin");
                    phase = 0;
                    t = 0;
                    return true;
                }
            }
            default -> { }
        }
        return false;
    }
}
