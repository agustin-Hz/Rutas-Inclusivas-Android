package rutas.inclusivas;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;

/** Pantalla 5 del diseño: Configuración de accesibilidad. */
public class AccesibilidadActivity extends BaseActivity {

    /** La abre HomeActivity automáticamente la primera vez que el usuario entra. */
    public static final String EXTRA_PRIMERA_VEZ = "primera_vez";

    private AccesibilidadPrefs prefs;
    private TextToSpeech tts;
    private boolean ttsListo = false;
    private String textoPendiente = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_accesibilidad);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.accesibilidad), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        prefs = new AccesibilidadPrefs(this);

        // Si se abrió automáticamente, no se vuelve a mostrar sola.
        if (getIntent().getBooleanExtra(EXTRA_PRIMERA_VEZ, false)) {
            prefs.marcarConfigurada();
        }

        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());
        findViewById(R.id.btnContinuar).setOnClickListener(v -> finish());

        configurarVoz();
        configurarVibracion();
        configurarLector();
        configurarTamanoTexto();
        configurarContraste();
    }

    // ------------------------------------------------------------------ Voz
    private void configurarVoz() {
        MaterialSwitch sw = findViewById(R.id.swVoz);
        View fila = findViewById(R.id.filaVoz);
        sw.setChecked(prefs.isVoz());
        prepararFilaSwitch(fila, sw);
        sw.setOnCheckedChangeListener((boton, activado) -> {
            prefs.setVoz(activado);
            actualizarEstado(fila, activado);
            // Confirmación hablada: sirve de prueba de que la voz funciona en el dispositivo.
            decir(getString(activado ? R.string.acc_voz_activada : R.string.acc_voz_desactivada));
        });
    }

    private void decir(String texto) {
        if (tts == null) {
            textoPendiente = texto;
            tts = new TextToSpeech(getApplicationContext(), estado -> {
                if (estado == TextToSpeech.SUCCESS) {
                    int r = tts.setLanguage(new Locale("es", "AR"));
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(new Locale("es"));
                    }
                    ttsListo = true;
                    if (textoPendiente != null) {
                        tts.speak(textoPendiente, TextToSpeech.QUEUE_FLUSH, null, "acc");
                        textoPendiente = null;
                    }
                } else {
                    Toast.makeText(this, R.string.acc_voz_error, Toast.LENGTH_LONG).show();
                }
            });
        } else if (ttsListo) {
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "acc");
        } else {
            textoPendiente = texto;
        }
    }

    // ------------------------------------------------------------ Vibración
    private void configurarVibracion() {
        MaterialSwitch sw = findViewById(R.id.swVibracion);
        View fila = findViewById(R.id.filaVibracion);
        sw.setChecked(prefs.isVibracion());
        prepararFilaSwitch(fila, sw);
        sw.setOnCheckedChangeListener((boton, activado) -> {
            prefs.setVibracion(activado);
            actualizarEstado(fila, activado);
            if (activado) AccesibilidadPrefs.vibrar(this); // muestra cómo se siente
        });
    }

    // -------------------------------------------------------- Lector pantalla
    private void configurarLector() {
        MaterialSwitch sw = findViewById(R.id.swLector);
        View fila = findViewById(R.id.filaLector);
        sw.setChecked(prefs.isLector(talkBackActivo()));
        prepararFilaSwitch(fila, sw);
        sw.setOnCheckedChangeListener((boton, activado) -> {
            prefs.setLector(activado);
            actualizarEstado(fila, activado);
            // Una app no puede encender TalkBack: se guía al usuario a los ajustes del sistema.
            if (activado && !talkBackActivo()) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.acc_talkback_titulo)
                        .setMessage(R.string.acc_talkback_mensaje)
                        .setPositiveButton(R.string.acc_abrir_ajustes, (d, w) ->
                                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                        .setNegativeButton(R.string.acc_ahora_no, null)
                        .show();
            }
        });
    }

    private boolean talkBackActivo() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }

    // ------------------------------------------------------- Tamaño de texto
    private void configurarTamanoTexto() {
        String[] nombres = {
                getString(R.string.acc_texto_normal),
                getString(R.string.acc_texto_grande),
                getString(R.string.acc_texto_muy_grande)
        };
        TextView valor = findViewById(R.id.tvTextoValor);
        valor.setText(nombres[prefs.getTamanoTexto()]);

        findViewById(R.id.filaTexto).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.acc_texto_titulo)
                        .setSingleChoiceItems(nombres, prefs.getTamanoTexto(), (dialogo, cual) -> {
                            dialogo.dismiss();
                            if (cual != prefs.getTamanoTexto()) {
                                prefs.setTamanoTexto(cual);
                                recreate(); // se vuelve a dibujar con el nuevo tamaño
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
    }

    // -------------------------------------------------------------- Contraste
    private void configurarContraste() {
        ((TextView) findViewById(R.id.tvContrasteValor)).setText(R.string.acc_contraste_estandar);
        // PENDIENTE: el alto contraste requiere pasar los colores de los layouts a recursos de tema.
        findViewById(R.id.filaContraste).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.acc_contraste_titulo)
                        .setMessage(R.string.acc_contraste_proximamente)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
    }

    // ----------------------------------------------------- Accesibilidad filas
    /**
     * La fila entera funciona como interruptor (objetivo táctil grande) y TalkBack la lee
     * como una sola unidad: título, detalle, "Interruptor" y su estado.
     */
    private void prepararFilaSwitch(View fila, MaterialSwitch sw) {
        sw.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        fila.setOnClickListener(v -> sw.toggle());
        ViewCompat.setAccessibilityDelegate(fila, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setRoleDescription(getString(R.string.acc_rol_interruptor));
            }
        });
        actualizarEstado(fila, sw.isChecked());
    }

    private void actualizarEstado(View fila, boolean activado) {
        ViewCompat.setStateDescription(fila, getString(
                activado ? R.string.acc_estado_activado : R.string.acc_estado_desactivado));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
