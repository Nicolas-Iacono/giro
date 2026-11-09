# WhatsApp Business con coexistencia en Jiro

Cada comercio autoriza su propia cuenta con Embedded Signup. Jiro verifica el token y el número en Meta; nunca ejecuta `/register` ni `/deregister` para forzar una migración. Tener un Config ID no sustituye la aprobación de Meta.

## Configuración del servidor (Railway)

| Variable | Valor / propósito |
| --- | --- |
| `GIRO_PUBLIC_URL` | `https://giro-production-61fc.up.railway.app`, sin `/api` ni barra final |
| `GIRO_WHATSAPP_APP_ID` | `1130736136012341` |
| `GIRO_WHATSAPP_ELS_CONFIG_ID` | `2002557904178859` |
| `GIRO_WHATSAPP_GRAPH` | `https://graph.facebook.com/v27.0` |
| `GIRO_WHATSAPP_APP_SECRET` | Secreto de **jirohub**, desde Meta; nunca el de Tuinmo |
| `GIRO_WHATSAPP_VERIFY_TOKEN` | Secreto elegido para verificar el callback, idéntico al de Meta |
| `GIRO_WHATSAPP_TOKEN_KEY` | Clave privada estable para cifrar los tokens. No rotarla sin migrar los tokens ya cifrados |

El servidor no carga `.env.example`: copiar sus nombres a Variables de Railway. No subir secretos a GitHub ni configurar el App Secret como variable `VITE_*`. El token de cada comercio se almacena cifrado en la base; no existe un único token comercial global.

## Configuración en Meta

1. En Facebook Login for Business, habilitar SDK de JavaScript y permitir el origen real del frontend (`https://jirohub.com/`; agregar otros orígenes solo si se usan).
2. Usar configuración de **Registro insertado de WhatsApp**, Cloud API, token de usuario del sistema y los permisos `whatsapp_business_messaging` y `whatsapp_business_management`. La opción de caducidad **Nunca** aplica a los tokens obtenidos por ese flujo; siguen siendo revocables.
3. El frontend lanza `FB.login` con `config_id`, `response_type: 'code'`, `override_default_response_type: true` y `extras: { setup: {}, featureType: 'whatsapp_business_app_onboarding', sessionInfoVersion: '3', version: 'v4' }`. Validar en el comprobador de Meta que **WhatsApp Business app onboarding** figure activado. El nombre de la configuración no activa la coexistencia.
4. Callback: `https://giro-production-61fc.up.railway.app/api/public/whatsapp`. Verificar con el verify token de Jiro. La firma de POST usa el App Secret de Jiro.
5. Suscribir los campos `messages`, `history`, `smb_app_state_sync`, `smb_message_echoes` y `account_update`. Además, suscribir **cada WABA** a la app. Son dos niveles diferentes; un paso exitoso no implica el otro.
6. Completar App Review para acceso avanzado, los requisitos del proveedor de tecnología y publicación. El error **2655111** observado en el registro indica que faltan permisos avanzados: cambiar el frontend no lo resuelve.

Las políticas, versión y elegibilidad las determina Meta. Referencias oficiales:
- https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/onboarding-business-app-users
- https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/overview
- https://developers.facebook.com/docs/facebook-login/facebook-login-for-business/

## Qué hace el código

- Abre Meta directamente en el gesto del clic, mientras prepara la sesión en backend. Maneja cancelación, timeout, orígenes no autorizados y eventos/código en ambos órdenes.
- Comprueba `is_valid`, `app_id`, permisos, pertenencia WABA/número y `is_on_biz_app`. No acepta un token de Tuinmo ni supone coexistencia por una bandera del navegador.
- No elige una cuenta arbitraria si Meta devuelve varias. Si falta `target_ids` pero se recibió la WABA, verifica pertenencia consultando sus números.
- Guarda la conexión como `PENDIENTE_WEBHOOK` si falta alguna suscripción. La cuenta puede quedar autorizada en Meta aunque falle un paso posterior: no repetir registros ni borrar el número para resolverlo.
- El bot no se activa por el registro. El comercio puede habilitarlo explícitamente.
- Solicita sincronización después de guardar la conexión y confirmar el webhook. Meta solo entrega el historial/contactos autorizados; no se promete importar todo el historial. Existe un botón para reintentar.
- Los ecos del celular se guardan como mensajes salientes y no disparan respuestas ni abren la ventana de atención. Los estados se aplican solo al comercio correspondiente.
- Desconectar en Jiro elimina la configuración local e intenta desuscribir la WABA; no equivale a revocar todos los permisos en Meta. Completar la revocación desde WhatsApp Business cuando corresponda.

## Prueba previa a App Review, sin tocar el número de Tuinmo

Usar un comercio de prueba dedicado en Jiro. No reutilizar ni desconectar el número real que ya funciona en Tuinmo.

En **Paso 1. Pruébalo** de jirohub se verificaron estos recursos (si Meta los reemplaza, usar los actuales del panel):

| Campo manual en Jiro | Valor |
| --- | --- |
| Phone Number ID | `1454635647729282` |
| WABA ID | `2764201230683975` |
| Access Token | Generarlo en Meta para jirohub y esa cuenta de prueba; no copiar aquí |

1. Generar el token con acceso a la cuenta de prueba y los dos permisos requeridos. No usar App ID, Config ID, App Secret o un token de otra app como Access Token.
2. En Jiro → Ajustes completar los tres campos manuales y guardar. Se verifican app, permisos y pertenencia antes de almacenar el token.
3. Probar conexión y suscribir la cuenta. Si la recepción queda pendiente, comprobar variables/callback, campos y modo de la app en Meta; no se presenta la recepción como verificada.
4. Desde la pantalla de pruebas de Meta enviar una plantilla al celular agregado como destinatario autorizado y responder desde ese celular.
5. Comprobar llegada del mensaje a la bandeja de Jiro. Responder desde Jiro dentro de la ventana de atención. Fuera de ella el envío de texto se rechaza: la aplicación todavía no ofrece un gestor de plantillas.
6. Para el video de `whatsapp_business_messaging`, mostrar el envío **desde Jiro** y la recepción real. Un envío solo desde Meta no demuestra la funcionalidad de Jiro.
7. El video de `whatsapp_business_management` requiere demostrar la funcionalidad solicitada a Meta (incluida creación de plantillas según el formulario). Este cambio no agrega un gestor de plantillas ni garantiza aceptación de la revisión; completar esa funcionalidad antes de afirmar que existe.

El número de prueba sirve para verificar Cloud API. No demuestra coexistencia de un número real.

## Aceptación con un número real elegible

Después de la habilitación de Meta: conectar desde Jiro; comprobar que el celular conserva WhatsApp Business y que `is_on_biz_app` es verdadero; enviar y recibir en ambos sentidos, comprobar ecos sin duplicados, estados, contactos e historial consentido y aislamiento de otro comercio. No se ha ejecutado esta prueba con recursos reales como parte del cambio de código.

## Diagnóstico sin secretos

- **100/33**: recurso inaccesible con ese token, ID incorrecto u operación no admitida; no prueba que se haya borrado la cuenta.
- **2655111**: revisar acceso avanzado aprobado de la app, no reinstalar WhatsApp.
- `granular_scopes` puede no contener `target_ids`: no inventar una “WABA real” ni elegir otra silenciosamente.
- Comprobar app/token/IDs del mismo proyecto. Nunca mezclar Tuinmo con Jiro.
- No registrar códigos OAuth, tokens, App Secrets ni cuerpos privados de conversaciones en logs.

## Verificación local

```sh
mvn test
```

El frontend incluye pruebas del diálogo con `npm test` y se compila con `npm run build`. Las pruebas de backend simulan Graph API: no envían mensajes ni conceden permisos en Meta.
