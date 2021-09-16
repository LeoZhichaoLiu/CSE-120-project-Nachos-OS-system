package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * A <i>GameMatch</i> groups together player threads of the same
 * ability into fixed-sized groups to play matches with each other.
 * Implement the class <i>GameMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into groups.
 */
public class GameMatch {

    /* Three levels of player ability. */
    public static final int abilityBeginner = 1,
	abilityIntermediate = 2,
	abilityExpert = 3;

    // Lock and condition for each level
    private int numPlayers;
    private Lock lock;
    private Condition cv1;
    private Condition cv2;
    private Condition cv3;

    // Count the number of waiting players in each level
    private int numOfOne = 0;
    private int numOfTwo = 0;
    private int numOfThree = 0;

    // Use link list to store the team mates for each level.
    private LinkedList<KThread> beginner_list;
    private LinkedList<KThread> intermediate_list;
    private LinkedList<KThread> expert_list;

    // record the match number in global.
    private int matchNum = 0;

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {

        numPlayers = numPlayersInMatch;

        lock = new Lock();
        cv1 = new Condition(lock);
        cv2 = new Condition(lock);
        cv3 = new Condition(lock);

        beginner_list = new LinkedList<KThread>();
        intermediate_list = new LinkedList<KThread>();
        expert_list = new LinkedList<KThread>();
    }

    /**
     * Wait for the required number of player threads of the same
     * ability to form a game match, and only return when a game match
     * is formed.  Many matches may be formed over time, but any one
     * player thread can be assigned to only one match.
     *
     * Returns the match number of the formed match.  The first match
     * returned has match number 1, and every subsequent match
     * increments the match number by one, independent of ability.  No
     * two matches should have the same match number, match numbers
     * should be strictly monotonically increasing, and there should
     * be no gaps between match numbers.
     *
     * @param ability should be one of abilityBeginner, abilityIntermediate,
     * or abilityExpert; return -1 otherwise.
     */
    public int play (int ability) {

        lock.acquire();

        // The first case is when player is beginner.
        if (ability == abilityBeginner) {

            // We increment the numbers of beginner players, and add the thread into begin list.
            numOfOne++;
            beginner_list.add(KThread.currentThread());

            // If there are enough beginners, wake up every sleeping beginners.
            if (numOfOne == numPlayers) {
                cv1.wakeAll();
                numOfOne = 0;

                // We count the match number, and set the match number inside thread.
                matchNum++;
                KThread.currentThread().setMatch(matchNum);

                // And we iterate all its team mates, set the match number and remove each item.
                while (!beginner_list.isEmpty()) {
                    beginner_list.removeFirst().setMatch(matchNum);
                }

            // If there are no enough, we just sleep that beginner.
            } else {
                cv1.sleep();
            }

            // When return back, release the lock and return the match number.
            lock.release();
            return KThread.currentThread().getMatch();

        // The second case is when player is intermdeiate.
        } else if (ability == abilityIntermediate) {

            // We increment the numbers of middle players, and add the thread into intermediate list.
            numOfTwo++;
            intermediate_list.add(KThread.currentThread());

            // wake up every sleeping players when there are enough people, else sleep.
            if (numOfTwo == numPlayers) {
                cv2.wakeAll();
                numOfTwo = 0;

                // We count the match number, and set the match number inside thread.
                matchNum++;
                KThread.currentThread().setMatch(matchNum);

                // And we iterate all its team mates, set the match number and remove each item.
                while (!intermediate_list.isEmpty()) {
                    intermediate_list.removeFirst().setMatch(matchNum);
                }

            } else {
                 cv2.sleep();
            }

            // Release the lock for each player, and return the updated match number.
            lock.release();
            return KThread.currentThread().getMatch();

        // The third case is when player is the expert.
        } else if (ability == abilityExpert) {

            // We increment the numbers of expert players, and add the thread into expert list.
            numOfThree++;
            expert_list.add(KThread.currentThread());

            // Wake up every sleeping player when there are enough people, else sleep.
            if (numOfThree == numPlayers) {
                cv3.wakeAll();
                numOfThree = 0;

                // We count the match number, and set the match number inside thread.
                matchNum++;
                KThread.currentThread().setMatch(matchNum);

                // And we iterate all its team mates, set the match number and remove each item.
                while (!expert_list.isEmpty()) {
                    expert_list.removeFirst().setMatch(matchNum);
                }

            } else {
                cv3.sleep();
            }

            // Release the lock for each player, and return the updated match number.
            lock.release();
            return KThread.currentThread().getMatch();
        }

        // If the player's ability is not defined within range, return -1.
        lock.release();
        return -1;
    }

    // Test ability error
	private static void matchTest1() {
        final GameMatch match = new GameMatch(2);
        KThread thread0 = new KThread(new Runnable(){
            public void run(){
                int r = match.play(0);
                System.out.println("Get "+r+" since ability is initialized as 0");
                r = match.play(4);
                System.out.println("Get "+r+" since ability is initialized as 4");
            }
        }).setName("thread0");
        thread0.fork();
        thread0.join();
    }

    // Test arranging different ability levels
	private static void matchTest2() {
	        final GameMatch match = new GameMatch(1);
	        KThread exp1 = new KThread(new Runnable(){
	            public void run(){
	                int r = match.play(GameMatch.abilityExpert);
	                System.out.println("Game "+r+" is matched for exp1");
	            }
	        }).setName("exp1");
	        exp1.fork();
	        KThread exp2 = new KThread(new Runnable(){
	            public void run(){
	                int r = match.play(GameMatch.abilityExpert);
	                System.out.println("Game "+r+" is matched for exp2");
	            }
	        }).setName("exp2");
	        exp2.fork();
	        KThread int1 = new KThread(new Runnable(){
	            public void run(){
	                int r = match.play(GameMatch.abilityIntermediate);
	                System.out.println("Game "+r+" is matched for int1");
	            }
	        }).setName("int1");
	        int1.fork();
	        KThread beg1 = new KThread(new Runnable(){
	            public void run(){
	                int r = match.play(GameMatch.abilityBeginner);
	                System.out.println("Game "+r+" is matched for beg1");
	            }
	        }).setName("beg1");
	        beg1.fork();
	        exp1.join();
	        exp2.join();
	        int1.join();
	        beg1.join();
	        System.out.println("Arranged matches for different threads and different ability levels");
	    }
    
    // Place GameMatch test code inside of the GameMatch class.
    public static void matchTest4 () {
        final GameMatch match = new GameMatch(2);

        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            } });

        beg1.setName("B1");

        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            } });
        beg2.setName("B2");

        KThread int1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                Lib.assertNotReached("int1 should not have matched!");
                }
            });
        int1.setName("I1");

        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                Lib.assertNotReached("exp1 should not have matched!");
            }});
        exp1.setName("E1");

        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        int1.fork();
        exp1.fork();
        beg2.fork();

        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 10; i++) {
             KThread.currentThread().yield();
        }
    }

    // Multiple GameMatch instances, complicated conditions
    public static void matchTest3() {
        final GameMatch match1 = new GameMatch(3);
        final GameMatch match2 = new GameMatch(2);
        System.out.println("The following runs two threads, exp1, 2, 6 should play in Game 1 of match1");
        System.out.println("beg1, 2 should play in Game 1 of match2, exp3, 4 should play in Game 2 of match2, exp5, 7 should play in Game 3 of match3");
        KThread exp1 = new KThread(new Runnable(){
            public void run(){
                System.out.println("exp1 enters and waits");
                int r1 = match1.play(GameMatch.abilityExpert);
                System.out.println("Game "+r1+" is matched for thread exp1");
            }
        }).setName("exp1");
        exp1.fork();
        KThread exp2 = new KThread(new Runnable(){
            public void run(){
                System.out.println("exp2 enters and waits");
                int r1 = match1.play(GameMatch.abilityExpert);
                System.out.println("Game "+r1+" is matched for thread exp2");
            }
        }).setName("exp2");
        exp2.fork();
        KThread beg1 = new KThread(new Runnable(){
            public void run(){
                System.out.println("beg1 enters and waits");
                int r1 = match2.play(GameMatch.abilityBeginner);
                System.out.println("Game "+r1+" is matched for thread beg1");
            }
        }).setName("beg1");
        beg1.fork();
        KThread exp3 = new KThread(new Runnable(){
            public void run(){
                System.out.println("exp3 enters and waits");
                int r1 = match2.play(GameMatch.abilityExpert);
                System.out.println("Game "+r1+" is matched for thread exp3");
            }
        }).setName("exp3");
        exp3.fork();
        KThread beg2 = new KThread(new Runnable(){
            public void run(){
                System.out.println("beg2 enters and waits");
                int r1 = match2.play(GameMatch.abilityBeginner);
                System.out.println("Game "+r1+" is matched for thread beg2");
            }
        }).setName("beg2");
        beg2.fork();
        KThread exp4 = new KThread(new Runnable(){
            public void run(){
                System.out.println("exp4 enters and waits");
                int r1 = match2.play(GameMatch.abilityExpert);
                System.out.println("Game "+r1+" is matched for thread exp4");
            }
        }).setName("exp4");
        exp4.fork();
        KThread exp5 = new KThread(new Runnable(){
            public void run(){
                System.out.println("exp5 enters and waits");
                int r1 = match2.play(GameMatch.abilityExpert);
                System.out.println("Game "+r1+" is matched for thread exp5");
            }
        }).setName("exp5");
        exp5.fork();
        KThread exp6 = new KThread(new Runnable(){
            public void run(){
                System.out.println("exp6 enters and waits");
                int r1 = match1.play(GameMatch.abilityExpert);
                System.out.println("Game "+r1+" is matched for thread exp6");
            }
        }).setName("exp6");
        exp6.fork();
        KThread exp7 = new KThread(new Runnable(){
            public void run(){
                System.out.println("exp7 enters and waits");
                int r1 = match2.play(GameMatch.abilityExpert);
                System.out.println("Game "+r1+" is matched for thread exp7");
            }
        }).setName("exp7");
        exp7.fork();

        exp1.join();
        exp2.join();
        beg1.join();
        exp3.join();
        beg2.join();
        exp4.join();
        exp5.join();
        exp6.join();
        exp7.join();
    }

    public static void selfTest() {
		System.out.println("Game_Match_Test1 ---------------------------------------");
        matchTest1();
		System.out.println("Game_Match_Test2 ---------------------------------------");
        matchTest2();
		System.out.println("Game_Match_Test3 ---------------------------------------");
        matchTest3();
		System.out.println("Game_Match_Test4 ---------------------------------------");
        matchTest4();
	}
}
