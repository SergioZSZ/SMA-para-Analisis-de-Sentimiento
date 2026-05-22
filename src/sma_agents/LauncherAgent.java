package sma_agents;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LauncherAgent extends Agent {

    private Path videosFile;
    private Set<String> monitoredVideos; // NUEVO: Set de videos que ya tienen agente
    private int agentCounter; // NUEVO: Contador para nombres únicos

    /** Leer el archivo de IDs de videos **/
    private List<String> readVideoIds() {
        List<String> videoIds = new ArrayList<>();

        try {
            List<String> lines = Files.readAllLines(videosFile, StandardCharsets.UTF_8);

            for (String line : lines) {
                line = line.trim();

                // Saltar líneas vacías o el header "postId"
                if (line.isEmpty() || line.equalsIgnoreCase("postId")) {
                    continue;
                }

                videoIds.add(line);
            }

        } catch (IOException e) {
            System.err.println("[" + getLocalName() + "] Error leyendo archivo de videos: " + e.getMessage());
        }

        return videoIds;
    }

    /** Crear un AcquisitionAgent para un video específico **/
    private void createAcquisitionAgent(String videoId) {
        try {
            ContainerController container = getContainerController();

            // Nombre único: acquisition_video1, acquisition_video2, etc.
            agentCounter++;
            String agentName = "acquisition_video" + agentCounter;

            // Parámetros: [ruta CSV, ID del video]
            Object[] params = new Object[2];
            params[0] = videosFile.toString(); // Ruta del CSV
            params[1] = videoId; // ID del video que debe procesar ESTE agente

            AgentController agnt = container.createNewAgent(
                    agentName,
                    "sma_agents.AcquisitionAgent",
                    params
            );

            agnt.start();

            System.out.println("[" + getLocalName() + "] Creado " + agentName + " para video: " + videoId);

            // Pequeña pausa para evitar condiciones de carrera
            Thread.sleep(500);

        } catch (StaleProxyException | InterruptedException e) {
            System.err.println("[" + getLocalName() + "] Error creando agente para video " + videoId + ": " + e.getMessage());
        }
    }

    /** NUEVO: Verificar si hay nuevos videos y crear agentes para ellos **/
    private void checkForNewVideos() {
        List<String> currentVideos = readVideoIds();

        if (currentVideos.isEmpty()) {
            return;
        }

        // Buscar videos nuevos que no estén siendo monitoreados
        List<String> newVideos = new ArrayList<>();
        for (String videoId : currentVideos) {
            if (!monitoredVideos.contains(videoId)) {
                newVideos.add(videoId);
            }
        }

        // Si hay nuevos videos, crear agentes para ellos
        if (!newVideos.isEmpty()) {
            System.out.println("[" + getLocalName() + "] Detectados " + newVideos.size() + " nuevos videos");

            for (String videoId : newVideos) {
                System.out.println("[" + getLocalName() + "] Creando agente para nuevo video: " + videoId);
                createAcquisitionAgent(videoId);
                monitoredVideos.add(videoId);
            }

            System.out.println("[" + getLocalName() + "] Agentes creados para los nuevos videos");
            System.out.println("[" + getLocalName() + "] Total de videos monitoreados: " + monitoredVideos.size());
            System.out.println();
        }
    }

    /*********************************** Behaviours ***********************************/

    /** NUEVO: Comportamiento cíclico que verifica nuevos videos cada X segundos **/
    TickerBehaviour monitorBehaviour = new TickerBehaviour(this, 5000) { // Cada 10 segundos
        @Override
        protected void onTick() {
            checkForNewVideos();
        }
    };

    /*********************************** setup ***********************************/

    @Override
    protected void setup() {
        monitoredVideos = new HashSet<>();
        agentCounter = 0;

        System.out.println("[" + getLocalName() + "] LauncherAgent iniciado");

        // Obtener ruta del archivo de videos
        Object[] args = getArguments();
        if (args == null || args.length < 1) {
            System.err.println("[" + getLocalName() + "] ERROR: Se requiere la ruta del archivo de videos");
            System.err.println("[" + getLocalName() + "] Uso: launcher:sma_agents.LauncherAgent(data/id_videos.csv)");
            doDelete();
            return;
        }

        videosFile = Paths.get((String) args[0]);
        System.out.println("[" + getLocalName() + "] Archivo de videos: " + videosFile.toAbsolutePath());
        System.out.println("[" + getLocalName() + "] Frecuencia de verificación: cada 10 segundos");
        System.out.println();

        // NUEVO: Añadir comportamiento de monitoreo continuo (NO se termina el agente)
        addBehaviour(monitorBehaviour);
    }

    @Override
    protected void takeDown() {
        System.out.println("[" + getLocalName() + "] LauncherAgent terminado");
        System.out.println("[" + getLocalName() + "] Total de videos monitoreados: " + monitoredVideos.size());
    }
}