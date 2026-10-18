using System.ComponentModel.DataAnnotations;

namespace RutasInclusivasAPI.Models
{
    public class LoginRequest
    {
        // Puede ser el correo o el nombre de usuario
        [Required]
        public string Identificador { get; set; } = string.Empty;

        [Required]
        public string Contrasena { get; set; } = string.Empty;
    }
}
