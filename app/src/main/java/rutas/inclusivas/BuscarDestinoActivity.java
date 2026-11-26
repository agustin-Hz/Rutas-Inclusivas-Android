package rutas.inclusivas;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;


import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;


import android.net.Uri;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.ArrayList;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import org.json.JSONException;
import android.app.AlertDialog;

import android.widget.LinearLayout;
import android.widget.TextView;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;

public class BuscarDestinoActivity extends BaseActivity {

    private Button btnGuardarFavorito;

    private EditText etDestino;

    private Handler handler = new Handler(Looper.getMainLooper());

    private Runnable buscarDestinoRunnable;

    private static final long DEMORA_BUSQUEDA = 600;

    private static final int REQUEST_VOZ = 1001;

    private RecyclerView rvDestinos;

    private ArrayList<Destino> listaDestinos;

    private DestinoAdapter destinoAdapter;

    private Destino destinoSeleccionado;

    private LinearLayout layoutDestinoSeleccionado;

    private TextView tvNombreDestinoSeleccionado;
    private TextView tvDireccionDestinoSeleccionado;

    private boolean seleccionandoDestino = false;

    private int idBusqueda = 0;
    private ActivityResultLauncher<Intent> seleccionarUbicacionLauncher;

    private LinearLayout layoutFavoritos;
    private LinearLayout contenedorFavoritos;
    private Button btnCerrarFavoritos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        seleccionarUbicacionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {

                    if (resultado.getResultCode() == RESULT_OK
                            && resultado.getData() != null) {

                        Intent data = resultado.getData();

                        double latitud = data.getDoubleExtra(
                                "latitudElegida", 0
                        );

                        double longitud = data.getDoubleExtra(
                                "longitudElegida", 0
                        );

                        // Mostramos temporalmente la ubicación mientras consultamos la API.
                        destinoSeleccionado = new Destino(
                                "Buscando ubicación...",
                                "Obteniendo dirección...",
                                latitud,
                                longitud
                        );

                        actualizarDestinoSeleccionado();

                        btnGuardarFavorito.setVisibility(View.VISIBLE);

                        seleccionandoDestino = true;
                        etDestino.setText("Buscando ubicación...");
                        seleccionandoDestino = false;

                        rvDestinos.setVisibility(View.GONE);

                       // Consultamos el nombre y la dirección mediante geocodificación inversa.
                        consultarDestinoPorCoordenadas(latitud, longitud);

                        Toast.makeText(
                                this,
                                "Ubicación seleccionada correctamente.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
        setContentView(R.layout.activity_buscar_destino);

        // Referencias a los controles del XML
        etDestino = findViewById(R.id.etDestino);

        layoutDestinoSeleccionado =
                findViewById(R.id.layoutDestinoSeleccionado);

        tvNombreDestinoSeleccionado =
                findViewById(R.id.tvNombreDestinoSeleccionado);

        tvDireccionDestinoSeleccionado =
                findViewById(R.id.tvDireccionDestinoSeleccionado);

        Button btnVolverHome = findViewById(R.id.btnVolverHome);

        Button btnDestinosFavoritos =
                findViewById(R.id.btnDestinosFavoritos);

        Button btnSeleccionarMapa =
                findViewById(R.id.btnSeleccionarMapa);

        Button btnUsarVoz =
                findViewById(R.id.btnUsarVoz);

        Button btnCompararRutas =
                findViewById(R.id.btnCompararRutas);
        btnGuardarFavorito =
                findViewById(R.id.btnGuardarFavorito);
        layoutFavoritos = findViewById(R.id.layoutFavoritos);

        contenedorFavoritos = findViewById(R.id.contenedorFavoritos);

        btnCerrarFavoritos = findViewById(R.id.btnCerrarFavoritos);


        // Volver a HomeActivity
        btnVolverHome.setOnClickListener(v -> {
            finish();
        });


        // Abrir destinos favoritos
        // Abrir destinos favoritos
        btnDestinosFavoritos.setOnClickListener(v -> {

            if (layoutFavoritos.getVisibility() == View.VISIBLE) {

                layoutFavoritos.setVisibility(View.GONE);

            } else {

                consultarDestinosFavoritos();
            }
        });

        btnCerrarFavoritos.setOnClickListener(v -> {

            layoutFavoritos.setVisibility(View.GONE);

        });

        // Selección desde el mapa: se conectará con OSMDroid
        btnSeleccionarMapa.setOnClickListener(v -> {

            Intent intent = new Intent(
                    BuscarDestinoActivity.this,
                    SeleccionarUbicacionActivity.class
            );

            // Recuperamos la ubicación inicial recibida desde HomeActivity.
            double latitudInicial = getIntent().getDoubleExtra(
                    "latitudInicial", -34.6037
            );

            double longitudInicial = getIntent().getDoubleExtra(
                    "longitudInicial", -58.3816
            );

            // Enviamos las coordenadas a la pantalla del mapa.
            intent.putExtra("latitudInicial", latitudInicial);
            intent.putExtra("longitudInicial", longitudInicial);

            seleccionarUbicacionLauncher.launch(intent);
        });

        btnGuardarFavorito.setOnClickListener(v ->
                mostrarDialogoGuardarFavorito()
        );

        // Reconocimiento de voz
        btnUsarVoz.setOnClickListener(v ->
                iniciarReconocimientoVoz()
        );

        // Ir a comparar rutas
        btnCompararRutas.setOnClickListener(v ->
                abrirComparacion()
        );

        configurarBusquedaAutomatica();
        configurarRecyclerView();
    }

    private void configurarBusquedaAutomatica() {

        etDestino.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                // Si estamos completando el campo desde una selección,
                // no necesitamos iniciar otra búsqueda.
                if (seleccionandoDestino) {
                    return;
                }

                String texto = s.toString().trim();

                // El usuario modificó el texto: ya no hay una selección vigente.
                destinoSeleccionado = null;
                actualizarDestinoSeleccionado();

                // Identificador de esta búsqueda.
                int idActual = ++idBusqueda;

                // Cancelamos la búsqueda pendiente anterior.
                if (buscarDestinoRunnable != null) {
                    handler.removeCallbacks(buscarDestinoRunnable);
                }

                // Con menos de 3 caracteres no buscamos.
                if (texto.length() < 3) {
                    destinoAdapter.actualizarDestinos(new ArrayList<>()
                    );

                    rvDestinos.setVisibility(View.GONE);
                    return;
                }

                // Preparamos la nueva búsqueda.
                buscarDestinoRunnable = () -> {
                    consultarApiDestinos(texto,idActual);
                };

                // Esperamos 600 milisegundos.
                handler.postDelayed(
                        buscarDestinoRunnable,
                        DEMORA_BUSQUEDA
                );
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void configurarRecyclerView() {

        rvDestinos = findViewById(R.id.rvDestinos);

        listaDestinos = new ArrayList<>();

        rvDestinos.setLayoutManager(new LinearLayoutManager(this));

        destinoAdapter = new DestinoAdapter(listaDestinos,
                destino -> {

                    destinoSeleccionado = destino;
                    actualizarDestinoSeleccionado();

                    btnGuardarFavorito.setVisibility(View.VISIBLE);

                    seleccionandoDestino = true;

                    etDestino.setText(destino.getNombre());

                    etDestino.setSelection(
                            etDestino.getText().length()
                    );

                    seleccionandoDestino = false;

                    rvDestinos.setVisibility(View.GONE);
                }
        );

        rvDestinos.setAdapter(destinoAdapter);
    }





    private void consultarApiDestinos(String texto, int idActual) {

        // Construimos la dirección de nuestra API.
        String ruta = "api/Destinos/buscar?texto=" + Uri.encode(texto);

        ApiClient.get(ruta, response -> {

            // Si llegó una respuesta de una búsqueda anterior,
            // la ignoramos.
            if (idActual != idBusqueda) {
                return;
            }

            // Comprobamos si hubo un problema de conexión.
            if (response.networkError) {

                rvDestinos.setVisibility(View.GONE);

                Toast.makeText(
                        this,
                        "No se pudo conectar con el servidor.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // Comprobamos el código HTTP.
            if (response.code < 200 || response.code >= 300) {

                rvDestinos.setVisibility(View.GONE);

                Toast.makeText(
                        this,
                        "Error al buscar destinos. Código: " + response.code,
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // Recuperamos el JSONArray de la respuesta.
            JSONArray resultados = response.array;

            // Convertimos el JSONArray en objetos Destino.
            ArrayList<Destino> nuevosDestinos = new ArrayList<>();

            try {

                for (int i = 0; i < resultados.length(); i++) {

                    JSONObject objeto = resultados.getJSONObject(i);

                    String nombre = objeto.optString("nombre");
                    String direccion = objeto.optString("direccion");

                    double latitud = objeto.optDouble("latitud");
                    double longitud = objeto.optDouble("longitud");

                    Destino destino = new Destino(
                            nombre,
                            direccion,
                            latitud,
                            longitud
                    );

                    nuevosDestinos.add(destino);
                }

                // Actualizamos los resultados del adaptador.
                destinoAdapter.actualizarDestinos(nuevosDestinos);

                // Mostramos la lista solamente si hay resultados.
                if (nuevosDestinos.isEmpty()) {
                    rvDestinos.setVisibility(View.GONE);
                } else {
                    rvDestinos.setVisibility(View.VISIBLE);
                }

            } catch (Exception e) {

                rvDestinos.setVisibility(View.GONE);

                Toast.makeText(
                        this,
                        "Error al procesar los destinos recibidos.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void consultarDestinosFavoritos() {

        ApiClient.get("api/Destinos/favoritos", response -> {

            if (response.networkError) {

                Toast.makeText(
                        this,
                        "No se pudo conectar con el servidor.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (!response.isSuccess()) {

                Toast.makeText(
                        this,
                        "No se pudieron cargar los favoritos. Código: "
                                + response.code,
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            JSONArray resultados = response.array;

            ArrayList<Destino> favoritos = new ArrayList<>();

            try {

                for (int i = 0; i < resultados.length(); i++) {

                    JSONObject objeto = resultados.getJSONObject(i);

                    Destino destino = new Destino(
                            objeto.optString("nombre"),
                            objeto.optString("direccion"),
                            objeto.optDouble("latitud"),
                            objeto.optDouble("longitud")
                    );

                    favoritos.add(destino);
                }

                mostrarListaFavoritos(favoritos);

            } catch (Exception e) {

                Toast.makeText(
                        this,
                        "Error al procesar los destinos favoritos.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void mostrarListaFavoritos(ArrayList<Destino> favoritos) {

        // Limpiamos las tarjetas anteriores.
        contenedorFavoritos.removeAllViews();

        // Mostramos el panel.
        layoutFavoritos.setVisibility(View.VISIBLE);

        // Si no hay favoritos, mostramos un mensaje.
        if (favoritos.isEmpty()) {

            TextView mensaje = new TextView(this);

            mensaje.setText("Todavía no tenés destinos favoritos.");
            mensaje.setTextColor(Color.rgb(82, 103, 128));
            mensaje.setTextSize(14);
            mensaje.setGravity(Gravity.CENTER);
            mensaje.setPadding(12, 20, 12, 20);

            contenedorFavoritos.addView(mensaje);

            return;
        }

        // Creamos una tarjeta para cada favorito.
        for (Destino destino : favoritos) {

            boolean seleccionado = destino == destinoSeleccionado;

            LinearLayout tarjeta = new LinearLayout(this);

            tarjeta.setOrientation(LinearLayout.HORIZONTAL);
            tarjeta.setGravity(Gravity.CENTER_VERTICAL);
            tarjeta.setPadding(dp(12), dp(12), dp(12), dp(12));

            GradientDrawable fondo = new GradientDrawable();

            fondo.setColor(
                    seleccionado
                            ? Color.rgb(227, 238, 252)
                            : Color.WHITE
            );

            fondo.setCornerRadius(dp(12));

            if (seleccionado) {
                fondo.setStroke(dp(2), Color.rgb(8, 102, 217));
            }

            tarjeta.setBackground(fondo);

            LinearLayout.LayoutParams parametrosTarjeta =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            parametrosTarjeta.bottomMargin = dp(8);

            tarjeta.setLayoutParams(parametrosTarjeta);

            // Ícono de ubicación.
            TextView icono = new TextView(this);

            icono.setText("📍");
            icono.setTextSize(23);

            LinearLayout.LayoutParams parametrosIcono =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            parametrosIcono.setMarginEnd(dp(12));

            tarjeta.addView(icono, parametrosIcono);

            // Contenedor del nombre y la dirección.
            LinearLayout datos = new LinearLayout(this);

            datos.setOrientation(LinearLayout.VERTICAL);

            LinearLayout.LayoutParams parametrosDatos =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1
                    );

            tarjeta.addView(datos, parametrosDatos);

            // Nombre del destino.
            TextView nombre = new TextView(this);

            nombre.setText(destino.getNombre());
            nombre.setTextColor(Color.rgb(18, 59, 112));
            nombre.setTextSize(16);
            nombre.setTypeface(null, android.graphics.Typeface.BOLD);

            datos.addView(nombre);

            // Dirección.
            TextView direccion = new TextView(this);

            String textoDireccion = destino.getDireccion();

            if (textoDireccion == null || textoDireccion.trim().isEmpty()) {
                textoDireccion = "Dirección no disponible";
            }

            direccion.setText(textoDireccion);
            direccion.setTextColor(Color.rgb(82, 103, 128));
            direccion.setTextSize(13);

            LinearLayout.LayoutParams parametrosDireccion =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            parametrosDireccion.topMargin = dp(4);

            datos.addView(direccion, parametrosDireccion);

            // Marca de selección.
            if (seleccionado) {

                TextView marca = new TextView(this);

                marca.setText("✓");
                marca.setTextColor(Color.WHITE);
                marca.setTextSize(17);
                marca.setGravity(Gravity.CENTER);

                GradientDrawable fondoMarca = new GradientDrawable();

                fondoMarca.setColor(Color.rgb(8, 102, 217));
                fondoMarca.setShape(GradientDrawable.OVAL);

                marca.setBackground(fondoMarca);

                LinearLayout.LayoutParams parametrosMarca =
                        new LinearLayout.LayoutParams(
                                dp(30),
                                dp(30)
                        );

                parametrosMarca.setMarginStart(dp(8));

                tarjeta.addView(marca, parametrosMarca);
            }

            // Seleccionar el destino al tocar la tarjeta.
            tarjeta.setOnClickListener(v -> {

                destinoSeleccionado = destino;

                actualizarDestinoSeleccionado();

                seleccionandoDestino = true;
                etDestino.setText(destino.getNombre());
                seleccionandoDestino = false;

                rvDestinos.setVisibility(View.GONE);

                // No permitir guardar nuevamente un favorito.
                btnGuardarFavorito.setVisibility(View.GONE);

                // Cerramos automáticamente la lista de favoritos.
                layoutFavoritos.setVisibility(View.GONE);
            });

            contenedorFavoritos.addView(tarjeta);
        }
    }

    private int dp(float valor) {
        return (int) (valor * getResources()
                .getDisplayMetrics().density + 0.5f);
    }
    private void actualizarDestinoSeleccionado() {

        if (destinoSeleccionado == null) {
            layoutDestinoSeleccionado.setVisibility(View.GONE);
            return;
        }

        // Mostrar el nombre del destino.
        tvNombreDestinoSeleccionado.setText(
                destinoSeleccionado.getNombre()
        );

        // Mostrar la dirección.
        String direccion = destinoSeleccionado.getDireccion();

        if (direccion == null || direccion.trim().isEmpty()) {
            direccion = "Dirección no disponible";
        }

        tvDireccionDestinoSeleccionado.setText(direccion);

        // Mostrar la tarjeta.
        layoutDestinoSeleccionado.setVisibility(View.VISIBLE);
    }

    private void consultarDestinoPorCoordenadas(double latitud, double longitud) {

        String ruta = "api/Destinos/buscar-coordenadas?latitud="
                + latitud
                + "&longitud="
                + longitud;

        ApiClient.get(ruta, response -> {

            if (response.isSuccess()) {

                try {

                    JSONObject objeto = response.body;

                    String nombre = objeto.optString(
                            "nombre",
                            "Ubicación seleccionada en el mapa"
                    );

                    String direccion = objeto.optString(
                            "direccion",
                            "Dirección no disponible"
                    );

                    destinoSeleccionado = new Destino(
                            nombre,
                            direccion,
                            latitud,
                            longitud
                    );

                    actualizarDestinoSeleccionado();

                    seleccionandoDestino = true;
                    etDestino.setText(nombre);
                    seleccionandoDestino = false;

                    Toast.makeText(
                            this,
                            "Ubicación identificada correctamente.",
                            Toast.LENGTH_SHORT
                    ).show();

                } catch (Exception e) {

                    Toast.makeText(
                            this,
                            "No se pudo interpretar la ubicación recibida.",
                            Toast.LENGTH_SHORT
                    ).show();
                }

            } else {

                Toast.makeText(
                        this,
                        "No se encontró una dirección para esta ubicación.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void guardarDestinoFavorito(String nombre) {

        if (destinoSeleccionado == null) {
            Toast.makeText(
                    this,
                    "Primero seleccioná un destino.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        try {

            JSONObject solicitud = new JSONObject();

            solicitud.put("nombre", nombre);
            solicitud.put("direccion", destinoSeleccionado.getDireccion());
            solicitud.put("latitud", destinoSeleccionado.getLatitud());
            solicitud.put("longitud", destinoSeleccionado.getLongitud());

            ApiClient.post("api/Destinos/favoritos", solicitud, response -> {

                if (response.isSuccess()) {

                    Toast.makeText(
                            this,
                            "Destino guardado en favoritos.",
                            Toast.LENGTH_SHORT
                    ).show();

                } else {

                    Toast.makeText(
                            this,
                            response.mensaje(
                                    "No se pudo guardar el destino. Código: " + response.code
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                }
            });

        } catch (JSONException e) {

            Toast.makeText(
                    this,
                    "Error al preparar los datos del destino.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
    private void mostrarDialogoGuardarFavorito() {

        if (destinoSeleccionado == null) {
            return;
        }

        EditText campoNombre = new EditText(this);

        campoNombre.setHint("Ejemplo: Casa, Trabajo, Facultad");
        campoNombre.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("Guardar destino favorito")
                .setMessage("Ingresá un nombre para identificar este destino.")
                .setView(campoNombre)
                .setPositiveButton("Guardar", (dialog, which) -> {

                    String nombre = campoNombre.getText().toString().trim();

                    if (nombre.isEmpty()) {
                        Toast.makeText(
                                this,
                                "Ingresá un nombre para el destino.",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    guardarDestinoFavorito(nombre);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void iniciarReconocimientoVoz() {
        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                new Locale("es", "AR")
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Decí la dirección o el lugar de destino"
        );

        try {
            startActivityForResult(intent, REQUEST_VOZ);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                    this,
                    "El dispositivo no dispone de reconocimiento de voz.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VOZ
                && resultCode == RESULT_OK
                && data != null) {

            ArrayList<String> resultados =
                    data.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS
                    );

            if (resultados != null && !resultados.isEmpty()) {
                etDestino.setText(resultados.get(0));
                etDestino.setSelection(etDestino.length());
            }
        }
    }

    private void abrirComparacion() {

        if (destinoSeleccionado == null) {
            etDestino.setError("Seleccioná un destino de la lista");
            etDestino.requestFocus();
            return;
        }

        // Preparamos los datos del destino seleccionado.
        Intent intent = new Intent();

        intent.putExtra("nombre", destinoSeleccionado.getNombre());
        intent.putExtra("direccion", destinoSeleccionado.getDireccion());
        intent.putExtra("latitud", destinoSeleccionado.getLatitud());
        intent.putExtra("longitud", destinoSeleccionado.getLongitud());

        // Devolvemos los datos a HomeActivity.
        setResult(RESULT_OK, intent);

        // Cerramos BuscarDestinoActivity.
        finish();
    }
}
