package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.Vector;
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

    //NEW:
    private Thread[] playersThreads;
    private PriorityQueue<int[][]> requests;

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

        //New:
        requests = new PriorityQueue<>(this);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        
        //Creating the player's threads:
        playersThreads = new Thread[players.length];
        for(int i = 0 ; i < playersThreads.length ; i++){
            playersThreads[i] = new Thread(players[i], "player" + players[i].id + ""); /////???
            playersThreads[i].start();
        }

        // Loop:
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        while (!terminate && System.currentTimeMillis() < reshuffleTime & !shouldFinish()){
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        for(int i = playersThreads.length-1 ; i >= 0 ; i--){
            players[i].terminate();
        }
        Thread.currentThread().interrupt();
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
    private synchronized void removeCardsFromTable() {
        if(requests.size() != 0){
            int[][] req = requests.take();
            int player = req[0][0];
            int[] slots = req[1];
            if(isLegitRequest(player)){
                synchronized(players[player]){ 
                    int[] cards = table.fromSlotsToCards(slots);
                    if(env.util.testSet(cards)){
                        players[player].point();
                        actuallyRemovingTheCards(slots);
                        updateTimerDisplay(true);
                    }else{ // illegal set
                        players[player].penalty();
                    }
                    players[player].notifyAll();
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private synchronized void placeCardsOnTable() {
        Vector<Integer> emptySlots = table.getEmptySlots();
        if(emptySlots.size() == 12) freezeAll(true);
        while(!emptySlots.isEmpty() & !deck.isEmpty()){
            int random = (int)(Math.random()*deck.size());
            int card = deck.remove(random);
            int slot = emptySlots.remove(0);
            table.placeCard(card, slot);
            delay();
        }
        freezeAll(false);
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() { 
       try {
        Thread.sleep(reshuffleTime%100);
        }catch (InterruptedException e) { }         
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset){
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        else{
            long time = reshuffleTime - System.currentTimeMillis();
            boolean warm = (time <= 5001);
            env.ui.setCountdown(time, warm);
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private synchronized void removeAllCardsFromTable() {
        freezeAll(true);
        // removing visually:
        for(int i = 0 ; i < 12 ; i++){
            table.removeCard(i);
            delay();
        }
        // removing the tokens from the player's queue:
        removeAllTokensFromPlayersQueue();
        freezeAll(false);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        env.ui.announceWinner(findWinners());
        terminate();
    }


    //NEW:
    public synchronized void addRequest(int playerId, int[] req){  
        int[][] newRequest = new int[2][];
        newRequest[0] = new int[1];
        newRequest[0][0] = playerId;
        newRequest[1] = req;
        requests.put(newRequest);       
    }

    private void freezeAll(boolean val){
        for(int i = 0 ; i < players.length ; i++){
            players[i].setCanPlay(val);
        }
    }

    private boolean isLegitRequest(int player){
        return players[player].getTokensSize() == 3;
    }

    protected void actuallyRemovingTheCards(int[] slots){
        // removing the tokens from the player's queue:
        for(int i = 0 ; i < players.length ; i++){
            players[i].removeTokens(slots);
        }
        // removing visually:
        synchronized(table){
            for(int i = 0 ; i < slots.length ; i++){
                table.removeCard(slots[i]);
                //delay();
            }
        }
    }

    private void removeAllTokensFromPlayersQueue(){
        for(int i = 0 ; i < players.length ; i++)
            players[i].resetTokens();
    }

    private void delay(){
        try{
            this.wait(env.config.tableDelayMillis); 
        }catch(InterruptedException ignored){}
    }

    private int[] findWinners(){
        int maxPoints = 0;
        int count = 0;
        // Finding max score and how many players has this score:
        for(int i = 0 ; i < players.length ; i++){
            if(players[i].score() == maxPoints){
                count++;
            }
            else if(players[i].score() > maxPoints){
                count = 1;
                maxPoints = players[i].score();
            }
        }

        // Creating winners' id array:
        int[] winners = new int[count];
        count = 0;
        for(int i = 0 ; i < players.length ; i++){
            if(players[i].score() == maxPoints){
                winners[count] = players[i].id;
                count++;
            }
        }

        return winners;
    }
    

    private class PriorityQueue<T>{

        private volatile Vector<T> vec;
        private final int MAX;
        private Dealer dealer;

        public PriorityQueue(Dealer dealer) {
            MAX = players.length;
            vec = new Vector<>();
            this.dealer = dealer;
        }

        public synchronized int size(){
            return vec.size();
        }

        public synchronized void put(T t){
            while(size()>=MAX){
                try{
                    this.wait();
                }catch(InterruptedException ignored){}
            }
            vec.add(t);
        }

        public synchronized T take(){
            while(size()==0){
                try{
                    this.wait();
                }catch(InterruptedException ignored){}
            }

            T t = vec.remove(0);
            dealer.notifyAll();
            return t;
        }

        @Override
        public String toString() {
            return "PriorityQueue []";
        }

    }

    //For Tests:
    public boolean isRequestsEmpty(){
        return requests.size() == 0;
    }

    
}
