package rutas.inclusivas;

import android.os.Bundle;
import org.osmdroid.views.MapView;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import android.widget.Button;
import android.widget.Button;
import android.widget.Toast;
import android.content.Intent;

public class SeleccionarUbicacionActivity extends BaseActivity {

    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_seleccionar_ubicacion);

        mapView = findViewById(R.id.mapSeleccionUbicacion);
        mapView.setTileSource(TileSourceFactory.MAPNIK);

        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(18.0);

        double latitudInicial = getIntent().getDoubleExtra("latitudInicial", -34.6037);
        double longitudInicial = getIntent().getDoubleExtra("longitudInicial", -58.3816);

        GeoPoint puntoInicial = new GeoPoint(latitudInicial, longitudInicial);
        mapView.getController().setCenter(puntoInicial);

        Button btnVolverBusqueda = findViewById(R.id.btnVolverBusqueda);

        btnVolverBusqueda.setOnClickListener(v -> {
            finish();
        });

        Button btnConfirmar = findViewById(R.id.btnConfirmarUbicacion);

        btnConfirmar.setOnClickListener(v -> {

            double latitudElegida = mapView.getMapCenter().getLatitude();
            double longitudElegida = mapView.getMapCenter().getLongitude();

            Intent resultado = new Intent();

            resultado.putExtra("latitudElegida", latitudElegida);
            resultado.putExtra("longitudElegida", longitudElegida);

            setResult(RESULT_OK, resultado);
            finish();
        });
    }
}