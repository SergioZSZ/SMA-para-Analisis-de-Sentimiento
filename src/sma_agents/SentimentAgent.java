package sma_agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;





public class SentimentAgent extends Agent {
/********************* Variables globales **********************/
    private String visualizationAgentName = "visualizer";
    private final String sentimentApiUrl = "http://localhost:8000/classifier/classify";
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Gson gson = new Gson();

    /********************* clases necesarias para las peticiones http **********************/

    class ClassifierInput {
        String text;

        ClassifierInput(String text) {
            this.text = text;
        }
    }

    class ClassifierOutput {
        String tipo;
        double score;
    }



    /********************* Aux **********************/

    /**metodo para clasificar los comentarios**/

    private String classifySentiment(String text){
        String sentiment = "";
        double score = 0;
        try {
            // creacion del json classifierinput
            ClassifierInput input = new ClassifierInput(text);
            String requestBody = gson.toJson(input);

            // creacion de la peticion
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sentimentApiUrl))   //declaración del a uri a la que lanzar la request
                    .timeout(Duration.ofSeconds(20))    //timeout de respuesta
                    .header("Content-Type", "application/json")//tipo de body, obligatorio
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody)) //convertir el string request en un body http
                    .build();   //construirla

            //envio de peticion request, el body de entrada es un string
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("[" + getLocalName() + "] Error API sentiment: "
                        + response.statusCode() + " - " + response.body());

                return "ERROR API";
            }
            //convertir
            ClassifierOutput output = gson.fromJson(response.body(), ClassifierOutput.class);
            sentiment = output.tipo;
            score = output.score;
            System.out.println("[" + getLocalName() + "] sentiment: "+sentiment+"\tscore: "+score);

            return sentiment;

        } catch (Exception e) {
            System.out.println("[" + getLocalName() + "] Error llamando a sentiment API: " + e.getMessage());
            return "ERROR";
        }
    }
    /**metodo para enviar los comentarios y sentimientos a visualización**/

    private void sendResultToVisualizationAgent(String postId, String commentId,
                                                String text, String sentiment){

        //POR IMPLEMENTAR
        String message = postId + "; " + commentId + "; " + text + " --> " + sentiment;
        System.out.println("WARNING: No implementado el envío a visualizerAgent.\n");
    }


    /**metodo para procesar los comentarios**/

    private void processCommentMessage(ACLMessage message){
        // separamos el contenido del mensaje como el csv
        String content = message.getContent();
        System.out.println("[" + getLocalName() + "] Comentario recibido: " + content);

        String[] parts = content.split(";", 3);
        String postId = parts[0].trim();
        String commentId = parts[1].trim();
        String text = parts[2].trim();

        // enviamos el texto a clasificar
        String sentiment = classifySentiment(text);

        System.out.println("[" + getLocalName() + "] Resultado:");
        System.out.println("    Publicacion: " + postId);
        System.out.println("    Comentario: " + commentId);
        System.out.println("    Sentimiento: " + sentiment);

        //lo enviamos al visualizador
        sendResultToVisualizationAgent(postId, commentId, text, sentiment);
    }





    @Override
    protected void setup() {
        Object[] args = getArguments();

        visualizationAgentName = args[0].toString();
        System.out.println("[" + getLocalName() + "] SentimentAgent iniciado");
        System.out.println("[" + getLocalName() + "] Enviara resultados a: " + visualizationAgentName);

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage message = receive();

                if (message != null) {
                    processCommentMessage(message);
                } else {
                    block();
                }
            }
        });
    }
}