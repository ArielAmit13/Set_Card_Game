package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UserInterfaceImpl;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        Thread[] threads = new Thread[players.length];
        boolean canstart=false;
        do {
            placeCardsOnTable();
            if (!canstart) { //initializing it once
                for (int i=0;i<players.length;i++) { //initialize the threads
                    threads[i] = new Thread(players[i]);
                    threads[i].start();
                }
                canstart = true;
            }
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        } while (!shouldFinish());
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis()+60000;
        env.ui.setCountdown(env.config.turnTimeoutMillis,false);
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            updateTimerDisplay(reshuffleTime - System.currentTimeMillis()<env.config.turnTimeoutWarningMillis);
            sleepUntilWokenOrTimeout();
//            removeCardsFromTable();
//            placeCardsOnTable();
        }
    }
    public void  examine(int[]cards , int id) {
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        for (int i=0;i<12;i++) {
            if(table.slotToCard[i]==null && !(deck.isEmpty())) {
                Random rand = new Random();
                int rnd = rand.nextInt(deck.size());
                table.placeCard(deck.get(rnd), i);
                env.ui.placeCard(deck.get(rnd),i);
                deck.remove(rnd);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
       int tosleep=1000;
       if (reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutMillis)
           tosleep=20;
        try{
           Thread.sleep(tosleep);
      } catch (InterruptedException e){};
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(),reset);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i=0;i<table.slotToCard.length;i++) {
            if (table.slotToCard[i] != null) {
                int card = table.slotToCard[i];
                table.removeCard(i);
                deck.add(card);
                env.ui.removeTokens(i);
                env.ui.removeCard(i);
                //update UI
                for (Player p : table.tokensonslot[i]) { //removing the token on slot i from players queue
                    p.gettokensplaced().remove(i);
                }
                table.tokensonslot[i] = null; // cleaning the slot in table tokens on slot from tokens
            }
        }

        }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }
}
