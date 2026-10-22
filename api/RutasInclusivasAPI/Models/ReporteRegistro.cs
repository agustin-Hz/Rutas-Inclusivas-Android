namespace RutasInclusivasAPI.Models
{
    public class ReporteRegistro
    {
        public int ID_Tipo { get; set; }

        public string Descripcion { get; set; } = string.Empty;

        public string? Ubicacion_Texto { get; set; }

        public decimal Latitud { get; set; }

        public decimal Longitud { get; set; }

        public string? Url_Fotografia { get; set; }
    }
}