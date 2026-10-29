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

public class ResetPasswordActivity extends BaseActivity {

    private EditText etPin, etNuevaContrasena;
    private Button btnConfirmar, btnCancelar;
    private String correo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reset_password);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        // Recuperar el correo que pasamos desde ForgotPasswordActivity
        correo = getIntent().getStringExtra("correo_recuperacion");

        etPin = findViewById(R.id.etPin);
        etNuevaContrasena = findViewById(R.id.etNuevaContrasena);
        btnConfirmar = findViewById(R.id.btnConfirmarReset);
        btnCancelar = findViewById(R.id.btnCancelarReset);

        btnConfirmar.setOnClickListener(v -> intentarCambioContrasena());
        btnCancelar.setOnClickListener(v -> finish());
    }

    private void intentarCambioContrasena() {
        String pin = etPin.getText().toString().trim();
        String nuevaContrasena = etNuevaContrasena.getText().toString();

        if (pin.length() != 6) {
            etPin.setError("Ingrese el PIN de 6 dígitos");
            etPin.requestFocus();
            return;
        }
        if (nuevaContrasena.isEmpty()) {
            etNuevaContrasena.setError("La contraseña no puede estar vacía");
            etNuevaContrasena.requestFocus();
            return;
        }

        JSONObject json = new JSONObject();
        try {
            json.put("Correo", correo);
            json.put("Pin", pin);
            json.put("NuevaContrasena", nuevaContrasena);
        } catch (JSONException e) {
            return;
        }

        setCargando(true);

        ApiClient.post("api/Auth/resetear", json, respuesta -> {
            if (isFinishing() || isDestroyed()) return;
            setCargando(false);

            if (respuesta.networkError) {
                Toast.makeText(this, R.string.error_sin_conexion, Toast.LENGTH_LONG).show();
            } else if (respuesta.code == 200) {
                Toast.makeText(this, "Contraseña actualizada correctamente", Toast.LENGTH_LONG).show();
                // Enviar al usuario al login limpiando el historial de pantallas
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else if (respuesta.code == 400) {
                Toast.makeText(this, "PIN incorrecto o expirado", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.error_servidor, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setCargando(boolean cargando) {
        btnConfirmar.setEnabled(!cargando);
        btnConfirmar.setText(cargando ? "Procesando..." : "Cambiar contraseña");
    }
}