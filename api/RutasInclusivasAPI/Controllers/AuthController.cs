using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.AspNetCore.Mvc;
using Microsoft.IdentityModel.Tokens;
using MySqlConnector;
using RutasInclusivasAPI.Models;

namespace RutasInclusivasAPI.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class AuthController : ControllerBase
    {
        private readonly string _cadenaConexion;
        private readonly IConfiguration _config;
        private readonly ILogger<AuthController> _logger;

        // Hash de relleno: si el usuario no existe se verifica contra este hash,
        // así la respuesta tarda lo mismo y no se revela qué cuentas existen.
        private static readonly string _hashRelleno =
            BCrypt.Net.BCrypt.HashPassword("relleno-que-no-es-una-contrasena-real");

        public AuthController(IConfiguration config, ILogger<AuthController> logger)
        {
            _config = config;
            _logger = logger;
            _cadenaConexion = config.GetConnectionString("ConexionMySQL") ?? throw new InvalidOperationException("Falta la cadena de conexión ConexionMySQL.");
        }

        // INICIAR SESIÓN - POST api/Auth/login 
        [HttpPost("login")]
        public async Task<IActionResult> IniciarSesion([FromBody] LoginRequest datos)
        {
            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    string query = @"
                        SELECT ID_Usuario, Nombre_Usuario, Correo, Contrasena, Zona
                        FROM USUARIOS
                        WHERE (Correo = @Identificador OR Nombre_Usuario = @Identificador)
                        AND Activo = TRUE";

                    bool existe = false;
                    int idUsuario = 0;
                    string nombre = "", correo = "", hash = "";
                    string? zona = null;

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Identificador", datos.Identificador.Trim());

                        using (var lector = await comando.ExecuteReaderAsync())
                        {
                            if (await lector.ReadAsync())
                            {
                                existe = true;
                                idUsuario = lector.GetInt32("ID_Usuario");
                                nombre = lector.GetString("Nombre_Usuario");
                                correo = lector.GetString("Correo");
                                hash = lector.GetString("Contrasena");
                                int posZona = lector.GetOrdinal("Zona");
                                zona = lector.IsDBNull(posZona) ? null : lector.GetString(posZona);
                            }
                        }
                    }

                    // Siempre se ejecuta Verify, exista o no el usuario, de modo de no dar información de cuentas existentes
                    bool contrasenaValida = BCrypt.Net.BCrypt.Verify(datos.Contrasena, existe ? hash : _hashRelleno);

                    // Mismo mensaje para "usuario inexistente" y "contraseña incorrecta"
                    if (!existe || !contrasenaValida)
                    {
                        return Unauthorized(new { error = "Credenciales inválidas" });
                    }

                    var acceso = GenerarAccessToken(idUsuario, nombre, correo);

                    return Ok(new
                    {
                        accessToken = acceso.Token,
                        expiraEn = acceso.Expira,
                        usuario = new { id = idUsuario, nombre, correo, zona }
                    });
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al iniciar sesión");
                return StatusCode(500, new { error = "Error al iniciar sesión" });
            }
        }

        private (string Token, DateTime Expira) GenerarAccessToken(int idUsuario, string nombre, string correo)
        {
            var clave = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_config["Jwt:Key"]!));
            var credenciales = new SigningCredentials(clave, SecurityAlgorithms.HmacSha256);
            DateTime expira = DateTime.UtcNow.AddMinutes(_config.GetValue("Jwt:AccessTokenMinutos", 15));

            var claims = new[]
            {
                new Claim(JwtRegisteredClaimNames.Sub, idUsuario.ToString()),
                new Claim(JwtRegisteredClaimNames.UniqueName, nombre),
                new Claim(JwtRegisteredClaimNames.Email, correo),
                new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
            };

            var token = new JwtSecurityToken(
                issuer: _config["Jwt:Issuer"],
                audience: _config["Jwt:Audience"],
                claims: claims,
                expires: expira,
                signingCredentials: credenciales);

            return (new JwtSecurityTokenHandler().WriteToken(token), expira);
        }
    }
}
