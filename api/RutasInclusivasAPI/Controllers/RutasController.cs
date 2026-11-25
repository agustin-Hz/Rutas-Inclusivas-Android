
using Microsoft.AspNetCore.Mvc;
using RutasInclusivasAPI.Models;
using RutasInclusivasAPI.Services;
using System.Globalization;
using System.Text.Json;

namespace RutasInclusivasAPI.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class RutasController : ControllerBase
    {
        private readonly HttpClient _httpClient;
        private readonly ServicioObstaculos _servicioObstaculos;

        public RutasController(IHttpClientFactory httpClientFactory,
    ServicioObstaculos servicioObstaculos)
        {
            _httpClient = httpClientFactory.CreateClient();
            _servicioObstaculos = servicioObstaculos;

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

            List<ObstaculoRuta> obstaculos =
    await _servicioObstaculos.ObtenerObstaculosActivos();

            Console.WriteLine(
                $"Obstáculos activos encontrados: {obstaculos.Count}"
            );

            // 3. Construimos la URL de consulta.

            string url =
    $"http://routing.openstreetmap.de/routed-foot/route/v1/driving/{origen};{destino}" +
    "?overview=full&geometries=geojson&alternatives=true";

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

                JsonElement rutasJson = raiz.GetProperty("routes");

                Console.WriteLine(
                    $"Cantidad de rutas devueltas por OSRM: {rutasJson.GetArrayLength()}"
                );

                const double RADIO_DETECCION_METROS = 20.0;

                // Datos de la mejor ruta encontrada.
                List<PuntoRuta> puntos = new List<PuntoRuta>();

                double distancia = 0;
                double duracion = 0;

                int menorCantidadObstaculos = int.MaxValue;
                bool encontroRuta = false;

                // Evaluamos todas las rutas devueltas por OSRM.
                for (int indiceRuta = 0;
                     indiceRuta < rutasJson.GetArrayLength();
                     indiceRuta++)
                {
                    JsonElement rutaCandidata = rutasJson[indiceRuta];

                    double distanciaCandidata =
                        rutaCandidata.GetProperty("distance").GetDouble();

                    double duracionCandidata =
                        rutaCandidata.GetProperty("duration").GetDouble();

                    JsonElement coordenadas = rutaCandidata
                        .GetProperty("geometry")
                        .GetProperty("coordinates");

                    List<PuntoRuta> puntosCandidatos = new List<PuntoRuta>();

                    foreach (JsonElement coordenada in coordenadas.EnumerateArray())
                    {
                        puntosCandidatos.Add(new PuntoRuta
                        {
                            Longitud = coordenada[0].GetDouble(),
                            Latitud = coordenada[1].GetDouble()
                        });
                    }

                    if (puntosCandidatos.Count < 2)
                    {
                        Console.WriteLine(
                            $"Ruta {indiceRuta + 1}: geometría insuficiente."
                        );

                        continue;
                    }

                    int cantidadObstaculosCercanos = 0;

                    foreach (var obstaculo in obstaculos)
                    {
                        double distanciaMinima = double.MaxValue;

                        for (int i = 0; i < puntosCandidatos.Count - 1; i++)
                        {
                            double distanciaObstaculo =
                                DistanciaPuntoASegmentoMetros(
                                    obstaculo.Latitud,
                                    obstaculo.Longitud,
                                    puntosCandidatos[i],
                                    puntosCandidatos[i + 1]
                                );

                            if (distanciaObstaculo < distanciaMinima)
                            {
                                distanciaMinima = distanciaObstaculo;
                            }
                        }

                        if (distanciaMinima <= RADIO_DETECCION_METROS)
                        {
                            cantidadObstaculosCercanos++;
                        }

                        Console.WriteLine(
                            $"Ruta {indiceRuta + 1} - " +
                            $"Obstáculo {obstaculo.ID_Reporte}: " +
                            $"{distanciaMinima:F2} metros."
                        );
                    }

                    Console.WriteLine(
                        $"Ruta {indiceRuta + 1}: " +
                        $"{cantidadObstaculosCercanos} obstáculos cercanos. " +
                        $"Distancia: {distanciaCandidata:F2} metros."
                    );

                    if (!encontroRuta ||
                        cantidadObstaculosCercanos < menorCantidadObstaculos ||
                        (cantidadObstaculosCercanos == menorCantidadObstaculos &&
                         distanciaCandidata < distancia))
                    {
                        encontroRuta = true;

                        menorCantidadObstaculos = cantidadObstaculosCercanos;

                        distancia = distanciaCandidata;
                        duracion = duracionCandidata;

                        puntos = puntosCandidatos;
                    }
                }

                if (!encontroRuta)
                {
                    return StatusCode(
                        502,
                        "No se encontraron rutas válidas para evaluar."
                    );
                }

                Console.WriteLine(
                    $"Ruta seleccionada: {distancia:F2} metros, " +
                    $"{menorCantidadObstaculos} obstáculos cercanos."
                );
                // 7. Construimos nuestra respuesta para Android.
                RutaRespuesta resultado = new RutaRespuesta
                {
                    DistanciaMetros = distancia,
                    DuracionSegundos = duracion,
                    CantidadObstaculos = menorCantidadObstaculos,
                    EsRutaLibre = menorCantidadObstaculos == 0,
                    EsAlternativaConMenosObstaculos = menorCantidadObstaculos > 0,
                    Mensaje = menorCantidadObstaculos == 0
    ? "Se encontró una ruta sin obstáculos registrados."
    : "No se encontró una ruta completamente libre. Se seleccionó la alternativa con menor cantidad de obstáculos registrados.",

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


        private static double DistanciaPuntoASegmentoMetros(
    double latitudObstaculo,
    double longitudObstaculo,
    PuntoRuta inicio,
    PuntoRuta fin)
        {
            // Convertimos la latitud del obstáculo a radianes.
            double latitudRad =
                latitudObstaculo * Math.PI / 180.0;

            // Factores de conversión aproximados a metros.
            double metrosPorGradoLatitud = 111320.0;

            double metrosPorGradoLongitud =
                111320.0 * Math.Cos(latitudRad);

            // Convertimos el inicio del segmento a coordenadas locales.
            double x1 =
                (inicio.Longitud - longitudObstaculo) *
                metrosPorGradoLongitud;

            double y1 =
                (inicio.Latitud - latitudObstaculo) *
                metrosPorGradoLatitud;

            // Convertimos el final del segmento.
            double x2 =
                (fin.Longitud - longitudObstaculo) *
                metrosPorGradoLongitud;

            double y2 =
                (fin.Latitud - latitudObstaculo) *
                metrosPorGradoLatitud;

            // Calculamos el vector del segmento.
            double dx = x2 - x1;
            double dy = y2 - y1;

            double longitudCuadrada = dx * dx + dy * dy;

            // Calculamos la posición más cercana sobre el segmento.
            double t = 0;

            if (longitudCuadrada > 0)
            {
                t = -(x1 * dx + y1 * dy) / longitudCuadrada;

                t = Math.Max(0, Math.Min(1, t));
            }

            double puntoCercanoX = x1 + t * dx;
            double puntoCercanoY = y1 + t * dy;

            // Distancia entre el obstáculo y el punto más cercano.
            return Math.Sqrt(
                puntoCercanoX * puntoCercanoX +
                puntoCercanoY * puntoCercanoY
            );
        }


    }
}
