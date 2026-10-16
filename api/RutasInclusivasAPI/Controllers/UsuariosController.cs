using Microsoft.AspNetCore.Mvc;
using MySqlConnector;
using RutasInclusivasAPI.Models;

namespace RutasInclusivasAPI.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class UsuariosController : ControllerBase
    {
        private readonly string _cadenaConexion;

        public UsuariosController(IConfiguration config)
        {
            // Toma la conexión del appsettings.json
            _cadenaConexion = config.GetConnectionString("ConexionMySQL");
        }

        // 1. ALTA (POST)
        [HttpPost]
        public IActionResult RegistrarUsuario([FromBody] UsuarioRegistro usuario)
        {
            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    conexion.Open();
                    string query = "INSERT INTO USUARIOS (Nombre_Usuario, Correo, Contrasena, Zona) VALUES (@Nombre, @Correo, @Contrasena, @Zona)";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Nombre", usuario.Nombre_Usuario);
                        comando.Parameters.AddWithValue("@Correo", usuario.Correo);
                        comando.Parameters.AddWithValue("@Contrasena", usuario.Contrasena);
                        comando.Parameters.AddWithValue("@Zona", usuario.Zona);
                        comando.ExecuteNonQuery();
                    }
                }
                return Ok(new { mensaje = "Usuario registrado exitosamente" });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { error = "Error al registrar", detalle = ex.Message });
            }
        }

        // 2. MODIFICACIÓN (PUT)
        [HttpPut("{id}")]
        public IActionResult ModificarUsuario(int id, [FromBody] UsuarioRegistro usuario)
        {
            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    conexion.Open();
                    // Modificamos el correo y la zona según el ID_Usuario[cite: 1]
                    string query = "UPDATE USUARIOS SET Correo = @Correo, Zona = @Zona WHERE ID_Usuario = @Id";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Correo", usuario.Correo);
                        comando.Parameters.AddWithValue("@Zona", usuario.Zona);
                        comando.Parameters.AddWithValue("@Id", id);

                        int filas = comando.ExecuteNonQuery();
                        if (filas == 0) return NotFound(new { mensaje = "Usuario no encontrado" });
                    }
                }
                return Ok(new { mensaje = "Usuario modificado correctamente" });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { error = "Error al modificar", detalle = ex.Message });
            }
        }

        // 3. BAJA (DELETE) BAJA FISICA
        [HttpDelete("{id}/fisica")]
        public IActionResult EliminarUsuario(int id)
        {
            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    conexion.Open();
                    // Al borrar, el ON DELETE CASCADE de tu base de datos borrará sus reportes automáticamente[cite: 1]
                    string query = "DELETE FROM USUARIOS WHERE ID_Usuario = @Id";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Id", id);

                        int filas = comando.ExecuteNonQuery();
                        if (filas == 0) return NotFound(new { mensaje = "Usuario no encontrado" });
                    }
                }
                return Ok(new { mensaje = "Usuario eliminado correctamente" });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { error = "Error al eliminar", detalle = ex.Message });
            }
        }

        // 4. BAJA (DELETE) BAJA LOGICA
        [HttpDelete("{id}/logica")]
        public IActionResult EliminarUsuarioLogica(int id)
        {
            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    conexion.Open();

                    string query = @"
                        UPDATE USUARIOS
                        SET Activo = FALSE
                        WHERE ID_Usuario = @Id
                        AND Activo = TRUE";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Id", id);

                        int filas = comando.ExecuteNonQuery();
                        if (filas == 0)
                        {
                            return NotFound(new
                            {
                                mensaje = "Usuario no encontrado o ya inactivo"
                            });
                        }
                    }
                }

                return Ok(new
                {
                    mensaje = "Usuario eliminado logicamente"
                });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new
                {
                    error = "Error al dar de baja al usuario",
                    detalle = ex.Message
                });
            }
        }

    }
}