using System.ComponentModel.DataAnnotations;

namespace RutasInclusivasAPI.Models
{
    /// <summary>
    /// Datos que se pueden cambiar desde "Mi perfil".
    /// Es un modelo a parte (no UsuarioRegistro) para no exigir nombre ni contraseña.
    /// </summary>
    public class UsuarioModificacion
    {
        [Required(ErrorMessage = "El correo es obligatorio.")]
        [EmailAddress(ErrorMessage = "El correo no tiene un formato válido.")]
        [MaxLength(100)]
        public string Correo { get; set; } = string.Empty;
        
        [MaxLength(100)]
        public string? Zona { get; set; }
    }
}
