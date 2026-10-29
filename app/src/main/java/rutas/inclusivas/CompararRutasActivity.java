package rutas.inclusivas;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comparar_rutas);

        // Recuperar el destino enviado desde BuscarDestinoActivity
        tvDestino = findViewById(R.id.tvDestino);

        String destino = getIntent().getStringExtra("destino");

        if (destino != null && !destino.trim().isEmpty()) {
            tvDestino.setText(destino);
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

    private void abrirMapa() {
        if (rutaSeleccionada == null) {
            Toast.makeText(
                    this,
                    "Seleccioná una ruta antes de continuar.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Toast.makeText(
                this,
                "La conexión con el mapa se implementará en el siguiente paso.",
                Toast.LENGTH_LONG
        ).show();
    }
}

