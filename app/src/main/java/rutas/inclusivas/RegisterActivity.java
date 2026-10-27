package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


import org.json.JSONException;
import org.json.JSONObject;

public class RegisterActivity extends BaseActivity {

    // Límites según las columnas de la tabla USUARIOS
    private static final int MAX_NOMBRE = 50;
    private static final int MAX_CORREO = 100;
    private static final int MAX_ZONA = 100;
    private static final int MIN_NOMBRE = 3;
    private static final int MIN_CONTRASENA = 8; // la API no valida el largo: se exige acá

    private EditText etNombre, etCorreo, etContrasena, etConfirmar, etZona;
    private Button btnRegistrar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etNombre = findViewById(R.id.etNombreUsuario);
        etCorreo = findViewById(R.id.etCorreoRegistro);
        etContrasena = findViewById(R.id.etContrasenaRegistro);
        etConfirmar = findViewById(R.id.etConfirmarContrasena);
        etZona = findViewById(R.id.etZona);
        btnRegistrar = findViewById(R.id.btnRegistrar);
        Button btnIrLogin = findViewById(R.id.btnYaTengoCuenta);

        etZona.setImeOptions(EditorInfo.IME_ACTION_DONE);
        etZona.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                intentarRegistro();
                return true;
            }
            return false;
        });

        btnRegistrar.setOnClickListener(v -> intentarRegistro());
        btnIrLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void intentarRegistro() {
        String nombre = etNombre.getText().toString().trim();
        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();
        String confirmar = etConfirmar.getText().toString();
        String zona = etZona.getText().toString().trim();

        // Se valida en orden y se enfoca el primer campo con error.
        if (nombre.length() < MIN_NOMBRE) {
            marcarError(etNombre, getString(R.string.error_nombre_corto, MIN_NOMBRE));
            return;
        }
        if (correo.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            marcarError(etCorreo, getString(R.string.error_correo_invalido));
            return;
        }
        if (contrasena.length() < MIN_CONTRASENA) {
            marcarError(etContrasena, getString(R.string.error_contrasena_corta, MIN_CONTRASENA));
            return;
        }
        if (!contrasena.equals(confirmar)) {
            marcarError(etConfirmar, getString(R.string.error_contrasenas_distintas));
            return;
        }
        // La API exige Zona (string no nulo): sin ella responde 400.
        if (zona.isEmpty()) {
            marcarError(etZona, getString(R.string.error_campo_obligatorio));
            return;
        }

        JSONObject json = new JSONObject();
        try {
            // Nombres de campo = modelo UsuarioRegistro de la API
            json.put("Nombre_Usuario", nombre);
            json.put("Correo", correo);
            json.put("Contrasena", contrasena);
            json.put("Zona", zona);
        } catch (JSONException e) {
            return;
        }

        setCargando(true);
        ApiClient.post("api/Usuarios", json, respuesta -> {
            if (isFinishing() || isDestroyed()) return;
            setCargando(false);

            if (respuesta.networkError) {
                mensaje(R.string.error_sin_conexion);
            } else if (respuesta.isSuccess()) {
                mensaje(R.string.registro_exitoso);
                Intent intent = new Intent(this, LoginActivity.class);
                intent.putExtra(LoginActivity.EXTRA_CORREO, correo);
                startActivity(intent);
                finish();
            } else if (respuesta.code == 409) {
                // Correo o nombre de usuario ya existen (UNIQUE en la BD)
                marcarError(etCorreo, getString(R.string.error_usuario_existente));
            } else if (respuesta.code == 400) {
                mensaje(R.string.error_datos_invalidos);
            } else {
                mensaje(R.string.error_servidor);
            }
        });
    }

    private void marcarError(EditText campo, String texto) {
        campo.setError(texto);
        campo.requestFocus();
    }

    private void setCargando(boolean cargando) {
        btnRegistrar.setEnabled(!cargando);
        btnRegistrar.setText(cargando ? R.string.registrando : R.string.crear_cuenta);
    }

    private void mensaje(int recurso) {
        Toast.makeText(this, recurso, Toast.LENGTH_LONG).show();
    }
}
