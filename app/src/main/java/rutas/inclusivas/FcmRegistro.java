package rutas.inclusivas;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Mantiene sincronizado el token FCM de este dispositivo con la API.
 */
public final class FcmRegistro {

    private static final String TAG = "RutasFCM";

    private FcmRegistro() {
    }

    /**
     * Obtiene el token actual y lo envía a la API (requiere sesión iniciada).
     */
    public static void registrarTokenActual() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tarea -> {
            if (!tarea.isSuccessful() || tarea.getResult() == null) {
                Log.w(TAG, "No se pudo obtener el token FCM", tarea.getException());
                return;
            }
            enviarToken(tarea.getResult());
        });
    }

    /**
     * Envía un token concreto a la API. Lo usa también onNewToken().
     */
    public static void enviarToken(String token) {
        try {
            JSONObject json = new JSONObject();
            json.put("token", token);

            ApiClient.post("api/Notificaciones/token", json, respuesta -> {
                if (respuesta.isSuccess()) {
                    Log.d(TAG, "Token registrado en la API");
                } else {
                    Log.w(TAG, "La API no registró el token. Código: " + respuesta.code);
                }
            });
        } catch (JSONException e) {
            Log.w(TAG, "No se pudo armar el JSON del token", e);
        }
    }

    /**
     * Da de baja el token en la API y recién después ejecuta alTerminar
     * (que debe cerrar la sesión). Tiene que llamarse ANTES de cerrarSesion(),
     * porque la baja necesita el JWT.
     */
    public static void darDeBaja(Runnable alTerminar) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tarea -> {
            if (!tarea.isSuccessful() || tarea.getResult() == null) {
                alTerminar.run();
                return;
            }

            try {
                JSONObject json = new JSONObject();
                json.put("token", tarea.getResult());

                ApiClient.post("api/Notificaciones/token/baja", json,
                        respuesta -> alTerminar.run());
            } catch (JSONException e) {
                alTerminar.run();
            }
        });
    }
}