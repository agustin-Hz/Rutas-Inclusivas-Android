
package rutas.inclusivas;

import java.util.List;

public class Ruta {

    private double distanciaMetros;
    private double duracionSegundos;
    private List<PuntoRuta> puntos;

    public Ruta(
            double distanciaMetros,
            double duracionSegundos,
            List<PuntoRuta> puntos) {

        this.distanciaMetros = distanciaMetros;
        this.duracionSegundos = duracionSegundos;
        this.puntos = puntos;
    }

    public double getDistanciaMetros() {
        return distanciaMetros;
    }

    public double getDuracionSegundos() {
        return duracionSegundos;
    }

    public List<PuntoRuta> getPuntos() {
        return puntos;
    }
}
