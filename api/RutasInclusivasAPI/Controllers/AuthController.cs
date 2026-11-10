using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using System.Net;           // Nuevo: Necesario para NetworkCredential
using System.Net.Mail;      // Nuevo: Necesario para SmtpClient y MailMessage
using Microsoft.AspNetCore.Mvc;
using Microsoft.IdentityModel.Tokens;
using MySqlConnector;
using RutasInclusivasAPI.Models;
using System.Security.Cryptography;

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
        private const int MaxIntentosPin = 5;

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

        // RECUPERAR CONTRASEÑA - POST api/Auth/recuperar
        [HttpPost("recuperar")]
        public async Task<IActionResult> Recuperar([FromBody] RecuperarRequest request)
        {
            if (string.IsNullOrWhiteSpace(request.Correo))
            {
                return BadRequest(new { mensaje = "El correo es obligatorio." });
            }

            // Misma respuesta exista o no el correo. De esta manera, no se revela qué cuentas existen.
            var respuestaGenerica = new { mensaje = "Si el correo está registrado, te enviamos un código de recuperación." };

            // PIN de 6 dígitos con generador criptográfico: de 000000 a 999999
            string pin = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");
            DateTime expiraEn = DateTime.Now.AddMinutes(15);
            string correo = request.Correo.Trim();

            try
            {
                int filasAfectadas;

                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    // Guarda el PIN nuevo y reinicia el contador de intentos
                    string query = @"UPDATE USUARIOS
                                     SET PinRecuperacion = @Pin, PinExpiraEn = @Expira, IntentosPin = 0
                                     WHERE Correo = @Correo AND Activo = TRUE";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Pin", pin);
                        comando.Parameters.AddWithValue("@Expira", expiraEn);
                        comando.Parameters.AddWithValue("@Correo", correo);

                        filasAfectadas = await comando.ExecuteNonQueryAsync();
                    }
                }

                // Si el correo no existe no se envía nada, pero se responde igual que si existiera, para no revelar información de cuentas existentes.
                if (filasAfectadas > 0)
                {
                    await EnviarCorreoPinAsync(correo, pin);
                }

                return Ok(respuestaGenerica);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al recuperar contraseña");
                return StatusCode(500, new { error = "Error en el servidor"});
            }
        }
        // RESETEAR CONTRASEÑA - POST api/Auth/resetear
        [HttpPost("resetear")]
        public async Task<IActionResult> Resetear([FromBody] ResetearRequest request)
        {
            // Misma respuesta para todos los casos de fallo (no revela si el correo existe)
            var pinInvalido = new { mensaje = "PIN incorrecto o expirado." };
            string correo = request.Correo.Trim();

            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    bool existe = false;
                    string? pinGuardado = null;
                    DateTime? expiraEn = null;
                    int intentos = 0;

                    // 1. Buscar PIN, vencimiento e intentos fallidos
                    string querySelect = @"SELECT PinRecuperacion, PinExpiraEn, IntentosPin 
                                            FROM USUARIOS 
                                            WHERE Correo = @Correo AND Activo = TRUE";


                    using (MySqlCommand cmdSelect = new MySqlCommand(querySelect, conexion))
                    {
                        cmdSelect.Parameters.AddWithValue("@Correo", correo);

                        using (var lector = await cmdSelect.ExecuteReaderAsync())
                        {
                            if (await lector.ReadAsync())
                            {
                                existe = true;
                                int posPin = lector.GetOrdinal("PinRecuperacion");
                                int posExpira = lector.GetOrdinal("PinExpiraEn");

                                pinGuardado = lector.IsDBNull(posPin) ? null : lector.GetString(posPin);
                                expiraEn = lector.IsDBNull(posExpira) ? null : (DateTime?)lector.GetDateTime(posExpira);
                                intentos = lector.GetInt32("IntentosPin");
                            }
                        }
                    }
                       
                    // 2. Sin usuario, sin PIN, vencido o bloqueado por intentos: fallo genérico
                    if (!existe || pinGuardado == null || expiraEn == null
                        || expiraEn < DateTime.Now || intentos >= MaxIntentosPin)
                    {
                        return BadRequest(pinInvalido);
                    }

                    // 3. PIN incorrecto: sumar un intento. Al llegar al máximo se borra el PIN
                    if (pinGuardado != request.Pin.Trim())
                    {
                        string queryFallo = @"UPDATE USUARIOS
                                              SET PinRecuperacion = IF(IntentosPin + 1 >= @Max, NULL, PinRecuperacion),
                                                  IntentosPin = IntentosPin + 1
                                              WHERE Correo = @Correo";

                        using (MySqlCommand cmdFallo = new MySqlCommand(queryFallo, conexion))
                        {
                            cmdFallo.Parameters.AddWithValue("@Max", MaxIntentosPin);
                            cmdFallo.Parameters.AddWithValue("@Correo", correo);
                            await cmdFallo.ExecuteNonQueryAsync();
                        }

                        return BadRequest(pinInvalido);
                    }

                    // 4. PIN correcto: guardar nueva contraseña y limpiar PIN e intentos
                    string hashNuevaContrasena = BCrypt.Net.BCrypt.HashPassword(request.NuevaContrasena);

                    string queryUpdate = @"UPDATE USUARIOS
                                           SET Contrasena = @Hash,
                                               PinRecuperacion = NULL,
                                               PinExpiraEn = NULL,
                                               IntentosPin = 0
                                           WHERE Correo = @Correo";

                    using (MySqlCommand cmdUpdate = new MySqlCommand(queryUpdate, conexion))
                    {
                        cmdUpdate.Parameters.AddWithValue("@Hash", hashNuevaContrasena);
                        cmdUpdate.Parameters.AddWithValue("@Correo", correo);
                        await cmdUpdate.ExecuteNonQueryAsync();
                    }

                    return Ok(new { mensaje = "Contraseña actualizada correctamente." });
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al resetear contraseña");
                return StatusCode(500, new { error = "Error en el servidor"});
            }
        }

        /* Envía el PIN por correo usando la configuración SMTP de User Secrets.
        Además de seguridad (Evitar credenciales en el código), separaremos responsabilidades.*/
        private async Task EnviarCorreoPinAsync(string destinatario, string pin)
        {
            string host = _config["Smtp:Host"] ?? throw new InvalidOperationException("Falta configurar Smtp:Host.");
            int puerto = _config.GetValue("Smtp:Puerto", 587);
            string usuario = _config["Smtp:Usuario"] ?? throw new InvalidOperationException("Falta configurar Smtp:Usuario.");
            string contrasena = _config["Smtp:Contrasena"] ?? throw new InvalidOperationException("Falta configurar Smtp:Contrasena.");
            string remitente = _config["Smtp:Remitente"] ?? "no-responder@rutasinclusivas.example";

            using var smtp = new SmtpClient(host, puerto)
            {
                Credentials = new NetworkCredential(usuario, contrasena),
                EnableSsl = true
            };

            using var mail = new MailMessage(remitente, destinatario)
            {
                Subject = "Recuperación de contraseña - Rutas Inclusivas",
                Body = $"Tu código de recuperación es: {pin}\nEste código expira en 15 minutos."
            };

            await smtp.SendMailAsync(mail);
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