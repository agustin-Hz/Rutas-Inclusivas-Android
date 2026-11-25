
package rutas.inclusivas;

import java.util.List;

public class Ruta {

    private double distanciaMetros;
    private double duracionSegundos;
    private List<PuntoRuta> puntos;
    private int cantidadObstaculos;
    private boolean esRutaLibre;
    private boolean esAlternativaConMenosObstaculos;
    private String mensaje;

    public Ruta(
            double distanciaMetros,
            double duracionSegundos,
            List<PuntoRuta> puntos,
            int cantidadObstaculos,
            boolean esRutaLibre,
            boolean esAlternativaConMenosObstaculos,
            String mensaje) {

        this.distanciaMetros = distanciaMetros;
        this.duracionSegundos = duracionSegundos;
        this.puntos = puntos;
        this.cantidadObstaculos = cantidadObstaculos;
        this.esRutaLibre = esRutaLibre;
        this.esAlternativaConMenosObstaculos = esAlternativaConMenosObstaculos;
        this.mensaje = mensaje;
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

    public int getCantidadObstaculos() {
        return cantidadObstaculos;
    }

    public boolean isRutaLibre() {
        return esRutaLibre;
    }

    public boolean isAlternativaConMenosObstaculos() {
        return esAlternativaConMenosObstaculos;
    }

    public String getMensaje() {
        return mensaje;
    }
}
