package rutas.inclusivas;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;

/**
 * Guarda la sesión del usuario (access token, refresh token y datos básicos)
 * en SharedPreferences privadas. El archivo "sesion.xml" está excluido del backup (ver res/xml).
 */
public class SessionManager {

    private static final String PREFS = "sesion";
    private static final String K_TOKEN = "token";
    private static final String K_EXPIRA = "expira_ms";
    private static final String K_REFRESH = "refresh";
    private static final String K_ID = "id";
    private static final String K_NOMBRE = "nombre";
    private static final String K_CORREO = "correo";
    private static final String K_ZONA = "zona";

    private final SharedPreferences prefs;

    public SessionManager(Context contexto) {
        prefs = contexto.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Guarda la respuesta del login o del refresh (vienen con el mismo formato).
     * Devuelve false si falta algún dato.
     */
    public boolean guardarDesdeApi(JSONObject body) {
        try {
            String token = body.getString("accessToken");
            String refresh = body.getString("refreshToken");
            JSONObject usuario = body.getJSONObject("usuario");

            long expiraMs;
            try {
                expiraMs = Instant.parse(body.getString("expiraEn")).toEpochMilli();
            } catch (Exception e) {
                expiraMs = System.currentTimeMillis() + 15 * 60 * 1000L; // por defecto de la API
            }

            String zona = usuario.isNull("zona") ? "" : usuario.getString("zona");

            prefs.edit()
                    .putString(K_TOKEN, token)
                    .putLong(K_EXPIRA, expiraMs)
                    .putString(K_REFRESH, refresh)
                    .putInt(K_ID, usuario.getInt("id"))
                    .putString(K_NOMBRE, usuario.getString("nombre"))
                    .putString(K_CORREO, usuario.getString("correo"))
                    .putString(K_ZONA, zona)
                    .apply();
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /** Hay sesión si hay refresh token. Si el access venció, ApiClient lo renueva. */
    public boolean haySesionValida() {
        return prefs.getString(K_REFRESH, null) != null;
    }

    public String getToken() { return prefs.getString(K_TOKEN, null); }
    public String getRefreshToken() { return prefs.getString(K_REFRESH, null); }
    public int getId() { return prefs.getInt(K_ID, 0); }
    public String getNombre() { return prefs.getString(K_NOMBRE, ""); }
    public String getCorreo() { return prefs.getString(K_CORREO, ""); }
    public String getZona() { return prefs.getString(K_ZONA, ""); }

    public void actualizarDatos(String correo, String zona) {
        prefs.edit()
                .putString(K_CORREO, correo)
                .putString(K_ZONA, zona == null ? "" : zona)
                .apply();
    }

    public void cerrarSesion() {
        prefs.edit().clear().apply();
    }
}
