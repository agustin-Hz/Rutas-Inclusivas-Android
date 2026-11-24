using FirebaseAdmin.Messaging;
using MySqlConnector;

namespace RutasInclusivasAPI.Services
{
    public class NotificacionService
    {
        private readonly string _cadenaConexion;

        public NotificacionService(IConfiguration config)
        {
            _cadenaConexion = config.GetConnectionString("ConexionMySQL")
                ?? throw new InvalidOperationException("Falta configurar ConexionMySQL");
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

            if (tokens.Count == 0)
                return 0;

            var mensaje = new MulticastMessage
            {
                Tokens = tokens,
                Notification = new Notification
                {
                    Title = titulo,
                    Body = cuerpo
                }
            };

            var respuesta = await FirebaseMessaging.DefaultInstance.SendEachForMulticastAsync(mensaje);
            return respuesta.SuccessCount;
        }

        public async Task<int> EnviarATodosExceptoAsync(int idUsuarioExcluido, string titulo, string cuerpo)
        {
            var tokens = new List<string>();

            using (var conexion = new MySqlConnection(_cadenaConexion))
            {
                await conexion.OpenAsync();

                using var cmd = new MySqlCommand(
                    "SELECT DISTINCT Token FROM DISPOSITIVOS_FCM WHERE ID_Usuario != @IdUsuarioExcluido",
                    conexion);
                cmd.Parameters.AddWithValue("@IdUsuarioExcluido", idUsuarioExcluido);

                using var lector = await cmd.ExecuteReaderAsync();
                while (await lector.ReadAsync())
                {
                    tokens.Add(lector.GetString("Token"));
                }
            }

            if (tokens.Count == 0)
                return 0;

            var mensaje = new MulticastMessage
            {
                Tokens = tokens,
                Notification = new Notification
                {
                    Title = titulo,
                    Body = cuerpo
                }
            };

            var respuesta = await FirebaseMessaging.DefaultInstance.SendEachForMulticastAsync(mensaje);
            return respuesta.SuccessCount;
        }
    }
}
