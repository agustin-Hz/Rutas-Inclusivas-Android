using MySqlConnector;
using RutasInclusivasAPI.Models;

namespace RutasInclusivasAPI.Services
{
    public class ServicioObstaculos
    {
        private readonly string _cadenaConexion;

        public ServicioObstaculos(IConfiguration config)
        {
            _cadenaConexion = config.GetConnectionString("ConexionMySQL")
                ?? throw new InvalidOperationException(
                    "Falta configurar ConexionMySQL"
                );
        }

        public async Task<List<ObstaculoRuta>> ObtenerObstaculosActivos()
        {
            List<ObstaculoRuta> obstaculos = new List<ObstaculoRuta>();

            await using (var conexion = new MySqlConnection(_cadenaConexion))
            {
                await conexion.OpenAsync();

                string consulta = @"
            SELECT ID_Reporte, ID_Tipo, Latitud, Longitud, Estado
            FROM REPORTES
            WHERE Estado IN ('Activo', 'Verificado')";

                await using (var comando = new MySqlCommand(consulta, conexion))
                await using (var lector = await comando.ExecuteReaderAsync())
                {
                    while (await lector.ReadAsync())
                    {
                        ObstaculoRuta obstaculo = new ObstaculoRuta
                        {
                            ID_Reporte = Convert.ToInt32(lector["ID_Reporte"]),
                            ID_Tipo = Convert.ToInt32(lector["ID_Tipo"]),
                            Latitud = Convert.ToDouble(lector["Latitud"]),
                            Longitud = Convert.ToDouble(lector["Longitud"]),
                            Estado = lector["Estado"].ToString() ?? string.Empty
                        };

                        obstaculos.Add(obstaculo);
                    }
                }
            }

            return obstaculos;
        }
    }
}