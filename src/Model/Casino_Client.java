package Model;
import Controlador.Controller;
import Network.*;
import Vista.Finestra;
import Vista.MainView;
import Vista.MainViewClient;

public class Casino_Client {
    public static void main(String[] args) {

        // Es crea la vista del Client
        //MainView view = new MainView(640,480);
        //MainViewClient view = new MainViewClient();

        Finestra finestra = new Finestra();

        MainViewClient mainView = new MainViewClient();

        //Es defineix el gestor de connectivitat amb el servidor
        NetworkManager networkManager = new NetworkManager();

        //Es crea el controlador del sistema i es relacionen controlador amb vista i controlador amb network
        Controller controller = new Controller(finestra, networkManager);

        //Es crea l'enllaç vista amb controlador
        finestra.addController(controller);
        finestra.setMainView();

        //Es realitza la conexio amb el servidor i sagafen els streams d'entrada / sortida
        networkManager.connectarAmbServidor(controller);

        //Es fa visible la finestra grafica
        finestra.setVisible(true);
    }
}
