using System.IdentityModel.Tokens.Jwt;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MySqlConnector;
using RutasInclusivasAPI.Models;
using RutasInclusivasAPI.Services;

namespace RutasInclusivasAPI.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    [Authorize]
    public class NotificacionesController : ControllerBase
    {
        private readonly string _cadenaConexion;
        private readonly NotificacionService _notificaciones;

        public NotificacionesController(IConfiguration config, NotificacionService notificaciones)
        {
            _cadenaConexion = config.GetConnectionString("ConexionMySQL")
                ?? throw new InvalidOperationException("Falta configurar ConexionMySQL");
            _notificaciones = notificaciones;
        }

        private bool TryObtenerIdUsuario(out int idUsuario)
        {
            string? idTexto = User.FindFirst(JwtRegisteredClaimNames.Sub)?.Value;
            return int.TryParse(idTexto, out idUsuario);
        }

        // La app envía su token FCM después de iniciar sesión o cuando Firebase lo renueva.
        [HttpPost("token")]
        public async Task<IActionResult> RegistrarToken([FromBody] TokenFcmRegistro solicitud)
        {
            if (!TryObtenerIdUsuario(out int idUsuario))
                return Unauthorized(new { mensaje = "Usuario no identificado" });

            if (string.IsNullOrWhiteSpace(solicitud.Token) || solicitud.Token.Length > 255)
                return BadRequest(new { mensaje = "Token inválido" });

            try
            {
                using var conexion = new MySqlConnection(_cadenaConexion);
                await conexion.OpenAsync();

                // Si el token ya existía (por ejemplo, otro usuario inició sesión en el mismo teléfono),
                // pasa a pertenecer al usuario actual.
                using var cmd = new MySqlCommand(@"
                    INSERT INTO DISPOSITIVOS_FCM (ID_Usuario, Token) VALUES (@IdUsuario, @Token)
                    ON DUPLICATE KEY UPDATE ID_Usuario = @IdUsuario, Fecha_Registro = CURRENT_TIMESTAMP", conexion);
                cmd.Parameters.AddWithValue("@IdUsuario", idUsuario);
                cmd.Parameters.AddWithValue("@Token", solicitud.Token);
                await cmd.ExecuteNonQueryAsync();

                return Ok(new { mensaje = "Dispositivo registrado" });
            }
            catch (Exception)
            {
                return StatusCode(500, new { mensaje = "Error al registrar el dispositivo" });
            }
        }

        // La app lo llama al cerrar sesión para dejar de recibir avisos de ese usuario.
        [HttpPost("token/baja")]
        public async Task<IActionResult> DarDeBajaToken([FromBody] TokenFcmRegistro solicitud)
        {
            if (!TryObtenerIdUsuario(out int idUsuario))
                return Unauthorized(new { mensaje = "Usuario no identificado" });

            try
            {
                using var conexion = new MySqlConnection(_cadenaConexion);
                await conexion.OpenAsync();

                using var cmd = new MySqlCommand(
                    "DELETE FROM DISPOSITIVOS_FCM WHERE Token = @Token AND ID_Usuario = @IdUsuario", conexion);
                cmd.Parameters.AddWithValue("@Token", solicitud.Token);
                cmd.Parameters.AddWithValue("@IdUsuario", idUsuario);
                await cmd.ExecuteNonQueryAsync();

                return Ok(new { mensaje = "Dispositivo dado de baja" });
            }
            catch (Exception)
            {
                return StatusCode(500, new { mensaje = "Error al dar de baja el dispositivo" });
            }
        }

        // TEMPORAL: para probar de punta a punta desde Swagger.
        // Borralo cuando conectes los eventos reales.
        [HttpPost("prueba")]
        public async Task<IActionResult> Prueba()
        {
            if (!TryObtenerIdUsuario(out int idUsuario))
                return Unauthorized(new { mensaje = "Usuario no identificado" });

            try
            {
                int enviados = await _notificaciones.EnviarAUsuarioAsync(
                    idUsuario, "Rutas Inclusivas", "Notificación de prueba desde la API.");

                return Ok(new { enviados });
            }
            catch (Exception ex)
            {
                // Solo para depurar: muestra el motivo real del fallo (credenciales, red, etc.).
                return StatusCode(500, new { mensaje = ex.Message });
            }
        }
    }
}
