# Sistema Multiagente para Análisis de Sentimiento

Este proyecto implementa un sistema multiagente desarrollado con **JADE en Java** y una API externa en **Python/FastAPI** para realizar análisis de sentimiento sobre comentarios de texto.

El sistema está compuesto por agentes que se comunican mediante mensajes ACL y delegan la clasificación de sentimiento a un modelo de lenguaje ejecutado desde una API Python basada en Hugging Face / `pysentimiento`.

---

## Objetivo

El objetivo principal del proyecto es desarrollar un sistema multiagente capaz de:

- Monitorizar los videos declarados en `data/id_videos.csv`.
- Enviar dichos comentarios a un agente especializado en análisis de sentimiento.
- Clasificar cada comentario como positivo, negativo o neutral.
- Enviar los resultados a un agente de visualización.
- Separar la lógica multiagente de la lógica de inteligencia artificial mediante una API independiente.

La arquitectura busca demostrar el uso de agentes autónomos, comunicación mediante mensajes ACL y delegación de tareas inteligentes a servicios externos.
Los agentes se comunican mediante mensajes ACL de JADE. El contenido de los mensajes entre agentes Java se envía como objetos serializables mediante `setContentObject()` y `getContentObject()`.
---

## Arquitectura general

![DIAGRAMA-FLUJO](/images/diagrama.png)


---
## Componentes principales
### LaunchernAgent
Sericio ofrecido: "launch-acc"
Agente encargado de monitorizar el documento de vídeos para por cada uno adjudicarle un AcquisitionAgent.
Contiene un comportamiento:
#### monitorBehaviour(TickBehaviour)
Cada 5 segundos monitoriza el archivo para lanzar un acquisitionAgent, dándole de argumento de creación el id del video adjudicado.

### AcquisitionAgent
Servicio ofrecido: "acquire comments"
Agente/s encargado/s de obtener los comentarios de los vídeos adjudicados por el launcherAgent.
Estos agentes contienen dos comportamientos:
#### checkBehaviour(TickBehaviour)
Accede a la API de youtube para extraer los comentarios cada 5 segundos. Por cada vídeo que se monitorice escala el 
sistema generando un nuevo agente de adquisición.
Tras guardar un identificador único por cada comentario se busca desde el DF a un agente que ofrezca el servicio 
"sentiment process" y se le envía un mensaje con performativa ``REQUEST`` con el texto del comentario + id para 
su procesamiento.
En caso de no encontrar en el DF ningún agente con dicho servicio, el AcquisitionAgent levanta por su cuenta un 
SentimentAgent.
#### sentimentResponseBehaviour(CycleBehaviour)
El `sentimentResponseBehaviour` espera respuestas del `SentimentAgent` con `conversationId` `"sentiment-analysis"` y performativas `INFORM`, `FAILURE` o `NOT_UNDERSTOOD`.

- `INFORM`: el comentario fue clasificado correctamente.
- `FAILURE`: hubo un error durante la clasificación.
- `NOT_UNDERSTOOD`: el `SentimentAgent` no pudo interpretar el objeto recibido.

En caso de error, el comentario se elimina del conjunto de comentarios procesados para permitir su reintento en un ciclo posterior.

### SentimentAgent
Servicio ofrecido: `"sentiment process"`.

Agente encargado de clasificar textos como positivos, negativos o neutrales. Recibe mensajes ACL con performativa `REQUEST` y `conversationId` `"sentiment-analysis"` desde el `AcquisitionAgent`.

El contenido del mensaje es un objeto serializado `SentimentRequest`.

El agente llama a una API local desarrollada con FastAPI, que utiliza el modelo `robertuito` de `pysentimiento` para clasificar sentimiento en español. Si la API no está levantada, el propio agente intenta levantarla mediante Docker Compose y reintenta la clasificación.

Tras procesar el comentario:
- Responde al `AcquisitionAgent` con `INFORM` y un objeto `SentimentResponse` si todo fue bien.
- Responde con `FAILURE` y un objeto `AgentError` si hubo un error durante la clasificación.
- Responde con `NOT_UNDERSTOOD` si no pudo interpretar el objeto recibido.

Finalmente, envía el resultado al `VisualizationAgent` mediante un mensaje ACL con performativa `INFORM`.


### VisualizationAgent
Servicio ofrecido: `"visualization-agent"`.

Agente encargado de mostrar los resultados de análisis de sentimiento en una interfaz gráfica Swing.
Recibe mensajes ACL con performativa `INFORM` y `conversationId` `"sentiment-result"` desde el `SentimentAgent`. 
El contenido del mensaje es un objeto serializado `SentimentResponse`, que contiene el identificador del 
vídeo/publicación, el identificador del comentario, el texto, el sentimiento clasificado y la puntuación del modelo.
El agente mantiene contadores globales y por publicación, además de una tabla de detalle para visualizar cada comentario procesado.
---

## Flujo de comunicación ACL

El sistema utiliza los siguientes intercambios entre agentes:

1. `AcquisitionAgent -> SentimentAgent`
   - Performativa: `REQUEST`
   - ConversationId: `sentiment-analysis`
   - Contenido: objeto `SentimentRequest`

2. `SentimentAgent -> AcquisitionAgent`
   - Si el procesamiento fue correcto:
      - Performativa: `INFORM`
      - Contenido: objeto `SentimentResponse`
   - Si falló el procesamiento:
      - Performativa: `FAILURE`
      - Contenido: objeto `AgentError`
   - Si el mensaje no pudo interpretarse:
      - Performativa: `NOT_UNDERSTOOD`
      - Contenido: objeto `AgentError`

3. `SentimentAgent -> VisualizationAgent`
   - Performativa: `INFORM`
   - ConversationId: `sentiment-result`
   - Contenido: objeto `SentimentResponse`


## Requisitos

- Java JDK 17 o superior
- IntelliJ IDEA 
- Cada librería de la carpeta ``/lib``. Todas deben estar añadidas como librerías del proyecto
- Docker version 29.0.1
- Docker Compose version v2.40.3-desktop.1

## Instalación
1. Clonar / descargar el proyecto
2. Añadir las librerías encontradas en `/lib` al proyecto (File > Project Structure > Modules > Dependencies > + > JARs or Directories)
3. Construir imagen docker `sentiment-api` desde el directorio `/sentiment_api`
    Mandato: `docker build -t sentiment-api .`
4. Generar un archivo ``.env`` en el directorio raíz del proyecto
5. Añadir al csv ``/data/id_videos.csv`` en una nueva línea el ID del vídeo de youtube que quieras monitorizar

6. Obtener la API key de youtube. Para ello se debe:
    1. Acceder a https://cloud.google.com/ con tu cuenta de google
    2. Acceder a la consola y en el menú desplegable de la izquierda, selecciona APIs y servicios/biblioteca
    3. En el buscador, busca ``youtube data api v3``, seleciónala y habilítala.
    4. Vuelve al menú desplegable, selecciona APIs y servicios/Credenciales y pulsa ``crear credenciales``
    5. Adjudícale el nombre que consideres y selecciona la API que acabamos de habilitar
    6. Pulsa crear. Te enseñará tu API key, cópiala en ``.env`` siguiendo el formato del ``.env.example`` 

## Ejecución
Debido a las automatizaciones de levantamiento de agentes y de la sentiment-api, para ejecutar únicamente
es necesario crear una configuración de ejecución con estos parámetros:
- Main class: jade.Boot
- Program Arguments: ``-gui -agents "sentiment:sma_agents.SentimentAgent;visualizer:sma_agents.VisualizationAgent;launcher:sma_agents.LauncherAgent(data/id_videos.csv)"``
- Working directory: el/directorio/raiz/del/proyecto (en principio ya por defecto)
- Environment variables: directorio/absoluto/al/.env

Posteriormente ejecutar esa configuración y se levanta el sistema.  Se pueden añadir nuevos vídeos al csv durante la ejecución.


## Declaración de uso de IA
Se usó IA generativa para buscar, entender y usar librerías java implementadas en el proyecto