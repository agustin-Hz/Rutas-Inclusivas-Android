package rutas.inclusivas;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class RutasFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "rutas_inclusivas_notificaciones";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        // Firebase llama a este método cuando genera o renueva el token.
        // Sin sesión no hay a quién asociarlo: HomeActivity lo registra al próximo ingreso.
        if (new SessionManager(this).haySesionValida()) {
            FcmRegistro.enviarToken(token);
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String titulo = null;
        String mensaje = null;

        // Datos enviados en el mensaje FCM.
        if (remoteMessage.getData() != null) {
            titulo = remoteMessage.getData().get("title");
            mensaje = remoteMessage.getData().get("body");
        }

        // Datos de la notificación, si existen.
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                titulo = remoteMessage.getNotification().getTitle();
            }

            if (remoteMessage.getNotification().getBody() != null) {
                mensaje = remoteMessage.getNotification().getBody();
            }
        }

        if (titulo == null || titulo.trim().isEmpty()) {
            titulo = getString(R.string.app_name);
        }

        if (mensaje == null || mensaje.trim().isEmpty()) {
            mensaje = "Tenés una nueva notificación.";
        }

        mostrarNotificacion(titulo, mensaje);
    }

    private void mostrarNotificacion(String titulo, String mensaje) {

        crearCanalNotificaciones();

        // Android 13 o superior requiere permiso de notificaciones.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Al tocar la notificación se abre la app (MainActivity decide si va a Home o a Login).
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(titulo)
                        .setContentText(mensaje)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(mensaje))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);

        NotificationManagerCompat.from(this)
                .notify((int) System.currentTimeMillis(), builder.build());
    }

    private void crearCanalNotificaciones() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID,
                    "Notificaciones de Rutas Inclusivas",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            canal.setDescription(
                    "Avisos sobre rutas favoritas y accesibilidad."
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(canal);
            }
        }
    }
}
