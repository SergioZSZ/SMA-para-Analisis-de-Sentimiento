# Sistema Multiagente para Análisis de Sentimiento

Este proyecto implementa un sistema multiagente desarrollado con **JADE en Java** y una API externa en **Python/FastAPI** para realizar análisis de sentimiento sobre comentarios de texto.

El sistema está compuesto por agentes que se comunican mediante mensajes ACL y delegan la clasificación de sentimiento a un modelo de lenguaje ejecutado desde una API Python basada en Hugging Face / `pysentimiento`.

---

## Objetivo

El objetivo principal del proyecto es desarrollar un sistema multiagente capaz de:

- Monitorizar los videos declarados en `data/newComments.csv`.
- Enviar dichos comentarios a un agente especializado en análisis de sentimiento.
- Clasificar cada comentario como positivo, negativo o neutral.
- Enviar los resultados a un agente de visualización.
- Separar la lógica multiagente de la lógica de inteligencia artificial mediante una API independiente.

La arquitectura busca demostrar el uso de agentes autónomos, comunicación mediante mensajes ACL y delegación de tareas inteligentes a servicios externos.

---

## Arquitectura general

*Crear un diagrama de flujo de la arquitectura*

---
## Componentes principales
### AcquisitionAgent
Servicio ofrecido: "acquire comments"
Agente/s encargado/s de obtener los comentarios de los vídeos declarados en el documento csv. 
Estos agentes contienen dos comportamientos:
#### checkBehaviour(TickBehaviour)
Accede a la API de youtube para extraer los comentarios cada 5 segundos. Por cada vídeo que se monitorice escala el 
sistema generando un nuevo agente de adquisición.
Tras guardar un identificador único por cada comentario se busca desde el DF a un agente que ofrezca el servicio 
"sentiment process" y se le envía un mensaje con performativa ``REQUEST`` con el texto del comentario + id para 
su procesamiento.
En caso de no encontrar en el DF ningún agente con dicho servicio, el AcquisitionAgent levanta por su cuenta un 
SentimentAgent.
#### errorBehaviour(CycleBehaviour)
Espera a recibir mensajes con performativas ``INFORM`` para obtener información sobre si hubo errores o 
no en el procesamiento del agente de sentimiento. Actualmente, en caso Error se intenta volver a procesar dicho comentario.

### SentimentAgent
Servicio ofrecido: sentiment process"
Agente encargado de la clasificación de textos como positivos o negativos. Tiene dos comportamientos:
#### processBehaviour(CycleBehaviour)
Está constantemente esperando a recibir mensajes con performativa ``REQUEST``. De ellos obtiene los
comentarios enviados por los AcquisitionAgent y le hace una petición http a una API local propia
(sentiment_api) desarrollada con FastAPI que utiliza el modelo 'robertuito' 
(https://github.com/pysentimiento/pysentimiento) para clasificar sentimiento de texto en español.
En caso de no estar la API levantada, el propio Agente levanta la API y espera a que esté ejecutada
para enviarle de nuevo el comentario para procesarlo.
Por último, busca en el DF agentes registrados con el servicio "visualization-agent" para enviarle
los resultados obtenidos, ID del vídeo, vídeo al que pertenece el comentario para añadir los campos
a la interfaz de visualización.


### VisualizationAgent
Agente encargado de mostrar o procesar los resultados obtenidos.

---

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


4. Obtener el token api de youtube. Para ello se debe:
    1. Acceder a cloud.google.com con tu cuenta de google
    2. Acceder a la consola y en el menú desplegable de la izquierda, selecciona APIs y servicios/biblioteca
    3. En el buscador, busca ``youtube data api v3``, seleciónala y habilítala.
    4. Vuelve al menú desplegable, selecciona APIs y servicios/Credenciales y pulsa ``crear credenciales``
    5. Adjudícale el nombre que consideres y selecciona la API que acabamos de habilitar
    6. Pulsa crear. Te enseñará tu API key, cópiala en ``.env`` siguiendo el formato del ``.env.example`` 

## Ejecución
Debido a las automatizaciones de levantamiento de agentes y de la sentiment-api, para ejecutar únicamente
es necesario crear una configuración de ejecución con estos parámetros:
- Main class: jade.Boot
- Program Arguments: -gui "acquisition:sma_agents.AcquisitionAgent(data/commentsNew.csv)"
- Working directory: el/directorio/raiz/del/proyecto (en principio ya por defecto)

Posteriormente ejecutar esa configuración y se levanta el sistema.  Se pueden añadir nuevos vídeos al csv durante la ejecución.


## Declaración de uso de IA
Se usó IA generativa para buscar y entender y usar librerías java como:

- Gson (usada para convertir objetos Java a formato JSON antes de enviarlos a la API Python, y para convertir la respuesta JSON de la API en objetos Java manejables dentro del agente.)
    
- java.net (usada para construir y enviar peticiones HTTP desde el agente `SentimentAgent` hacia la API de FastAPI, incluyendo la configuración de la URL, el método `POST`, las cabeceras HTTP y el cuerpo de la petición.)