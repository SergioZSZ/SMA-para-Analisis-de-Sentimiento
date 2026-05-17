package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;

public class Agente extends Agent {
    @Override
    protected void setup() {
        AID aid = getAID();
        System.out.println(aid.getName() + " activated");

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                block();
            }
        }
        );
    }
}