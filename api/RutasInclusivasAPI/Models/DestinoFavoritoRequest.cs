using System.ComponentModel.DataAnnotations;

namespace RutasInclusivasAPI.Models
{
    public class DestinoFavoritoRequest
    {
        [Required]
        [StringLength(100)]
        public string Nombre { get; set; } = string.Empty;

        [StringLength(255)]
        public string? Direccion { get; set; }

        [Required]
        [Range(-90, 90)]
        public double? Latitud { get; set; }

        [Required]
        [Range(-180, 180)]
        public double? Longitud { get; set; }
    }
}