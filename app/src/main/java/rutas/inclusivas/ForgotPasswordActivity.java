package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class ForgotPasswordActivity extends BaseActivity {

    private EditText etCorreo;
    private Button btnAceptar, btnCancelar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_forgotpassword);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        etCorreo = findViewById(R.id.etRecuperarCorreo);
        btnAceptar = findViewById(R.id.button2);
        btnCancelar = findViewById(R.id.button);

        btnAceptar.setOnClickListener(v -> intentarRecuperacion());
        btnCancelar.setOnClickListener(v -> finish());
    }

    private void intentarRecuperacion() {
        String correo = etCorreo.getText().toString().trim();

        if (correo.isEmpty()) {
            etCorreo.setError(getString(R.string.error_campo_obligatorio));
            etCorreo.requestFocus();
            return;
        }

        JSONObject json = new JSONObject();
        try {
            json.put("Correo", correo);
        } catch (JSONException e) {
            return;
        }

        setCargando(true);

        // Ajusta la ruta "api/Auth/recuperar" al endpoint real del backend en C#
        ApiClient.post("api/Auth/recuperar", json, respuesta -> {
            if (isFinishing() || isDestroyed()) return;
            setCargando(false);

            if (respuesta.networkError) {
                mensaje(R.string.error_sin_conexion);
            } else if (respuesta.code == 200) {
                Toast.makeText(this, "Instrucciones enviadas", Toast.LENGTH_LONG).show();

                // Iniciar la nueva pantalla
                Intent intent = new Intent(this, ResetPasswordActivity.class);
                intent.putExtra("correo_recuperacion", correo);
                startActivity(intent);
                finish(); // Cerramos la de recuperar correo

                finish();
            } else if (respuesta.code == 404) {
                etCorreo.setError("Correo no encontrado");
                etCorreo.requestFocus();
            } else {
                mensaje(R.string.error_servidor);
            }
        });
    }

    private void setCargando(boolean cargando) {
        btnAceptar.setEnabled(!cargando);
        btnAceptar.setText(cargando ? "Procesando..." : "Aceptar");
    }

    private void mensaje(int recurso) {
        Toast.makeText(this, recurso, Toast.LENGTH_LONG).show();
    }
}