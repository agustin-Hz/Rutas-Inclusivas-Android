package rutas.inclusivas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Preferencias de accesibilidad del DISPOSITIVO (no del usuario logueado):
 * sobreviven al cierre de sesión y no viajan a la base de datos.
 */
public class AccesibilidadPrefs {

    private static final String PREFS = "accesibilidad";
    private static final String K_VOZ = "voz";
    private static final String K_VIBRACION = "vibracion";
    private static final String K_LECTOR = "lector";
    private static final String K_TEXTO = "tam_texto";
    private static final String K_CONFIGURADA = "configurada";

    /** Tamaños de texto: 0 = Normal, 1 = Grande, 2 = Muy grande. Se multiplican por la escala del sistema. */
    public static final float[] FACTORES_TEXTO = {1.0f, 1.25f, 1.5f};
    private static final float ESCALA_MAXIMA = 2.0f;

    private final SharedPreferences prefs;

    public AccesibilidadPrefs(Context contexto) {
        prefs = contexto.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- Interruptores ----
    // Valores por defecto: voz apagada (no sorprender con audio), vibración encendida.
    public boolean isVoz() { return prefs.getBoolean(K_VOZ, false); }
    public void setVoz(boolean v) { prefs.edit().putBoolean(K_VOZ, v).apply(); }

    public boolean isVibracion() { return prefs.getBoolean(K_VIBRACION, true); }
    public void setVibracion(boolean v) { prefs.edit().putBoolean(K_VIBRACION, v).apply(); }

    /** Si nunca se eligió, usa el valor sugerido (el estado actual de TalkBack en el sistema). */
    public boolean isLector(boolean valorSugerido) { return prefs.getBoolean(K_LECTOR, valorSugerido); }
    public void setLector(boolean v) { prefs.edit().putBoolean(K_LECTOR, v).apply(); }

    // ---- Tamaño de texto ----
    public int getTamanoTexto() {
        int i = prefs.getInt(K_TEXTO, 0);
        return (i >= 0 && i < FACTORES_TEXTO.length) ? i : 0;
    }
    public void setTamanoTexto(int indice) { prefs.edit().putInt(K_TEXTO, indice).apply(); }
    public float getFactorTexto() { return FACTORES_TEXTO[getTamanoTexto()]; }

    // ---- Primera vez ----
    public boolean estaConfigurada() { return prefs.getBoolean(K_CONFIGURADA, false); }
    public void marcarConfigurada() { prefs.edit().putBoolean(K_CONFIGURADA, true).apply(); }

    /** Escala final de fuente: la del sistema por el factor elegido, con tope para no romper layouts. */
    public static float escalaFinal(float escalaSistema, float factor) {
        return Math.min(escalaSistema * factor, ESCALA_MAXIMA);
    }

    // ---- Vibración ----
    /** Vibra solo si el usuario lo tiene activado. Para usar desde cualquier pantalla (errores, avisos...). */
    public void vibrarSiCorresponde(Context contexto) {
        if (isVibracion()) vibrar(contexto);
    }

    @SuppressWarnings("deprecation")
    public static void vibrar(Context contexto) {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) contexto.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = (vm == null) ? null : vm.getDefaultVibrator();
        } else {
            v = (Vibrator) contexto.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}
