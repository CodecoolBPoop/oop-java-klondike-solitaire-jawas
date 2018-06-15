package com.codecool.klondike;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Game extends Pane {

    private List<Card> deck = new ArrayList<>();

    private Pile stockPile;
    private Pile discardPile;
    private List<Pile> foundationPiles = FXCollections.observableArrayList();
    private List<Pile> tableauPiles = FXCollections.observableArrayList();

    private double dragStartX, dragStartY;
    private List<Card> draggedCards = FXCollections.observableArrayList();

    private static double STOCK_GAP = 1;
    private static double FOUNDATION_GAP = 0;
    private static double TABLEAU_GAP = 30;

    private List<Stack> lastMoves = new ArrayList<>();

    private EventHandler<MouseEvent> onMouseClickedHandler = e -> {
        Card card = (Card) e.getSource();

        // 1. Checking conditions for double click move
        // 1.1. Checking if clicked twice with left mouse button
        Pile containingPile = card.getContainingPile();
        boolean isCorrectPileClicked = containingPile.getPileType() == Pile.PileType.TABLEAU || card.getContainingPile().getPileType() == Pile.PileType.DISCARD;

        MouseButton whichButton = e.getButton();
        boolean isLeftButton = whichButton.toString().equals("PRIMARY");

        Card topCard = card.getContainingPile().getTopCard();
        boolean isTopCardClicked = topCard.equals(card);

        int numberOfClicks = e.getClickCount();
        boolean isDoubleClick = numberOfClicks == 2;

        boolean doubleClickCondition = (isCorrectPileClicked && isDoubleClick && isLeftButton && isTopCardClicked);

        // 1.2. Checking foundation pile constraints for double click move
        boolean allowedMove = false;
        int allowedPileIndex = -1;
        for (int i = 0; i < foundationPiles.size(); i++) {
            allowedMove = isMoveValid(card, foundationPiles.get(i));
            if (allowedMove) {
                allowedPileIndex = i;
                break;
            }
        }

        Game thisGame = this;

        // Moving cards: if with single click, else if with double click
        if (card.getContainingPile().getPileType() == Pile.PileType.STOCK) {
            Stack storeItem = new Stack();

            List<Card> cards = new ArrayList<>();
            cards.add(card);
            storeItem.push(cards);
            storeItem.push(card.getContainingPile());
            lastMoves.add(storeItem);

            card.moveToPile(discardPile);
            card.flip();
            card.setMouseTransparent(false);
            System.out.println("Placed " + card + " to the waste.");
        } else if (doubleClickCondition && allowedMove) {
            List<Card> listOfCurrentCard = new ArrayList<>();
            listOfCurrentCard.add(card);
            MouseUtil.slideToDest(listOfCurrentCard, foundationPiles.get(allowedPileIndex), thisGame);
            card.isGameWon(thisGame);

        }

        // Automatic ending; single click triggered:
        boolean autoEndingCanBegin = canAutoEndingBegin();
        if (autoEndingCanBegin) {
            boolean isNotWonYet = true;
            while (isNotWonYet) {
                outer:
                for (int i = 0 ;i < foundationPiles.size(); i++) {
                    Pile foundationPile = foundationPiles.get(i);
                    for (int j = 0 ;j < tableauPiles.size(); j++) {
                        if (!tableauPiles.get(j).isEmpty()) {
                            Card topTableauCard = tableauPiles.get(j).getTopCard();
                            if (!tableauPiles.get(j).isEmpty() && isMoveValid(topTableauCard, foundationPile)) {
                                topTableauCard.moveToPile(foundationPile);
                                break outer;
                            }
                        }
                    }
                }
                isNotWonYet = autoWinCondition();
            }
            card.isGameWon(thisGame);
        }
    };

    private boolean autoWinCondition() {
        int[] pilesLengths = new int[4];
        for (int i = 0 ;i < foundationPiles.size(); i++) {
            pilesLengths[i] = foundationPiles.get(i).getCards().size();
        }
        boolean isNotWonYet = false;
        for (int length : pilesLengths) {
            if (length != 13) {
                isNotWonYet = true;
                break;
            }
        }
        return isNotWonYet;
    };


    private boolean canAutoEndingBegin() {
        // Auto Ending can begin when stock pile and discard pile is empty and all cards in tableau pile is face up
        boolean isWinState = true;
        if (stockPile.isEmpty() && discardPile.isEmpty()) {
            outer:
            for (Pile tableauPile : tableauPiles) {
                if (!tableauPile.isEmpty()) {
                    for (Card tableauCard : tableauPile.getCards()) {
                        if (tableauCard.isFaceDown()) {
                            isWinState = false;
                            break outer;
                        }
                    }
                }
            }
        } else {
            isWinState = false;
        }
        return isWinState;
    }

    public void dealWithCheat() {
        // move 39 cards to foundation piles
        int cardIndex = 0;
        for (int i = 0; i < foundationPiles.size() - 1; i++) {
            for (int j = 0; j < 13; j++) {
                foundationPiles.get(i).addCard(deck.get(cardIndex));
                addMouseEventHandlers(deck.get(cardIndex));
                getChildren().add(deck.get(cardIndex));
                deck.get(cardIndex).flip();
                cardIndex++;
            }
        }
        // move 6 cards to stock pile
        int CARD_INDEX_START1 = cardIndex;
        for (int i = CARD_INDEX_START1; i < CARD_INDEX_START1 + 6; i++) {
            stockPile.addCard(deck.get(i));
            addMouseEventHandlers(deck.get(i));
            getChildren().add(deck.get(i));
            cardIndex++;
        }
        // move 7 cards to tableau: one to each pile
        int CARD_INDEX_START2 = cardIndex;
        for (int i = 0; i < 7; i++) {
            tableauPiles.get(i).addCard(deck.get(cardIndex));
            addMouseEventHandlers(deck.get(cardIndex));
            getChildren().add(deck.get(cardIndex));
            deck.get(cardIndex).flip();
            cardIndex++;
        }

    }

    private EventHandler<MouseEvent> stockReverseCardsHandler = e -> {
        refillStockFromDiscard();
    };

    private EventHandler<MouseEvent> onMousePressedHandler = e -> {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
    };

    private EventHandler<MouseEvent> onMouseDraggedHandler = e -> {
        Card card = (Card) e.getSource();
        Pile activePile = card.getContainingPile();
        if (activePile.getPileType() == Pile.PileType.STOCK || card.isFaceDown())
            return;
        double offsetX = e.getSceneX() - dragStartX;
        double offsetY = e.getSceneY() - dragStartY;
        draggedCards.clear(); // error on dropping card from tableau pile to foundations
        for (int i = 0; i < activePile.getCards().size() ; i++) {
            if (activePile.getCards().get(i).equals(card)){
                for (int j = i; j < activePile.getCards().size(); j++) {
                    draggedCards.add(activePile.getCards().get(j));
                }
            }
        }
        for (Card draggedCard:draggedCards
             ) {
            draggedCard.getDropShadow().setRadius(20);
            draggedCard.getDropShadow().setOffsetX(10);
            draggedCard.getDropShadow().setOffsetY(10);

            draggedCard.toFront();
            draggedCard.setTranslateX(offsetX);
            draggedCard.setTranslateY(offsetY);
        }
    };

    private EventHandler<MouseEvent> onMouseReleasedHandler = e -> {
        if (draggedCards.isEmpty())
            return;
        Card card = (Card) e.getSource();
        Pile pile = getValidIntersectingPile(card, tableauPiles);

        if (pile == null) {pile = getValidIntersectingPile(card, foundationPiles);}
        if (pile != null) {
            handleValidMove(card, pile);
        } else {
            draggedCards.forEach(MouseUtil::slideBack);
            draggedCards.clear();
        }
    };

    public Game() {
        deck = Card.createNewDeck();
        initPiles();
        dealCards();
        posButton("Undo", 50,5, this.undo);
        posButton("Restart", 5,5, this.restart);
        cheatButton();
    }

    public void addMouseEventHandlers(Card card) {
        card.setOnMousePressed(onMousePressedHandler);
        card.setOnMouseDragged(onMouseDraggedHandler);
        card.setOnMouseReleased(onMouseReleasedHandler);
        card.setOnMouseClicked(onMouseClickedHandler);
    }

    public void refillStockFromDiscard() {
        if (stockPile.isEmpty()) {
            Pile tempPile = new Pile(Pile.PileType.STOCK, "Stock", STOCK_GAP);
            for (int i = discardPile.getCards().size() - 1; i >= 0; --i) {
                discardPile.getCards().get(i).flip();
                tempPile.addCard(discardPile.getCards().get(i));
            }
            Game thisGame = this;
            MouseUtil.slideToDest(tempPile.getCards(), stockPile, thisGame);
            discardPile = new Pile(Pile.PileType.DISCARD, "Discard", STOCK_GAP);
            discardPile.setBlurredBackground();
            discardPile.setLayoutX(285);
            discardPile.setLayoutY(20);
            getChildren().add(discardPile);
            lastMoves.clear();
            System.out.println("Stock refilled from discard pile.");
        }
    }

    public boolean isMoveValid(Card card, Pile destPile) {
        if (destPile.getPileType().equals(Pile.PileType.TABLEAU)){
            if (destPile.getTopCard() == null && card.getRank() == 13){
                return true;
            }else if (destPile.getTopCard() != null && (destPile.getTopCard().getRank() == card.getRank() + 1) && (card.isOppositeColor(card, destPile.getTopCard()))){
                return true;
            }
        }else{
            if ((destPile.getTopCard() == null) && card.getRank() == 1){
                return true;
            }else if (destPile.getTopCard() != null && (destPile.getTopCard().getRank() == card.getRank() - 1) && (card.getSuit() == destPile.getTopCard().getSuit())){
                return true;
            }
        }
        return false;
    }
    
    private Pile getValidIntersectingPile(Card card, List<Pile> piles) {
        Pile result = null;
        for (Pile pile : piles) {
            if (!pile.equals(card.getContainingPile()) &&
                    isOverPile(card, pile) &&
                    isMoveValid(card, pile))
                result = pile;

        }
        return result;
    }

    private boolean isOverPile(Card card, Pile pile) {
        if (pile.isEmpty())
            return card.getBoundsInParent().intersects(pile.getBoundsInParent());
        else
            return card.getBoundsInParent().intersects(pile.getTopCard().getBoundsInParent());
    }

    private void handleValidMove(Card card, Pile destPile) {
        String msg = null;
        if (destPile.isEmpty()) {
            if (destPile.getPileType().equals(Pile.PileType.FOUNDATION))
                msg = String.format("Placed %s to the foundation.", card);
            if (destPile.getPileType().equals(Pile.PileType.TABLEAU))
                msg = String.format("Placed %s to a new pile.", card);
        } else {
            msg = String.format("Placed %s to %s.", card, destPile.getTopCard());
        }

        Stack storeItem = new Stack();
        List<Card> cards = new ArrayList<>();;

        for(Card item : draggedCards) {
            cards.add(item);
        }


        storeItem.push(cards);
        storeItem.push(card.getContainingPile());
        lastMoves.add(storeItem);

        System.out.println(msg);
        Game thisGame = this;
        MouseUtil.slideToDest(draggedCards, destPile, thisGame);
        draggedCards.clear();
    }


    private void initPiles() {
        stockPile = new Pile(Pile.PileType.STOCK, "Stock", STOCK_GAP);
        stockPile.setBlurredBackground();
        stockPile.setLayoutX(95);
        stockPile.setLayoutY(20);
        stockPile.setOnMouseClicked(stockReverseCardsHandler);
        getChildren().add(stockPile);

        discardPile = new Pile(Pile.PileType.DISCARD, "Discard", STOCK_GAP);
        discardPile.setBlurredBackground();
        discardPile.setLayoutX(285);
        discardPile.setLayoutY(20);
        getChildren().add(discardPile);

        for (int i = 0; i < 4; i++) {
            Pile foundationPile = new Pile(Pile.PileType.FOUNDATION, "Foundation " + i, FOUNDATION_GAP);
            foundationPile.setBlurredBackground();
            foundationPile.setLayoutX(610 + i * 180);
            foundationPile.setLayoutY(20);
            foundationPiles.add(foundationPile);
            getChildren().add(foundationPile);
        }
        for (int i = 0; i < 7; i++) {
            Pile tableauPile = new Pile(Pile.PileType.TABLEAU, "Tableau " + i, TABLEAU_GAP);
            tableauPile.setBlurredBackground();
            tableauPile.setLayoutX(95 + i * 180);
            tableauPile.setLayoutY(275);
            tableauPiles.add(tableauPile);
            getChildren().add(tableauPile);
        }
    }

    public void dealCards() {
        int cardIndex = 0;
        for (int i = 0; i < tableauPiles.size(); i++) {
            for (int j = 0; j < i+1; j++) {
                tableauPiles.get(i).addCard(deck.get(cardIndex));
                addMouseEventHandlers(deck.get(cardIndex));
                getChildren().add(deck.get(cardIndex));
                if (j==i){deck.get(cardIndex).flip();}
                cardIndex++;
            }
        }
        for (int i = cardIndex; i < deck.size(); i++) {
            stockPile.addCard(deck.get(i));
            addMouseEventHandlers(deck.get(i));
            getChildren().add(deck.get(i));
        }
    }

    public void setTableBackground(Image tableBackground) {
        setBackground(new Background(new BackgroundImage(tableBackground,
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER, BackgroundSize.DEFAULT)));
    }

    private EventHandler<ActionEvent> restart = e -> {
        getChildren().clear();
        stockPile.clear();
        foundationPiles.clear();
        tableauPiles.clear();
        discardPile.clear();
        deck = Card.createNewDeck();
        lastMoves.clear();
        initPiles();
        dealCards();
        posButton("Undo", 50,5, this.undo);
        posButton("Restart", 5,5, this.restart);
        cheatButton();

    };

    private EventHandler<ActionEvent> undo = e -> {


        if (!lastMoves.isEmpty()) {
            Stack lastMove = lastMoves.get(lastMoves.size() - 1);
            lastMoves.remove(lastMoves.size() - 1);

            Pile pile = (Pile) lastMove.pop();
            List<Card> cards = (List<Card>) lastMove.peek();

            for (int i = 0; i < cards.size(); i++) {

                if (pile.getTopCard() != null && cards.get(i).getContainingPile().getPileType() == Pile.PileType.TABLEAU) {

                    if(pile.getTopCard().isFaceDown()) {
                        pile.getTopCard().flip();
                    }

                    if(cards.get(i).isFaceDown()) {
                        pile.getTopCard().flip();
                    }


                }

                cards.get(i).moveToPile(pile);

                if (cards.get(i).getContainingPile().getPileType() == Pile.PileType.STOCK) {
                    pile.getTopCard().flip();
                }
            }
        }

    };


    public void posButton(String value, int y, int x, EventHandler<ActionEvent> event) {
        Button btn = new Button(value);
        btn.setLayoutY(y);
        btn.setLayoutX(x);
        btn.setOnAction(event);
        getChildren().add(btn);
    }

    public List<Pile> getListOfPiles() {
        List<Pile> listOfPiles = new ArrayList<>();
        for (Pile tableauPile: tableauPiles) {
            listOfPiles.add(tableauPile);
        }
        listOfPiles.add(discardPile);
        listOfPiles.add(stockPile);
        return listOfPiles;
    }

    private EventHandler<ActionEvent> restartWithCheat = e -> {
        getChildren().clear();
        stockPile.clear();
        foundationPiles.clear();
        tableauPiles.clear();
        discardPile.clear();
        draggedCards.clear();
        Card.toggleCheat(false);
        deck = Card.createNewDeck();
        initPiles();
        dealWithCheat();
        posButton("Undo", 50,5, this.undo);
        posButton("Restart", 5,5, this.restart);
        cheatButton();
    };


    public void cheatButton() {
        Button btn = new Button("Cheat restart");
        btn.setLayoutY(700);
        btn.setLayoutX(5);
        btn.setOnAction(restartWithCheat);
        getChildren().add(btn);
    }

}
