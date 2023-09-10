package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;


public class DealerTest {

    Dealer dealer;

    @Mock
    Util util;
    @Mock
    private MockUserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Player[] players;
    @Mock
    private MockLogger logger;

    void assertInvariants() {
        assertTrue(players.length > 0);
        assertTrue(table.countCards() >= 0);
    }

    @BeforeEach
    void setUp() {
        
        logger = new MockLogger();
        ui = new MockUserInterface();
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        table = new Table(env);
        players = new Player[2];
        players[0] = new Player(env, dealer, table, 0, false);
        players[1] = new Player(env, dealer, table, 1, true);      
        
        dealer = new Dealer(env, table, players);
        
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    private int[] createRequest() {
        int[] req = new int[3];
        for(int i = 0 ; i < 3 ; i++)
            req[i] = i;
        return req;
    }

    @Test
    void addRequest(){

        int[]req = createRequest();

        // call the method we are testing
        dealer.addRequest(0, req);

        assertEquals(false, dealer.isRequestsEmpty());

    }

    @Test 
    void actuallyRemovingTheCards(){

        int[] slots = fillSomeSlots();

         // call the method we are testing
         dealer.actuallyRemovingTheCards(slots);

         assertEquals(12, table.getEmptySlots().size());

    }

    private int[] fillSomeSlots() {
        table.slotToCard[1] = 3;
        table.slotToCard[2] = 5;
        table.slotToCard[3] = 7;
        table.cardToSlot[3] = 1;
        table.cardToSlot[5] = 2;
        table.cardToSlot[7] = 3;

        int[] slots = {1, 2, 3};
        return slots;
    }

    static class MockLogger extends Logger {
        protected MockLogger() {
            super("", null);
        }
    }

    static class MockUserInterface implements UserInterface {
        @Override
        public void dispose() {}
        @Override
        public void placeCard(int card, int slot) {}
        @Override
        public void removeCard(int slot) {}
        @Override
        public void setCountdown(long millies, boolean warn) {}
        @Override
        public void setElapsed(long millies) {}
        @Override
        public void setScore(int player, int score) {}
        @Override
        public void setFreeze(int player, long millies) {}
        @Override
        public void placeToken(int player, int slot) {}
        @Override
        public void removeTokens() {}
        @Override
        public void removeTokens(int slot) {}
        @Override
        public void removeToken(int player, int slot) {}
        @Override
        public void announceWinner(int[] players) {}
    };


}
