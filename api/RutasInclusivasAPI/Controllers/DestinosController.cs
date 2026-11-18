using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using RutasInclusivasAPI.Models;
using System.Text.Json;
using System.Linq;

namespace RutasInclusivasAPI.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class DestinosController : ControllerBase
    {
        private readonly HttpClient _httpClient;

        public DestinosController(IHttpClientFactory factory)
        {
            _httpClient = factory.CreateClient();
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
    }
}
