package bguspl.set.ex;

import java.util.Vector;
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
    // Package private:
    Thread playerThread;

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
    private volatile int score;

    //NEW:
    private Dealer dealer;
    private Vector<Integer> tokens;
    private volatile boolean canNOTPlay = true;
    private long freezeTimer = Long.MAX_VALUE;
    private volatile boolean penaltyOrPointFreeze = false;;

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

        //NEW:
        this.dealer = dealer;
        tokens = new Vector<>();
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
           updateFreezeTimerDisplay();
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!) (kiddin...)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");    
            while (!terminate) {  
                if(!canNOTPlay & !penaltyOrPointFreeze){
                    //Reset:
                    if(tokens.size() != 0){
                            for(int i = 0 ; i < tokens.size() ; i++){
                               keyPressed(tokens.get(i));
                            }
                    }
                    delay();
                    // random Choose:
                    int[] rand = randomKeyPresses();
                        if(table.isLegitRequest(rand) && env.util.testSet(table.fromSlotsToCards(rand))){
                            for(int i = 0 ; i < 3 ; i++){
                                keyPressed(rand[i]);
                                synchronized(this){
                                    this.notifyAll();
                                }
                            }
                        }
                }
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true; 
        if(!human) try {aiThread.join();} catch (InterruptedException ignored) {}
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized(dealer){ 
            if(!canNOTPlay & !penaltyOrPointFreeze){
                if(tokens.contains(slot)){ 
                    tokens.removeElement(slot);
                    table.removeToken(id, slot); 
                }
                else if(tokens.size()<3 & table.slotToCard[slot] != null){
                    tokens.add(slot); 
                    table.placeToken(id, slot);
                    if(tokens.size() == 3){
                        penaltyOrPointFreeze = true;
                        int[] myRec = {tokens.get(0), tokens.get(1), tokens.get(2)}; 
                        dealer.addRequest(id, myRec);
                    }
                }
                dealer.notifyAll();
            }
            
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
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
        
        freezeTimer = System.currentTimeMillis() + env.config.pointFreezeMillis; 
        penaltyOrPointFreeze = true;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
        freezeTimer = System.currentTimeMillis() + env.config.penaltyFreezeMillis; 

        penaltyOrPointFreeze = true;
    }

    public synchronized int score() {
        return score;
    }

    //NEW:
    public void setCanPlay(boolean val){ //SYNC?
        canNOTPlay = val;
    }

    public synchronized int getTokensSize(){
        return tokens.size();
    }

    public synchronized void removeToken(int slot){
        tokens.removeElement(slot);
    }

    public synchronized void removeTokens(int[] slots){
        for(int i = 0 ; i < slots.length ; i++)
            removeToken(slots[i]);
    }

    public void resetTokens(){
        tokens = new Vector<>();
    }

    private synchronized void updateFreezeTimerDisplay(){        
        long time = freezeTimer - System.currentTimeMillis();
        if(penaltyOrPointFreeze & System.currentTimeMillis() < freezeTimer){
            env.ui.setFreeze(id, time%10000);
            penaltyOrPointFreeze = true;
        }
        if(time <= 0){
            env.ui.setFreeze(id, 0);
            penaltyOrPointFreeze = false;
            freezeTimer = Long.MAX_VALUE;
            try{
                this.wait();
            }catch(InterruptedException ignored){}
        }
    }

    private void delay(){
        try{
            Thread.sleep(200); 
        }catch(InterruptedException ignored){}
    }

    private int[] randomKeyPresses(){
        int[] rand = new int[3];
        for(int i = 0 ; i < 3 ; i++)
            rand[i] = (int)(Math.random()*12);
        return rand;
    }

    //For Tests:

    public synchronized boolean isPenaltyOrPointFreeze(){
        return penaltyOrPointFreeze;
    }

    public long getPenaltyFreezeMillis(){
        return env.config.penaltyFreezeMillis;
    }

    public boolean isASlotEmpty(int slot){
        return !tokens.contains(slot);
    }

    public synchronized boolean isTerminateTrue(){
        return terminate;
    }

    //Package private
    void setPlayerThread(Thread thread){
        playerThread = thread;
    }
}
