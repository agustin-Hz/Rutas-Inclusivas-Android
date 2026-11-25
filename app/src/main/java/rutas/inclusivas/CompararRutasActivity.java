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

    private Button btnVerMapa;

    private TextView tvEstadoAccesibilidad;
    private TextView tvDistanciaRuta;
    private TextView tvDuracionRuta;
    private TextView tvCantidadObstaculos;
    private TextView tvMensajeRuta;

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
        // Obtener los elementos de información de la ruta
        tvEstadoAccesibilidad = findViewById(R.id.tvEstadoAccesibilidad);
        tvDistanciaRuta = findViewById(R.id.tvDistanciaRuta);
        tvDuracionRuta = findViewById(R.id.tvDuracionRuta);
        tvCantidadObstaculos = findViewById(R.id.tvCantidadObstaculos);
        tvMensajeRuta = findViewById(R.id.tvMensajeRuta);

// Botón para volver al mapa
        btnVerMapa = findViewById(R.id.btnVerMapa);



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



    private void mostrarDatosRuta() {

        if (rutaPeatonal == null) {
            return;
        }

        // Convertir distancia de metros a kilómetros
        double distanciaKm =
                rutaPeatonal.getDistanciaMetros() / 1000.0;

        // Convertir duración de segundos a minutos
        int minutos = (int) Math.round(
                rutaPeatonal.getDuracionSegundos() / 60.0
        );

        // Mostrar distancia
        tvDistanciaRuta.setText(
                String.format(
                        java.util.Locale.getDefault(),
                        "%.2f km",
                        distanciaKm
                )
        );

        // Mostrar duración
        tvDuracionRuta.setText(minutos + " min");

        // Mostrar cantidad de obstáculos
        tvCantidadObstaculos.setText(
                String.valueOf(rutaPeatonal.getCantidadObstaculos())
        );

        // Mostrar estado de accesibilidad
        if (rutaPeatonal.isRutaLibre()) {

            tvEstadoAccesibilidad.setText(
                    "Ruta sin obstáculos registrados"
            );

            tvEstadoAccesibilidad.setTextColor(0xFF218838);

        } else {

            tvEstadoAccesibilidad.setText(
                    "Alternativa con menos obstáculos"
            );

            tvEstadoAccesibilidad.setTextColor(0xFFD97706);
        }

        // Mostrar mensaje devuelto por el backend
        tvMensajeRuta.setText(rutaPeatonal.getMensaje());
    }
    private void abrirMapa() {

        // Verificar que exista una ruta
        if (rutaPeatonal == null ||
                rutaPeatonal.getPuntos() == null ||
                rutaPeatonal.getPuntos().isEmpty()) {

            Toast.makeText(
                    this,
                    "No hay una ruta disponible para mostrar.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // Obtener los puntos de la ruta
        int cantidadPuntos = rutaPeatonal.getPuntos().size();

        double[] latitudes = new double[cantidadPuntos];
        double[] longitudes = new double[cantidadPuntos];

        for (int i = 0; i < cantidadPuntos; i++) {

            PuntoRuta punto = rutaPeatonal.getPuntos().get(i);

            latitudes[i] = punto.getLatitud();
            longitudes[i] = punto.getLongitud();
        }

        // Preparar los datos para devolver a HomeActivity
        Intent resultado = new Intent();

        resultado.putExtra("latitudesRuta", latitudes);
        resultado.putExtra("longitudesRuta", longitudes);

        resultado.putExtra(
                "distanciaMetros",
                rutaPeatonal.getDistanciaMetros()
        );

        resultado.putExtra(
                "duracionSegundos",
                rutaPeatonal.getDuracionSegundos()
        );

        resultado.putExtra(
                "cantidadObstaculos",
                rutaPeatonal.getCantidadObstaculos()
        );

        resultado.putExtra(
                "esRutaLibre",
                rutaPeatonal.isRutaLibre()
        );

        resultado.putExtra(
                "esAlternativaConMenosObstaculos",
                rutaPeatonal.isAlternativaConMenosObstaculos()
        );

        resultado.putExtra(
                "mensajeRuta",
                rutaPeatonal.getMensaje()
        );

        setResult(RESULT_OK, resultado);
        finish();
    }
}

