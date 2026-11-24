
/*
CREATE DATABASE RutasInclusivas;
USE RutasInclusivas;


-- 1. Tabla USUARIOS 
CREATE TABLE USUARIOS (
    ID_Usuario INT AUTO_INCREMENT PRIMARY KEY,
    Nombre_Usuario VARCHAR(50) NOT NULL UNIQUE,
    Correo VARCHAR(100) NOT NULL UNIQUE,
    Contrasena VARCHAR(255) NOT NULL,
    Zona VARCHAR(100),
    Activo BOOLEAN NOT NULL DEFAULT TRUE
);

-- 2. Tabla TIPOS_OBSTACULO 
CREATE TABLE TIPOS_OBSTACULO (
    ID_Tipo INT AUTO_INCREMENT PRIMARY KEY,
    Nombre VARCHAR(50) NOT NULL,
    Descripcion TEXT
);

-- 3. Tabla REPORTES (Depende de USUARIOS y TIPOS_OBSTACULO)
CREATE TABLE REPORTES (
    ID_Reporte INT AUTO_INCREMENT PRIMARY KEY,
    ID_Usuario INT NOT NULL,
    ID_Tipo INT NOT NULL,
    Descripcion TEXT NOT NULL,
    Ubicacion_Texto VARCHAR(255),
    Latitud DECIMAL(10, 8) NOT NULL,
    Longitud DECIMAL(11, 8) NOT NULL,
    Url_Fotografia VARCHAR(255),
    Fecha_Reporte DATETIME DEFAULT CURRENT_TIMESTAMP,
    Estado VARCHAR(50) DEFAULT 'Activo',
    FOREIGN KEY (ID_Usuario) REFERENCES USUARIOS(ID_Usuario) ON DELETE CASCADE,
    FOREIGN KEY (ID_Tipo) REFERENCES TIPOS_OBSTACULO(ID_Tipo)
);

-- 4. Tabla VALIDACIONES_REPORTE (Depende de REPORTES y USUARIOS)
CREATE TABLE VALIDACIONES_REPORTE (
    ID_Validacion INT AUTO_INCREMENT PRIMARY KEY,
    ID_Reporte INT NOT NULL,
    ID_Usuario INT NOT NULL,
    Tipo_Voto VARCHAR(50) NOT NULL, -- Ej: 'Sigue existiendo', 'Ya no existe'
    Comentario_Evidencia TEXT,
    Fecha_Voto DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ID_Reporte) REFERENCES REPORTES(ID_Reporte) ON DELETE CASCADE,
    FOREIGN KEY (ID_Usuario) REFERENCES USUARIOS(ID_Usuario) ON DELETE CASCADE
);

-- 5. Tabla RUTAS_FAVORITAS (Depende de USUARIOS)
CREATE TABLE RUTAS_FAVORITAS (
    ID_Ruta INT AUTO_INCREMENT PRIMARY KEY,
    ID_Usuario INT NOT NULL,
    Nombre VARCHAR(100) NOT NULL,
    Latitud_Origen DECIMAL(10, 8) NOT NULL,
    Longitud_Origen DECIMAL(11, 8) NOT NULL,
    Latitud_Destino DECIMAL(10, 8) NOT NULL,
    Longitud_Destino DECIMAL(11, 8) NOT NULL,
    Fecha_Creacion DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ID_Usuario) REFERENCES USUARIOS(ID_Usuario) ON DELETE CASCADE
);

-- 6. Tabla HISTORIAL_NOTIFICACIONES (Depende de USUARIOS)
CREATE TABLE HISTORIAL_NOTIFICACIONES (
    ID_Notificacion INT AUTO_INCREMENT PRIMARY KEY,
    ID_Usuario INT NOT NULL,
    Mensaje TEXT NOT NULL,
    Leida BOOLEAN DEFAULT FALSE,
    Fecha_Envio DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ID_Usuario) REFERENCES USUARIOS(ID_Usuario) ON DELETE CASCADE
);
*/
USE RutasInclusivas;

ALTER TABLE USUARIOS 
ADD COLUMN PinRecuperacion VARCHAR(6) NULL, 
ADD COLUMN PinExpiraEn DATETIME NULL;

ALTER TABLE USUARIOS
ADD COLUMN IntentosPin INT NOT NULL DEFAULT 0;

-- Carga inicial de tipos de obstáculos
-- Estos IDs corresponden a las categorías del formulario Android.

INSERT INTO TIPOS_OBSTACULO (ID_Tipo, Nombre, Descripcion)
VALUES
(1, 'Vereda rota', 'Veredas deterioradas que dificultan la circulación peatonal'),
(2, 'Rampa rota o bloqueada', 'Rampas dañadas u obstruidas que impiden el acceso'),
(3, 'Vereda obstruida', 'Obstáculos que bloquean la circulación por la vereda'),
(4, 'Semáforo sonoro averiado', 'Semáforos con señalización sonora fuera de servicio'),
(5, 'Obra sin paso accesible', 'Obras que impiden el paso seguro de peatones')
ON DUPLICATE KEY UPDATE ID_Tipo = ID_Tipo;

-- Dispositivos para notificaciones push (un token FCM por dispositivo)
CREATE TABLE IF NOT EXISTS DISPOSITIVOS_FCM (
    ID_Dispositivo INT AUTO_INCREMENT PRIMARY KEY,
    ID_Usuario INT NOT NULL,
    Token VARCHAR(255) NOT NULL UNIQUE,
    Fecha_Registro DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ID_Usuario) REFERENCES USUARIOS(ID_Usuario) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS REFRESH_TOKENS (
    ID_Token    INT AUTO_INCREMENT PRIMARY KEY,
    ID_Usuario  INT NOT NULL,
    TokenHash   CHAR(64) NOT NULL UNIQUE,
    Creado      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Expira      DATETIME NULL,
    RevocadoEn  DATETIME NULL,
    FOREIGN KEY (ID_Usuario) REFERENCES USUARIOS(ID_Usuario) ON DELETE CASCADE
);
