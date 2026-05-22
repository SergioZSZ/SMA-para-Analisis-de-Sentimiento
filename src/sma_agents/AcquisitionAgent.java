package sma_agents;

import com.google.api.services.youtube.model.CommentThread;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
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
import youtube.YoutubeCommentsAPI;
import youtube.YoutubeResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class AcquisitionAgent extends Agent {
    /*********************************** variables globales ***********************************/

    private static final String SENTIMENT_SERVICE_TYPE = "sentiment process";
    private static final String SENTIMENT_ANALYSIS_CONVERSATION_ID = "sentiment-analysis";
    private static final String MESSAGE_LANGUAGE = "java-serialization";

    private Path commentsFile; //archivo csv
    private final Set<String> processedComments = new HashSet<>(); //set de comentarios procesados
    private final String servicio = "acquire comments"; //servicio que proporciona el agente

    /*********************************** DF register y busqueda servicios ***********************************/

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


    /** método para buscar el agente que proporciona el servicio de sentimiento **/
    private AID buscaServicioSentiment() {
        DFAgentDescription template = new DFAgentDescription();

        //Creamos un descriptor de servicios con el type que registra SentimentAgent
        ServiceDescription sd = new ServiceDescription();
        sd.setType(SENTIMENT_SERVICE_TYPE);
        template.addServices(sd);

        try {
            //Consultamos al DF los servicios que encajan con el descriptor
            DFAgentDescription[] result = DFService.search(this, template);

            if (result.length == 0) {
                System.out.println("[" + getLocalName() + "] No se encontró ningún agente con servicio sentiment process. levantando uno...");
                levantarSentimentAgent();
                doWait(3000);
                return buscaServicioSentiment();
            }

            AID sentimentAID = result[0].getName();
            System.out.println("[" + getLocalName() + "] Usando agente de sentimiento: " + sentimentAID.getLocalName());
            return sentimentAID;

        } catch (FIPAException ex) {
            System.err.println("[" + getLocalName() + "] Error buscando agente de sentimiento en el DF: " + ex.getMessage());
            return null;
        }
    }

    /*********************************** levantamiento de sentiment agent si no hay ***********************************/

    private void levantarSentimentAgent() {
        try {
            ContainerController container = getContainerController();

            AgentController sentiment = container.createNewAgent(
                    "sentiment",
                    "sma_agents.SentimentAgent",
                    new Object[]{}
            );

            sentiment.start();

            System.out.println("[" + getLocalName() + "] SentimentAgent levantado dinámicamente");

        } catch (StaleProxyException e) {
            System.err.println("[" + getLocalName() + "] No se pudo levantar SentimentAgent: " + e.getMessage());
        }
    }


    /*********************************** Aux ***********************************/

    /** método para crear un id único del comentario procesado **/
    private String createUniqueCommentId(String postId, String commentId) {
        return postId + "_" + commentId;
    }


    /** método para realizar envio a sentiment **/
    private void sendCommentToSentimentAgent(String postId, String commentId, String text) {
        AID sentimentAID = this.buscaServicioSentiment();

        if (sentimentAID == null) {
            System.out.println("[" + getLocalName() + "] No se puede enviar el comentario porque no hay SentimentAgent disponible");
            return;
        }

        try {
            SentimentRequest request = new SentimentRequest(postId, commentId, text);

            ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
            message.addReceiver(sentimentAID);
            message.setConversationId(SENTIMENT_ANALYSIS_CONVERSATION_ID);
            message.setLanguage(MESSAGE_LANGUAGE);
            message.setReplyWith("sentiment-request-" + commentId);
            message.setContentObject(request);

            send(message);

            System.out.println("[" + getLocalName() + "] Comentario enviado a " + sentimentAID.getName());
            System.out.println("\n");

        } catch (IOException e) {
            System.err.println("[" + getLocalName() + "] Error serializando mensaje: " + e.getMessage());
        }
    }


    /** método para checkear por nuevos comentarios **/
    private void checkNewComments() {
        try {
            // guardamos todas las líneas del csv en una lista
            List<String> lines = Files.readAllLines(commentsFile, StandardCharsets.UTF_8);

            // y por cada una la normalizamos en minusculas y sin espacios, si está vacia o es la primera continuamos
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("postId;")) {
                    continue;
                }

                // sacamos id del video (que es la línea del archivo)
                String idvideo = line;

                YoutubeResponse response = YoutubeCommentsAPI.getComments(idvideo);

                for (CommentThread comment : response.comments()) {
                    String postId = response.title();
                    String commentId = comment.getSnippet().getTopLevelComment().getId();
                    String commentText = comment.getSnippet().getTopLevelComment().getSnippet().getTextDisplay();

                    if (commentId == null || commentId.isBlank()) {
                        commentId = comment.getId();
                    }

                    String uniqueID = createUniqueCommentId(postId, commentId);

                    if (processedComments.contains(uniqueID)) {
                        continue;
                    }

                    processedComments.add(uniqueID);
                    sendCommentToSentimentAgent(postId, commentId, commentText);
                }
            }

        } catch (IOException e) {
            System.out.println("[" + getLocalName() + "] Error leyendo fichero: " + e.getMessage());
        }
    }


    /** método para manejar respuestas correctas recibidas desde SentimentAgent **/
    private void handleSentimentOk(ACLMessage message) {
        try {
            SentimentResponse response = (SentimentResponse) message.getContentObject();

            System.out.println("[" + getLocalName() + "] Informe recibido desde " + message.getSender().getLocalName());
            System.out.println("    Publicacion: " + response.getPostId());
            System.out.println("    Comentario: " + response.getCommentId());
            System.out.println("    Sentimiento: " + response.getSentiment());
            System.out.println("    Score: " + response.getScore());
            System.out.println("\n");

        } catch (UnreadableException | ClassCastException e) {
            System.err.println("[" + getLocalName() + "] No se pudo leer la respuesta correcta de SentimentAgent: " + e.getMessage());
        }
    }


    /** método para manejar errores recibidos desde SentimentAgent **/
    private void handleSentimentError(ACLMessage message) {
        try {
            AgentError error = (AgentError) message.getContentObject();

            String uniqueCommentId = createUniqueCommentId(error.getPostId(), error.getCommentId());
            processedComments.remove(uniqueCommentId);

            System.out.println("[" + getLocalName() + "] Error recibido desde " + message.getSender().getLocalName());
            System.out.println("    Publicacion: " + error.getPostId());
            System.out.println("    Comentario: " + error.getCommentId());
            System.out.println("    Motivo: " + error.getReason());
            System.out.println("    Comentario eliminado de procesados. Se reintentará en el próximo ciclo.");
            System.out.println("\n");

        } catch (UnreadableException | ClassCastException e) {
            System.err.println("[" + getLocalName() + "] No se pudo leer el error de SentimentAgent: " + e.getMessage());
        }
    }


    /** método para manejar mensajes no entendidos por SentimentAgent **/
    private void handleSentimentNotUnderstood(ACLMessage message) {
        try {
            AgentError error = (AgentError) message.getContentObject();

            String uniqueCommentId = createUniqueCommentId(error.getPostId(), error.getCommentId());
            processedComments.remove(uniqueCommentId);

            System.out.println("[" + getLocalName() + "] SentimentAgent no entendió el mensaje enviado");
            System.out.println("    Publicacion: " + error.getPostId());
            System.out.println("    Comentario: " + error.getCommentId());
            System.out.println("    Motivo: " + error.getReason());
            System.out.println("    Comentario eliminado de procesados. Se reintentará en el próximo ciclo.");
            System.out.println("\n");

        } catch (UnreadableException | ClassCastException e) {
            System.err.println("[" + getLocalName() + "] No se pudo leer el NOT_UNDERSTOOD de SentimentAgent: " + e.getMessage());
        }
    }

    /*********************************** Behaviours ***********************************/

    TickerBehaviour checkBehaviour = new TickerBehaviour(this, 5000) {
        @Override
        public void onTick() {
            checkNewComments();
        }
    };


    CyclicBehaviour sentimentResponseBehaviour = new CyclicBehaviour(this) {
        @Override
        public void action() {
            MessageTemplate performativeTemplate = MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.or(
                            MessageTemplate.MatchPerformative(ACLMessage.FAILURE),
                            MessageTemplate.MatchPerformative(ACLMessage.NOT_UNDERSTOOD)
                    )
            );

            MessageTemplate template = MessageTemplate.and(
                    performativeTemplate,
                    MessageTemplate.MatchConversationId(SENTIMENT_ANALYSIS_CONVERSATION_ID)
            );

            ACLMessage message = receive(template);

            if (message == null) {
                block();
                return;
            }

            switch (message.getPerformative()) {
                case ACLMessage.INFORM:
                    handleSentimentOk(message);
                    break;

                case ACLMessage.FAILURE:
                    handleSentimentError(message);
                    break;

                case ACLMessage.NOT_UNDERSTOOD:
                    handleSentimentNotUnderstood(message);
                    break;

                default:
                    System.out.println("[" + getLocalName() + "] Performativa no esperada desde SentimentAgent: " + message.getPerformative());
                    break;
            }
        }
    };

    /*********************************** setup ***********************************/

    @Override
    protected void setup() {
        Object[] args = getArguments();

        if (args == null || args.length == 0) {
            System.err.println("[" + getLocalName() + "] No se ha indicado la ruta del fichero de comentarios");
            doDelete();
            return;
        }

        String filePath = (String) args[0];
        commentsFile = Paths.get(filePath);

        System.out.println("[" + getLocalName() + "] Agente de adquisicion iniciado");

        registerAgent(servicio);

        System.out.println("[" + getLocalName() + "] Vigilando fichero: " + commentsFile.toAbsolutePath());
        System.out.println("\n");

        addBehaviour(checkBehaviour);
        addBehaviour(sentimentResponseBehaviour);
    }


    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            System.err.println("[" + getLocalName() + "] Error desregistrando del DF: " + e.getMessage());
        }
    }
}
