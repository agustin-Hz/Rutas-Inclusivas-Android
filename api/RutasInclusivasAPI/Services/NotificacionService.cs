using FirebaseAdmin.Messaging;
using MySqlConnector;

namespace RutasInclusivasAPI.Services
{
    public class NotificacionService
    {
        // Límite de FCM por llamada a SendEachForMulticastAsync.
        private const int MaxTokensPorLote = 500;

        private readonly string _cadenaConexion;
        private readonly ILogger<NotificacionService> _logger;

        public NotificacionService(IConfiguration config, ILogger<NotificacionService> logger)
        {
            _cadenaConexion = config.GetConnectionString("ConexionMySQL")
                ?? throw new InvalidOperationException("Falta configurar ConexionMySQL");
            _logger = logger;
        }

        public async Task<int> EnviarAUsuarioAsync(int idUsuario, string titulo, string cuerpo)
        {
            var tokens = new List<string>();

            using (var conexion = new MySqlConnection(_cadenaConexion))
            {
                await conexion.OpenAsync();

                using var cmd = new MySqlCommand(
                    "SELECT DISTINCT Token FROM DISPOSITIVOS_FCM WHERE ID_Usuario = @IdUsuario",
                    conexion);
                cmd.Parameters.AddWithValue("@IdUsuario", idUsuario);

                using var lector = await cmd.ExecuteReaderAsync();
                while (await lector.ReadAsync())
                {
                    tokens.Add(lector.GetString("Token"));
                }
            }

            await GuardarHistorialAsync(new[] { idUsuario }, titulo, cuerpo);

            return await EnviarATokensAsync(tokens, titulo, cuerpo);
        }

        public async Task<int> EnviarATodosExceptoAsync(int idUsuarioExcluido, string titulo, string cuerpo)
        {
            var usuarios = new HashSet<int>();
            var tokens = new HashSet<string>();

            using (var conexion = new MySqlConnection(_cadenaConexion))
            {
                await conexion.OpenAsync();

                // Solo usuarios activos. LEFT JOIN: un usuario sin token igual queda en el historial.
                using var cmd = new MySqlCommand(@"
                    SELECT u.ID_Usuario, d.Token
                    FROM USUARIOS u
                    LEFT JOIN DISPOSITIVOS_FCM d ON d.ID_Usuario = u.ID_Usuario
                    WHERE u.Activo = TRUE AND u.ID_Usuario <> @IdUsuarioExcluido",
                    conexion);
                cmd.Parameters.AddWithValue("@IdUsuarioExcluido", idUsuarioExcluido);

                using var lector = await cmd.ExecuteReaderAsync();
                int colUsuario = lector.GetOrdinal("ID_Usuario");
                int colToken = lector.GetOrdinal("Token");

                while (await lector.ReadAsync())
                {
                    usuarios.Add(lector.GetInt32(colUsuario));

                    if (!lector.IsDBNull(colToken))
                    {
                        tokens.Add(lector.GetString(colToken));
                    }
                }
            }

            await GuardarHistorialAsync(usuarios, titulo, cuerpo);

            return await EnviarATokensAsync(tokens.ToList(), titulo, cuerpo);
        }

        // Envía en lotes de 500 y borra de DISPOSITIVOS_FCM los tokens que FCM marca como inexistentes.
        private async Task<int> EnviarATokensAsync(List<string> tokens, string titulo, string cuerpo)
        {
            if (tokens.Count == 0)
                return 0;

            int exitosos = 0;
            var invalidos = new List<string>();

            for (int i = 0; i < tokens.Count; i += MaxTokensPorLote)
            {
                var lote = tokens.GetRange(i, Math.Min(MaxTokensPorLote, tokens.Count - i));

                var mensaje = new MulticastMessage
                {
                    Tokens = lote,
                    Notification = new Notification
                    {
                        Title = titulo,
                        Body = cuerpo
                    }
                };

                var respuesta = await FirebaseMessaging.DefaultInstance.SendEachForMulticastAsync(mensaje);
                exitosos += respuesta.SuccessCount;

                for (int j = 0; j < respuesta.Responses.Count; j++)
                {
                    var r = respuesta.Responses[j];

                    if (!r.IsSuccess &&
                        r.Exception?.MessagingErrorCode == MessagingErrorCode.Unregistered)
                    {
                        invalidos.Add(lote[j]);
                    }
                }
            }

            if (invalidos.Count > 0)
            {
                await EliminarTokensAsync(invalidos);
            }

            return exitosos;
        }

        private async Task EliminarTokensAsync(List<string> tokens)
        {
            try
            {
                using var conexion = new MySqlConnection(_cadenaConexion);
                await conexion.OpenAsync();

                using var cmd = new MySqlCommand(
                    "DELETE FROM DISPOSITIVOS_FCM WHERE Token = @Token", conexion);
                var pToken = cmd.Parameters.Add("@Token", MySqlDbType.VarChar);

                foreach (var token in tokens)
                {
                    pToken.Value = token;
                    await cmd.ExecuteNonQueryAsync();
                }

                _logger.LogInformation("Se eliminaron {Cantidad} tokens FCM inválidos", tokens.Count);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "No se pudieron eliminar los tokens FCM inválidos");
            }
        }

        // Deja constancia del aviso para cada usuario. Si falla, no impide el envío.
        private async Task GuardarHistorialAsync(IEnumerable<int> usuarios, string titulo, string cuerpo)
        {
            try
            {
                string mensaje = $"{titulo}: {cuerpo}";

                using var conexion = new MySqlConnection(_cadenaConexion);
                await conexion.OpenAsync();

                using var transaccion = await conexion.BeginTransactionAsync();

                using var cmd = new MySqlCommand(
                    "INSERT INTO HISTORIAL_NOTIFICACIONES (ID_Usuario, Mensaje) VALUES (@IdUsuario, @Mensaje)",
                    conexion, transaccion);
                var pUsuario = cmd.Parameters.Add("@IdUsuario", MySqlDbType.Int32);
                cmd.Parameters.AddWithValue("@Mensaje", mensaje);

                foreach (int idUsuario in usuarios)
                {
                    pUsuario.Value = idUsuario;
                    await cmd.ExecuteNonQueryAsync();
                }

                await transaccion.CommitAsync();
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "No se pudo guardar el historial de notificaciones");
            }
        }
    }
}
