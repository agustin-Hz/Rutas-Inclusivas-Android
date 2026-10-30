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

                    string query = @"INSERT INTO REPORTES 
                                    (ID_Usuario, ID_Tipo, Descripcion, Ubicacion_Texto, Latitud, Longitud, Url_Fotografia)
                                    VALUES (@IdUsuario, @IdTipo, @Descripcion, @Ubicacion, @Latitud, @Longitud, @Fotografia)";

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

        [HttpPut("{id:int}")]
        [Authorize]
        public async Task<IActionResult> ModificarReporte(int id, [FromBody] ReporteRegistro reporte)
        {
            string? idTexto = User.FindFirst(JwtRegisteredClaimNames.Sub)?.Value;

            if (!int.TryParse(idTexto, out int idUsuario))
            {
                return Unauthorized(new
                {
                    mensaje = "Usuario no identificado"
                });
            }

            if (id <= 0)
            {
                return BadRequest(new
                {
                    mensaje = "El ID del reporte debe ser válido"
                });
            }

            if (reporte.ID_Tipo <= 0 ||
                string.IsNullOrWhiteSpace(reporte.Descripcion))
            {
                return BadRequest(new
                {
                    mensaje = "El tipo y la descripción son obligatorios"
                });
            }

            if (reporte.Latitud < -90 || reporte.Latitud > 90 ||
                reporte.Longitud < -180 || reporte.Longitud > 180)
            {
                return BadRequest(new
                {
                    mensaje = "Coordenadas inválidas"
                });
            }

            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    string query = @"UPDATE REPORTES
                                    SET
                                    ID_Tipo = @IdTipo,
                                    Descripcion = @Descripcion,
                                    Ubicacion_Texto = @Ubicacion,
                                    Latitud = @Latitud,
                                    Longitud = @Longitud,
                                    Url_Fotografia = @Fotografia
                                    WHERE ID_Reporte = @IdReporte AND ID_Usuario = @IdUsuario";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@IdReporte", id);
                        comando.Parameters.AddWithValue("@IdUsuario", idUsuario);
                        comando.Parameters.AddWithValue("@IdTipo", reporte.ID_Tipo);
                        comando.Parameters.AddWithValue("@Descripcion", reporte.Descripcion);
                        comando.Parameters.AddWithValue("@Ubicacion", (object?)reporte.Ubicacion_Texto ?? DBNull.Value);
                        comando.Parameters.AddWithValue("@Latitud", reporte.Latitud);
                        comando.Parameters.AddWithValue("@Longitud", reporte.Longitud);
                        comando.Parameters.AddWithValue("@Fotografia", (object?)reporte.Url_Fotografia ?? DBNull.Value);

                        int filas = await comando.ExecuteNonQueryAsync();

                        if (filas == 0)
                        {
                            return NotFound(new
                            {
                                mensaje = "Reporte no encontrado o no pertenece al usuario"
                            });
                        }

                        return Ok(new
                        {
                            mensaje = "Reporte modificado correctamente"
                        });
                    }
                }
            }
            catch (MySqlException ex) when (ex.Number == 1452)
            {
                return BadRequest(new
                {
                    mensaje = "El tipo de obstáculo no existe"
                });
            }
            catch (Exception)
            {
                return StatusCode(500, new
                {
                    mensaje = "Error al modificar el reporte"
                });
            }
        }

        [HttpDelete("{id:int}")]
        [Authorize]
        public async Task<IActionResult> EliminarReporte(int id)
        {
            string? idTexto = User.FindFirst(JwtRegisteredClaimNames.Sub)?.Value;

            if (!int.TryParse(idTexto, out int idUsuario))
            {
                return Unauthorized(new
                {
                    mensaje = "Usuario no identificado"
                });
            }

            if (id <= 0)
            {
                return BadRequest(new
                {
                    mensaje = "El ID del reporte debe ser válido"
                });
            }

            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    string query = @"UPDATE REPORTES
                                    SET Estado = 'Eliminado'
                                    WHERE ID_Reporte = @IdReporte AND ID_Usuario = @IdUsuario AND Estado = 'Activo'";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@IdReporte", id);
                        comando.Parameters.AddWithValue("@IdUsuario", idUsuario);

                        int filas = await comando.ExecuteNonQueryAsync();

                        if (filas == 0)
                        {
                            return NotFound(new
                            {
                                mensaje = "Reporte no encontrado, no activo o no pertenece al usuario"
                            });
                        }

                        return Ok(new
                        {
                            mensaje = "Reporte dado de baja correctamente"
                        });
                    }
                }
            }
            catch (Exception)
            {
                return StatusCode(500, new
                {
                    mensaje = "Error al dar de baja el reporte"
                });
            }
        }

        [HttpGet]
        [Authorize]
        public async Task<IActionResult> ObtenerReportesActivos()
        {
            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    string query = @"SELECT
                    r.ID_Reporte,
                    r.ID_Usuario,
                    r.ID_Tipo,
                    t.Nombre AS Tipo_Obstaculo,
                    r.Descripcion,
                    r.Ubicacion_Texto,
                    r.Latitud,
                    r.Longitud,
                    r.Url_Fotografia,
                    r.Fecha_Reporte,
                    r.Estado
                    FROM REPORTES r
                    INNER JOIN TIPOS_OBSTACULO t
                    ON r.ID_Tipo = t.ID_Tipo
                    WHERE r.Estado = 'Activo'
                    ORDER BY r.Fecha_Reporte DESC";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        using (var lector = await comando.ExecuteReaderAsync())
                        {
                            var reportes = new List<object>();

                            while (await lector.ReadAsync())
                            {
                                string? ubicacion = null;
                                if (lector["Ubicacion_Texto"] != DBNull.Value)
                                {
                                    ubicacion = lector.GetString("Ubicacion_Texto");
                                }

                                string? fotografia = null;
                                if (lector["Url_Fotografia"] != DBNull.Value)
                                {
                                    fotografia = lector.GetString("Url_Fotografia");
                                }

                                DateTime? fecha = null;
                                if (lector["Fecha_Reporte"] != DBNull.Value)
                                {
                                    fecha = lector.GetDateTime("Fecha_Reporte");
                                }

                                reportes.Add(new
                                {
                                    idReporte = lector.GetInt32("ID_Reporte"),
                                    idUsuario = lector.GetInt32("ID_Usuario"),
                                    idTipo = lector.GetInt32("ID_Tipo"),
                                    tipoObstaculo = lector.GetString("Tipo_Obstaculo"),
                                    descripcion = lector.GetString("Descripcion"),
                                    ubicacionTexto = ubicacion,
                                    latitud = lector.GetDecimal("Latitud"),
                                    longitud = lector.GetDecimal("Longitud"),
                                    urlFotografia = fotografia,
                                    fechaReporte = fecha,
                                    estado = lector.GetString("Estado")
                                });
                            }

                            return Ok(reportes);
                        }
                    }
                }
            }
            catch (Exception)
            {
                return StatusCode(500, new
                {
                    mensaje = "Error al obtener los reportes"
                });
            }
        }
    }
}