package agents;
import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;

public class AgSimple extends Agent {
    /********************Comportamientos*************************/
    // Comportamiento 1, cuenta hasta x tareas metidas por argumento
    class ComportamientoSimple extends SimpleBehaviour{
        private int tareas;

        public ComportamientoSimple(int tareas){
            this.tareas = tareas;
        }

        @Override
        public void action() {
                for(int i =0;i<tareas;i++){
                    System.out.println("cs1 Ejecutando " + i);
                }
                System.out.println("Terminadas tareas");
        }

        @Override
        public boolean done() {
            return true;
            //return false;
        }
    }

    //Comportamiento 2, cuenta hasta 3 unicamente
    class ComportamientoSimple2 extends SimpleBehaviour{

        @Override
        public void action() {
            for (int i = 0; i < 3; i++){
                System.out.println("Soy cs2, Ejecuto tarea " + i);
            }
        }

        @Override
        public boolean done() {
            return true;
        }
    }

/************************ Setup del agente *************************/
    protected void setup(){
        System.out.println("Iniciado " + this.getLocalName());

        Object[] args = getArguments();
        int tareas = 0;

        if (args != null && args.length > 0) {
            tareas = Integer.parseInt(args[0].toString());
        }
        ComportamientoSimple cs = new ComportamientoSimple(tareas);
        addBehaviour(cs);
        ComportamientoSimple2 cs2 = new ComportamientoSimple2();
        addBehaviour(cs2);

    }
}
