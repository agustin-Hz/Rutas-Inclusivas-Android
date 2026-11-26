using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using RutasInclusivasAPI.Models;
using System.Text.Json;
using System.Linq;
using Microsoft.AspNetCore.Authorization;
using MySqlConnector;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;

namespace RutasInclusivasAPI.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class DestinosController : ControllerBase
    {
        private readonly HttpClient _httpClient;
        private readonly IConfiguration _configuration;

        public DestinosController(
    IHttpClientFactory factory,
    IConfiguration configuration)
        {
            _httpClient = factory.CreateClient();
            _configuration = configuration;
        }

        [HttpGet("buscar")]
        public async Task<IActionResult> BuscarDestinos(
            [FromQuery] string texto)
        {
            if (string.IsNullOrWhiteSpace(texto) || texto.Trim().Length < 3)
            {
                return BadRequest(new
                {
                    mensaje = "Ingresá al menos 3 caracteres."
                });
            }

            try
            {
                string url = "https://photon.komoot.io/api/?q="
    + Uri.EscapeDataString(texto.Trim())
    + "&limit=8&lang=default";

                using HttpResponseMessage respuesta = await _httpClient.GetAsync(url);

                string contenido = await respuesta.Content.ReadAsStringAsync();

                if (!respuesta.IsSuccessStatusCode)
                {
                    Console.WriteLine("Error de Photon:");
                    Console.WriteLine("Código HTTP: " + (int)respuesta.StatusCode);
                    Console.WriteLine("Respuesta: " + contenido);

                    return StatusCode(502, new
                    {
                        mensaje = "Photon respondió con un error.",
                        estadoPhoton = (int)respuesta.StatusCode,
                        detalle = contenido
                    });
                }

                using JsonDocument documento =
                    JsonDocument.Parse(contenido);

                JsonElement features =
                documento.RootElement.GetProperty("features");

                List<DestinoRepuesta> destinos = new();

                foreach (JsonElement feature in features.EnumerateArray())
                {
                    JsonElement propiedades =
                        feature.GetProperty("properties");

                    JsonElement geometria =
                        feature.GetProperty("geometry");

                    JsonElement coordenadas =
                        geometria.GetProperty("coordinates");

                    double longitud = coordenadas[0].GetDouble();
                    double latitud = coordenadas[1].GetDouble();

                    string nombre = ObtenerTexto(propiedades, "name");

                    string calle = ObtenerTexto(propiedades, "street");
                    string numero = ObtenerTexto(propiedades, "housenumber");
                    string ciudad = ObtenerTexto(propiedades, "city");
                    string estado = ObtenerTexto(propiedades, "state");
                    string pais = ObtenerTexto(propiedades, "country");

                    string direccion = string.Join(", ",
                        new[]
                        {
                            string.Join(" ", new[] { calle, numero }
                                .Where(x => !string.IsNullOrWhiteSpace(x))),
                            ciudad,
                            estado,
                            pais
                        }
                        .Where(x => !string.IsNullOrWhiteSpace(x)));

                    if (string.IsNullOrWhiteSpace(nombre))
                    {
                        nombre = ciudad;
                    }

                    if (string.IsNullOrWhiteSpace(nombre))
                    {
                        nombre = calle;
                    }

                    destinos.Add(new DestinoRepuesta
                    {
                        Nombre = nombre,
                        Direccion = direccion,
                        Latitud = latitud,
                        Longitud = longitud
                    });
                }

                return Ok(destinos);
            }
            catch (HttpRequestException)
            {
                return StatusCode(502, new
                {
                    mensaje = "Error de conexión con Photon."
                });
            }
            catch (JsonException)
            {
                return StatusCode(502, new
                {
                    mensaje = "El servicio de lugares devolvió una respuesta inválida."
                });
            }
            catch (KeyNotFoundException)
            {
                return StatusCode(502, new
                {
                    mensaje = "La respuesta de Photon no contiene los datos esperados."
                });
            }
            catch (Exception)
            {
                return StatusCode(500, new
                {
                    mensaje = "Ocurrió un error al buscar destinos."
                });
            }
        }


        [Authorize]
        [HttpPost("favoritos")]
        public async Task<IActionResult> GuardarDestinoFavorito(
            [FromBody] DestinoFavoritoRequest request)
        {
            // Obtener el usuario desde el token JWT.
            int? idUsuario = ObtenerIdUsuario();

            if (idUsuario == null)
            {
                return Unauthorized(new
                {
                    mensaje = "No se pudo identificar al usuario."
                });
            }

            // Verificar que las coordenadas estén presentes.
            if (request.Latitud == null || request.Longitud == null)
            {
                return BadRequest(new
                {
                    mensaje = "Las coordenadas son obligatorias."
                });
            }

            string? cadenaConexion =
                _configuration.GetConnectionString("ConexionMySQL");

            if (string.IsNullOrWhiteSpace(cadenaConexion))
            {
                return StatusCode(500, new
                {
                    mensaje = "No está configurada la conexión a la base de datos."
                });
            }

            try
            {
                using MySqlConnection conexion =
                    new MySqlConnection(cadenaConexion);

                await conexion.OpenAsync();

                string consulta = @"
            INSERT INTO DESTINOS_FAVORITOS
            (ID_Usuario, Nombre, Direccion, Latitud, Longitud)
            VALUES
            (@idUsuario, @nombre, @direccion, @latitud, @longitud);
        ";

                using MySqlCommand comando =
                    new MySqlCommand(consulta, conexion);

                comando.Parameters.AddWithValue("@idUsuario", idUsuario.Value);
                comando.Parameters.AddWithValue("@nombre", request.Nombre.Trim());
                comando.Parameters.AddWithValue(
                    "@direccion",
                    string.IsNullOrWhiteSpace(request.Direccion)
                        ? DBNull.Value
                        : request.Direccion.Trim());
                comando.Parameters.AddWithValue("@latitud", request.Latitud.Value);
                comando.Parameters.AddWithValue("@longitud", request.Longitud.Value);

                await comando.ExecuteNonQueryAsync();

                int idDestino = (int)comando.LastInsertedId;

                return Ok(new
                {
                    mensaje = "Destino guardado en favoritos correctamente.",
                    idDestino = idDestino
                });
            }
            catch (Exception)
            {
                return StatusCode(500, new
                {
                    mensaje = "Ocurrió un error al guardar el destino favorito."
                });
            }
        }
        
[Authorize]
[HttpGet("favoritos")]
public async Task<IActionResult> ObtenerDestinosFavoritos()
{
    int? idUsuario = ObtenerIdUsuario();

    if (idUsuario == null)
    {
        return Unauthorized(new
        {
            mensaje = "No se pudo identificar al usuario."
        });
    }

    string? cadenaConexion =
        _configuration.GetConnectionString("ConexionMySQL");

    if (string.IsNullOrWhiteSpace(cadenaConexion))
    {
        return StatusCode(500, new
        {
            mensaje = "No está configurada la conexión a la base de datos."
        });
    }

    try
    {
        using MySqlConnection conexion =
            new MySqlConnection(cadenaConexion);

        await conexion.OpenAsync();

        string consulta = @"
            SELECT ID_Destino, Nombre, Direccion, Latitud, Longitud
            FROM DESTINOS_FAVORITOS
            WHERE ID_Usuario = @idUsuario
            ORDER BY Fecha_Creacion DESC;
        ";

        using MySqlCommand comando =
            new MySqlCommand(consulta, conexion);

        comando.Parameters.AddWithValue("@idUsuario", idUsuario.Value);

        using MySqlDataReader lector =
            await comando.ExecuteReaderAsync();

        List<object> destinos = new();

        while (await lector.ReadAsync())
        {
            destinos.Add(new
            {
                idDestino = lector.GetInt32("ID_Destino"),
                nombre = lector.GetString("Nombre"),
                direccion = lector.IsDBNull(2)
                    ? null
                    : lector.GetString("Direccion"),
                latitud = lector.GetDouble("Latitud"),
                longitud = lector.GetDouble("Longitud")
            });
        }

        return Ok(destinos);
    }
    catch (Exception)
    {
        return StatusCode(500, new
        {
            mensaje = "Ocurrió un error al consultar los destinos favoritos."
        });
    }
}
        [HttpGet("buscar-coordenadas")]
        public async Task<IActionResult> BuscarDestinoPorCoordenadas(
    [FromQuery] double latitud,
    [FromQuery] double longitud)
        {
            // Validar las coordenadas recibidas.
            if (latitud < -90 || latitud > 90 ||
                longitud < -180 || longitud > 180)
            {
                return BadRequest(new
                {
                    mensaje = "Las coordenadas no son válidas."
                });
            }

            try
            {
                // Consultar Photon mediante geocodificación inversa.
                string url = "https://photon.komoot.io/reverse?lat="
                    + latitud.ToString(
                        System.Globalization.CultureInfo.InvariantCulture)
                    + "&lon="
                    + longitud.ToString(
                        System.Globalization.CultureInfo.InvariantCulture)
                    + "&lang=default";

                using HttpResponseMessage respuesta =
                    await _httpClient.GetAsync(url);

                string contenido =
                    await respuesta.Content.ReadAsStringAsync();

                if (!respuesta.IsSuccessStatusCode)
                {
                    return StatusCode(502, new
                    {
                        mensaje = "Photon respondió con un error."
                    });
                }

                using JsonDocument documento =
                    JsonDocument.Parse(contenido);

                JsonElement features =
                    documento.RootElement.GetProperty("features");

                if (features.GetArrayLength() == 0)
                {
                    return NotFound(new
                    {
                        mensaje = "No se encontró información para esa ubicación."
                    });
                }

                // Recuperar los datos del primer resultado.
                JsonElement feature = features[0];

                JsonElement propiedades =
                    feature.GetProperty("properties");

                string nombre = ObtenerTexto(propiedades, "name");

                string calle = ObtenerTexto(propiedades, "street");
                string numero = ObtenerTexto(propiedades, "housenumber");
                string ciudad = ObtenerTexto(propiedades, "city");
                string estado = ObtenerTexto(propiedades, "state");
                string pais = ObtenerTexto(propiedades, "country");

                // Construir la dirección.
                string direccion = string.Join(", ",
                    new[]
                    {
                string.Join(" ", new[] { calle, numero }
                    .Where(x => !string.IsNullOrWhiteSpace(x))),
                ciudad,
                estado,
                pais
                    }
                    .Where(x => !string.IsNullOrWhiteSpace(x)));

                // Buscar un nombre alternativo si Photon no devuelve uno.
                if (string.IsNullOrWhiteSpace(nombre))
                {
                    nombre = !string.IsNullOrWhiteSpace(calle)
                        ? calle
                        : ciudad;
                }

                if (string.IsNullOrWhiteSpace(nombre))
                {
                    nombre = "Ubicación seleccionada en el mapa";
                }

                return Ok(new
                {
                    nombre = nombre,
                    direccion = direccion,
                    latitud = latitud,
                    longitud = longitud
                });
            }
            catch (HttpRequestException)
            {
                return StatusCode(502, new
                {
                    mensaje = "Error de conexión con Photon."
                });
            }
            catch (JsonException)
            {
                return StatusCode(502, new
                {
                    mensaje = "Photon devolvió una respuesta inválida."
                });
            }
            catch (Exception)
            {
                return StatusCode(500, new
                {
                    mensaje = "Ocurrió un error al buscar la ubicación."
                });
            }
        }


        private static string ObtenerTexto(
            JsonElement elemento,
            string propiedad)
        {
            if (elemento.TryGetProperty(propiedad, out JsonElement valor)
                && valor.ValueKind == JsonValueKind.String)
            {
                return valor.GetString() ?? "";
            }

            return "";
        }
        private int? ObtenerIdUsuario()
        {
            string? idUsuarioClaim = User.FindFirst(JwtRegisteredClaimNames.Sub)?.Value;

            if (int.TryParse(idUsuarioClaim, out int idUsuario))
            {
                return idUsuario;
            }

            return null;
        }
    }
}
