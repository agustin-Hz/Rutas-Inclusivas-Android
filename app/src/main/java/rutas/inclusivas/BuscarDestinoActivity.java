package rutas.inclusivas;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class BuscarDestinoActivity extends BaseActivity {

    private EditText etDestino;

    private static final int REQUEST_VOZ = 1001;

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
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
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
        String destino = etDestino.getText()
                .toString()
                .trim();

        if (destino.isEmpty()) {
            etDestino.setError(
                    "Ingresá o seleccioná un destino"
            );
            etDestino.requestFocus();
            return;
        }

        Intent intent = new Intent(
                BuscarDestinoActivity.this,
                CompararRutasActivity.class
        );

        intent.putExtra("destino", destino);

        startActivity(intent);
    }
}
