using System.Text;
using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using RutasInclusivasAPI.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddHttpClient();
builder.Services.AddSingleton<NotificacionService>();
builder.Services.AddScoped<ServicioObstaculos>();
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Name = "Authorization",
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        In = ParameterLocation.Header,
        Description = "Pegá el accesoToken."
    });

    c.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" }
            },
            Array.Empty<string>()
        }
    });
});

string claveJwt = builder.Configuration["Jwt:Key"] ?? throw new InvalidOperationException("Falta configurar Jwt:Key (User Secrets).");
string emisorJwt = builder.Configuration["Jwt:Issuer"] ?? throw new InvalidOperationException("Falta configurar Jwt:Issuer (User Secrets).");
string audienciaJwt = builder.Configuration["Jwt:Audience"] ?? throw new InvalidOperationException("Falta configurar Jwt:Audience (User Secrets).");

builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(opciones =>
    {
        opciones.MapInboundClaims = false;

        opciones.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = emisorJwt,
            ValidAudience = audienciaJwt,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(claveJwt)),
            ClockSkew = TimeSpan.Zero
        };

        // Deja en la consola de la API el motivo exacto por el que se rechaza un token.
        opciones.Events = new JwtBearerEvents
        {
            OnAuthenticationFailed = contexto =>
            {
                contexto.HttpContext.RequestServices
                    .GetRequiredService<ILoggerFactory>()
                    .CreateLogger("JwtBearer")
                    .LogWarning("Token rechazado: {Motivo}", contexto.Exception.Message);
                return Task.CompletedTask;
            }
        };
    });

var app = builder.Build();

// Firebase (notificaciones push). Si no hay credenciales la API igual arranca,
// pero el envío de notificaciones falla hasta que se configuren.
// Ruta del JSON de la cuenta de servicio: User Secrets "Firebase:CredentialPath"
// o la variable de entorno GOOGLE_APPLICATION_CREDENTIALS.
try
{
    string? rutaFirebase = builder.Configuration["Firebase:CredentialPath"]
        ?? Environment.GetEnvironmentVariable("GOOGLE_APPLICATION_CREDENTIALS");

    if (!string.IsNullOrWhiteSpace(rutaFirebase) && File.Exists(rutaFirebase))
    {
        FirebaseApp.Create(new AppOptions { Credential = GoogleCredential.FromFile(rutaFirebase) });
    }
    else
    {
        app.Logger.LogWarning("Firebase sin credenciales (Firebase:CredentialPath): las notificaciones push no se enviarán.");
    }
}
catch (Exception ex)
{
    app.Logger.LogWarning(ex, "No se pudo iniciar Firebase: las notificaciones push no se enviarán.");
}

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();