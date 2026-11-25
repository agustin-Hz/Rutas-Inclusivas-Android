namespace RutasInclusivasAPI.Models
{
    public class ObstaculoRuta
    {
        public int ID_Reporte { get; set; }

        public int ID_Tipo { get; set; }

        public double Latitud { get; set; }

        public double Longitud { get; set; }

        public string Estado { get; set; } = string.Empty;
    }
}