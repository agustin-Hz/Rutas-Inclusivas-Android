package rutas.inclusivas;

import android.app.Application;

/**
 * Clase Application: Android crea UN objeto de esta clase al iniciar la app,
 * antes que cualquier Activity. Es el lugar para inicializar cosas globales.
 */
public class RutasInclusivasApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ApiClient.init(this);
    }
}
