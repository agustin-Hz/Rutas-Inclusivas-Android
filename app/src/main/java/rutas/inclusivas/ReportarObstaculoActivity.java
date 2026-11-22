
package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.Locale;

import android.widget.ArrayAdapter;

import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONException;

public class ReportarObstaculoActivity extends BaseActivity {

    private Spinner spTipoObstaculo;
    private Button btnSeleccionarEnMapa;
    private Button btnMiUbicacionReporte;
    private Button btnEnviarReporte;
    private EditText etDescripcionReporte;
    private TextView tvUbicacionReporte;

    private Double latitudSeleccionada;
    private Double longitudSeleccionada;

    private Double latitudInicial;
    private Double longitudInicial;

    // Lanzador para abrir la pantalla de selección de ubicación.
    private ActivityResultLauncher<Intent> seleccionarUbicacionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reportar_obstaculo);

        // Recuperamos la ubicación enviada desde HomeActivity.
        latitudInicial = getIntent().getDoubleExtra("latitudInicial", 0);
        longitudInicial = getIntent().getDoubleExtra("longitudInicial", 0);

        // Registramos el resultado de la pantalla de selección.
        seleccionarUbicacionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {

                    if (resultado.getResultCode() == RESULT_OK &&
                            resultado.getData() != null) {

                        Intent datos = resultado.getData();

                        latitudSeleccionada = datos.getDoubleExtra(
                                "latitudElegida", 0);

                        longitudSeleccionada = datos.getDoubleExtra(
                                "longitudElegida", 0);

                        // Mostramos las coordenadas seleccionadas.
                        tvUbicacionReporte.setText(
                                String.format(
                                        Locale.getDefault(),
                                        "Latitud: %.6f\nLongitud: %.6f",
                                        latitudSeleccionada,
                                        longitudSeleccionada
                                )
                        );
                    }
                }
        );

        // Vinculamos los elementos del XML.
        spTipoObstaculo = findViewById(R.id.spTipoObstaculo);
        btnSeleccionarEnMapa = findViewById(R.id.btnSeleccionarEnMapa);
        btnMiUbicacionReporte = findViewById(R.id.btnMiUbicacionReporte);
        btnEnviarReporte = findViewById(R.id.btnEnviarReporte);
        etDescripcionReporte = findViewById(R.id.etDescripcionReporte);
        tvUbicacionReporte = findViewById(R.id.tvUbicacionReporte);

        String[] tiposObstaculos = {
                "Vereda rota",
                "Rampa rota o bloqueada",
                "Vereda obstruida",
                "Semáforo sonoro averiado",
                "Obra sin paso accesible"
        };

        ArrayAdapter<String> adaptador = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                tiposObstaculos
        );

        adaptador.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spTipoObstaculo.setAdapter(adaptador);

        // Abrimos la pantalla para seleccionar una ubicación.
        btnSeleccionarEnMapa.setOnClickListener(v -> {

            Intent intent = new Intent(
                    ReportarObstaculoActivity.this,
                    SeleccionarUbicacionActivity.class
            );

            // Enviamos la ubicación inicial si está disponible.
            if (latitudInicial != null && longitudInicial != null
                    && latitudInicial != 0 && longitudInicial != 0) {

                intent.putExtra("latitudInicial", latitudInicial);
                intent.putExtra("longitudInicial", longitudInicial);
            }

            seleccionarUbicacionLauncher.launch(intent);
        });

        btnMiUbicacionReporte.setOnClickListener(v -> {

            if (latitudInicial != 0 && longitudInicial != 0) {

                latitudSeleccionada = latitudInicial;
                longitudSeleccionada = longitudInicial;

                tvUbicacionReporte.setText(
                        String.format(
                                Locale.getDefault(),
                                "Mi ubicación actual:\nLatitud: %.6f\nLongitud: %.6f",
                                latitudSeleccionada,
                                longitudSeleccionada
                        )
                );

            } else {
                Toast.makeText(
                        this,
                        "No se pudo obtener tu ubicación actual",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
        btnEnviarReporte.setOnClickListener(v -> {

            // Obtenemos la descripción escrita por el usuario.
            String descripcion = etDescripcionReporte.getText()
                    .toString()
                    .trim();

            // Validamos la descripción.
            if (descripcion.isEmpty()) {
                etDescripcionReporte.setError("Ingresá una descripción");
                etDescripcionReporte.requestFocus();
                return;
            }

            // Validamos que se haya seleccionado una ubicación.
            if (latitudSeleccionada == null || longitudSeleccionada == null) {

                Toast.makeText(
                        this,
                        "Seleccioná una ubicación para el reporte",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            // Obtenemos el ID del tipo de obstáculo.
            int idTipo = spTipoObstaculo.getSelectedItemPosition() + 1;

            // Enviamos el reporte al backend.
            enviarReporteApi(
                    descripcion,
                    idTipo,
                    latitudSeleccionada,
                    longitudSeleccionada
            );
        });
    }

    private void enviarReporteApi(
            String descripcion,
            int idTipo,
            double latitud,
            double longitud
    ) {

        JSONObject json = new JSONObject();

        try {
            json.put("ID_Tipo", idTipo);
            json.put("Descripcion", descripcion);
            json.put("Latitud", latitud);
            json.put("Longitud", longitud);

        } catch (JSONException e) {
            Toast.makeText(
                    this,
                    "Error al preparar el reporte",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        ApiClient.post("api/Reportes", json, respuesta -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            String mensaje;

            if (respuesta.networkError) {

                mensaje = "Error de conexión con la API";

            } else if (respuesta.isSuccess()) {

                mensaje = "¡Reporte enviado exitosamente!";
                setResult(RESULT_OK);
                finish();

            } else {

                mensaje = "Error HTTP: " + respuesta.code;
            }

            Toast.makeText(
                    this,
                    mensaje,
                    Toast.LENGTH_SHORT
            ).show();
        });
    }
}
