package bguspl.set.ex;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.logging.Level;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private Dealer dealer;

    private List<Integer> tokensplaced;

    private boolean keyBlock;

    private int slotPressed;

    private Queue<Integer> inputpresses;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.tokensplaced = new LinkedList<Integer>();
        this.keyBlock = false;
        inputpresses = new LinkedList<>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            if (!inputpresses.isEmpty()) {
                int slot = inputpresses.poll();
                handleKeyPress(slot);
            }

        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {

                Random rand = new Random();
                int rndslot = rand.nextInt(12);
                keyPressed(rndslot);

                //need to wait when queue size is 3
                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException ignored) {
                }
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    public List gettokensplaced() {
        return tokensplaced;
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
     if (inputpresses.size()<3 && !keyBlock) {
         inputpresses.add(slot);
     }
    }

    private void handleKeyPress(int slot) {
        if (tokensplaced.size() < 3) {
            if (tokensplaced.contains(slot)) {
                tokensplaced.remove((Object) slot);
                table.tokensonslot[slot].remove(this);
                env.ui.removeToken(this.id, slot);

            } else {
                tokensplaced.add(slot); //adding to the queue of the player tokens
                table.tokensonslot[slot].add(this); // adding the player to the table list of tokens placed on slots
                env.ui.placeToken(this.id, slot);
                if (tokensplaced.size() == 3) {
                    int[] cards = new int[3];
                    for (int i = 0; i < cards.length; i++) {
                        cards[i] = table.slotToCard[tokensplaced.get(i)];
                    }
                    keyBlock = true;
                    dealer.examine(cards, id);
                    keyBlock = false;
                }
            }
        } else if (tokensplaced.size() == 3 && tokensplaced.contains(slot)) {
            tokensplaced.remove((Object) slot);
            table.tokensonslot[slot].remove(this);
            env.ui.removeToken(this.id, slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty(long sleeptime) {
        try {
            Thread.sleep(sleeptime);
        } catch (InterruptedException e){
        };
    }


    public int getScore() {
        return score;
    }
}
