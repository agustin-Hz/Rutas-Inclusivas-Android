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

/** import necesarios para dialog (validacion de obstaculos) */
import android.app.Dialog;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;


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
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;



public class HomeActivity extends BaseActivity {

    private MapView mapView;
    private TextView tvSaludo;
    private TextView tvDatos;
    private FusedLocationProviderClient fusedLocationClient;
    private Double latitudActual = null;
    private Double longitudActual = null;
    private final List<Marker> marcadoresReportes = new ArrayList<>();
    private ActivityResultLauncher<String[]> solicitarPermisosUbicacion;
    private CancellationTokenSource tokenUbicacion;

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

        tvSaludo = findViewById(R.id.tvSaludo);
        tvDatos = findViewById(R.id.tvDatos);
        Button btnMiPerfil = findViewById(R.id.btnMiPerfil);
        btnMiPerfil.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, PerfilActivity.class)));

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

        // Cerrar sesión
        btnCerrar.setOnClickListener(v -> {
            sesion.cerrarSesion();
            irAlLogin();
        });
    }

    private void mostrarDatosUsuario() {
        SessionManager sesion = new SessionManager(this);
        tvSaludo.setText(getString(R.string.home_saludo, sesion.getNombre()));
        String zona = sesion.getZona();
        tvDatos.setText(zona.isEmpty()
                ? sesion.getCorreo()
                : getString(R.string.home_datos, sesion.getCorreo(), zona));
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

        // OSMDroid libera su repositorio interno cuando la vista se separa de la ventana
        // (por ejemplo al abrir otra Activity). Se desactiva ese modo y se libera a mano
        // en onDestroy(), para que un callback tardío no cree un Marker sobre un mapa muerto.
        mapView.setDestroyMode(false);

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

            } else {

                android.widget.Toast.makeText(
                        this,
                        "No se pudo obtener la ubicación",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
            }

        }).addOnFailureListener(error -> {

            if (!activityViva()) {
                return;
            }

            android.widget.Toast.makeText(
                    this,
                    "Error al obtener la ubicación",
                    android.widget.Toast.LENGTH_SHORT
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

    /** La Activity sigue en pantalla y el mapa existe: es seguro tocar la interfaz. */
    private boolean activityViva() {
        return mapView != null && !isFinishing() && !isDestroyed();
    }

    private void mostrarDialogoReportar() {

        String[] tipos = {
                "Vereda rota",
                "Rampa rota o bloqueada",
                "Vereda obstruida",
                "Semáforo sonoro averiado",
                "Obra sin paso accesible"
        };

        android.widget.Spinner selectorTipo = new android.widget.Spinner(this);

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

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Describí el obstáculo encontrado");

        android.widget.TextView etiqueta = new android.widget.TextView(this);
        etiqueta.setText("Tipo de obstáculo:");

        android.widget.LinearLayout contenido = new android.widget.LinearLayout(this);

        contenido.setOrientation(android.widget.LinearLayout.VERTICAL);

        int margen = (int) (20 * getResources().getDisplayMetrics().density);
        contenido.setPadding(margen, 10, margen, 10);

        contenido.addView(etiqueta);
        contenido.addView(selectorTipo);
        contenido.addView(input);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reportar obstáculo")
                .setView(contenido)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String desc = input.getText().toString().trim();
                    if (!desc.isEmpty()) {
                        int idTipo = selectorTipo.getSelectedItemPosition() + 1;
                        enviarReporteApi(desc, idTipo);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }


    private void enviarReporteApi(String desc, int idTipo) {
        if (latitudActual == null || longitudActual == null) {
            android.widget.Toast.makeText(
                    this,
                    "Todavía no se pudo obtener tu ubicación",
                    android.widget.Toast.LENGTH_LONG
            ).show();
            return;
        }

        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("ID_Tipo", idTipo);
            json.put("Descripcion", desc);
            json.put("Latitud", latitudActual);
            json.put("Longitud", longitudActual);
        } catch (org.json.JSONException ignored) {}

        ApiClient.post("api/Reportes", json, respuesta -> {
            if (isFinishing() || isDestroyed()) return;

            String msj;

            if (respuesta.networkError) {
                msj = "Error de conexión con la API";
            } else if (respuesta.isSuccess()) {
                msj = "¡Reporte enviado exitosamente!";

                cargarReportesEnMapa();

            } else {
                msj = "Error HTTP: " + respuesta.code;
            }

            android.widget.Toast.makeText(this, msj, android.widget.Toast.LENGTH_SHORT).show();
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

                android.widget.Toast.makeText(
                        this,
                        mensaje,
                        android.widget.Toast.LENGTH_LONG
                ).show();

                return;
            }

            JSONArray reportes = respuesta.array;   // la API devuelve una lista

            // Eliminar los marcadores de reportes anteriores
            mapView.getOverlays().removeAll(marcadoresReportes);
            marcadoresReportes.clear();

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

                String ubicacionTexto = reporte.optString("ubicacionTexto", "").trim();
                String detalleUbicacion;

                if (ubicacionTexto.isEmpty() || ubicacionTexto.equalsIgnoreCase("null")) {
                    detalleUbicacion = "<b>Ubicación GPS:</b> " + latitud + ", " + longitud;
                } else {
                    detalleUbicacion = "<b>Ubicación:</b> " +
                            android.text.TextUtils.htmlEncode(ubicacionTexto);
                }

                Marker marcador = new Marker(mapView);
                marcador.setPosition(new GeoPoint(latitud, longitud));
                String descripcion = reporte.optString("descripcion", "Sin descripción");
                String estado = reporte.optString("estado", "Desconocido");
                String fechaOriginal = reporte.optString("fechaReporte", "");
                String fecha = "Sin fecha";
                String hora = "Sin hora";

                if (fechaOriginal.length() >= 10) {
                    String anio = fechaOriginal.substring(0, 4);
                    String mes = fechaOriginal.substring(5, 7);
                    String dia = fechaOriginal.substring(8, 10);

                    fecha = dia + "/" + mes + "/" + anio;

                    if (fechaOriginal.length() >= 16) {
                        hora = fechaOriginal.substring(11, 16);
                    }
                }

                marcador.setTitle(reporte.optString("tipoObstaculo", "Obstáculo"));

                /** código para guardar el ID del reporte en el marcador y decirle que abra el diálogo DE verificacionDE OBSTACULO al tocarlo: AGUSTIN*/
                marcador.setId(String.valueOf(reporte.optInt("idReporte", -1)));
                marcador.setOnMarkerClickListener((marker, mapView) -> {
                    marker.showInfoWindow(); // Muestra el cartelito normal con la info

                    int id = Integer.parseInt(marker.getId());
                    if (id != -1) {
                        mostrarDialogoValidacion(id); // Llama al diálogo que creaste
                    }
                    return true;
                });

                marcador.setSnippet(
                        "<b>Descripción:</b> " + android.text.TextUtils.htmlEncode(descripcion) +
                                "<br><br><b>Estado:</b> " + estado +
                                "<br><b>Fecha:</b> " + fecha +
                                "<br><b>Hora:</b> " + hora +
                                "<br><br>" + detalleUbicacion
                );

                mapView.getOverlays().add(marcador);
                marcadoresReportes.add(marcador);
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
    private void mostrarDialogoValidacion(int idReporte) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_validar_obstaculo);

        // Ajustar el ancho del diálogo
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        Button btnSigue = dialog.findViewById(R.id.btnSigueExistiendo);
        Button btnNoExiste = dialog.findViewById(R.id.btnYaNoExiste);
        EditText etComentario = dialog.findViewById(R.id.etComentarioValidacion);

        btnSigue.setOnClickListener(v -> {
            String comentario = etComentario.getText().toString().trim();
            enviarValidacionAPI(idReporte, "Sigue existiendo", comentario);
            dialog.dismiss();
        });

        btnNoExiste.setOnClickListener(v -> {
            String comentario = etComentario.getText().toString().trim();
            enviarValidacionAPI(idReporte, "Ya no existe", comentario);
            dialog.dismiss();
        });

        dialog.show();
    }
    private void enviarValidacionAPI(int idReporte, String tipoVoto, String comentario) {

        JSONObject json = new JSONObject();
        try {
            json.put("idReporte", idReporte);
            json.put("tipoVoto", tipoVoto);
            json.put("comentarioEvidencia", comentario.isEmpty() ? JSONObject.NULL : comentario);
        } catch (org.json.JSONException ignored) {
        }

        // ApiClient agrega el token, usa la URL central y maneja el 401 (sesión vencida).
        ApiClient.post("api/Reportes/validar", json, respuesta -> {
            if (!activityViva()) {
                return;
            }

            if (respuesta.networkError) {
                Toast.makeText(this, "Error de conexión con el servidor.", Toast.LENGTH_SHORT).show();
            } else if (respuesta.code == 200) {
                Toast.makeText(this, "¡Gracias! Tu validación fue registrada.", Toast.LENGTH_SHORT).show();
                cargarReportesEnMapa();
            } else if (respuesta.code == 400) {
                Toast.makeText(this, "No se permiten votos duplicados en el mismo reporte.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Error al validar el reporte.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
