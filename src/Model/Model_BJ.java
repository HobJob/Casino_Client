package Model;

import java.util.LinkedList;

public class Model_BJ {


    public static final int MARGIN_BETWEEN_CARDS = 5;
    public static final int MARGIN_TOP = 10;
    public static final int MARGIN_BOTTOM = 10;
    public static final int MARGIN_CENTER = 100;
    public static final int CARD_WIDTH = 150;
    public static final int CARD_HEIGHT = 210;
    public static final int MAX_CARDS_IN_HAND = 6;

    private LinkedList<Card> userCards;
    private LinkedList<Card> IACards;

    public Model_BJ(){
        IACards = new LinkedList<>();
        userCards = new LinkedList<>();
    }

    public void addCard(Card card){
        if(card.isForIA()){
            //La carta es per la ia
            IACards.add(card);
        }else {
            //La carta es per a la persona
            userCards.add(card);
        }
    }

    public void giraIA() {
        for(Card card : IACards){
            card.setGirada(false);
        }
    }

    public LinkedList<Card> getIACards() {
        return IACards;
    }

    public LinkedList<Card> getUserCards() {
        return userCards;
    }

    public boolean IAHasCards() {
        return IACards.isEmpty();
    }
    public boolean userHasCards() {
        return userCards.isEmpty();
    }
}
