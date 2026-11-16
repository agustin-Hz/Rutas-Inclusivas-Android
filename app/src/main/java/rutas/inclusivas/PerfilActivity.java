package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

/** Mi perfil: consultar y modificar los datos de la cuenta, y darla de baja. */
public class PerfilActivity extends BaseActivity {

    private static final int MAX_CORREO = 100;
    private static final int MAX_ZONA = 100;
    private SessionManager sesion;
    private TextView tvNombre;
    private EditText etCorreo;
    private EditText etZona;
    private Button btnGuardar;
    private Button btnBaja;

    // Últimos datos guardados: para saber si el usuario cambió algo
    private String correoGuardado = "";
    private String zonaGuardada = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sesion = new SessionManager(this);
        if (!sesion.haySesionValida()) {
            irAlLogin();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_perfil);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.perfil), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        tvNombre = findViewById(R.id.tvNombrePerfil);
        etCorreo = findViewById(R.id.etCorreoPerfil);
        etZona = findViewById(R.id.etZonaPerfil);
        btnGuardar = findViewById(R.id.btnGuardarPerfil);
        btnBaja = findViewById(R.id.btnBajaCuenta);
        Button btnVolver = findViewById(R.id.btnVolverPerfil);

        btnGuardar.setOnClickListener(v -> guardarCambios());
        btnBaja.setOnClickListener(v -> confirmarBaja());
        btnVolver.setOnClickListener(v -> finish());

        // 1) Se muestra primero lo que hay guardado en el celular.
        mostrarDatos(sesion.getNombre(), sesion.getCorreo(), sesion.getZona());
        // 2) Se actualiza con los datos reales de la API.
        cargarPerfil();
    }

    // Consultar
    private void cargarPerfil() {
        ApiClient.get("api/Usuarios/" + sesion.getId(), respuesta -> {
            if (isFinishing() || isDestroyed()) return;

            if (respuesta.isSuccess()) {
                JSONObject datos = respuesta.body;
                String nombre = datos.optString("nombre", sesion.getNombre());
                String correo = datos.optString("correo", sesion.getCorreo());
                String zona = datos.isNull("zona") ? "" : datos.optString("zona", "");

                mostrarDatos(nombre, correo, zona);
                sesion.actualizarDatos(correo, zona);
            } else if (respuesta.networkError) {
                // Sin conexión: se siguen mostrando los datos guardados previamente
                Toast.makeText(this, R.string.perfil_datos_locales, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.error_servidor, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarDatos(String nombre, String correo, String zona) {
        tvNombre.setText(nombre);
        etCorreo.setText(correo);
        etZona.setText(zona);
        correoGuardado = correo;
        zonaGuardada = zona;
    }

    // Modificar
    private void guardarCambios() {
        String correo = etCorreo.getText().toString().trim();
        String zona = etZona.getText().toString().trim();

        // Validaciones (las mismas reglas que el registro)
        if (correo.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            marcarError(etCorreo, getString(R.string.error_correo_invalido));
            return;
        }
        if (correo.length() > MAX_CORREO) {
            marcarError(etCorreo, getString(R.string.error_datos_invalidos));
            return;
        }
        if (zona.length() > MAX_ZONA) {
            marcarError(etZona, getString(R.string.error_datos_invalidos));
            return;
        }

        // Si no cambió nada, no tiene sentido llamar a la API
        if (correo.equalsIgnoreCase(correoGuardado) && zona.equals(zonaGuardada)) {
            Toast.makeText(this, R.string.perfil_sin_cambios, Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject json = new JSONObject();
        try {
            // Nombres de los atributos = modelo UsuarioModificacion de la API
            json.put("Correo", correo);
            json.put("Zona", zona);
        } catch (JSONException e) {
            return;
        }

        setCargando(true);
        ApiClient.put("api/Usuarios/" + sesion.getId(), json, respuesta -> {
            if (isFinishing() || isDestroyed()) return;
            setCargando(false);

            if (respuesta.networkError) {
                Toast.makeText(this, R.string.error_sin_conexion, Toast.LENGTH_LONG).show();
            } else if (respuesta.isSuccess()) {
                sesion.actualizarDatos(correo, zona);
                correoGuardado = correo;
                zonaGuardada = zona;
                Toast.makeText(this, R.string.perfil_guardado, Toast.LENGTH_SHORT).show();
            } else if (respuesta.code == 409) {
                marcarError(etCorreo, getString(R.string.perfil_correo_en_uso));
            } else if (respuesta.code == 400) {
                Toast.makeText(this, R.string.error_datos_invalidos, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.error_servidor, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Dar de baja
    private void confirmarBaja() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.perfil_baja_titulo)
                .setMessage(R.string.perfil_baja_mensaje)
                .setPositiveButton(R.string.perfil_baja_confirmar, (dialogo, which) -> darDeBaja())
                .setNegativeButton(R.string.perfil_baja_cancelar, null)
                .show();
    }
    private void darDeBaja() {
        setCargando(true);
        ApiClient.delete("api/Usuarios/" + sesion.getId() + "/logica", respuesta -> {
            if (isFinishing() || isDestroyed()) return;
            setCargando(false);

            if (respuesta.networkError) {
                Toast.makeText(this, R.string.error_sin_conexion, Toast.LENGTH_LONG).show();
            } else if (respuesta.isSuccess()) {
                Toast.makeText(this, R.string.perfil_baja_ok, Toast.LENGTH_LONG).show();
                sesion.cerrarSesion();
                irAlLogin();
            } else {
                Toast.makeText(this, R.string.error_servidor, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Utilidades
    private void setCargando(boolean cargando) {
        btnGuardar.setEnabled(!cargando);
        btnBaja.setEnabled(!cargando);
        btnGuardar.setText(cargando ? R.string.perfil_guardando : R.string.perfil_guardar);
    }

    private void marcarError(EditText campo, String mensaje) {
        campo.setError(mensaje);
        campo.requestFocus();
    }

    private void irAlLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
