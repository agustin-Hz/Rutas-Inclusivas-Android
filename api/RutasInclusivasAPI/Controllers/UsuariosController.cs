using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MySqlConnector;
using RutasInclusivasAPI.Models;
using System.IdentityModel.Tokens.Jwt;

namespace RutasInclusivasAPI.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class UsuariosController : ControllerBase
    {
        private readonly string _cadenaConexion;
        private readonly ILogger<UsuariosController> _logger;

        public UsuariosController(IConfiguration config, ILogger<UsuariosController> logger)
        {
            _cadenaConexion = config.GetConnectionString("ConexionMySQL")
                ?? throw new InvalidOperationException("Falta la cadena de conexión ConexionMySQL.");
            _logger = logger;
        }

        // 1. ALTA (POST) - api/Usuarios
        [HttpPost]
        public async Task<IActionResult> RegistrarUsuario([FromBody] UsuarioRegistro usuario)
        {
            try
            {
                string contrasenaHash = BCrypt.Net.BCrypt.HashPassword(usuario.Contrasena);

                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();
                    string query = "INSERT INTO USUARIOS (Nombre_Usuario, Correo, Contrasena, Zona) VALUES (@Nombre, @Correo, @Contrasena, @Zona)";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Nombre", usuario.Nombre_Usuario);
                        comando.Parameters.AddWithValue("@Correo", usuario.Correo);
                        comando.Parameters.AddWithValue("@Contrasena", contrasenaHash);
                        comando.Parameters.AddWithValue("@Zona", usuario.Zona);
                        await comando.ExecuteNonQueryAsync();
                    }
                }
                return Ok(new { mensaje = "Usuario registrado exitosamente" });
            }
            catch (MySqlException ex) when (ex.ErrorCode == MySqlErrorCode.DuplicateKeyEntry)
            {
                return Conflict(new { error = "El correo o el nombre de usuario ya están registrados." });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al registrar usuario");
                return StatusCode(500, new { error = "Error al registrar" });
            }
        }

        // 2. CONSULTA (GET) - api/Usuarios/{id}
        // Devuelve los datos del perfil. Solo el dueño de la cuenta puede verlos.
        [HttpGet("{id:int}")]
        [Authorize]
        public async Task<IActionResult> ObtenerUsuario(int id)
        {
            if (!EsElMismoUsuario(id))
                return StatusCode(403, new { mensaje = "No podés ver los datos de otro usuario" });

            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    string query = @"SELECT ID_Usuario, Nombre_Usuario, Correo, Zona
                                     FROM USUARIOS
                                     WHERE ID_Usuario = @Id AND Activo = TRUE";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Id", id);

                        using (var lector = await comando.ExecuteReaderAsync())
                        {
                            if (!await lector.ReadAsync())
                                return NotFound(new { mensaje = "Usuario no encontrado" });

                            int posZona = lector.GetOrdinal("Zona");

                            return Ok(new
                            {
                                id = lector.GetInt32("ID_Usuario"),
                                nombre = lector.GetString("Nombre_Usuario"),
                                correo = lector.GetString("Correo"),
                                zona = lector.IsDBNull(posZona) ? null : lector.GetString(posZona)
                            });
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al obtener usuario {Id}", id);
                return StatusCode(500, new { error = "Error al obtener los datos" });
            }
        }

        // 3. MODIFICACIÓN (PUT) - api/Usuarios/{id}
        // usa UsuarioModificacion (solo correo y zona)
        // responde 409 si el correo ya está en uso y devuelve los datos guardados.
        [HttpPut("{id:int}")]
        [Authorize]
        public async Task<IActionResult> ModificarUsuario(int id, [FromBody] UsuarioModificacion datos)
        {
            if (!EsElMismoUsuario(id))
                return StatusCode(403, new { mensaje = "No podés modificar la cuenta de otro usuario" });

            string correo = datos.Correo.Trim();
            string? zona = string.IsNullOrWhiteSpace(datos.Zona) ? null : datos.Zona.Trim();

            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    string query = @"UPDATE USUARIOS
                                     SET Correo = @Correo, Zona = @Zona
                                     WHERE ID_Usuario = @Id AND Activo = TRUE";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Correo", correo);
                        comando.Parameters.AddWithValue("@Zona", (object?)zona ?? DBNull.Value);
                        comando.Parameters.AddWithValue("@Id", id);

                        int filas = await comando.ExecuteNonQueryAsync();
                        if (filas == 0)
                            return NotFound(new { mensaje = "Usuario no encontrado" });
                    }
                }

                return Ok(new
                {
                    mensaje = "Datos actualizados correctamente",
                    usuario = new { id, correo, zona }
                });
            }
            catch (MySqlException ex) when (ex.ErrorCode == MySqlErrorCode.DuplicateKeyEntry)
            {
                return Conflict(new { mensaje = "Ese correo ya está registrado en otra cuenta." });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al modificar usuario {Id}", id);
                return StatusCode(500, new { error = "Error al modificar" });
            }
        }

        // 4. BAJA FÍSICA (DELETE) - api/Usuarios/{id}/fisica
        // Borra la cuenta y, por ON DELETE CASCADE, todos sus reportes. Por el momento no se utiliza.
        [HttpDelete("{id:int}/fisica")]
        [Authorize]
        public async Task<IActionResult> EliminarUsuario(int id)
        {
            if (!EsElMismoUsuario(id))
                return StatusCode(403, new { mensaje = "No podés dar de baja la cuenta de otro usuario" });

            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();
                    string query = "DELETE FROM USUARIOS WHERE ID_Usuario = @Id";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Id", id);

                        int filas = await comando.ExecuteNonQueryAsync();
                        if (filas == 0)
                            return NotFound(new { mensaje = "Usuario no encontrado" });
                    }
                }
                return Ok(new { mensaje = "Usuario eliminado correctamente" });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al eliminar usuario {Id}", id);
                return StatusCode(500, new { error = "Error al eliminar" });
            }
        }

        // 5. BAJA LÓGICA (DELETE) - api/Usuarios/{id}/logica
        // Desactiva la cuenta (Activo = FALSE). Es la que se utiliza desde la app.
        [HttpDelete("{id:int}/logica")]
        [Authorize]
        public async Task<IActionResult> EliminarUsuarioLogica(int id)
        {
            if (!EsElMismoUsuario(id))
                return StatusCode(403, new { mensaje = "No podés dar de baja la cuenta de otro usuario" });

            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    string query = @"UPDATE USUARIOS
                                     SET Activo = FALSE
                                     WHERE ID_Usuario = @Id AND Activo = TRUE";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Id", id);

                        int filas = await comando.ExecuteNonQueryAsync();
                        if (filas == 0)
                            return NotFound(new { mensaje = "Usuario no encontrado o ya inactivo" });
                    }
                }

                return Ok(new { mensaje = "Cuenta dada de baja correctamente" });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al dar de baja usuario {Id}", id);
                return StatusCode(500, new { error = "Error al dar de baja al usuario" });
            }
        }

        // Devuelve true si el usuario del token es el mismo que el {id} de la URL.
        private bool EsElMismoUsuario(int id)
        {
            string? idTexto = User.FindFirst(JwtRegisteredClaimNames.Sub)?.Value;
            return int.TryParse(idTexto, out int idToken) && idToken == id;
        }
    }
}
