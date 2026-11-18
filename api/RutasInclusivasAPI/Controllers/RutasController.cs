
using Microsoft.AspNetCore.Mvc;
using RutasInclusivasAPI.Models;
using System.Globalization;
using System.Text.Json;

namespace RutasInclusivasAPI.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class RutasController : ControllerBase
    {
        private readonly HttpClient _httpClient;

        public RutasController(IHttpClientFactory httpClientFactory)
        {
            _httpClient = httpClientFactory.CreateClient();

            // Identificamos nuestra aplicación ante el servidor.
            _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd(
                "RutasInclusivas/1.0"
            );
        }

        [HttpPost("calcular")]
        public async Task<IActionResult> CalcularRuta(
            [FromBody] SolicitudRuta solicitud)
        {
            // 1. Validamos las coordenadas recibidas.
            if (solicitud.LatitudOrigen < -90 ||
                solicitud.LatitudOrigen > 90 ||
                solicitud.LatitudDestino < -90 ||
                solicitud.LatitudDestino > 90 ||
                solicitud.LongitudOrigen < -180 ||
                solicitud.LongitudOrigen > 180 ||
                solicitud.LongitudDestino < -180 ||
                solicitud.LongitudDestino > 180)
            {
                return BadRequest("Las coordenadas no son válidas.");
            }

            // 2. OSRM recibe primero longitud y después latitud.
            string origen = string.Format(
                CultureInfo.InvariantCulture,
                "{0},{1}",
                solicitud.LongitudOrigen,
                solicitud.LatitudOrigen
            );

            string destino = string.Format(
                CultureInfo.InvariantCulture,
                "{0},{1}",
                solicitud.LongitudDestino,
                solicitud.LatitudDestino
            );

            // 3. Construimos la URL de consulta.

            string url =
     $"http://routing.openstreetmap.de/routed-foot/route/v1/driving/{origen};{destino}" +
     "?overview=full&geometries=geojson";

            try
            {
                // 4. Consultamos OSRM.
                HttpResponseMessage respuesta =
                    await _httpClient.GetAsync(url);

                string contenido =
                    await respuesta.Content.ReadAsStringAsync();

                if (!respuesta.IsSuccessStatusCode)
                {
                    return StatusCode(
                        502,
                        new
                        {
                            mensaje = "El servidor de rutas respondió con un error.",
                            estadoHttp = (int)respuesta.StatusCode,
                            descripcion = respuesta.ReasonPhrase,
                            respuestaServidor = contenido
                        }
                    );
                }

                // 5. Interpretamos el JSON recibido.
                using JsonDocument documento =
                    JsonDocument.Parse(contenido);

                JsonElement raiz = documento.RootElement;

                if (!raiz.TryGetProperty("code", out JsonElement codigo) ||
                    codigo.GetString() != "Ok")
                {
                    return BadRequest(
                        "OSRM no pudo encontrar una ruta entre esos puntos."
                    );
                }

                JsonElement rutaJson =
                    raiz.GetProperty("routes")[0];

                double distancia =
                    rutaJson.GetProperty("distance").GetDouble();

                double duracion =
                    rutaJson.GetProperty("duration").GetDouble();

                // 6. Recuperamos los puntos de la geometría GeoJSON.
                JsonElement coordenadas =
                    rutaJson
                        .GetProperty("geometry")
                        .GetProperty("coordinates");

                List<PuntoRuta> puntos = new List<PuntoRuta>();

                foreach (JsonElement coordenada in coordenadas.EnumerateArray())
                {
                    // GeoJSON utiliza [longitud, latitud].
                    double longitud = coordenada[0].GetDouble();
                    double latitud = coordenada[1].GetDouble();

                    puntos.Add(new PuntoRuta
                    {
                        Latitud = latitud,
                        Longitud = longitud
                    });
                }

                // 7. Construimos nuestra respuesta para Android.
                RutaRespuesta resultado = new RutaRespuesta
                {
                    DistanciaMetros = distancia,
                    DuracionSegundos = duracion,
                    Puntos = puntos
                };

                return Ok(resultado);
            }
            catch (HttpRequestException ex)
            {
                return StatusCode(
                    502,
                    new
                    {
                        mensaje = "No se pudo establecer conexión con el servidor de rutas.",
                        detalle = ex.Message,
                        causaInterna = ex.InnerException?.Message
                    }
                );
            }
            catch (JsonException)
            {
                return StatusCode(
                    502,
                    "No se pudo interpretar la respuesta del servidor de rutas."
                );
            }
        }
    }
}
