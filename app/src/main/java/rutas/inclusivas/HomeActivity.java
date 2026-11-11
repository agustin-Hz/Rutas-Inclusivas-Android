package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONArray;

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
import android.widget.ImageButton;

/** Importo lo nececario para trabajar con la ubicación. */

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.Map;

import org.json.JSONObject;



public class HomeActivity extends BaseActivity {

    private MapView mapView;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> solicitarPermisosUbicacion;

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

        solicitarPermisosUbicacion =
                registerForActivityResult(
                        new ActivityResultContracts.RequestMultiplePermissions(),
                        resultado -> {

                            boolean ubicacionPrecisa =
                                    Boolean.TRUE.equals(
                                            resultado.get(Manifest.permission.ACCESS_FINE_LOCATION));

                            boolean ubicacionAproximada =
                                    Boolean.TRUE.equals(
                                            resultado.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                            if (ubicacionPrecisa || ubicacionAproximada) {
                                obtenerUbicacion();
                            }
                        }
                );





        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home), (v, insets) -> {
            Insets barras = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
            );
            v.setPadding(
                    barras.left,
                    barras.top,
                    barras.right,
                    barras.bottom
            );
            return insets;
        });

        inicializarMapa();
        verificarPermisosUbicacion();
        cargarReportesEnMapa();

        TextView tvSaludo = findViewById(R.id.tvSaludo);
        TextView tvDatos = findViewById(R.id.tvDatos);
        Button btnCerrar = findViewById(R.id.btnCerrarSesion);

        // Acceso rápido: buscar destino
        ImageButton btnBuscarDestinoHome =
                findViewById(R.id.btnBuscarDestinoHome);

        btnBuscarDestinoHome.setOnClickListener(v -> {
            startActivity(
                    new Intent(HomeActivity.this, BuscarDestinoActivity.class)
            );
        });

        // Acceso rápido: reportar obstáculo
        ImageButton btnReportarHome =
                findViewById(R.id.btnReportarHome);

        btnReportarHome.setOnClickListener(v ->
                mostrarDialogoReportar()
        );

        // Acceso rápido: configurar accesibilidad
        ImageButton btnAccesibilidadHome =
                findViewById(R.id.btnAccesibilidadHome);

        btnAccesibilidadHome.setOnClickListener(v -> {
            startActivity(
                    new Intent(HomeActivity.this, AccesibilidadActivity.class)
            );
        });

        // Primera configuración de accesibilidad
        if (!new AccesibilidadPrefs(this).estaConfigurada()) {
            Intent primera = new Intent(
                    HomeActivity.this,
                    AccesibilidadActivity.class
            );
            primera.putExtra(
                    AccesibilidadActivity.EXTRA_PRIMERA_VEZ,
                    true
            );
            startActivity(primera);
        }

        // Saludo y datos del usuario
        tvSaludo.setText(getString(R.string.home_saludo, sesion.getNombre()));
        String zona = sesion.getZona();
        tvDatos.setText(zona.isEmpty()
                ? sesion.getCorreo()
                : getString(R.string.home_datos, sesion.getCorreo(), zona));

        // Cerrar sesión
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

    private void verificarPermisosUbicacion() {

        boolean permisoPreciso =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        boolean permisoAproximado =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        if (permisoPreciso || permisoAproximado) {

            obtenerUbicacion();

        } else {

            solicitarPermisosUbicacion.launch(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }
            );
        }
    }

    private void obtenerUbicacion() {

        boolean permisoPreciso =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        boolean permisoAproximado =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        if (!permisoPreciso && !permisoAproximado) {
            return;
        }

        int prioridad = permisoPreciso
                ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;

        CancellationTokenSource token =
                new CancellationTokenSource();

        fusedLocationClient.getCurrentLocation(
                prioridad,
                token.getToken()
        ).addOnSuccessListener(ubicacion -> {

            if (ubicacion != null) {

                double latitud = ubicacion.getLatitude();
                double longitud = ubicacion.getLongitude();

                centrarMapaEnUsuario(latitud, longitud);

            } else {

                android.widget.Toast.makeText(
                        this,
                        "No se pudo obtener la ubicación",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
            }

        }).addOnFailureListener(error -> {

            android.widget.Toast.makeText(
                    this,
                    "Error al obtener la ubicación",
                    android.widget.Toast.LENGTH_SHORT
            ).show();

        });
    }
    private void centrarMapaEnUsuario(double latitud, double longitud) {

        GeoPoint posicionUsuario =
                new GeoPoint(latitud, longitud);

        IMapController controlador = mapView.getController();

        controlador.setZoom(17.0);

        controlador.setCenter(posicionUsuario);

        Marker marcador = new Marker(mapView);

        marcador.setPosition(posicionUsuario);

        marcador.setTitle("Mi ubicación");

        mapView.getOverlays().add(marcador);

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
    private void mostrarDialogoReportar() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Ej: Rampa rota o bloqueada");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reportar Obstáculo")
                .setMessage("Ingresá la descripción del obstáculo:")
                .setView(input)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String desc = input.getText().toString().trim();
                    if (!desc.isEmpty()) enviarReporteApi(desc);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enviarReporteApi(String desc) {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("ID_Tipo", 1);
            json.put("Descripcion", desc);
            json.put("Latitud", -34.45612);
            json.put("Longitud", -58.65432);
        } catch (org.json.JSONException ignored) {}

        ApiClient.post("api/Reportes", json, respuesta -> {
            if (isFinishing() || isDestroyed()) return;

            String msj;

            if (respuesta.networkError) {
                msj = "Error de conexión con la API";
            } else if (respuesta.isSuccess()) {
                msj = "¡Reporte enviado exitosamente!";
            } else {
                msj = "Error HTTP: " + respuesta.code;
            }

            android.widget.Toast.makeText(this, msj, android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    private void cargarReportesEnMapa() {

        ApiClient.get("api/Reportes", respuesta -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (!respuesta.isSuccess()) {
                String mensaje;

                if (respuesta.networkError) {
                    mensaje = "GET: Error de conexión con la API";
                } else {
                    mensaje = "GET: Error HTTP " + respuesta.code;
                }

                android.widget.Toast.makeText(
                        this,
                        mensaje,
                        android.widget.Toast.LENGTH_LONG
                ).show();

                return;
            }

            JSONArray reportes = respuesta.array;   // la API devuelve una lista

            for (int i = 0; i < reportes.length(); i++) {

                JSONObject reporte = reportes.optJSONObject(i);

                if (reporte == null) {
                    continue;
                }

                double latitud = reporte.optDouble("latitud", Double.NaN);
                double longitud = reporte.optDouble("longitud", Double.NaN);

                if (Double.isNaN(latitud) || Double.isNaN(longitud)) {
                    continue;
                }

                Marker marcador = new Marker(mapView);
                marcador.setPosition(new GeoPoint(latitud, longitud));
                marcador.setTitle(reporte.optString("tipoObstaculo", "Obstáculo"));
                marcador.setSnippet(reporte.optString("descripcion", "Sin descripción"));

                mapView.getOverlays().add(marcador);
            }
            mapView.invalidate();
        });
    }


    private void irAlLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

}
