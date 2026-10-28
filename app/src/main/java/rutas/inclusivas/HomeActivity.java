package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Importo lo nececario para OSMDroid. */

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.api.IMapController;
import org.osmdroid.views.overlay.Marker;

public class HomeActivity extends BaseActivity {

    private MapView mapView;

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

        inicializarMapa();

        TextView tvSaludo = findViewById(R.id.tvSaludo);
        TextView tvDatos = findViewById(R.id.tvDatos);
        Button btnCerrar = findViewById(R.id.btnCerrarSesion);
        Button btnAccesibilidad = findViewById(R.id.btnAccesibilidad);

        btnAccesibilidad.setOnClickListener(v ->
                startActivity(new Intent(this, AccesibilidadActivity.class)));

        // La primera vez que entra, se le ofrece configurar la accesibilidad.
        if (!new AccesibilidadPrefs(this).estaConfigurada()) {
            Intent primera = new Intent(this, AccesibilidadActivity.class);
            primera.putExtra(AccesibilidadActivity.EXTRA_PRIMERA_VEZ, true);
            startActivity(primera);
        }

        tvSaludo.setText(getString(R.string.home_saludo, sesion.getNombre()));
        String zona = sesion.getZona();
        tvDatos.setText(zona.isEmpty()
                ? sesion.getCorreo()
                : getString(R.string.home_datos, sesion.getCorreo(), zona));


        // Botón para Reportar Obstáculo (Etapa 3)
        Button btnReportar = findViewById(R.id.btnReportar);
        if (btnReportar != null) {
            btnReportar.setOnClickListener(v -> mostrarDialogoReportar(sesion));
        }

        btnCerrar.setOnClickListener(v -> {
            sesion.cerrarSesion();
            irAlLogin();
        });
    }

    private void inicializarMapa() {

        // Cargar la configuración de OSMDroid.
        Configuration.getInstance().load(
                getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));

        Configuration.getInstance()
                .setUserAgentValue(getPackageName());

        // Obtener el mapa del XML de la pantalla principal.
        mapView = findViewById(R.id.mapView);

        // Configurar el mapa de calles.
        mapView.setTileSource(TileSourceFactory.MAPNIK);

        // Permitir zoom con los dedos.
        mapView.setMultiTouchControls(true);

        // Obtener el controlador del mapa.
        IMapController controlador = mapView.getController();

        // Definir la ubicación inicial.
        GeoPoint ubicacionInicial =
                new GeoPoint(-34.6037, -58.3816);

        // Establecer el nivel de zoom.
        controlador.setZoom(15.0);

        // Centrar el mapa.
        controlador.setCenter(ubicacionInicial);

        // Crear un marcador.
        Marker marcador = new Marker(mapView);

        // Establecer la posición del marcador.
        marcador.setPosition(ubicacionInicial);

        // Texto que aparecerá al tocar el marcador.
        marcador.setTitle("Ciudad de Buenos Aires");

        // Agregar el marcador al mapa.
        mapView.getOverlays().add(marcador);

        // Redibujar el mapa.
        mapView.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }

        super.onPause();
    }
    private void mostrarDialogoReportar(SessionManager sesion) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Ej: Rampa rota o bloqueada");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reportar Obstáculo")
                .setMessage("Ingresá la descripción del obstáculo:")
                .setView(input)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String desc = input.getText().toString().trim();
                    if (!desc.isEmpty()) enviarReporteApi(sesion, desc);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enviarReporteApi(SessionManager sesion, String desc) {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("ID_Tipo", 1);
            json.put("Descripcion", desc);
            json.put("Latitud", -34.45612);
            json.put("Longitud", -58.65432);
        } catch (org.json.JSONException ignored) {}

        ApiClient.post("api/Reportes", json, sesion.getToken(), resp -> {
            if (isFinishing() || isDestroyed()) return;
            String msj = resp.isSuccess() ? "¡Reporte enviado exitosamente!" : "Error al enviar el reporte";
            android.widget.Toast.makeText(this, msj, android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    private void irAlLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
