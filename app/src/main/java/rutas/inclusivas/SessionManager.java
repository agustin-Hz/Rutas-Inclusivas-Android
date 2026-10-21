package rutas.inclusivas;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Guarda la sesión del usuario (token JWT + datos básicos) en SharedPreferences privadas.
 * El archivo "sesion.xml" está excluido del backup (ver res/xml).
 */
public class SessionManager {

    private static final String PREFS = "sesion";
    private static final String K_TOKEN = "token";
    private static final String K_EXPIRA = "expira_ms";
    private static final String K_ID = "id";
    private static final String K_NOMBRE = "nombre";
    private static final String K_CORREO = "correo";
    private static final String K_ZONA = "zona";

    private final SharedPreferences prefs;

    public SessionManager(Context contexto) {
        prefs = contexto.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void guardar(String token, long expiraMs, int id, String nombre, String correo, String zona) {
        prefs.edit()
                .putString(K_TOKEN, token)
                .putLong(K_EXPIRA, expiraMs)
                .putInt(K_ID, id)
                .putString(K_NOMBRE, nombre)
                .putString(K_CORREO, correo)
                .putString(K_ZONA, zona)
                .apply();
    }

    /** Hay token guardado y todavía no venció. */
    public boolean haySesionValida() {
        return prefs.getString(K_TOKEN, null) != null
                && System.currentTimeMillis() < prefs.getLong(K_EXPIRA, 0);
    }

    public String getToken() { return prefs.getString(K_TOKEN, null); }
    public int getId() { return prefs.getInt(K_ID, 0); }
    public String getNombre() { return prefs.getString(K_NOMBRE, ""); }
    public String getCorreo() { return prefs.getString(K_CORREO, ""); }
    public String getZona() { return prefs.getString(K_ZONA, ""); }

    public void cerrarSesion() {
        prefs.edit().clear().apply();
    }
}
