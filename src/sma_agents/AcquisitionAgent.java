package sma_agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets; //
import java.nio.file.*;  //para paths y metodos relacionados con leer archivos
import java.util.HashSet;
import java.util.List;   //listas de java
import java.util.Set;

public class AcquisitionAgent extends Agent{
    /*********************************** variables globales ***********************************/

    private Path commentsFile; //archivo csv
    private final Set<String> processedComments = new HashSet<>(); //set de comentarios procesados
    private String sentimentAgentName = "sentiment"; //agente de procesamiento de sentimiento


    /*********************************** Aux ***********************************/

    /** método para realizar envio a sentiment **/
    private void sendCommentToSentimentAgent(String postId, String commentId, String text){
        ACLMessage message = new ACLMessage(ACLMessage.INFORM);

        message.addReceiver(new AID(sentimentAgentName,AID.ISLOCALNAME)); //
        message.setConversationId("new-comment"); //el tipo de mensaje que envía para que pueda filtrar
        message.setContent(postId + ";" + commentId + ";" + text); //contenido del mensaje

        send(message);
        System.out.println("[" + getLocalName() + "] Comentario enviado a " + sentimentAgentName);
        System.out.println("\n");


    }


    /** método para checkear por nuevos comentarios **/
    private void checkNewComments(){
        try {
            // guardamos todas las líneas del csv en una lista
            List<String> lines = Files.readAllLines(commentsFile, StandardCharsets.UTF_8);

            // y por cada una la normalizamos en minusculas y sin espacios, si está vacia o es la primera continuamos
            for(String line : lines){
                line = line.trim().toLowerCase();
                if (line.isEmpty() || line.startsWith("postid;")) {
                    continue;
                }

                // separamos cada parte del csv para tener el texto por un lado y generar un id unico
                String[] parts = line.split(";", 3);
                String postId = parts[0].trim();
                String commentId = parts[1].trim();
                String text = parts[2].trim();

                String uniqueCommentId = postId + "_" + commentId;

                // si no estaba el mensaje lo detecta para procesar y lo añade
                if (!processedComments.contains(uniqueCommentId)){
                    processedComments.add(uniqueCommentId);
                    System.out.println("[" + getLocalName() + "] Nuevo comentario detectado:");
                    System.out.println("    Publicacion: " + postId);
                    System.out.println("    Comentario: " + commentId);
                    System.out.println("    Texto: " + text);

                    // envío a sentimentAgent
                    sendCommentToSentimentAgent(postId, commentId, text);
                }

            }

        } catch (IOException e) {
        System.out.println("[" + getLocalName() + "] Error leyendo fichero: " + e.getMessage());
        }
    }



    /*********************************** Behaviours ***********************************/


    TickerBehaviour checkBehaviour = new TickerBehaviour(this, 2000) {
        @Override
        protected void onTick() {
            checkNewComments();
        }
    };



    /*********************************** setup ***********************************/


    protected void setup() {
        Object[] args = getArguments();
        String filePath = (String)args[0];
        sentimentAgentName =(String)args[1];
        commentsFile = Paths.get(filePath);

        System.out.println("[" + getLocalName() + "] Agente de adquisicion iniciado");
        System.out.println("[" + getLocalName() + "] Vigilando fichero: " + commentsFile.toAbsolutePath());
        System.out.println("[" + getLocalName() + "] Enviara comentarios al agente: " + sentimentAgentName);
        System.out.println("\n");

        addBehaviour(checkBehaviour);


    }
}