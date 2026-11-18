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


public class BuscarDestinoActivity extends BaseActivity {

    private EditText etDestino;

    private Handler handler = new Handler(Looper.getMainLooper());

    private Runnable buscarDestinoRunnable;

    private static final long DEMORA_BUSQUEDA = 600;

    private static final int REQUEST_VOZ = 1001;

    private RecyclerView rvDestinos;

    private ArrayList<Destino> listaDestinos;

    private DestinoAdapter destinoAdapter;

    private Destino destinoSeleccionado;

    private boolean seleccionandoDestino = false;

    private int idBusqueda = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_buscar_destino);

        // Referencias a los controles del XML
        etDestino = findViewById(R.id.etDestino);

        Button btnMiUbicacion =
                findViewById(R.id.btnMiUbicacion);

        Button btnCasa =
                findViewById(R.id.btnCasa);

        Button btnTrabajo =
                findViewById(R.id.btnTrabajo);

        Button btnPuntosInteres =
                findViewById(R.id.btnPuntosInteres);

        Button btnSeleccionarMapa =
                findViewById(R.id.btnSeleccionarMapa);

        Button btnUsarVoz =
                findViewById(R.id.btnUsarVoz);

        Button btnCompararRutas =
                findViewById(R.id.btnCompararRutas);

        // Accesos rápidos
        btnMiUbicacion.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "La ubicación GPS se integrará en el siguiente paso.",
                        Toast.LENGTH_LONG
                ).show()
        );

        btnCasa.setOnClickListener(v ->
                etDestino.setText("Casa")
        );

        btnTrabajo.setOnClickListener(v ->
                etDestino.setText("Trabajo")
        );

        btnPuntosInteres.setOnClickListener(v ->
                etDestino.setText("Puntos de interés")
        );

        // Selección desde el mapa: se conectará con OSMDroid
        btnSeleccionarMapa.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "La selección en el mapa se integrará con OSMDroid.",
                        Toast.LENGTH_LONG
                ).show()
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
