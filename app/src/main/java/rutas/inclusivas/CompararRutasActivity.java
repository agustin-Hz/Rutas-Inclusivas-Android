package rutas.inclusivas;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.content.Intent;

public class CompararRutasActivity extends BaseActivity {

    private TextView tvDestino;

    private Button btnTodasLasRutas;
    private Button btnSoloAccesibles;
    private Button btnVerMapa;

    private LinearLayout cardRuta1;
    private LinearLayout cardRuta2;
    private LinearLayout cardRuta3;

    private LinearLayout rutaSeleccionada;

    private static final int COLOR_AZUL = 0xFF0866D9;
    private static final int COLOR_AZUL_CLARO = 0xFFE3EEFC;
    private static final int COLOR_BLANCO = 0xFFFFFFFF;

    private boolean filtroAccesibles = false;

    // Coordenadas del origen.
    private double latitudOrigen;
    private double longitudOrigen;

    // Coordenadas del destino.
    private double latitudDestino;
    private double longitudDestino;

    // Información del destino.
    private String nombreDestino;
    private String direccionDestino;

    // Ruta recibida desde el backend.
    private Ruta rutaPeatonal;

    private TextView tvDatosRuta1;
    private TextView tvAccesibilidadRuta1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comparar_rutas);

        // Recuperar los datos enviados desde HomeActivity.
        Intent datos = getIntent();

        latitudOrigen = datos.getDoubleExtra("latitudOrigen", Double.NaN);
        longitudOrigen = datos.getDoubleExtra("longitudOrigen", Double.NaN);

        nombreDestino = datos.getStringExtra("nombreDestino");
        direccionDestino = datos.getStringExtra("direccionDestino");

        latitudDestino = datos.getDoubleExtra("latitudDestino", Double.NaN);
        longitudDestino = datos.getDoubleExtra("longitudDestino", Double.NaN);

        // Recuperar el destino enviado desde BuscarDestinoActivity
        tvDestino = findViewById(R.id.tvDestino);

        String destino = getIntent().getStringExtra("destino");

        if (nombreDestino != null && !nombreDestino.trim().isEmpty()) {
            tvDestino.setText(nombreDestino);
        } else {
            tvDestino.setText("Destino no especificado");
        }

        // Obtener los filtros
        btnTodasLasRutas = findViewById(R.id.btnTodasLasRutas);
        btnSoloAccesibles = findViewById(R.id.btnSoloAccesibles);
        btnVerMapa = findViewById(R.id.btnVerMapa);

        // Obtener las tarjetas
        cardRuta1 = findViewById(R.id.cardRuta1);
        cardRuta2 = findViewById(R.id.cardRuta2);
        cardRuta3 = findViewById(R.id.cardRuta3);

        tvDatosRuta1 = findViewById(R.id.tvDatosRuta1);
        tvAccesibilidadRuta1 = findViewById(R.id.tvAccesibilidadRuta1);

        // Mostrar todas las alternativas inicialmente
        mostrarTodasLasRutas();

        // Filtro: todas las rutas
        btnTodasLasRutas.setOnClickListener(v ->
                mostrarTodasLasRutas()
        );

        // Filtro: solo rutas accesibles verificadas
        btnSoloAccesibles.setOnClickListener(v ->
                mostrarSoloAccesibles()
        );

        // Seleccionar cada alternativa
        cardRuta1.setOnClickListener(v ->
                seleccionarRuta(cardRuta1)
        );

        cardRuta2.setOnClickListener(v ->
                seleccionarRuta(cardRuta2)
        );

        cardRuta3.setOnClickListener(v ->
                seleccionarRuta(cardRuta3)
        );

        // Preparar el paso siguiente: abrir el mapa
        btnVerMapa.setOnClickListener(v ->
                abrirMapa()
        );
        calcularRutaPeatonal();
    }

    private void calcularRutaPeatonal() {

        // Verificar que las coordenadas sean válidas.
        if (Double.isNaN(latitudOrigen)
                || Double.isNaN(longitudOrigen)
                || Double.isNaN(latitudDestino)
                || Double.isNaN(longitudDestino)) {

            Toast.makeText(
                    this,
                    "Faltan las coordenadas para calcular la ruta.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Consultar al backend.
        ApiClient.calcularRuta(
                latitudOrigen,
                longitudOrigen,
                latitudDestino,
                longitudDestino,
                (ruta, error) -> {

                    if (isFinishing() || isDestroyed()) {
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

                    // Guardar la ruta recibida.
                    rutaPeatonal = ruta;
                    mostrarDatosRuta();

                    Toast.makeText(
                            this,
                            "Ruta peatonal calculada correctamente.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );
    }

    private void mostrarTodasLasRutas() {
        filtroAccesibles = false;

        cardRuta1.setVisibility(View.VISIBLE);
        cardRuta2.setVisibility(View.VISIBLE);
        cardRuta3.setVisibility(View.VISIBLE);

        btnTodasLasRutas.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(COLOR_AZUL)
        );

        btnTodasLasRutas.setTextColor(COLOR_BLANCO);

        btnSoloAccesibles.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(COLOR_AZUL_CLARO)
        );

        btnSoloAccesibles.setTextColor(0xFF123B70);
    }

    private void mostrarSoloAccesibles() {
        filtroAccesibles = true;

        /*
         * Todavía no tenemos rutas con accesibilidad
         * verificada mediante datos reales.
         *
         * Por seguridad, no mostramos ninguna alternativa
         * como accesible sin evidencia.
         */
        cardRuta1.setVisibility(View.GONE);
        cardRuta2.setVisibility(View.GONE);
        cardRuta3.setVisibility(View.GONE);

        rutaSeleccionada = null;

        btnSoloAccesibles.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(COLOR_AZUL)
        );

        btnSoloAccesibles.setTextColor(COLOR_BLANCO);

        btnTodasLasRutas.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(COLOR_AZUL_CLARO)
        );

        btnTodasLasRutas.setTextColor(0xFF123B70);

        Toast.makeText(
                this,
                "Todavía no hay rutas con accesibilidad verificada.",
                Toast.LENGTH_LONG
        ).show();
    }

    private void seleccionarRuta(LinearLayout tarjeta) {
        if (filtroAccesibles) {
            return;
        }

        // Restaurar el aspecto de la tarjeta anterior
        if (rutaSeleccionada != null) {
            rutaSeleccionada.setBackgroundColor(COLOR_BLANCO);
        }

        // Guardar y destacar la nueva selección
        rutaSeleccionada = tarjeta;
        rutaSeleccionada.setBackgroundColor(COLOR_AZUL_CLARO);

        Toast.makeText(
                this,
                "Ruta seleccionada.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void mostrarDatosRuta() {

        if (rutaPeatonal == null) {
            return;
        }

        // Convertir la distancia de metros a kilómetros.
        double distanciaKm =
                rutaPeatonal.getDistanciaMetros() / 1000.0;

        // Convertir la duración de segundos a minutos.
        int minutos = (int) Math.round(
                rutaPeatonal.getDuracionSegundos() / 60.0
        );

        // Mostrar los datos en la primera tarjeta.
        tvDatosRuta1.setText(
                String.format(
                        java.util.Locale.getDefault(),
                        "%.2f km - %d min",
                        distanciaKm,
                        minutos
                )
        );

        // No afirmar que la ruta es accesible sin verificarla.
        tvAccesibilidadRuta1.setText(
                "Accesibilidad todavía no verificada"
        );
    }

    private void abrirMapa() {

        if (rutaSeleccionada == null) {
            Toast.makeText(this,
                    "Seleccioná una ruta antes de continuar.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (rutaPeatonal == null || rutaPeatonal.getPuntos() == null) {
            Toast.makeText(this,
                    "No hay una ruta disponible para mostrar.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int cantidadPuntos = rutaPeatonal.getPuntos().size();

        double[] latitudes = new double[cantidadPuntos];
        double[] longitudes = new double[cantidadPuntos];

        for (int i = 0; i < cantidadPuntos; i++) {

            PuntoRuta punto = rutaPeatonal.getPuntos().get(i);

            latitudes[i] = punto.getLatitud();
            longitudes[i] = punto.getLongitud();
        }

        Intent resultado = new Intent();

        resultado.putExtra("latitudesRuta", latitudes);
        resultado.putExtra("longitudesRuta", longitudes);

        setResult(RESULT_OK, resultado);
        finish();
    }
}

