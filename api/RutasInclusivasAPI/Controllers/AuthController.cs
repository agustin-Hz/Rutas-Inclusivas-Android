using System.IdentityModel.Tokens.Jwt;
using System.Net;           // NetworkCredential
using System.Net.Mail;      // SmtpClient y MailMessage
using System.Security.Claims;
using System.Security.Cryptography;
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
        // =====================================================================
        // CONFIGURACIÓN
        // =====================================================================

        private readonly string _cadenaConexion;
        private readonly IConfiguration _config;
        private readonly ILogger<AuthController> _logger;

        // Hash de relleno: si el usuario no existe se verifica contra este hash,
        // así la respuesta tarda lo mismo y no se revela qué cuentas existen.
        private static readonly string _hashRelleno =
            BCrypt.Net.BCrypt.HashPassword("relleno-que-no-es-una-contrasena-real");

        // Intentos fallidos permitidos antes de anular el PIN
        private const int MaxIntentosPin = 5;

        // Minutos que dura el PIN de recuperación
        private const int MinutosVigenciaPin = 15;

        // Días sin usar la app hasta que la sesión vence (cada uso los reinicia)
        private const int DiasVigenciaRefresh = 15;

        // Recibe la configuración (User Secrets / appsettings) y el logger por inyección de dependencias.
        public AuthController(IConfiguration config, ILogger<AuthController> logger)
        {
            _config = config;
            _logger = logger;
            _cadenaConexion = config.GetConnectionString("ConexionMySQL") ?? throw new InvalidOperationException("Falta la cadena de conexión ConexionMySQL.");
        }

        // =====================================================================
        // SESIÓN: login, renovación y cierre
        // =====================================================================

        // INICIAR SESIÓN - POST api/Auth/login
        // Valida correo/usuario y contraseña. Devuelve access token, refresh token y datos del usuario.
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
                    string refresh = await CrearRefreshTokenAsync(conexion, idUsuario);

                    return Ok(new
                    {
                        accessToken = acceso.Token,
                        expiraEn = acceso.Expira,
                        refreshToken = refresh,
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

        // RENOVAR SESIÓN - POST api/Auth/refresh
        // Recibe el refresh token y devuelve un par nuevo (rotación). Misma forma que el login.
        [HttpPost("refresh")]
        public async Task<IActionResult> Refrescar([FromBody] RefreshRequest datos)
        {
            string hash = HashToken(datos.RefreshToken);

            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    // 1. Buscar el token: no revocado, no vencido y con la cuenta activa
                    string querySelect = @"
                        SELECT rt.ID_Token, u.ID_Usuario, u.Nombre_Usuario, u.Correo, u.Zona
                        FROM REFRESH_TOKENS rt
                        JOIN USUARIOS u ON u.ID_Usuario = rt.ID_Usuario
                        WHERE rt.TokenHash = @Hash
                          AND rt.RevocadoEn IS NULL
                          AND (rt.Expira IS NULL OR rt.Expira > UTC_TIMESTAMP())
                          AND u.Activo = TRUE";

                    int idToken = 0, idUsuario = 0;
                    string nombre = "", correo = "";
                    string? zona = null;

                    using (MySqlCommand cmdSelect = new MySqlCommand(querySelect, conexion))
                    {
                        cmdSelect.Parameters.AddWithValue("@Hash", hash);

                        using (var lector = await cmdSelect.ExecuteReaderAsync())
                        {
                            if (!await lector.ReadAsync())
                                return Unauthorized(new { error = "Sesión inválida o vencida" });

                            idToken = lector.GetInt32("ID_Token");
                            idUsuario = lector.GetInt32("ID_Usuario");
                            nombre = lector.GetString("Nombre_Usuario");
                            correo = lector.GetString("Correo");
                            int posZona = lector.GetOrdinal("Zona");
                            zona = lector.IsDBNull(posZona) ? null : lector.GetString(posZona);
                        }
                    }

                    // 2. Anular el token usado. El "AND RevocadoEn IS NULL" evita que dos
                    //    pedidos simultáneos con el mismo token obtengan dos sesiones.
                    string queryRevocar = @"UPDATE REFRESH_TOKENS
                                            SET RevocadoEn = UTC_TIMESTAMP()
                                            WHERE ID_Token = @IdToken AND RevocadoEn IS NULL";

                    using (MySqlCommand cmdRevocar = new MySqlCommand(queryRevocar, conexion))
                    {
                        cmdRevocar.Parameters.AddWithValue("@IdToken", idToken);
                        if (await cmdRevocar.ExecuteNonQueryAsync() == 0)
                            return Unauthorized(new { error = "Sesión inválida o vencida" });
                    }

                    // 3. Par nuevo: el refresh nuevo vuelve a durar DiasVigenciaRefresh (vencimiento por inactividad)
                    var acceso = GenerarAccessToken(idUsuario, nombre, correo);
                    string refresh = await CrearRefreshTokenAsync(conexion, idUsuario);

                    return Ok(new
                    {
                        accessToken = acceso.Token,
                        expiraEn = acceso.Expira,
                        refreshToken = refresh,
                        usuario = new { id = idUsuario, nombre, correo, zona }
                    });
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al renovar la sesión");
                return StatusCode(500, new { error = "Error al renovar la sesión" });
            }
        }

        // CERRAR SESIÓN - POST api/Auth/logout
        // Anula el refresh token de ESTE celular. No lleva [Authorize]: el access token
        // puede estar vencido, y tener el refresh token ya prueba que la sesión es tuya.
        [HttpPost("logout")]
        public async Task<IActionResult> CerrarSesion([FromBody] RefreshRequest datos)
        {
            try
            {
                using (MySqlConnection conexion = new MySqlConnection(_cadenaConexion))
                {
                    await conexion.OpenAsync();

                    string query = @"UPDATE REFRESH_TOKENS
                                     SET RevocadoEn = UTC_TIMESTAMP()
                                     WHERE TokenHash = @Hash AND RevocadoEn IS NULL";

                    using (MySqlCommand comando = new MySqlCommand(query, conexion))
                    {
                        comando.Parameters.AddWithValue("@Hash", HashToken(datos.RefreshToken));
                        await comando.ExecuteNonQueryAsync();
                    }
                }
                // Siempre 204: no se informa si el token existía
                return NoContent();
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al cerrar sesión");
                return StatusCode(500, new { error = "Error al cerrar sesión" });
            }
        }

        // =====================================================================
        // RECUPERACIÓN DE CONTRASEÑA
        // =====================================================================

        // RECUPERAR CONTRASEÑA - POST api/Auth/recuperar
        // Genera un PIN de 6 dígitos, lo guarda con vencimiento y lo envía por correo.
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
            DateTime expiraEn = DateTime.Now.AddMinutes(MinutosVigenciaPin);
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
                return StatusCode(500, new { error = "Error en el servidor" });
            }
        }

        // RESETEAR CONTRASEÑA - POST api/Auth/resetear
        // Verifica el PIN (vencimiento e intentos), guarda la nueva contraseña y cierra todas las sesiones.
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

                    // 5. Cerrar todas las sesiones abiertas de la cuenta (en todos los celulares)
                    string queryRevocar = @"UPDATE REFRESH_TOKENS
                                            SET RevocadoEn = UTC_TIMESTAMP()
                                            WHERE RevocadoEn IS NULL
                                              AND ID_Usuario = (SELECT ID_Usuario FROM USUARIOS WHERE Correo = @Correo)";

                    using (MySqlCommand cmdRevocar = new MySqlCommand(queryRevocar, conexion))
                    {
                        cmdRevocar.Parameters.AddWithValue("@Correo", correo);
                        await cmdRevocar.ExecuteNonQueryAsync();
                    }

                    return Ok(new { mensaje = "Contraseña actualizada correctamente." });
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al resetear contraseña");
                return StatusCode(500, new { error = "Error en el servidor" });
            }
        }

        // =====================================================================
        // AUXILIARES: tokens
        // =====================================================================

        // Crea el access token (JWT firmado con HS256). Dura Jwt:AccessTokenMinutos (15 por defecto).
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

        // Hash SHA-256 en hexadecimal (64 caracteres). Es lo único que se guarda en la BD.
        private static string HashToken(string token)
        {
            return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
        }

        // Crea un refresh token aleatorio, guarda su hash con vencimiento y devuelve el token para el celular.
        private async Task<string> CrearRefreshTokenAsync(MySqlConnection conexion, int idUsuario)
        {
            // 32 bytes aleatorios criptográficos → texto seguro para JSON/URL
            string token = Base64UrlEncoder.Encode(RandomNumberGenerator.GetBytes(32));

            string query = @"INSERT INTO REFRESH_TOKENS (ID_Usuario, TokenHash, Expira)
                             VALUES (@Id, @Hash, UTC_TIMESTAMP() + INTERVAL @Dias DAY)";

            using (MySqlCommand comando = new MySqlCommand(query, conexion))
            {
                comando.Parameters.AddWithValue("@Id", idUsuario);
                comando.Parameters.AddWithValue("@Hash", HashToken(token));
                comando.Parameters.AddWithValue("@Dias", DiasVigenciaRefresh);
                await comando.ExecuteNonQueryAsync();
            }
            return token;
        }

        // =====================================================================
        // AUXILIARES: correo
        // =====================================================================

        // Envía el PIN por correo con la configuración SMTP de User Secrets
        // (así no hay credenciales en el código y el envío queda separado de la lógica).
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

            using var mail = new MailMessage
            {
                From = new MailAddress(remitente, "Rutas Inclusivas"),
                Subject = $"Tu código de recuperación: {pin}",
                SubjectEncoding = Encoding.UTF8,
                Body =
                    $"Tu código para recuperar la contraseña es: {pin}\n\n" +
                    $"Vence en {MinutosVigenciaPin} minutos.\n\n" +
                    $"Si no lo pediste, ignorá este mensaje.\n" +
                    $"Este es un correo automático, no lo respondas.",
                BodyEncoding = Encoding.UTF8
            };
            mail.To.Add(destinatario);

            await smtp.SendMailAsync(mail);
        }
    }
}
