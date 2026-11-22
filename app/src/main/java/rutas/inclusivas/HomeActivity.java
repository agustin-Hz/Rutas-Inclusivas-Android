
package rutas.inclusivas;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends BaseActivity {

    private MapView mapView;
    private Polyline rutaDibujada;

    // Marcador azul de la ubicación del usuario.
    private Marker marcadorUsuario;

    // Panel de información de la ruta.
    private LinearLayout panelRuta;
    private TextView tvDistanciaRuta;
    private TextView tvTiempoRuta;
    private TextView tvSaludo;
    private TextView tvDatos;

    private FusedLocationProviderClient fusedLocationClient;

    private Double latitudActual = null;
    private Double longitudActual = null;

    private Double latitudManualSeleccionada = null;
    private Double longitudManualSeleccionada = null;

    // Lista para controlar los marcadores de reportes.
    private final List<Marker> marcadoresReportes = new ArrayList<>();

    private ActivityResultLauncher<String[]> solicitarPermisosUbicacion;
    private ActivityResultLauncher<Intent> compararRutasLauncher;
    private ActivityResultLauncher<Intent> seleccionarUbicacionLauncher;
    private CancellationTokenSource tokenUbicacion;

    private ActivityResultLauncher<Intent> buscarDestinoLauncher;
    private ActivityResultLauncher<Intent> reportarObstaculoLauncher;

    // Datos del destino seleccionado.
    private String nombreDestinoSeleccionado;
    private String direccionDestinoSeleccionado;

    // NaN indica que todavía no se seleccionó un destino.
    private double latitudDestinoSeleccionado = Double.NaN;
    private double longitudDestinoSeleccionado = Double.NaN;

    private int filtroTipoSeleccionado = 0;

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

        seleccionarUbicacionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {

                    if (resultado.getResultCode() != RESULT_OK) {
                        return;
                    }

                    Intent datos = resultado.getData();

                    if (datos == null) {
                        return;
                    }

                    if (!datos.hasExtra("latitudElegida")
                            || !datos.hasExtra("longitudElegida")) {
                        return;
                    }

                    latitudManualSeleccionada =
                            datos.getDoubleExtra("latitudElegida", 0.0);

                    longitudManualSeleccionada =
                            datos.getDoubleExtra("longitudElegida", 0.0);
                }
        );

        reportarObstaculoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {

                    if (resultado.getResultCode() == RESULT_OK) {
                        cargarReportesEnMapa();
                    }
                }
        );

        // Vinculamos el panel de información de la ruta.
        panelRuta = findViewById(R.id.panelRuta);
        tvDistanciaRuta = findViewById(R.id.tvDistanciaRuta);
        tvTiempoRuta = findViewById(R.id.tvTiempoRuta);

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

        compararRutasLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {

                    if (resultado.getResultCode() != RESULT_OK) {
                        return;
                    }

                    Intent datos = resultado.getData();

                    if (datos == null) {
                        return;
                    }

                    double[] latitudes =
                            datos.getDoubleArrayExtra("latitudesRuta");

                    double[] longitudes =
                            datos.getDoubleArrayExtra("longitudesRuta");

                    if (latitudes == null || longitudes == null) {
                        return;
                    }

                    if (latitudes.length != longitudes.length
                            || latitudes.length < 2) {

                        Toast.makeText(
                                this,
                                "Los datos de la ruta no son válidos.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    List<GeoPoint> puntosRuta = new ArrayList<>();

                    for (int i = 0; i < latitudes.length; i++) {
                        puntosRuta.add(
                                new GeoPoint(latitudes[i], longitudes[i])
                        );
                    }

                    // Dibujamos la ruta seleccionada.
                    dibujarRutaEnMapa(puntosRuta);
                }
        );

        buscarDestinoLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        resultado -> {

                            if (resultado.getResultCode() == RESULT_OK) {

                                Intent datos = resultado.getData();

                                if (datos != null) {

                                    nombreDestinoSeleccionado =
                                            datos.getStringExtra("nombre");

                                    direccionDestinoSeleccionado =
                                            datos.getStringExtra("direccion");

                                    latitudDestinoSeleccionado =
                                            datos.getDoubleExtra("latitud", Double.NaN);

                                    longitudDestinoSeleccionado =
                                            datos.getDoubleExtra("longitud", Double.NaN);

                                    if (Double.isNaN(latitudDestinoSeleccionado)
                                            || Double.isNaN(longitudDestinoSeleccionado)) {

                                        Toast.makeText(
                                                this,
                                                "No se recibieron las coordenadas del destino",
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        return;
                                    }

                                    Toast.makeText(
                                            this,
                                            "Destino recibido: " + nombreDestinoSeleccionado,
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    // Obtenemos la ubicación actual para calcular el origen.
                                    obtenerUbicacion();
                                }
                            }
                        }
                );

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.home),
                (v, insets) -> {

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
                }
        );

        inicializarMapa();
        verificarPermisosUbicacion();
        cargarReportesEnMapa();

        tvSaludo = findViewById(R.id.tvSaludo);
        tvDatos = findViewById(R.id.tvDatos);

        Button btnMiPerfil = findViewById(R.id.btnMiPerfil);

        btnMiPerfil.setOnClickListener(v ->
                startActivity(
                        new Intent(HomeActivity.this, PerfilActivity.class)
                )
        );

        Button btnCerrar = findViewById(R.id.btnCerrarSesion);

        // Acceso rápido: buscar destino.
        ImageButton btnBuscarDestinoHome =
                findViewById(R.id.btnBuscarDestinoHome);

        btnBuscarDestinoHome.setOnClickListener(v -> {

            Intent intent = new Intent(
                    HomeActivity.this,
                    BuscarDestinoActivity.class
            );

            buscarDestinoLauncher.launch(intent);
        });

        // Acceso rápido: reportar obstáculo.
        ImageButton btnReportarHome =
                findViewById(R.id.btnReportarHome);

        btnReportarHome.setOnClickListener(v -> {

            Intent intent = new Intent(
                    HomeActivity.this,
                    ReportarObstaculoActivity.class
            );

            if (latitudActual != null && longitudActual != null) {
                intent.putExtra("latitudInicial", latitudActual);
                intent.putExtra("longitudInicial", longitudActual);
            }

            reportarObstaculoLauncher.launch(intent);
        });

        Button btnFiltrarObstaculos =
                findViewById(R.id.btnFiltrarObstaculos);

        btnFiltrarObstaculos.setOnClickListener(v -> {

            String[] tipos = {
                    "Todos los obstáculos",
                    "Vereda rota",
                    "Rampa rota o bloqueada",
                    "Vereda obstruida",
                    "Semáforo sonoro averiado",
                    "Obra sin paso accesible"
            };

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Filtrar obstáculos")
                    .setItems(tipos, (dialog, opcion) -> {

                        filtroTipoSeleccionado = opcion;
                        cargarReportesEnMapa();

                        Toast.makeText(
                                this,
                                "Seleccionaste: " + tipos[opcion],
                                Toast.LENGTH_SHORT
                        ).show();
                    })
                    .show();
        });

        // Acceso rápido: configurar accesibilidad.
        ImageButton btnAccesibilidadHome =
                findViewById(R.id.btnAccesibilidadHome);

        btnAccesibilidadHome.setOnClickListener(v -> {
            startActivity(
                    new Intent(
                            HomeActivity.this,
                            AccesibilidadActivity.class
                    )
            );
        });

        // Primera configuración de accesibilidad.
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

        // Cerrar sesión.
        btnCerrar.setOnClickListener(v -> {
            sesion.cerrarSesion();
            irAlLogin();
        });
    }

    private void mostrarDatosUsuario() {

        SessionManager sesion = new SessionManager(this);

        tvSaludo.setText(
                getString(R.string.home_saludo, sesion.getNombre())
        );

        String zona = sesion.getZona();

        tvDatos.setText(
                zona.isEmpty()
                        ? sesion.getCorreo()
                        : getString(
                        R.string.home_datos,
                        sesion.getCorreo(),
                        zona
                )
        );
    }

    private void inicializarMapa() {

        Configuration.getInstance().load(
                getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE)
        );

        Configuration.getInstance()
                .setUserAgentValue(getPackageName());

        mapView = findViewById(R.id.mapView);

        mapView.setDestroyMode(false);

        mapView.setTileSource(TileSourceFactory.MAPNIK);

        mapView.setMultiTouchControls(true);

        IMapController controlador = mapView.getController();

        // Ubicación inicial del mapa.
        GeoPoint ubicacionInicial =
                new GeoPoint(-34.6037, -58.3816);

        controlador.setZoom(15.0);
        controlador.setCenter(ubicacionInicial);

        // No creamos un marcador fijo en Buenos Aires.
        // El marcador azul se creará cuando obtengamos el GPS.

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

        if (tokenUbicacion != null) {
            tokenUbicacion.cancel();
        }

        tokenUbicacion = new CancellationTokenSource();

        fusedLocationClient.getCurrentLocation(
                prioridad,
                tokenUbicacion.getToken()
        ).addOnSuccessListener(ubicacion -> {

            if (!activityViva()) {
                return;
            }

            if (ubicacion != null) {

                double latitud = ubicacion.getLatitude();
                double longitud = ubicacion.getLongitude();

                latitudActual = latitud;
                longitudActual = longitud;

                centrarMapaEnUsuario(latitud, longitud);

                // Si ya seleccionamos un destino, abrimos Comparar Rutas.
                if (!Double.isNaN(latitudDestinoSeleccionado)
                        && !Double.isNaN(longitudDestinoSeleccionado)) {

                    abrirCompararRutas();
                }

            } else {

                Toast.makeText(
                        this,
                        "No se pudo obtener la ubicación",
                        Toast.LENGTH_SHORT
                ).show();
            }

        }).addOnFailureListener(error -> {

            if (!activityViva()) {
                return;
            }

            Toast.makeText(
                    this,
                    "Error al obtener la ubicación",
                    Toast.LENGTH_SHORT
            ).show();

        });
    }

    private void centrarMapaEnUsuario(double latitud, double longitud) {

        if (!activityViva()) {
            return;
        }

        GeoPoint posicionUsuario =
                new GeoPoint(latitud, longitud);

        IMapController controlador = mapView.getController();

        controlador.setZoom(17.0);
        controlador.setCenter(posicionUsuario);

        // Crear el marcador azul solamente la primera vez.
        if (marcadorUsuario == null) {

            marcadorUsuario = new Marker(mapView);

            marcadorUsuario.setTitle("Mi ubicación");

            marcadorUsuario.setIcon(
                    ContextCompat.getDrawable(
                            this,
                            R.drawable.ic_ubicacion_usuario
                    )
            );

            mapView.getOverlays().add(marcadorUsuario);
        }

        // Actualizar la posición del mismo marcador.
        marcadorUsuario.setPosition(posicionUsuario);

        mapView.invalidate();
    }

    private void dibujarRutaEnMapa(List<GeoPoint> puntos) {

        // Eliminar solamente la ruta anterior.
        if (rutaDibujada != null) {
            mapView.getOverlays().remove(rutaDibujada);
        }

        rutaDibujada = new Polyline();

        rutaDibujada.setPoints(puntos);
        rutaDibujada.setColor(Color.BLUE);
        rutaDibujada.setWidth(12f);

        mapView.getOverlays().add(rutaDibujada);

        // Ajustar el mapa para mostrar toda la ruta.
        BoundingBox limites = BoundingBox.fromGeoPoints(puntos);

        mapView.zoomToBoundingBox(limites, true);

        mapView.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }

        if (tvSaludo != null) {
            mostrarDatosUsuario();
        }
    }

    @Override
    protected void onPause() {

        if (mapView != null) {
            mapView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (tokenUbicacion != null) {
            tokenUbicacion.cancel();
        }

        if (mapView != null) {
            mapView.onDetach();
        }

        super.onDestroy();
    }

    private void abrirCompararRutas() {

        if (latitudActual == null || longitudActual == null) {

            Toast.makeText(
                    this,
                    "Todavía no tenemos tu ubicación",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent = new Intent(
                HomeActivity.this,
                CompararRutasActivity.class
        );

        // Datos del origen.
        intent.putExtra("latitudOrigen", latitudActual);
        intent.putExtra("longitudOrigen", longitudActual);

        // Datos del destino.
        intent.putExtra("nombreDestino", nombreDestinoSeleccionado);
        intent.putExtra("direccionDestino", direccionDestinoSeleccionado);
        intent.putExtra("latitudDestino", latitudDestinoSeleccionado);
        intent.putExtra("longitudDestino", longitudDestinoSeleccionado);

        compararRutasLauncher.launch(intent);
    }

    private void solicitarRuta() {

        if (latitudActual == null || longitudActual == null) {

            Toast.makeText(
                    this,
                    "Todavía no tenemos tu ubicación",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (Double.isNaN(latitudDestinoSeleccionado)
                || Double.isNaN(longitudDestinoSeleccionado)) {

            Toast.makeText(
                    this,
                    "No hay un destino seleccionado",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        ApiClient.calcularRuta(
                latitudActual,
                longitudActual,
                latitudDestinoSeleccionado,
                longitudDestinoSeleccionado,
                (ruta, error) -> {

                    if (!activityViva()) {
                        return;
                    }

                    if (error != null) {

                        Toast.makeText(
                                this,
                                error,
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    dibujarRuta(ruta);
                }
        );
    }

    private void dibujarRuta(Ruta ruta) {

        if (ruta == null || ruta.getPuntos().isEmpty()) {

            Toast.makeText(
                    this,
                    "La ruta no contiene puntos para dibujar",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        List<GeoPoint> puntosMapa = new ArrayList<>();

        for (PuntoRuta punto : ruta.getPuntos()) {

            GeoPoint coordenada = new GeoPoint(
                    punto.getLatitud(),
                    punto.getLongitud()
            );

            puntosMapa.add(coordenada);
        }

        if (rutaDibujada != null) {
            mapView.getOverlays().remove(rutaDibujada);
        }

        rutaDibujada = new Polyline();

        rutaDibujada.setPoints(puntosMapa);
        rutaDibujada.setColor(Color.BLUE);
        rutaDibujada.setWidth(12f);

        mapView.getOverlays().add(rutaDibujada);

        mapView.invalidate();

        double distanciaKm =
                ruta.getDistanciaMetros() / 1000.0;

        int minutos = (int) Math.round(
                ruta.getDuracionSegundos() / 60.0
        );

        tvDistanciaRuta.setText(
                String.format(
                        Locale.getDefault(),
                        "%.2f km",
                        distanciaKm
                )
        );

        tvTiempoRuta.setText(minutos + " min");

        panelRuta.setVisibility(View.VISIBLE);
    }

    /** Comprueba que la Activity y el mapa sigan disponibles. */
    private boolean activityViva() {
        return mapView != null && !isFinishing() && !isDestroyed();
    }

    private void mostrarDialogoReportar() {

        latitudManualSeleccionada = null;
        longitudManualSeleccionada = null;

        String[] tipos = {
                "Vereda rota",
                "Rampa rota o bloqueada",
                "Vereda obstruida",
                "Semáforo sonoro averiado",
                "Obra sin paso accesible"
        };

        android.widget.Spinner selectorTipo =
                new android.widget.Spinner(this);

        android.widget.ArrayAdapter<String> adaptador =
                new android.widget.ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        tipos
                );

        adaptador.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        selectorTipo.setAdapter(adaptador);

        EditText input = new EditText(this);
        input.setHint("Describí el obstáculo encontrado");

        TextView etiqueta = new TextView(this);
        etiqueta.setText("Tipo de obstáculo:");

        LinearLayout contenido = new LinearLayout(this);

        contenido.setOrientation(LinearLayout.VERTICAL);

        int margen = (int) (
                20 * getResources().getDisplayMetrics().density
        );

        contenido.setPadding(margen, 10, margen, 10);

        contenido.addView(etiqueta);
        contenido.addView(selectorTipo);
        contenido.addView(input);

        android.widget.RadioGroup grupoUbicacion =
                new android.widget.RadioGroup(this);

        android.widget.RadioButton opcionGPS =
                new android.widget.RadioButton(this);

        opcionGPS.setId(View.generateViewId());
        opcionGPS.setText("Usar ubicación del GPS");

        android.widget.RadioButton opcionManual =
                new android.widget.RadioButton(this);

        opcionManual.setId(View.generateViewId());
        opcionManual.setText("Seleccionar manualmente");

        grupoUbicacion.addView(opcionGPS);
        grupoUbicacion.addView(opcionManual);

        opcionGPS.setChecked(true);

        contenido.addView(grupoUbicacion);

        opcionManual.setOnClickListener(v -> {

            Intent intent = new Intent(
                    HomeActivity.this,
                    SeleccionarUbicacionActivity.class
            );

            if (latitudActual != null && longitudActual != null) {

                intent.putExtra(
                        "latitudInicial",
                        latitudActual.doubleValue()
                );

                intent.putExtra(
                        "longitudInicial",
                        longitudActual.doubleValue()
                );
            }

            seleccionarUbicacionLauncher.launch(intent);
        });

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reportar obstáculo")
                .setView(contenido)
                .setPositiveButton("Enviar", (dialog, which) -> {

                    String desc =
                            input.getText().toString().trim();

                    if (!desc.isEmpty()) {

                        int idTipo =
                                selectorTipo.getSelectedItemPosition() + 1;

                        if (opcionManual.isChecked()) {

                            if (latitudManualSeleccionada == null
                                    || longitudManualSeleccionada == null) {

                                Toast.makeText(
                                        this,
                                        "Seleccioná una ubicación en el mapa",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }

                            enviarReporteApi(
                                    desc,
                                    idTipo,
                                    latitudManualSeleccionada,
                                    longitudManualSeleccionada
                            );

                        } else {

                            if (latitudActual == null
                                    || longitudActual == null) {

                                Toast.makeText(
                                        this,
                                        "Todavía no se pudo obtener tu ubicación",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }

                            enviarReporteApi(
                                    desc,
                                    idTipo,
                                    latitudActual,
                                    longitudActual
                            );
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enviarReporteApi(
            String desc,
            int idTipo,
            double latitud,
            double longitud
    ) {

        JSONObject json = new JSONObject();

        try {
            json.put("ID_Tipo", idTipo);
            json.put("Descripcion", desc);
            json.put("Latitud", latitud);
            json.put("Longitud", longitud);

        } catch (org.json.JSONException ignored) {
        }

        ApiClient.post("api/Reportes", json, respuesta -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            String msj;

            if (respuesta.networkError) {

                msj = "Error de conexión con la API";

            } else if (respuesta.isSuccess()) {

                msj = "¡Reporte enviado exitosamente!";

                cargarReportesEnMapa();

            } else {

                msj = "Error HTTP: " + respuesta.code;
            }

            Toast.makeText(
                    this,
                    msj,
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void cargarReportesEnMapa() {

        ApiClient.get("api/Reportes", respuesta -> {

            if (!activityViva()) {
                return;
            }

            if (!respuesta.isSuccess()) {

                String mensaje;

                if (respuesta.networkError) {
                    mensaje = "GET: Error de conexión con la API";
                } else {
                    mensaje = "GET: Error HTTP " + respuesta.code;
                }

                Toast.makeText(
                        this,
                        mensaje,
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            JSONArray reportes = respuesta.array;

            // Eliminar solamente los marcadores de reportes anteriores.
            mapView.getOverlays().removeAll(marcadoresReportes);
            marcadoresReportes.clear();

            int reportesMostrados = 0;

            for (int i = 0; i < reportes.length(); i++) {

                JSONObject reporte = reportes.optJSONObject(i);

                if (reporte == null) {
                    continue;
                }

                if (filtroTipoSeleccionado != 0
                        && reporte.optInt("idTipo", -1)
                        != filtroTipoSeleccionado) {

                    continue;
                }

                double latitud =
                        reporte.optDouble("latitud", Double.NaN);

                double longitud =
                        reporte.optDouble("longitud", Double.NaN);

                if (Double.isNaN(latitud)
                        || Double.isNaN(longitud)) {

                    continue;
                }

                String ubicacionTexto =
                        reporte.optString("ubicacionTexto", "").trim();

                String detalleUbicacion;

                if (ubicacionTexto.isEmpty()
                        || ubicacionTexto.equalsIgnoreCase("null")) {

                    detalleUbicacion =
                            "<b>Ubicación GPS:</b> "
                                    + latitud + ", " + longitud;

                } else {

                    detalleUbicacion =
                            "<b>Ubicación:</b> "
                                    + android.text.TextUtils.htmlEncode(
                                    ubicacionTexto
                            );
                }

                // Crear marcador del reporte.
                Marker marcador = new Marker(mapView);

                marcador.setPosition(
                        new GeoPoint(latitud, longitud)
                );

                // Icono naranja para diferenciar los reportes.
                marcador.setIcon(
                        ContextCompat.getDrawable(
                                this,
                                R.drawable.ic_reporte_obstaculo
                        )
                );

                String descripcion =
                        reporte.optString(
                                "descripcion",
                                "Sin descripción"
                        );

                String estado =
                        reporte.optString(
                                "estado",
                                "Desconocido"
                        );

                String fechaOriginal =
                        reporte.optString("fechaReporte", "");

                String fecha = "Sin fecha";
                String hora = "Sin hora";

                if (fechaOriginal.length() >= 10) {

                    String anio =
                            fechaOriginal.substring(0, 4);

                    String mes =
                            fechaOriginal.substring(5, 7);

                    String dia =
                            fechaOriginal.substring(8, 10);

                    fecha = dia + "/" + mes + "/" + anio;

                    if (fechaOriginal.length() >= 16) {
                        hora = fechaOriginal.substring(11, 16);
                    }
                }

                marcador.setTitle(
                        reporte.optString(
                                "tipoObstaculo",
                                "Obstáculo"
                        )
                );

                // Guardamos el ID del reporte en el marcador.
                marcador.setId(
                        String.valueOf(
                                reporte.optInt("idReporte", -1)
                        )
                );

                final String tipoFinal = reporte.optString(
                        "tipoObstaculo", "Obstáculo"
                );

                final String descripcionFinal = descripcion;
                final String estadoFinal = estado;
                final String fechaFinal = fecha;
                final String horaFinal = hora;
                final String ubicacionFinal = detalleUbicacion;

                // Al tocar el marcador, mostramos la información
                // y abrimos el diálogo de validación.
                marcador.setOnMarkerClickListener(
                        (marker, mapView) -> {



                            int id = Integer.parseInt(marker.getId());

                            if (id != -1) {
                                mostrarDialogoDetallesReporte(
                                        id,
                                        tipoFinal,
                                        descripcionFinal,
                                        estadoFinal,
                                        fechaFinal,
                                        horaFinal,
                                        ubicacionFinal
                                );
                            }

                            return true;
                        }
                );
                // Información que aparece al tocar el marcador.
                marcador.setSnippet(
                        "<b>Descripción:</b> "
                                + android.text.TextUtils.htmlEncode(
                                descripcion
                        )
                                + "<br><br><b>Estado:</b> " + estado
                                + "<br><b>Fecha:</b> " + fecha
                                + "<br><b>Hora:</b> " + hora
                                + "<br><br>" + detalleUbicacion
                );

                // Agregamos el marcador al mapa.
                mapView.getOverlays().add(marcador);

                // Lo guardamos para poder eliminarlo al actualizar.
                marcadoresReportes.add(marcador);

                reportesMostrados++;
            }

            if (reportesMostrados == 0
                    && filtroTipoSeleccionado != 0) {

                Toast.makeText(
                        this,
                        "No se encontraron obstáculos de la categoría seleccionada.",
                        Toast.LENGTH_SHORT
                ).show();
            }

            mapView.invalidate();
        });
    }

    private void irAlLogin() {

        Intent intent = new Intent(this, LoginActivity.class);

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    private void mostrarDialogoValidacion(int idReporte) {

        Dialog dialog = new Dialog(this);

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        dialog.setContentView(
                R.layout.dialog_validar_obstaculo
        );

        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        Button btnSigue =
                dialog.findViewById(R.id.btnSigueExistiendo);

        Button btnNoExiste =
                dialog.findViewById(R.id.btnYaNoExiste);

        EditText etComentario =
                dialog.findViewById(R.id.etComentarioValidacion);

        btnSigue.setOnClickListener(v -> {

            String comentario =
                    etComentario.getText().toString().trim();

            enviarValidacionAPI(
                    idReporte,
                    "Sigue existiendo",
                    comentario
            );

            dialog.dismiss();
        });

        btnNoExiste.setOnClickListener(v -> {

            String comentario =
                    etComentario.getText().toString().trim();

            enviarValidacionAPI(
                    idReporte,
                    "Ya no existe",
                    comentario
            );

            dialog.dismiss();
        });

        dialog.show();
    }


    private void mostrarDialogoDetallesReporte(
            int idReporte,
            String tipo,
            String descripcion,
            String estado,
            String fecha,
            String hora,
            String ubicacion
    ) {

        Dialog dialog = new Dialog(this);

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_detalles_reporte);

        TextView tvTipo = dialog.findViewById(R.id.tvDetalleTipo);
        TextView tvDescripcion = dialog.findViewById(R.id.tvDetalleDescripcion);
        TextView tvEstado = dialog.findViewById(R.id.tvDetalleEstado);
        TextView tvFechaHora = dialog.findViewById(R.id.tvDetalleFechaHora);
        TextView tvUbicacion = dialog.findViewById(R.id.tvDetalleUbicacion);

        Button btnValidar = dialog.findViewById(R.id.btnValidarReporte);
        Button btnCerrar = dialog.findViewById(R.id.btnCerrarDetalles);

        // Cargamos los datos del reporte.
        tvTipo.setText(tipo);
        tvDescripcion.setText(descripcion);
        tvEstado.setText(estado);
        tvFechaHora.setText(fecha + " - " + hora);
        tvUbicacion.setText(ubicacion);

        // Abrimos la ventana de validación existente.
        btnValidar.setOnClickListener(v -> {
            dialog.dismiss();
            mostrarDialogoValidacion(idReporte);
        });

        // Cerramos los detalles.
        btnCerrar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent
            );

            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }


    private void enviarValidacionAPI(
            int idReporte,
            String tipoVoto,
            String comentario
    ) {

        JSONObject json = new JSONObject();

        try {

            json.put("idReporte", idReporte);

            json.put("tipoVoto", tipoVoto);

            json.put(
                    "comentarioEvidencia",
                    comentario.isEmpty()
                            ? JSONObject.NULL
                            : comentario
            );

        } catch (org.json.JSONException ignored) {
        }

        ApiClient.post(
                "api/Reportes/validar",
                json,
                respuesta -> {

                    if (!activityViva()) {
                        return;
                    }

                    if (respuesta.networkError) {

                        Toast.makeText(
                                this,
                                "Error de conexión con el servidor.",
                                Toast.LENGTH_SHORT
                        ).show();

                    } else if (respuesta.code == 200) {

                        Toast.makeText(
                                this,
                                "¡Gracias! Tu validación fue registrada.",
                                Toast.LENGTH_SHORT
                        ).show();

                        cargarReportesEnMapa();

                    } else if (respuesta.code == 400) {

                        Toast.makeText(
                                this,
                                "No se permiten votos duplicados en el mismo reporte.",
                                Toast.LENGTH_LONG
                        ).show();

                    } else {

                        Toast.makeText(
                                this,
                                "Error al validar el reporte.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }
}
