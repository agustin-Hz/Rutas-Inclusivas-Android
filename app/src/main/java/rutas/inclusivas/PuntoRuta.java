package rutas.inclusivas;

public class PuntoRuta {

    private double latitud;
    private double longitud;

    public PuntoRuta(double latitud, double longitud) {
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }
}
