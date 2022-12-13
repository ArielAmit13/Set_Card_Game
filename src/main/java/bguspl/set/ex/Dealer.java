package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UserInterfaceImpl;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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

    private Thread[] threads;

    private Queue<Integer[]> SetsToTest;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        threads = new Thread[players.length];
        SetsToTest = new LinkedList<>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
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
        reshuffleTime = System.currentTimeMillis()+env.config.turnTimeoutMillis;
        env.ui.setCountdown(env.config.turnTimeoutMillis,false);
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            updateTimerDisplay(reshuffleTime - System.currentTimeMillis()<env.config.turnTimeoutWarningMillis);
            sleepUntilWokenOrTimeout();
            if (!SetsToTest.isEmpty()) {
                examine(SetsToTest.poll());
            }

        }
    }
    public synchronized void HandleTest (int[] cards, int id) { // queue of sets to test by dealer
        Integer[] Set = new Integer[4];
        Set[0] = id;
        for (int i=1;i<Set.length;i++) {
            Set[i] = cards[i-1];
        }
        SetsToTest.add(Set);
        notifyAll();
    }
    private void examine(Integer[] Set) { //handle the test of the set and its outcomes
        int playerid = Set[0];
        int[] cards = new int[3];
        for (int i=0;i<cards.length;i++) {
            cards[i]=Set[i+1];
        }
        boolean isSet = env.util.testSet(cards);
        if (isSet) {
            removeCardsFromTable(cards); //removing the three cards of the set
            placeCardsOnTable();
            players[playerid].SetSleep(env.config.pointFreezeMillis);

        } else {
                players[playerid].SetSleep(env.config.penaltyFreezeMillis);
        }

        if (isSet) {
            reshuffleTime = System.currentTimeMillis() + 60000;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
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
    private void removeCardsFromTable(int [] cards) {
        for (int i = 0; i < cards.length; i++) {
            int slot = table.cardToSlot[cards[i]];
            table.removeCard(slot);
            env.ui.removeTokens(slot);
            env.ui.removeCard(slot);
            for (Player p : table.tokensonslot[slot]) { //removing the token on slot i from players queue
                for (Integer[] Set: SetsToTest) { // if the player sent a set to test with this card, we remove the set from the queue
                    if(Set[0]==p.id) SetsToTest.remove(Set);
                }
                p.gettokensplaced().remove((Object)slot);
            }
            table.tokensonslot[slot].clear();
        }
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
                for (Player p : table.tokensonslot[i]) { //removing the token on slot i from players queue
                      p.gettokensplaced().remove((Object)i);
                }
                table.tokensonslot[i].clear(); // cleaning the slot in table tokens on slot from tokens
            }
        }

        }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int[]playersId = new int[players.length];
        for (int i=0; i<players.length;i++) {
            playersId[i] = players[i].id;
        }
        env.ui.announceWinner(playersId);
    }
}
