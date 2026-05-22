package sma_agents;

import com.google.gson.Gson;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import sma_messages.AgentError;
import sma_messages.SentimentRequest;
import sma_messages.SentimentResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;


public class SentimentAgent extends Agent {
    /******* Variables globales ********/

    public static final String CONV_SENTIMENT_ANALYSIS = "sentiment-analysis";    //mensajes que vienen del Acquisition Agent
    private static final String RESULT_CONVERSATION_ID = "sentiment-result";    //mensajes que se envian al Visualization Agent
    private static final String SENTIMENT_SERVICE_TYPE = "sentiment process";
    private static final String VISUALIZATION_SERVICE_TYPE = "visualization-agent";
    private static final String MESSAGE_LANGUAGE = "java-serialization";

    private boolean apiUp = true;

    private final String sentimentApiUrl = "http://localhost:8000/classifier/classify";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Gson gson = new Gson();
    private final String servicio = SENTIMENT_SERVICE_TYPE; //servicio que proporciona el agente

    /******* clases necesarias para las peticiones http ********/

    class ClassifierInput {
        String text;

        ClassifierInput(String text) {
            this.text = text;
        }
    }

    class ClassifierOutput {
        String tipo;
        double score;
        String errorMessage;
    }

    /*********************************** DF register ***********************************/

    private void registerAgent(String servicio) {
        //descripcion del agente y su nombre (descriptor de servicios)
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(this.getAID());

        //el servicio que proporciona y su tipo para localizarlos por el
        ServiceDescription sd = new ServiceDescription();
        sd.setName(servicio);
        sd.setType(servicio);

        //añadimos al descriptor de servicios
        dfd.addServices(sd);

        //realizamos el registro del descriptor de servicios en el DF
        try {
            DFService.register(this, dfd);
            System.out.println("El Agente :" + getLocalName() + " fue registrado en el DF");

        } catch (FIPAException ex) {
            System.err.println("El Agente :" + getLocalName() + " no ha podido registrar el servicio " + ex.getMessage());
            doDelete();
        }
    }


    /*********************************** Aux ***********************************/

    /** método para clasificar los comentarios **/
    private ClassifierOutput classifySentiment(String text) {
        try {
            return llamarApiSentiment(text);

        } catch (Exception e) {
            apiUp = false;
            System.out.println("[" + getLocalName() + "] API no disponible. Intentando levantarla con Docker Compose...");
            System.out.println("[" + getLocalName() + "] Error original: " + e.getMessage());

            levantarSentimentApi();

            // Esperamos a que el contenedor arranque del todo
            doWait(10000);

            try {
                System.out.println("[" + getLocalName() + "] Reintentando llamada a la API...");
                return llamarApiSentiment(text);

            } catch (Exception retryException) {
                String error = "Error llamando a sentiment API tras intentar levantarla: "
                        + retryException.getMessage();

                System.out.println("[" + getLocalName() + "] " + error);

                ClassifierOutput errorOutput = new ClassifierOutput();
                errorOutput.tipo = "ERROR";
                errorOutput.score = 0.0;
                errorOutput.errorMessage = error;

                return errorOutput;
            }
        }
    }


    /** método para buscar agentes de visualización **/
    private AID buscaServicioVisualization() {
        DFAgentDescription template = new DFAgentDescription();

        //Creamos un descriptor de servicios con el type que registra VisualizationAgent
        ServiceDescription sd = new ServiceDescription();
        sd.setType(VISUALIZATION_SERVICE_TYPE);
        template.addServices(sd);

        try {
            //Consultamos al DF los servicios y los devuelve en el dfd
            DFAgentDescription[] result = DFService.search(this, template);

            if (result.length == 0) {
                System.out.println("[" + getLocalName() + "] No se encontró ningún agente con servicio de visualización");
                doWait(3000);
                return buscaServicioVisualization();
            }

            return result[0].getName();

        } catch (FIPAException ex) {
            System.err.println("[" + getLocalName() + "] Error buscando agente de visualización en el DF: " + ex.getMessage());
            return null;
        }
    }


    /** método para enviar los comentarios y sentimientos a visualización **/
    private void sendResultToVisualizationAgent(SentimentResponse response) {
        AID visualizerAgent = buscaServicioVisualization();

        if (visualizerAgent == null) {
            System.out.println("[" + getLocalName() + "] No se puede enviar el resultado porque no hay VisualizationAgent disponible");
            return;
        }

        try {
            ACLMessage message = new ACLMessage(ACLMessage.INFORM);
            message.addReceiver(visualizerAgent);
            message.setConversationId(RESULT_CONVERSATION_ID);
            message.setLanguage(MESSAGE_LANGUAGE);
            message.setContentObject(response);

            send(message);

            System.out.println("[" + getLocalName() + "] Resultado enviado a " + visualizerAgent.getName());

        } catch (IOException e) {
            System.err.println("[" + getLocalName() + "] Error serializando resultado para VisualizationAgent: " + e.getMessage());
        }
    }


    /** método para enviar mensaje OK a AcquisitionAgent tras procesar el comentario **/
    private void sendOkToAcquisitionAgent(ACLMessage originalMessage, SentimentResponse response) {
        try {
            ACLMessage reply = originalMessage.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setLanguage(MESSAGE_LANGUAGE);
            reply.setContentObject(response);

            send(reply);

            System.out.println("[" + getLocalName() + "] Confirmación OK enviada al AcquisitionAgent");
            System.out.println("    Publicacion: " + response.getPostId());
            System.out.println("    Comentario: " + response.getCommentId());
            System.out.println("    Sentimiento: " + response.getSentiment());
            System.out.println("\n");

        } catch (IOException e) {
            System.err.println("[" + getLocalName() + "] Error serializando respuesta: " + e.getMessage());
        }
    }


    /** método para enviar errores al AcquisitionAgent **/
    private void sendErrorToAcquisitionAgent(ACLMessage originalMessage,
                                             String postId,
                                             String commentId,
                                             String reason) {
        try {
            ACLMessage reply = originalMessage.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setLanguage(MESSAGE_LANGUAGE);

            AgentError error = new AgentError(postId, commentId, reason);

            reply.setContentObject(error);
            send(reply);

            System.out.println("[" + getLocalName() + "] Error enviado al AcquisitionAgent");
            System.out.println("    Publicacion: " + postId);
            System.out.println("    Comentario: " + commentId);
            System.out.println("    Motivo: " + reason);
            System.out.println("\n");

        } catch (IOException e) {
            System.err.println("[" + getLocalName() + "] Error serializando error: " + e.getMessage());
        }
    }


    /** método para enviar mensaje NOT_UNDERSTOOD al AcquisitionAgent **/
    private void sendNotUnderstoodToAcquisitionAgent(ACLMessage originalMessage,
                                                     String reason) {
        try {
            ACLMessage reply = originalMessage.createReply();
            reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
            reply.setLanguage(MESSAGE_LANGUAGE);

            AgentError error = new AgentError("UNKNOWN_POST", "UNKNOWN_COMMENT", reason);

            reply.setContentObject(error);
            send(reply);

            System.out.println("[" + getLocalName() + "] NOT_UNDERSTOOD enviado al AcquisitionAgent");
            System.out.println("    Motivo: " + reason);
            System.out.println("\n");

        } catch (IOException e) {
            System.err.println("[" + getLocalName() + "] Error serializando NOT_UNDERSTOOD: " + e.getMessage());
        }
    }


    /** método para procesar los comentarios **/
    private void processCommentMessage(ACLMessage message) {
        SentimentRequest request;

        try {
            request = (SentimentRequest) message.getContentObject();

        } catch (UnreadableException | ClassCastException e) {
            sendNotUnderstoodToAcquisitionAgent(message, "No se pudo leer el objeto recibido: " + e.getMessage());
            return;
        }

        if (request == null || request.getPostId() == null || request.getCommentId() == null || request.getText() == null) {
            sendNotUnderstoodToAcquisitionAgent(message, "El objeto SentimentRequest está incompleto");
            return;
        }

        String postId = request.getPostId();
        String postTitle = request.getPostTitle();
        String commentId = request.getCommentId();
        String text = request.getText();

        System.out.println("[" + getLocalName() + "] Comentario recibido: " + text);

        ClassifierOutput output = classifySentiment(text);

        if (output == null) {
            sendErrorToAcquisitionAgent(message, postId, commentId, "La API devolvió una respuesta nula");
            return;
        }

        if (output.tipo == null || output.tipo.startsWith("ERROR")) {
            String error = output.errorMessage != null
                    ? output.errorMessage
                    : "Error desconocido clasificando el comentario";

            sendErrorToAcquisitionAgent(message, postId, commentId, error);
            return;
        }

        SentimentResponse response = new SentimentResponse(postId, postTitle,commentId, text, output.tipo, output.score);

        System.out.println("[" + getLocalName() + "] Resultado:");
        System.out.println("    Publicacion: " + postId);
        System.out.println("    Comentario: " + commentId);
        System.out.println("    Sentimiento: " + output.tipo);
        System.out.println("    Score: " + output.score);

        sendOkToAcquisitionAgent(message, response);
        sendResultToVisualizationAgent(response);
    }


    /** método para levantar la API **/
    private void levantarSentimentApi() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker",
                    "compose",
                    "up",
                    "-d",
                    "sentiment-api"
            );

            // Ruta raíz del proyecto, donde está el docker-compose.yml
            pb.directory(java.nio.file.Path.of("").toAbsolutePath().toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("[" + getLocalName() + "] API de sentimiento levantada con Docker Compose");
                apiUp = true;
            } else {
                System.err.println("[" + getLocalName() + "] Error levantando API de sentimiento. Exit code: " + exitCode);
            }

        } catch (Exception e) {
            System.err.println("[" + getLocalName() + "] No se pudo levantar la API de sentimiento: " + e.getMessage());
        }
    }


    /** método para llamar a la API de sentimiento **/
    private ClassifierOutput llamarApiSentiment(String text) throws Exception {
        ClassifierInput input = new ClassifierInput(text);
        String requestBody = gson.toJson(input);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sentimentApiUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String error = "Error API sentiment: " + response.statusCode() + " - " + response.body();

            ClassifierOutput errorOutput = new ClassifierOutput();
            errorOutput.tipo = "ERROR_API";
            errorOutput.score = 0.0;
            errorOutput.errorMessage = error;

            return errorOutput;
        }

        return gson.fromJson(response.body(), ClassifierOutput.class);
    }

    /******************************* Behaviours *************************/

    CyclicBehaviour processBehaviour = new CyclicBehaviour(this) {
        @Override
        public void action() {
            MessageTemplate newCommentTemplate = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchConversationId(CONV_SENTIMENT_ANALYSIS)
            );

            ACLMessage message = receive(newCommentTemplate);

            if (message == null) {
                block();
                return;
            }

            processCommentMessage(message);
        }
    };

    /******************************* setup *************************/

    @Override
    protected void setup() {
        System.out.println("[" + getLocalName() + "] SentimentAgent iniciado");

        registerAgent(servicio);
        addBehaviour(processBehaviour);
    }


    //apaga la api al terminar de cualquier manera
    @Override
    protected void takeDown() {
        if (apiUp) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "docker",
                        "compose",
                        "stop",
                        "sentiment-api"
                );

                pb.directory(java.nio.file.Path.of("").toAbsolutePath().toFile());
                pb.inheritIO();

                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    System.out.println("[" + getLocalName() + "] API de sentimiento apagada correctamente");
                } else {
                    System.err.println("[" + getLocalName() + "] Error apagando API. Exit code: " + exitCode);
                }

            } catch (Exception e) {
                System.err.println("[" + getLocalName() + "] No se pudo apagar la API de sentimiento: " + e.getMessage());
            }
        } else {
            System.out.println("[" + getLocalName() + "] No se apaga la API porque no fue levantada");
        }

        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            System.err.println("[" + getLocalName() + "] Error desregistrando del DF: " + e.getMessage());
        }
    }
}
