package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Pantalla provisoria post-login. Acá va a ir el mapa de reportes. */
public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sesion = new SessionManager(this);
        if (!sesion.haySesionValida()) {
            irAlLogin();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        TextView tvSaludo = findViewById(R.id.tvSaludo);
        TextView tvDatos = findViewById(R.id.tvDatos);
        Button btnCerrar = findViewById(R.id.btnCerrarSesion);

        tvSaludo.setText(getString(R.string.home_saludo, sesion.getNombre()));
        String zona = sesion.getZona();
        tvDatos.setText(zona.isEmpty()
                ? sesion.getCorreo()
                : getString(R.string.home_datos, sesion.getCorreo(), zona));

        btnCerrar.setOnClickListener(v -> {
            sesion.cerrarSesion();
            irAlLogin();
        });
    }

    private void irAlLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
