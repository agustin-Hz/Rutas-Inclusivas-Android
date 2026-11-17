namespace RutasInclusivasAPI.Models
{
    public class ValidacionRegistro
    {
        public int IdReporte { get; set; }
        public string TipoVoto { get; set; } // "Sigue existiendo" o "Ya no existe"
        public string? ComentarioEvidencia { get; set; }
    }
}