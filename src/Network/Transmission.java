package Network;

import Model.RouletteModel.RouletteBetMessage;
import Model.WalletEvolutionMessage;
import Utils.JsonManager;
import Model.Transaction;
import Model.User;
import Model.Card;
import Vista.MainFrame.Finestra;

import static java.lang.Thread.sleep;

/** Transmission gesiona el process de logIn del sistema*/

public class Transmission implements Runnable {

    /** Diferents contexts que poden adoptar els missatges*/
    public static final String CONTEXT_LOGIN = "login";
    public static final String CONTEXT_LOGIN_GUEST = "loginGuest";
    public static final String CONTEXT_LOGOUT = "logout";
    public static final String CONTEXT_SIGNUP = "signup";
    public static final String CONTEXT_BJ = "blackjack";
    public static final String CONTEXT_BJ_INIT = "blackjackinit";
    public static final String CONTEXT_BJ_FINISH_USER = "blackjackFinish";
    public static final String CONTEXT_TRANSACTION = "transaction";
    public static final String CONTEXT_DEPOSIT = "deposit";
    public static final String CONTEXT_WALLET_REQUEST = "walletRequest";
    public static final String CONTEXT_ROULETTE_BET = "rouletteBet";
    public static final String CONTEXT_WALLET_EVOLUTION = "walletEvolution";
    public static final String CONTEXT_CHANGE_PASSWORD = "change password";

    /** Missatge que es vol enviar*/
    private Message msg;

    /** Context del missatge que es vol enviar*/
    private String context;

    /** Referencia al networkManager per a poder enviar dades al servidor*/
    private NetworkManager networkManager;

    /**
     * Crea una nova transmissio basada en un missatge.
     * @param msg missatge que es vol enviar
     * @param networkManager dona la possibilitat a transmitter d'enviar i llegir missatges amb el servidor
     */
    public Transmission(Message msg, NetworkManager networkManager){
        this.networkManager = networkManager;
        this.msg = msg;
        this.context = msg.getContext();

        (new Thread(this)).start();
    }

    /** Segons el context del missatge es gestiona la seva solicitud i en algunes situacions s'espera la resposta del servidor*/
    @Override
    public void run() {

        switch (context) {

            case CONTEXT_LOGIN:
                if (!updateConnection()) networkManager.setLoginErrorMessage("⋙ Error, dades incorrectes");
                break;

            case CONTEXT_SIGNUP:
                if(!updateConnection()){
                    networkManager.signUpErrorMessage(((User)msg).getSignUpErrorReason());
                }

                break;

            case CONTEXT_LOGIN_GUEST:
                updateConnection();
                break;

            case CONTEXT_LOGOUT:
                System.out.println("[TRANSMISION]: sending log out");
                updateConnection();
                break;

            case CONTEXT_BJ_INIT:
            case CONTEXT_BJ:
            case CONTEXT_BJ_FINISH_USER:
                blackJackRequestCard();
                break;

            case CONTEXT_TRANSACTION:
                transaction();
                break;

            case CONTEXT_DEPOSIT:
                deposit();
                break;


            case CONTEXT_WALLET_EVOLUTION:
                walletEvolution();
                break;

            case CONTEXT_CHANGE_PASSWORD:
                changePassword();
                break;

            case CONTEXT_ROULETTE_BET:
                rouletteBet((RouletteBetMessage) msg);
                break;

            case CONTEXT_WALLET_REQUEST:
                networkManager.send(msg);
                this.networkManager.updateWallet(((User)msg).getWallet());
                requestWallet(msg);
                break;

            default:
                networkManager.send(msg);
        }
    }

    /**
     * Espera al servidor a que comuniqui el moneder de l'usuari
     * @param msg Missatge original
     */
    private void requestWallet(Message msg) {
        try {
            Message resp = waitResponse(msg);
            networkManager.setRouletteWallet(((User) resp).getWallet());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Solicita al servidor un canvi de contrasenya i gestiona la resposta del servidor a la solicitud
     */
    private void changePassword() {
        try {
            System.out.println("Enviada new password");
            networkManager.send(msg);

            User response = (User) waitResponse(msg);
            networkManager.managePasswordChange(response.areCredentialsOk());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Envia una solicitud al servidor per a l'evolucio monetaria de l'usuari.
     * Un cop el servidor repspon la solicitud, s'ompla la taula de l'evolucio monetaria en el menu de settings
     */
    private void walletEvolution() {
        try {
            networkManager.send(msg);

            WalletEvolutionMessage newWallet = (WalletEvolutionMessage) waitResponse(msg);
            networkManager.updateWalletEvolution(newWallet);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Mètode que s'executa al enviar una petició per apostar al tauler de la ruleta.
     * Primer s'envia el missatge i seguidament s'espera una resposta que indiqui si
     * l'aposta s'ha pogut o no realitzar. En cas d'haver-se realitzat satisfactoriament,
     * s'introdueix l'aposta al contingut gràfic de la partida; en cas contrari es mostra
     * un missatge d'error indicant que l'aposta ha estat denegada.
     * @param msg Missatge rebut
     */
    private void rouletteBet(RouletteBetMessage msg) {
        if (msg.getCellID() >= 0) networkManager.send(msg);

        try {
            msg = (RouletteBetMessage) waitResponse(msg);

            if (msg.isSuccessful()) networkManager.betToRoulette(msg);
            else Finestra.showDialog("Bet rejected", "Error");

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }



    /**
     * Envia una solicitud d'ingres i gestiona la resposta del servidor a aquest intent d'ingres monetari
     */
    private void deposit() {
        try {
            networkManager.send(msg);

            //La resposta del servidor
            Transaction response = (Transaction) waitResponse(msg);

            if(response.isTransactionOk()){
                networkManager.transactionOK(0);
            }else{
                if(response.getType() > 3){
                    networkManager.transactionOK(2);
                }else{
                    networkManager.transactionOK(1);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Envia una nova transacio al servidor i no espera cap resposta
     */
    private void transaction(){
        Transaction transaction = (Transaction) msg;
        //S'envia la transaccio al servidor
        networkManager.send(transaction);
    }

    /**
     * Demana una carta al servidor i l'afegeix al tauler del BlackJack
     */
    private void blackJackRequestCard() {
        try {
            Card carta = (Card) msg;
            //S'envia la carta
            networkManager.send(carta);

            //S'espera a la resposta del servidor
            Card cartaResposta = (Card) waitResponse(carta);

            //Si la carta resposta es una de les 4 inicials i la aposta proposada es incorrecte
            if(cartaResposta.getContext().equals(CONTEXT_BJ_INIT) && !cartaResposta.isBetOk()) {
                //Es mostra error
                networkManager.displayError("Money error","Impossible to place that bet!");
                networkManager.restartBlackJackModel();
                networkManager.showGamesView();
            }else{
                //S'afegeix la carta al model del joc
                networkManager.newBJCard(cartaResposta);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gestiona el logIn i el log out de l'usuari
     */
    private boolean updateConnection() {
        try {
            //Enviem l'usuari per intentar accedir al sistema amb les creedencials introduides
            User usuariIntent = (User) msg;

            //Set online indica al servidor que el paquet que rebrá no correspon a una desconexio d'un usuari
            usuariIntent.setOnline(true);
            networkManager.send(usuariIntent);

            //Esperem a una resposta del servidor
            User responseUser = (User) waitResponse(usuariIntent);

            //Si la resposta del servidor indica que tot es correcte. Es completa el logIn
            if(responseUser.areCredentialsOk()){
                finishUpdate(responseUser);

                return true;
            } else {
                //De lo contari, s'indica al usuari que s'ha equivocat
                ((User) msg).setSignUpErrorReason(responseUser.getSignUpErrorReason());
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Espera a que el servidor respongui una peticio. Es mira cada 100ms si ha arribat la resposta
     * @param SolicitudEnviada Missatge que s'ha enviat i s'espera una resposta
     * @return la resposta del servidor al missatge enviat
     * @throws InterruptedException excepcio provocada per l'interrupcio del sleep
     */

    public Message waitResponse(Message SolicitudEnviada) throws InterruptedException {
        Message resposta;

        do {
            //Es demana al networkManager la resposta del servidor associada a l'id del Message que s'ha enviat anteriorment.
            resposta = networkManager.read(SolicitudEnviada.getID());
            sleep(100);
        }while(resposta == null);

        return resposta;
    }

    /**
     *  Finalitza el logIn, actualitzant les dades de l'usuari. Tambe es gestiona la copia local del logIn
     * @param userVerificat usuari s'ha autentificat satisfactoriament
     */
    private void finishUpdate(User userVerificat){
        //S'actualitza el user
        networkManager.setUser(userVerificat);

        //En el cas de haver marcat la casella de recordar usuari
        if(networkManager.rememberLogIn()){
            //L'afegim al json
            JsonManager.addRemember(userVerificat.getUsername(),userVerificat.getPassword());
        }else{
            //El borrem del json si existeix
            JsonManager.removeRemember();
        }
        networkManager.enterToGames();
    }
}
