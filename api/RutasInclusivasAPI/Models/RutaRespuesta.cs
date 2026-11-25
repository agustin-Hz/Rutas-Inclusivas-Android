using System.Collections.Generic;

namespace RutasInclusivasAPI.Models
{
    public class RutaRespuesta
    {
        public double DistanciaMetros { get; set; }

        public double DuracionSegundos { get; set; }

        public List<PuntoRuta> Puntos { get; set; } = new List<PuntoRuta>();

        public int CantidadObstaculos { get; set; }
        public bool EsRutaLibre { get; set; }
        public bool EsAlternativaConMenosObstaculos { get; set; }
        public string Mensaje { get; set; } = string.Empty;
    }
}
