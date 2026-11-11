package rutas.inclusivas;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente HTTP para hablar con RutasInclusivasAPI.
 * La app NUNCA se conecta directo a MySQL: todo pasa por la API.

 * Si un pedido con sesión recibe 401 (token vencido o inválido), ApiClient cierra
 * la sesión y lleva al login. En ese caso el callback de la pantalla NO se ejecuta.
 */
public final class ApiClient {

    // Se define en app/build.gradle (buildConfigField "API_BASE_URL").
    public static final String BASE_URL = BuildConfig.API_BASE_URL;
    private static final int TIMEOUT_MS = 10_000;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // Contexto de toda la app: lo carga RutasInclusivasApp al iniciar.
    private static Context appContext;

    private ApiClient() {
    }

    /**
     * Se llama una sola vez, al arrancar la app (ver RutasInclusivasApp).
     */
    public static void init(Context contexto) {
        appContext = contexto.getApplicationContext();
    }

    // Resultado de un pedido
    public static final class Response {
        public final int code;               // código HTTP, o -1 si no hubo conexión
        public final JSONObject body;        // respuesta si es un objeto {...}; nunca null
        public final JSONArray array;        // respuesta si es una lista [...]; nunca null
        public final boolean networkError;   // true si no se pudo conectar con la API

        Response(int code, JSONObject body, JSONArray array, boolean networkError) {
            this.code = code;
            this.body = body;
            this.array = array;
            this.networkError = networkError;
        }

        /**
         * Conexion exitosa y el código está entre 200 y 299.
         */
        public boolean isSuccess() {
            return !networkError && code >= 200 && code < 300;
        }

        /**
         * Mensaje enviado por la API o el texto de respaldo.
         */
        public String mensaje(String respaldo) {
            String texto = body.optString("mensaje", "");
            if (texto.isEmpty()) texto = body.optString("error", "");
            return texto.isEmpty() ? respaldo : texto;
        }
    }

    public interface Callback {
        /**
         * Se ejecuta siempre en el hilo principal.
         */
        void onResult(Response response);
    }

    // Pedidos públicos (sin sesión): no envían token ni manejan el 401
    public static void postPublico(String path, JSONObject json, Callback callback) {
        enviar("POST", path, json, false, callback);
    }

    // Pedidos con sesión: envían el token y manejan el 401
    public static void get(String path, Callback callback) {
        enviar("GET", path, null, true, callback);
    }

    public static void post(String path, JSONObject json, Callback callback) {
        enviar("POST", path, json, true, callback);
    }

    public static void put(String path, JSONObject json, Callback callback) {
        enviar("PUT", path, json, true, callback);
    }

    public static void delete(String path, Callback callback) {
        enviar("DELETE", path, null, true, callback);
    }

    // Motor común: todos los métodos públicos terminan acá

    private static void enviar(String metodo, String path, JSONObject json, boolean conSesion, Callback callback) {
        if (appContext == null) {
            throw new IllegalStateException("Falta llamar a ApiClient.init(...)");
        }
        // El token se lee acá (hilo principal) y se pasa al hilo de fondo.
        String token = conSesion ? new SessionManager(appContext).getToken() : null;

        EXECUTOR.execute(() -> {
            // [hilo secundario] pedido HTTP
            Response response = ejecutar(metodo, path, json, token);

            MAIN.post(() -> {
                // [hilo principal] token vencido o inválido: login
                if (conSesion && response.code == 401 && manejarSesionExpirada()) {
                    return;
                }
                callback.onResult(response);
            });
        });
    }

    /** Arma, envía y lee un pedido HTTP. Se ejecuta en un hilo secundario. */
    private static Response ejecutar(String metodo, String path, JSONObject json, String token) {
        HttpURLConnection conexion = null;
        try {
            conexion = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            conexion.setRequestMethod(metodo);
            conexion.setConnectTimeout(TIMEOUT_MS);
            conexion.setReadTimeout(TIMEOUT_MS);
            conexion.setRequestProperty("Accept", "application/json");

            if (token != null && !token.trim().isEmpty()) {
                conexion.setRequestProperty("Authorization", "Bearer " + token);
            }

            // Solo POST y PUT llevan cuerpo
            if (json != null) {
                conexion.setDoOutput(true);
                conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] datos = json.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream salida = conexion.getOutputStream()) {
                    salida.write(datos);
                }
            }

            int codigo = conexion.getResponseCode();
            InputStream flujo = codigo < 400 ? conexion.getInputStream() : conexion.getErrorStream();
            return crearResponse(codigo, leerTexto(flujo));

        } catch (IOException e) {
            // API apagada, sin internet o timeout
            return new Response(-1, new JSONObject(), new JSONArray(), true);
        } finally {
            if (conexion != null) conexion.disconnect();
        }
    }

    /** Lee todo el flujo como texto UTF-8. Devuelve "" si no hay cuerpo. */
    private static String leerTexto(InputStream flujo) throws IOException {
        if (flujo == null) return "";
        try (InputStream in = flujo) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] bloque = new byte[1024];
            int leidos;
            while ((leidos = in.read(bloque)) != -1) {
                buffer.write(bloque, 0, leidos);
            }
            return buffer.toString("UTF-8").trim();
        }
    }

    /** Detecta si el texto es un objeto {...} o una lista [...] y arma el Response. */
    private static Response crearResponse(int codigo, String texto) {
        JSONObject objeto = new JSONObject();
        JSONArray lista = new JSONArray();

        if (!texto.isEmpty()) {
            try {
                Object valor = new JSONTokener(texto).nextValue();
                if (valor instanceof JSONObject) {
                    objeto = (JSONObject) valor;
                } else if (valor instanceof JSONArray) {
                    lista = (JSONArray) valor;
                }
            } catch (JSONException ignored) {
                // La respuesta no es JSON (por ejemplo, una página de error): queda vacía
            }
        }
        return new Response(codigo, objeto, lista, false);
    }

    /**
     * Cierra la sesión y lleva al login. Devuelve true si había sesión para cerrar.
     * Si no había token guardado (ya se cerró por otro pedido), devuelve false
     * y el callback de la pantalla se ejecuta normalmente.
     */
    private static boolean manejarSesionExpirada() {
        SessionManager sesion = new SessionManager(appContext);
        if (sesion.getToken() == null) {
            return false;
        }

        sesion.cerrarSesion();
        Toast.makeText(appContext, R.string.sesion_expirada, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(appContext, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        appContext.startActivity(intent);
        return true;
    }
}
