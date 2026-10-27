package rutas.inclusivas;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity base de la app. Aplica el tamaño de texto elegido en "Configuración de accesibilidad".
 * TODAS las pantallas deben extender de esta clase (en vez de AppCompatActivity).
 */
public class BaseActivity extends AppCompatActivity {

    private float factorAplicado = 1.0f;

    @Override
    protected void attachBaseContext(Context base) {
        factorAplicado = new AccesibilidadPrefs(base).getFactorTexto();

        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = AccesibilidadPrefs.escalaFinal(config.fontScale, factorAplicado);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    /**
     * Workaround conocido de AppCompat: sin esto, el delegate pisa la configuración
     * del contexto envuelto y se pierde el fontScale.
     */
    @Override
    public void applyOverrideConfiguration(Configuration overrideConfiguration) {
        if (overrideConfiguration != null) {
            int uiMode = overrideConfiguration.uiMode;
            overrideConfiguration.setTo(getBaseContext().getResources().getConfiguration());
            overrideConfiguration.uiMode = uiMode;
        }
        super.applyOverrideConfiguration(overrideConfiguration);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si se cambió el tamaño de texto en otra pantalla, esta se reconstruye con el nuevo valor.
        if (new AccesibilidadPrefs(this).getFactorTexto() != factorAplicado) {
            recreate();
        }
    }
}
