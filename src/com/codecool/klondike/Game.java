package com.codecool.klondike;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Alert;
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
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

    private EventHandler<MouseEvent> onMouseClickedHandler = e -> {
        Card card = (Card) e.getSource();

        // 1. Checking conditions
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

        // 1.2. Checking foundation pile constraints
        boolean allowedMove = false;
        int allowedPileIndex = -1;
        for (int i = 0; i < foundationPiles.size(); i++) {
            allowedMove = isMoveValid(card, foundationPiles.get(i));
            if (allowedMove) {
                allowedPileIndex = i;
                break;
            }
        }
        // Moving cards
        if (card.getContainingPile().getPileType() == Pile.PileType.STOCK) {
            card.moveToPile(discardPile);
            card.flip();
            card.setMouseTransparent(false);
            System.out.println("Placed " + card + " to the waste.");
        } else if (doubleClickCondition && allowedMove) {
            card.moveToPile(foundationPiles.get(allowedPileIndex));
        }

        // AutoEnding:
        boolean AutoEndingCanBegin = canAutoEndingBegin();
        if (AutoEndingCanBegin) {
            boolean isNotWonYet = true;
            while (isNotWonYet) {
                // Validate move
                outer:
                for (int i = 0 ;i < foundationPiles.size(); i++) {
                    Pile foundationPile = foundationPiles.get(i);
                    for (int j = 0 ;j < tableauPiles.size(); j++) {
                        Card topTableauCard = tableauPiles.get(j).getTopCard();
                        if (isMoveValid(topTableauCard, foundationPile)) {
                            // and move cards with moveToPile after each other
                            topTableauCard.moveToPile(foundationPile);
                            // sol1.: just move them with movetopile
                            // sol2.: create myDraggedCards and rewrite handleValid move
                            break outer;
                        }
                    }
                }
                isNotWonYet = autoWinCondition();
            }
        }
    };

    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!! ------------------- !!!!!!!!!!!!!!!!!!!!!!!!!!!!
    private boolean canAutoEndingBegin() {
        // Auto Ending
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
        System.out.println(draggedCards);
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
        if (draggedCards.isEmpty()) // error
            return;
        Card card = (Card) e.getSource();
        Pile pile = getValidIntersectingPile(card, tableauPiles);

        //TODO
        if (pile == null) {pile = getValidIntersectingPile(card, foundationPiles);}
        if (pile != null) {
            handleValidMove(card, pile);
        } else {
            draggedCards.forEach(MouseUtil::slideBack);
            draggedCards.clear();
        }
    };


    public boolean isGameWon() {
        //TODO
        return false;
    }

    public Game() {
        deck = Card.createNewDeck();
        initPiles();
        dealCards();
        posButton();
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
            MouseUtil.slideToDest(tempPile.getCards(), stockPile);
            discardPile = new Pile(Pile.PileType.DISCARD, "Discard", STOCK_GAP);
            discardPile.setBlurredBackground();
            discardPile.setLayoutX(285);
            discardPile.setLayoutY(20);
            getChildren().add(discardPile);
            System.out.println("Stock refilled from discard pile.");
        }
    }

    public boolean isMoveValid(Card card, Pile destPile) {
        //TODO
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
        System.out.println(msg);
        MouseUtil.slideToDest(draggedCards, destPile);
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
        draggedCards.clear();
        deck = Card.createNewDeck();
        initPiles();
        dealCards();
        posButton();
    };


    public void posButton() {
        Button btn = new Button("Restart");
        btn.setLayoutY(5);
        btn.setLayoutX(5);
        btn.setOnAction(restart);
        getChildren().add(btn);
    }

}


