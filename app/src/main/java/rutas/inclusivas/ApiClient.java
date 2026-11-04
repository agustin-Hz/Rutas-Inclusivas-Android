package rutas.inclusivas;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

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
 * Cliente HTTP mínimo para hablar con RutasInclusivasAPI.
 * La app NUNCA se conecta directo a MySQL: todo pasa por la API.
 * Usa solo clases del SDK (HttpURLConnection + org.json), sin dependencias extra.
 */
public final class ApiClient {

    // Emulador de Android Studio: 10.0.2.2 apunta al localhost de la PC.
    // Celular físico: usar la IP de la PC en la red (ej. "http://192.168.0.15:5126/")
    // y levantar la API escuchando en esa IP.
    public static final String BASE_URL = "http://10.0.2.2:5126/";


    private static final int TIMEOUT_MS = 10_000;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ApiClient() {}

    /** Resultado de una llamada. */
    public static final class Response {
        public final int code;               // código HTTP, o -1 si no hubo conexión
        public final JSONObject body;        // nunca null (puede estar vacío)
        public final boolean networkError;

        Response(int code, JSONObject body, boolean networkError) {
            this.code = code;
            this.body = body;
            this.networkError = networkError;
        }

        public boolean isSuccess() {
            return !networkError && code >= 200 && code < 300;
        }
    }

    public interface Callback {
        /** Se ejecuta siempre en el hilo principal. */
        void onResult(Response response);
    }

    public interface GetCallback {
        void onResult(int codigo, JSONArray reportes, boolean errorRed);
    }

    public static void post(String path, JSONObject json, Callback callback) {
        post(path, json, null, callback);
    }

    public static void post(String path, JSONObject json, String token, Callback callback) {
        EXECUTOR.execute(() -> {
            Response response = ejecutarPost(path, json, token);
            MAIN.post(() -> callback.onResult(response));
        });
    }

    private static Response ejecutarPost(String path, JSONObject json, String token) {
        HttpURLConnection conexion = null;
        try {
            conexion = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            conexion.setRequestMethod("POST");
            conexion.setConnectTimeout(TIMEOUT_MS);
            conexion.setReadTimeout(TIMEOUT_MS);
            conexion.setDoOutput(true);
            conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conexion.setRequestProperty("Accept", "application/json");
            if (token != null && !token.trim().isEmpty()) {
                conexion.setRequestProperty("Authorization", "Bearer " + token);
            }

            byte[] datos = json.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream salida = conexion.getOutputStream()) {
                salida.write(datos);
            }

            int codigo = conexion.getResponseCode();
            InputStream flujo = codigo < 400 ? conexion.getInputStream() : conexion.getErrorStream();
            return new Response(codigo, leerJson(flujo), false);
        } catch (IOException e) {
            return new Response(-1, new JSONObject(), true);
        } finally {
            if (conexion != null) conexion.disconnect();
        }
    }

    private static JSONObject leerJson(InputStream flujo) {
        if (flujo == null) return new JSONObject();
        try (InputStream in = flujo) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] bloque = new byte[1024];
            int leidos;
            while ((leidos = in.read(bloque)) != -1) buffer.write(bloque, 0, leidos);
            String texto = buffer.toString("UTF-8").trim();
            return texto.isEmpty() ? new JSONObject() : new JSONObject(texto);
        } catch (IOException | JSONException e) {
            // Respuesta vacía o que no es JSON (ej. página de error): se trata como cuerpo vacío.
            return new JSONObject();
        }
    }

    private static JSONArray leerJsonArray(InputStream flujo)
            throws IOException, JSONException {

        if (flujo == null) {
            throw new IOException("La respuesta está vacía");
        }

        try (InputStream in = flujo) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] bloque = new byte[1024];
            int leidos;
            while ((leidos = in.read(bloque)) != -1) {
                buffer.write(bloque, 0, leidos);
            }
            String texto = buffer.toString("UTF-8");
            return new JSONArray(texto);
        }
    }

    public static void get(String path, String token, GetCallback callback) {

        EXECUTOR.execute(() -> {
            HttpURLConnection conexion = null;
            int codigo = -1;
            JSONArray reportes = new JSONArray();
            boolean errorRed = false;

            try {
                // Abrir la conexión con la API
                conexion = (HttpURLConnection) new URL(BASE_URL + path).openConnection();

                // Configurar la solicitud GET
                conexion.setRequestMethod("GET");
                conexion.setConnectTimeout(TIMEOUT_MS);
                conexion.setReadTimeout(TIMEOUT_MS);
                conexion.setRequestProperty("Accept", "application/json");

                // Enviar el token JWT
                if (token != null && !token.trim().isEmpty()) {
                    conexion.setRequestProperty("Authorization", "Bearer " + token);
                }

                // Obtener el código de respuesta HTTP
                codigo = conexion.getResponseCode();

                // Si la respuesta fue exitosa, lee los reportes
                if (codigo >= 200 && codigo < 300) {
                    reportes = leerJsonArray(conexion.getInputStream());
                }

            } catch (IOException e) {
                errorRed = true;

            } catch (JSONException e) {
                codigo = -1;

            } finally {
                if (conexion != null) {
                    conexion.disconnect();
                }
            }

            // Enviar los resultados al hilo principal de Android
            int codigoFinal = codigo;
            JSONArray reportesFinal = reportes;
            boolean errorRedFinal = errorRed;

            MAIN.post(() ->
                    callback.onResult(codigoFinal, reportesFinal, errorRedFinal)
            );
        });
    }
}
