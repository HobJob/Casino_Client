package Network;

import Controlador.Controller;
import Controlador.Game_Controlers.HorseRaceController;
import Controlador.Game_Controlers.RouletteController;
import Utils.Sounds;
import Model.*;
import Model.HorseRace_Model.HorseSchedule;
import Model.RouletteModel.RouletteBetMessage;
import Utils.JsonManager;
import Controlador.SplashScreen;
import Utils.Seguretat;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Stack;

import static Network.Transmission.CONTEXT_BJ_FINISH_USER;

/**
 * NetworkManager gestiona totes les comunicacions del client amb el servidor en les dues direccions.
 *
 * El seu funcionament es basa en anar rebent tots els missatges del servidor i anar-los guardant
 * en una llista de lectures, que més endavant cada part del codi s'encarregarà de recollir.
 *
 * Al contenir un ID propi cada missatge, en la major part dels casos les lectures d'aquests es realitzen
 * cercant l'identificador entre tot el llistat, tot i així, en els jocs com la ruleta o els cavalls
 * on és el servidor qui inicia la comunicació, es prescindeix del valor de l'ID per a cercar directament
 * segons el context del propi missatge.
 */
public class NetworkManager extends Thread {

    /** Controlador del sistema*/
    private Controller controller;

    /** Socket que es connectara al servidor*/
    private Socket socket;

    /** IP del servidor*/
    private final String IP;

    /** Port del servidor on el client es connectara*/
    private final int PORT;

    /** Usuari que esta utilitzant el client. Equival a null si aquest no ha fet LogIn*/
    private User user;

    /** Canal d'emissio d'objectes cap al serividor*/
    private ObjectOutputStream oos;

    /** Canal de recepció d'objectes del serividor*/
    private ObjectInputStream ois;

    /**
     * Un cop el client s'ha connectat amb el servidor, aquest no parara de llegir tot el que se li comuniqui.
     * Com totes les commandes del servidor no es poden resoldre alhora, aquestes son guardades en aquesta llista.
     * D'aquesta manera assegurem la lectura i el processament de tota la informacio que ens envia el servidor.
     */
    private ArrayList<Message> lectures;

    /** Indica si s'ha de fer LogIn automatic amb les dades locals*/
    private boolean autoLogin;

    /** Inidica si estem connectats amb el servidor. Es indiferent si el client esta autentificat o no.*/
    private boolean conectatAmbServidor;

    /** Splash screen del programa. En cas de tenir conexio amb el servidor, networkManager sencarrega de tencar-la*/
    private SplashScreen splashScreen;

    /** Indica el nombre de cops que el client ha intentat reconectarse amb el servidor*/
    private int nTryConnect;

    /** Controla la comunicació entre servidor i el RouletteController */
    private RouletteManager rouletteManager;

    /**
     * Inicialitza el NetworkManager carregant les condicions inicials del JSON. Un cop inicialitzat tot, s'inicia el thread.
     * @param splashScreen pantalla de carrega del sistema
     */
    public NetworkManager(SplashScreen splashScreen) {
        this.splashScreen = splashScreen;
        nTryConnect = 0;

        lectures = new ArrayList<>();

        Object[] configuracio = JsonManager.llegirJson("IpServidor", "PortServidor",JsonManager.BOOLEAN_R);

        IP = (String) configuracio[0];
        PORT = (int) configuracio[1];

        autoLogin = configuracio[2] != null && (boolean)configuracio[2];
        rouletteManager = new RouletteManager(this);

        start();
    }

    /**
     * Intenta fer logIn a partir de 2 creedencials. Usuari - Password.
     * @param credentials usuari i password del usuari que vol autentificar-se
     */
    public void logIn(Object ... credentials) {
        //Si el client no esta connectat al servidor, es connecta
        if(!conectatAmbServidor){
            connectarAmbServidor();
        }
        if(conectatAmbServidor) {
            //Configurem el logIn i enviem la solicitud al servidor
            User user = new User((String)credentials[0],(String)credentials[1],Transmission.CONTEXT_LOGIN);
            new Transmission( user, this);
        }else{
            displayError("Connection error","Our servers aren't available right now.\nTry it again later...");
        }
    }


    /** Solicita al servidor tencar la sessio actual*/
    public void requestLogOut(){
        if(conectatAmbServidor){
            User user = new User("","",Transmission.CONTEXT_LOGOUT);
            user.setOnline(false);
            send(user);
        }else{
            logOut();
        }
    }

    /** Completa el tencament de la sessio actual despres de rebre la confirmacio per part del servidor*/
    private void logOut(){
        controller.exit();
        conectatAmbServidor = false;
        lectures = new ArrayList<>();
        user = null;
    }

    /** Fil d'execucio del network manager, sempre esta llegint els missatges del servidor i els guarda a lectures
     *  Donada la situacio de no estar connectat amb el servidor, espera a estarho
     */
    @Override
    public void run() {

        while(true) {
            try {
                //Si el client no esta connectat al servidor, esperem a que ho estigui
                if(!conectatAmbServidor) {
                    sleep(300);

                }else{
                    //Quan el client estigui connectat es llegeixen les commandes del servidor i
                    //es guarden en la llista de lectures.

                        Message missatge = (Message) ois.readObject();

                        //Si el servidor vol desconnectar aquest client, no guardem el missatge a lectures i acabem el logOut
                        if(ServidorVolDesconnectarAquestClient(missatge))
                            continue;
                        lectures.add(missatge);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
                conectatAmbServidor = false;
                controller.showErrorConnection();
            }catch (InterruptedException | ClassNotFoundException cnf){

            }
        }
    }

    /**
     * Reconeix el missatge rebut del servidor i indica si el servidor vol la desconnexio d'aquest client
     * @param missatge nou missatge rebut del servidor
     * @return indica si el servidor vol la desconexio del usuari
     */
    private boolean ServidorVolDesconnectarAquestClient(Message missatge){
        //Si el missatge es un usuari amb el flag online a false significa que el servidor
        //desitja/permet la desconnexio d'aquest client
        if(missatge instanceof User){
            if(!((User)missatge).isOnline()){
                logOut();
                return true;
            }
        }
        return false;
    }


    /**
     * Connecta el client amb el servidor. Tambe enllaça el NetworkManager amb el controlador del sistema
     * @param controller controlador que es vol associar amb el NetworkManager
     */
    public void connectarAmbServidor(Controller controller){
        this.controller = controller;
        user = null;

        try {
            //S'intenta realitzar la connexio al servidor
            socket = new Socket(IP, PORT);
            ois = new ObjectInputStream(socket.getInputStream());
            oos = new ObjectOutputStream(socket.getOutputStream());
            //En el cas d'haver arribat aquest punt del codi, significa que ens hem connectat correctament
            conectatAmbServidor = true;

            //Si en el json de configuracio inicial apareix l'indicador
            //d'autoLogin, s'executa el login de forma automatica.
            if (autoLogin) {
                splashScreen.infoMessage("Logging in...");
                Object credentials[] = JsonManager.llegirJson(JsonManager.USERNAME_R, JsonManager.PASSWORD_R);
                logIn(credentials[0],Seguretat.desencripta(credentials[1]));
           }else{
                //Com ja estem conectats al servidor, ja podem obrir la vista i tencar la SplashScreen
                exitLoadingScreen();
            }

        }catch (IOException e){
            if(nTryConnect == 0) {
                splashScreen.showError("Server connection failed");
                nTryConnect++;
            }else{
                splashScreen.showError("Attempting to reconnect ["+nTryConnect+"]");
                nTryConnect++;
            }
            connectarAmbServidor(controller);
            try {
                sleep(100);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }
    }

    /**
     * Connecta el client amb el servidor sense fer l'enllaç amb el controlador. Tampoc revisa el autoLogin.
     * Es molt util quan es vol fer un logIn despres d'haver fet un logOut
     */

    private void connectarAmbServidor(){
        try {
            socket = new Socket(IP,PORT);
            ois = new ObjectInputStream(socket.getInputStream());
            oos = new ObjectOutputStream(socket.getOutputStream());
            conectatAmbServidor = true;
        }catch (IOException e){
            //impossible connectarse amb el servidor, problema controlat més endevant en el proces de logIn o signIn
        }
    }

    /***
     * Envia un objecte al servidor.
     * @param objectToSend objecte que es vol enviar al servidor
     */
    public synchronized void send(Object objectToSend){
        try {
            oos.writeObject(objectToSend);
        } catch (IOException e) {
            System.out.println("IMPOSSIBLE ENVIAR MISSATGE!\n");
            e.printStackTrace();
        }
    }

    /**
     * Com la lectura del sevidor es fa constantment de manera paral·lela, el funcionament de read varia bastant.
     * Read es limita a buscar un missatge identificat amb l'ID rebut per parametres dins del conjunt de missatges
     * rebuts pel client, guardats a la llista lectures.
     * @param ID Identificador del missatge que es vol buscar.
     * @return Si read no troba el missatge que es desitja, retorna null. De lo contrari retorna el missatge.
     */

    public synchronized Message read(double ID){
        //Es miren tots els missatges registrats fins el moment
        for(int index = lectures.size() - 1; index >= 0; index--){
            Message message = lectures.get(index);
            //Si el missatge de l'iteracio conte l'id que es buscava, es retorna l'objecte.
            if(message != null && message.getID() == ID) {
                lectures.remove(message);
                return message;
            }
        }
        //Si no s'ha trobat l'ID, es retorna null;
        return null;
    }

    /**
     * Mètode que cada 5ms comprova si s'ha rebut un missatge amb un context concret
     * @param context Context a cercar
     * @return Missatge rebut amb el context indicat
     */
    public Message waitForContext(String context) {
        while (true) {
            for (int i = 0; i < lectures.size(); i++) if (lectures.get(i).getContext().equals(context)) {
                Message m = lectures.get(i);
                lectures.remove(i);

                return m;
            }

            //petit sleep per no maltractar el pc
            try {
                sleep(5);
            } catch (InterruptedException e) {

            }
        }
    }


    /**
     * Com la lectura del sevidor es fa constantment de manera paral·lela, el funcionament de read varia bastant.
     * Read es limita a buscar un missatge identificat amb el contexte rebut per parametres dins del conjunt de missatges
     * rebuts pel client, guardats a la llista lectures.
     * @param context Context del missatge
     * @return Si read no troba el missatge que es desitja, retorna null. De lo contrari retorna el missatge.
     */
    public Message readContext(String context){
        //Es miren tots els missatges registrats fins el moment
        for(int index = lectures.size() - 1; index >= 0; index--){
            Message message = lectures.get(index);
            //Si el missatge de l'iteracio conte l'id que es buscava, es retorna l'objecte.
            if(message != null && message.getContext().equals(context)) {
                lectures.remove(message);
                return message;
            }
        }
        //Si no s'ha trobat l'ID, es retorna null;
        return null;
    }

    /**
     * Inicialitza l'usuari un cop aquest s'ha autentificat
     * @param user usuari que s'hacaba d'autentificar satisfactoriament
     */
    public void setUser(User user) {
        this.user = user;
        controller.setUser(user);
        System.out.println("[NETWORK MANAGER]: Logged In");
    }

    /**
     * Inidica si es vol recordar el logIn
     * @return retorna si s'ha de recordar el logIn localment
     */
    public boolean rememberLogIn() {
        if(autoLogin)
            return true;
        return controller.rememberLogIn();
    }

    /**
     * Mostra un error amb una alerta al centre de la finestra grafica
     * @param title titol de l'alerta
     * @param errorText missatge de l'alerta
     */
    public void displayError(String title, String errorText){
        controller.displayError(title,errorText);
    }

    /**
     * Surt de la loadingScreen i mostra el menu dels jocs
     */
    public void enterToGames() {
        exitLoadingScreen();
        controller.showGamesView();
    }

    /**
     * Surt de la loadingScreen i mostra el menu principal
     */
    public void exitLoadingScreen() {
        //Com ja estem conectats al servidor, ja podem obrir la vista i tencar la SplashScreen
        splashScreen.exit();
        controller.showFinestra();
    }

    /**
     * Envia al servidor una petició de SignUp per a un usuari concret
     * @param user usuari que es vol registrar al casino
     */
    public void requestSignUp(User user) {

        //Si el client no esta connectat al servidor, es connecta
        if(!conectatAmbServidor){
            connectarAmbServidor();
        }
        if(conectatAmbServidor) {
            //Configurem el signIn i enviem la solicitud al servidor
            new Transmission( user, this);
        }else{
            displayError("Connection error","Our servers aren't available right now.\nTry it again later...");
        }
    }

    /**
     * Entra al casino com a guest
     */
    public void enterAsGuest() {
        if(!conectatAmbServidor){
            connectarAmbServidor();
        }

        if(conectatAmbServidor) {
            //Configurem el logIn i enviem la solicitud al servidor
            new Transmission( new User(), this);
        }else{
            displayError("Connection error","Our servers aren't available right now.\nTry it again later...");
        }
    }

    /**
     * Indica al servidor que es vol iniciar una nova partida del BlackJack.
     * Aquesta partida s'inicialitza amb una aposta inicial i una baralla de cartes
     * @param nomCartes nom de totes les cartes de la baralla
     * @param bet aposta que ha plantejat l'usuari
     */
    public boolean initBlackJack(Stack<String> nomCartes,long bet) {
        //Si l'aposta es correcte, aquesta sera diferent de 0
        if(bet != 0){
            //Es crea la carta que iniciara la partida i s'envia
            Card card = new Card("",bet,Transmission.CONTEXT_BJ_INIT,nomCartes,false);
            new Transmission(card,this);
            return false;
        }
        return true;
    }

    /**
     * Demana al servidor una nova carta per al BlackJack
     * @param forIa indica si la carta es per la IA o per l'usuari
     */
    public void newBlackJackCard(boolean forIa) {
        new Transmission(new Card("",Transmission.CONTEXT_BJ,forIa),this);
    }

    /**
     *  Pont transmitter - controlador - Model BlackJack
     *  En el cas que cartaResposta sigui la carta que ha iniciat la partida, es demanen 3 cartes mes.
     * @param cartaResposta carta rebuda que s'ha de processar en el joc BlackJack
     */
    public void newBJCard(Card cartaResposta) {

        if(cartaResposta.getContext().equals(Transmission.CONTEXT_BJ_INIT)){
            new Transmission(new Card("",Transmission.CONTEXT_BJ,false),this);
            new Transmission(new Card("",Transmission.CONTEXT_BJ,true),this);
            new Transmission(new Card("",Transmission.CONTEXT_BJ,true),this);
            controller.initBlackJack();
        }
        controller.newBJCard(cartaResposta);
    }

    /**
     * Gestiona les noves cartes per a la ia que es volen demanar al servidor
     */
    public void newCardForIaTurn() {
        Sounds.play("cardPlace1.wav");
        try {
            sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new Transmission(new Card("",CONTEXT_BJ_FINISH_USER,true),this);
    }

    /**
     * Mètode que inicia un Roulette Manager en el moment en el que l'usuari
     * intenta accedir a la ruleta.
     * @param c Controlador a iniciar.
     */
    public void initRoulette(RouletteController c) {
        rouletteManager = new RouletteManager(this);
        System.gc();
        rouletteManager.init(c);
    }

    /**
     * Mètode que desconnecta l'usuari del joc de la ruleta
     */
    public void endRoulette() {
        rouletteManager.disconnect();
    }

    /**
     * Metode per indicar al servidor de que volem jugar als cavalls
     */
    public void sendHorseRaceRequest() {
        HorseMessage horseMessage = new HorseMessage((HorseSchedule) null, "Connect");
        horseMessage.setID(user.getID());
        new Transmission(horseMessage, this);
    }

    /**
     * Metode que indica al servidor que ens desconectem de la cursa de cavalls
     */
    public void exitHorses() {
        HorseRaceController.exit();
        HorseMessage horseMessage = new HorseMessage((HorseSchedule) null, "Disconnect");
        horseMessage.setID(user.getID());
        new Transmission(horseMessage, this);
    }

    /**
     * Inicia una transmissio amb el servidor, solicitant la walletEvolution del usuari
     */
    public void getWalletEvolution() {
        new Transmission(new WalletEvolutionMessage(),this);
    }

    /**
     * Actualitza el valors de la taula on apareix l'evolucio dels diners de l'usuari
     * @param newWallet tots els valors que figuren a la taula
     */
    public void updateWalletEvolution(WalletEvolutionMessage newWallet) {
        controller.updateWalletEvolution(newWallet);
    }

    public void managePasswordChange(boolean result) {
        controller.manageChangePass(result);
    }

    public void transactionOK(int type){
        controller.transactionOK(type);
    }
    public void betToRoulette(RouletteBetMessage msg) {
        rouletteManager.bet(msg);
    }

    /**
     * Mètode que tanca el panell gràfic del joc de la ruleta i canvia la vista de
     * la finestra al menú principal de selecció de jocs
     */
    public void exitRoulette() {
        controller.endRouletteGraphics();
        showGamesView();
        System.gc();
    }

    /**
     * Quan es realitza una petició al servidor per a saber el valor monetari que
     * posseeix l'usuari, al respondre el servidor, s'executa aquesta funció per a
     * establir el valor de la cartera en el joc de la ruleta.
     * @param wallet Valor del moneder
     */
    public void setRouletteWallet(long wallet) {
        if (rouletteManager != null) rouletteManager.setWallet(wallet);
    }

    /**
     * El sistema d'actualització de la llista d'apostes i usuaris connectats es basa en
     * de part del servidor enviar el llistat actual cada cop que sigui necessari, i per
     * part de l'usuari, el que es fa consisteix en cada cert temps, comprovar si s'ha rebut
     * alguna actualització.
     *
     * Aquest mètode el que fa consisteix en cercar per tot el llistat de lectures realitzades
     * un missatge que contingui el context que indica que consisteix en una actualització del
     * llistat d'apostes.
     *
     * @return En cas de trobar una nova actualització s'envia el seu contingut, altrament val 'null'
     */
    public String[][] updateRouletteList() {
        String[][] info = null;

        for(int i = lectures.size()-1; i > 0; i--) {
            Message msg = lectures.get(i);
            if (msg.getContext().equals("rouletteListUpdate")) {
                info = ((BetList) msg).getInfo();
                lectures.remove(msg);
            }
        }

        return info;
    }

    /**
     * Finalitza tots els graphics oberts dels jocs
     */
    public void endGraphics() {
        controller.endRouletteGraphics();
    }

    /**
     * S'actualitza la llista d'apostes dels cavalls en cas d'haber rebut un missatge del servidor amb el contexte de
     * HORSES_ListUpdate
     * @return Array de Strings amb les apostes de tots els jugadors de la partida
     */
    public String[][] updateHorseList(String[][] info) {
        BetList msg = (BetList) readContext("HORSES-ListUpdate");
        if(msg != null) info = msg.getInfo();

        return info;
    }

    /**
     * S'actualitzen les monedes del client
     * @param wallet nova quantitat de monedes
     */
    public void updateWallet(long wallet) {
        this.user.setWallet(wallet);
    }

    /**
     * Mostra la vista central amb els diferents jocs de l'aplicacio
     */
    public void showGamesView() {
        controller.showGamesView();
    }
    /**Borra totes les dades del model del BJ per quan es surt d'aquest*/
    public void restartBlackJackModel(){
        controller.restartBlackJackModel();
    }

    /**
     * Mostra un missatge d'error a al finestra del sign in
     * @param message missatge a mostrar
     */
    public void signUpErrorMessage(String message) {
        controller.signUpErrorMessage(message);
    }
    /**
     * Metode que posa el missatge d'error que es passa per parametres visible
     * @param errorMessage String amb el missatge d'error que es vol mostrar
     */
    public void setLoginErrorMessage(String errorMessage) { controller.showErrorLogIn(errorMessage); }
}
