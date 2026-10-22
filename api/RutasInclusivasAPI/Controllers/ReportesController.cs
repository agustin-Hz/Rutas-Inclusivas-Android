using Microsoft.AspNetCore.Mvc;
using RutasInclusivasAPI.Models;
using Microsoft.AspNetCore.Authorization;
using System.IdentityModel.Tokens.Jwt;
using MySqlConnector;

namespace RutasInclusivasAPI.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class ReportesController : ControllerBase
    {
        private readonly string _cadenaConexion;

        public ReportesController(IConfiguration config)
        {
            _cadenaConexion = config.GetConnectionString("ConexionMySQL")
                ?? throw new InvalidOperationException("Falta configurar ConexionMySQL");
        }

        [HttpPost]
        [Authorize]
        public async Task<IActionResult> RegistrarReporte([FromBody] ReporteRegistro reporte)
        {
            // Obtenemos el ID del usuario desde su token.
            string? idTexto = User.FindFirst(JwtRegisteredClaimNames.Sub)?.Value;

            if (!int.TryParse(idTexto, out int idUsuario))
            {
                return Unauthorized(new { mensaje = "Usuario no identificado" });
            }

            // Validamos los datos obligatorios.
            if (reporte.ID_Tipo <= 0 ||
                string.IsNullOrWhiteSpace(reporte.Descripcion))
            {
                return BadRequest(new {
                    mensaje = "El tipo y la descripción son obligatorios"
                }
                );
            }

            // Validamos que las coordenadas sean posibles.
            if (reporte.Latitud < -90 || reporte.Latitud > 90 ||
                reporte.Longitud < -180 || reporte.Longitud > 180)
            {
                return BadRequest(new {
                    mensaje = "Coordenadas inválidas"
                });
            }

            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    string query = @"
                        INSERT INTO REPORTES
                        (ID_Usuario, ID_Tipo, Descripcion,
                         Ubicacion_Texto, Latitud, Longitud, Url_Fotografia)
                        VALUES
                        (@IdUsuario, @IdTipo, @Descripcion,
                         @Ubicacion, @Latitud, @Longitud, @Fotografia)";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@IdUsuario", idUsuario);
                        comando.Parameters.AddWithValue("@IdTipo", reporte.ID_Tipo);
                        comando.Parameters.AddWithValue("@Descripcion", reporte.Descripcion);
                        comando.Parameters.AddWithValue("@Ubicacion", (object?)reporte.Ubicacion_Texto ?? DBNull.Value);
                        comando.Parameters.AddWithValue("@Latitud", reporte.Latitud);
                        comando.Parameters.AddWithValue("@Longitud", reporte.Longitud);
                        comando.Parameters.AddWithValue("@Fotografia", (object?)reporte.Url_Fotografia ?? DBNull.Value);

                        int filas = await comando.ExecuteNonQueryAsync();

                        if (filas != 1)
                        {
                            return StatusCode(500, new {
                                mensaje = "No se pudo guardar el reporte"
                            });
                        }

                        return StatusCode(201, new {
                            mensaje = "Reporte registrado correctamente",
                            idReporte = comando.LastInsertedId
                        });
                    }
                }
            }
            catch (MySqlException ex) when (ex.Number == 1452)
            {
                return BadRequest(new {
                    mensaje = "El usuario o el tipo de obstáculo no existe"
                });
            }
            catch (Exception)
            {
                return StatusCode(500, new {
                    mensaje = "Error al registrar el reporte"
                });
            }
        }
    }
}