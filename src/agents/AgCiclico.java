package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;

public class AgCiclico extends Agent{
    class ComportamientoCiclico extends CyclicBehaviour{
        int limite = 0;
        public void action(){
            limite ++;
            System.out.println("Tarea " + limite);
        }
    }

    public void setup(){
        System.out.println("Agente " + getLocalName());
        ComportamientoCiclico cc = new ComportamientoCiclico();
        addBehaviour(cc);
        System.out.println("Despues de añadir el comportamiento Ciclico");
    }
}
