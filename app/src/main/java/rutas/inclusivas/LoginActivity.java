package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;

public class LoginActivity extends AppCompatActivity {

    /** Extra opcional para precargar el correo (lo usa RegisterActivity). */
    public static final String EXTRA_CORREO = "correo";

    private EditText etCorreo;
    private EditText etContrasena;
    private Button btnIngresar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etCorreo = findViewById(R.id.etCorreo);
        etContrasena = findViewById(R.id.etContrasena);
        btnIngresar = findViewById(R.id.btnIngresar);
        Button btnRecuperar = findViewById(R.id.btnRecuperar);
        Button btnIrRegistro = findViewById(R.id.btnIrRegistro);

        String correoPrecargado = getIntent().getStringExtra(EXTRA_CORREO);
        if (correoPrecargado != null) {
            etCorreo.setText(correoPrecargado);
            etContrasena.requestFocus();
        }

        // Enter/Listo en el teclado también inicia sesión
        etContrasena.setImeOptions(EditorInfo.IME_ACTION_DONE);
        etContrasena.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                intentarLogin();
                return true;
            }
            return false;
        });

        btnIngresar.setOnClickListener(v -> intentarLogin());
        btnRecuperar.setOnClickListener(v -> mostrarRecuperarNoDisponible());
        btnIrRegistro.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void intentarLogin() {
        // La API acepta correo O nombre de usuario como identificador.
        String identificador = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString(); // sin trim: los espacios cuentan

        if (identificador.isEmpty()) {
            etCorreo.setError(getString(R.string.error_campo_obligatorio));
            etCorreo.requestFocus();
            return;
        }
        if (contrasena.isEmpty()) {
            etContrasena.setError(getString(R.string.error_campo_obligatorio));
            etContrasena.requestFocus();
            return;
        }

        JSONObject json = new JSONObject();
        try {
            json.put("Identificador", identificador);
            json.put("Contrasena", contrasena);
        } catch (JSONException e) {
            return; // no ocurre con claves/valores String
        }

        setCargando(true);
        ApiClient.post("api/Auth/login", json, respuesta -> {
            if (isFinishing() || isDestroyed()) return;
            setCargando(false);

            android.util.Log.e("RutasAPI", "login -> code=" + respuesta.code + " body=" + respuesta.body);

            if (respuesta.networkError) {
                mensaje(R.string.error_sin_conexion);
            } else if (respuesta.code == 200) {
                if (guardarSesion(respuesta.body)) {
                    Intent intent = new Intent(this, HomeActivity.class);
                    // Limpia la pila: con "atrás" no se vuelve al login
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else {
                    mensaje(R.string.error_respuesta_invalida);
                }
            } else if (respuesta.code == 401) {
                etContrasena.setError(getString(R.string.error_credenciales));
                etContrasena.requestFocus();
            } else if (respuesta.code == 400) {
                mensaje(R.string.error_datos_invalidos);
            } else {
                mensaje(R.string.error_servidor);
            }
        });
    }

    /** Lee {accessToken, expiraEn, usuario{id,nombre,correo,zona}} y lo guarda. */
    private boolean guardarSesion(JSONObject body) {
        try {
            String token = body.getString("accessToken");
            JSONObject usuario = body.getJSONObject("usuario");

            long expiraMs;
            try {
                expiraMs = Instant.parse(body.getString("expiraEn")).toEpochMilli();
            } catch (Exception e) {
                expiraMs = System.currentTimeMillis() + 15 * 60 * 1000L; // por defecto de la API
            }

            String zona = usuario.isNull("zona") ? "" : usuario.getString("zona");
            new SessionManager(this).guardar(
                    token,
                    expiraMs,
                    usuario.getInt("id"),
                    usuario.getString("nombre"),
                    usuario.getString("correo"),
                    zona);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private void mostrarRecuperarNoDisponible() {
        // La API todavía no tiene un endpoint de recuperación de contraseña.
        new AlertDialog.Builder(this)
                .setTitle(R.string.recuperar_titulo)
                .setMessage(R.string.recuperar_no_disponible)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void setCargando(boolean cargando) {
        btnIngresar.setEnabled(!cargando);
        btnIngresar.setText(cargando ? R.string.ingresando : R.string.ingresar);
    }

    private void mensaje(int recurso) {
        Toast.makeText(this, recurso, Toast.LENGTH_LONG).show();
    }
}

