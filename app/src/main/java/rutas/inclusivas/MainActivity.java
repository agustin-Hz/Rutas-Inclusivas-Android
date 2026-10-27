package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Si ya hay una sesión vigente, se salta la bienvenida.
        if (new SessionManager(this).haySesionValida()) {
            irAInicio();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars());

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                });

        Button btnComenzar = findViewById(R.id.btnComenzar);
        Button btnIniciarSesion = findViewById(R.id.btnIniciarSesion);

        // "Comenzar" = usuario nuevo -> registro
        btnComenzar.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, RegisterActivity.class)));

        // "Ya tengo una cuenta" -> login
        btnIniciarSesion.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, LoginActivity.class)));
    }

    private void irAInicio() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
