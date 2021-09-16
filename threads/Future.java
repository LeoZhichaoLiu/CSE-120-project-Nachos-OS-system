package nachos.threads;

import java.util.*;
import java.util.function.IntSupplier;
import nachos.machine.*;

/**
 * A <i>Future</i> is a convenient mechanism for using asynchonous
 * operations.
 */
public class Future {

    private IntSupplier func;
    private boolean hasVal;
    private int val;

    private Lock lock;
    private Condition cond;

    /**
     * Instantiate a new <i>Future</i>.  The <i>Future</i> will invoke
     * the supplied <i>function</i> asynchronously in a KThread.  In
     * particular, the constructor should not block as a consequence
     * of invoking <i>function</i>.
     */
    public Future (IntSupplier function) {
        func = function;
        hasVal = false;
        lock = new Lock();
        cond = new Condition(lock);

        // Invoke the function here. Wake up all the threads when the function returns.
        lock.acquire();
        val = func.getAsInt();
        hasVal = true;
        cond.wakeAll();
        lock.release();
    }

    /**
     * Return the result of invoking the <i>function</i> passed in to
     * the <i>Future</i> when it was created.  If the function has not
     * completed when <i>get</i> is invoked, then the caller is
     * blocked.  If the function has completed, then <i>get</i>
     * returns the result of the function.  Note that <i>get</i> may
     * be called any number of times (potentially by multiple
     * threads), and it should always return the same value.
     */
    public int get () {
        lock.acquire();
        
        // If the function has returned, just return the value
        if(hasVal) {
            lock.release();
            return val;
        }

        // otherwise wait for the function to return
        cond.sleep();

        lock.release();
        return val;
    }
    
    private static void selfTest1() {
        Future future = new Future(new IntSupplier(){
            public int getAsInt() {
                ThreadedKernel.alarm.waitUntil(1000000);
                return 1;
            }
        });
        KThread t1 = new KThread(new Runnable(){
            public void run(){
                System.out.println("t1 enters and waits");
                int r1 = future.get();
                System.out.println("The return value from t1 is "+r1);
            }
        }).setName("t1");
        t1.fork();
        KThread t2 = new KThread(new Runnable(){
            public void run(){
                System.out.println("t2 enters and waits");
                int r1 = future.get();
                System.out.println("The return value from t2 is "+r1);
            }
        }).setName("t2");
        t2.fork();
        KThread t3 = new KThread(new Runnable(){
            public void run(){
                System.out.println("t3 enters and waits");
                int r1 = future.get();
                System.out.println("The return value from t3 is "+r1);
            }
        }).setName("t3");
        t3.fork();

        ThreadedKernel.alarm.waitUntil(1000000);
        KThread t4 = new KThread(new Runnable(){
            public void run(){
                System.out.println("t4 enters and waits");
                int r1 = future.get();
                System.out.println("The return value from t4 is "+r1);
            }
        }).setName("t4");
        t4.fork();

        t1.join();
        t2.join();
        t3.join();
        t4.join();
        System.out.println("Future test 1 ended.");
    }

    public static void selfTest() {
        System.out.println("Testing Future : test1 ---------------------------------------");
        selfTest1();
        System.out.println("Future tests ended!");
    }
}
