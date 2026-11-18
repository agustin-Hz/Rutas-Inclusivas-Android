using System.Collections.Generic;

namespace RutasInclusivasAPI.Models
{
    public class RutaRespuesta
    {
        public double DistanciaMetros { get; set; }

        public double DuracionSegundos { get; set; }

        public List<PuntoRuta> Puntos { get; set; } = new List<PuntoRuta>();
    }
}
