package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class AgBasico extends Agent {
    protected void setup()
    {
        AID aid = this.getAID(); //id del agente
        Object[] args = getArguments(); //argumentos del agente metidos entre () al final

        // Prints basicos del nombre y los parametros metidos + como hacerlos esperar y suspender
        System.out.println("Agente con AID: " + aid.getName());

        if((args==null) || (args.length < 1)){    System.out.println("Sin parametros. "); }
        else {
            for (int i=0; i < args.length; ++i){
                System.out.println("Param " + i + ": " + (String)args[i]);
            }
        }
        this.doWait(3000);
        System.out.println("Saliendo de espera, entrando en suspendido");
        this.doSuspend();
        System.out.println("Saliendo de suspendido");


// creacion de agentes desde el propio agente, en este caso el Agente blocked

        AgentContainer container=(AgentContainer) getContainerController();
        Object[] params=new Object[1];
        params[0]="nuevo_parametro";
        try{
            AgentController agnt=container.createNewAgent("agenteBlock", "agents.Agente", params);
            agnt.start();
        }
        catch(Exception e){e.printStackTrace();}


        // terminacion del agente, doDelete, que llama a takeDown para usarlo
        doDelete();
        }
    protected void takeDown(){
        this.doWait(3000);
        System.out.println("AgBasico terminado");
    }
}

