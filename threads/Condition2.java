package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 *
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;

		// We initialize the queue to store the sleeping thread.
		waitQueue = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		// We add the current threadd into the wait queue. And release the lock.
		waitQueue.add(KThread.currentThread());
		conditionLock.release();

		// We make currentThread to sleep.
        KThread.currentThread().sleep();

		// When the current thread waked up, it will automatically acquire the lock.
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		// We try to wake up the first thread in the queue.
		if(!waitQueue.isEmpty()){

			// We get the first thread, if it hasn't sleepfor by timer, we make it ready.
			KThread thread = waitQueue.removeFirst();
			if (thread.getStatus() == false) {
				thread.ready();

			// If it is called by sleepfor, and it hasn't waked up by timer, we cancel it.
			} else if (ThreadedKernel.alarm.cancel (thread)) {
				// The thread is free, so not called by sleepFor anyway.
				thread.setStatus(false);
			}
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		// We try to wake up all the thread in the queue.
		while(!waitQueue.isEmpty()){

			// We get the first thread, if it hasn't sleepfor by timer, we make it ready.
			KThread thread = waitQueue.removeFirst();
			if (thread.getStatus() == false) {
				thread.ready();

			// If it is called by sleepfor, and it hasn't waked up by timer, we cancel it.
			} else if (ThreadedKernel.alarm.cancel (thread)) {
				// The thread is free, so not called by sleepFor anyway.
				thread.setStatus(false);
			}
		}
		Machine.interrupt().restore(intStatus);
	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
    public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		// We add that thread into the wait queue, and release the lock.
		waitQueue.add(KThread.currentThread());
		conditionLock.release();

		// We set the "inAlarm" to true, meaning we call sleepFor on this thread.
		KThread.currentThread().setStatus(true);

		// Rather than directly sleep it, we call waitUntil to sleep it by time.
        ThreadedKernel.alarm.waitUntil (timeout);

		//When it was waked up, we automatically acquire the lock.
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

    private Lock conditionLock;
	private LinkedList<KThread> waitQueue;

	private static class InterlockTest {
		private static Lock lock;
		private static Condition2 cv;
		private static class Interlocker implements Runnable {
			public void run () {
				lock.acquire();
				for (int i = 0; i < 10; i++) {
					System.out.println(KThread.currentThread().getName());
					cv.wake();
					cv.sleep();
				}
				lock.release();
			}
		}
		public InterlockTest () {
			lock = new Lock();
			cv = new Condition2(lock);
			KThread ping = new KThread(new Interlocker());
			ping.setName("ping");
			KThread pong = new KThread(new Interlocker());
			pong.setName("pong");
			ping.fork();
			pong.fork();
			 for (int i = 0; i < 50; i++) {
				 KThread.currentThread().yield();
			 }
		 }
	 }

	 // Place Condition2 test code inside of the Condition2 class.
	 // Test programs should have exactly the same behavior with the
	 // Condition and Condition2 classes.  You can first try a test with
	 // Condition, which is already provided for you, and then try it
	 // with Condition2, which you are implementing, and compare their
	 // behavior.
	 // Do not use this test program as your first Condition2 test.
	 // First test it with more basic test programs to verify specific
	 // functionality.
	 public static void cvTest5() {
		 final Lock lock = new Lock();
		 // final Condition empty = new Condition(lock);
		 final Condition2 empty = new Condition2(lock);
		 final LinkedList<Integer> list = new LinkedList<>();
		 KThread consumer = new KThread( new Runnable () {
			 public void run() {
				 lock.acquire();
				 while(list.isEmpty()){
					 empty.sleep();
					}
					Lib.assertTrue(list.size() == 5, "List should have 5 values.");
					while(!list.isEmpty()) {
						// context swith for the fun of it
						KThread.currentThread().yield();
						System.out.println("Removed " + list.removeFirst());
					}
					lock.release();
				}
			});
			KThread producer = new KThread( new Runnable () {
				public void run() {
					lock.acquire();
					for (int i = 0; i < 5; i++) {
						list.add(i);
						System.out.println("Added " + i);
						// context swith for the fun of it
						KThread.currentThread().yield();
					}
					empty.wake();
					lock.release();
				}
			});
			consumer.setName("Consumer");
			producer.setName("Producer");
			consumer.fork();
			producer.fork();
			// We need to wait for the consumer and producer to finish,
			// and the proper way to do so is to join on them.  For this
			// to work, join must be implemented.  If you have not
			// implemented join yet, then comment out the calls to join
			// and instead uncomment the loop with yield; the loop has the
			// same effect, but is a kludgy way to do it.
        consumer.join();
		producer.join();
		//for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}

	// No lock expect error
	private static void Condition2Test3() {
		final Lock no_lock = new Lock();
		final Condition2 nolock = new Condition2(no_lock);
		KThread nl = new KThread( new Runnable() {
			public void run() {
				try{
					nolock.sleep();
					nolock.wake();
				} catch (Throwable e) {
					System.out.println("Catched no lock error");
				}

			}
		});
		nl.setName("Nolock");
		nl.fork();
		nl.join();
	}

	// Multiple threads test with complicated wake, wakeall, sleep
	private static void Condition2Test4() {
		final Lock lock2 = new Lock();
		final Condition2 cv2 = new Condition2(lock2);

		KThread thread0 = new KThread(new Runnable(){
            public void run(){
                lock2.acquire();
                System.out.println("Thread 0 before wake");
                cv2.wake();
				System.out.println("Thread 0 after wake");
				lock2.release();
        	}
    	}).setName("thread0");
		thread0.fork();

		KThread thread4 = new KThread(new Runnable(){
            public void run(){
                lock2.acquire();
                System.out.println("Thread 4 before sleep");
                cv2.sleep();
				System.out.println("Thread 4 after sleep");
				lock2.release();
        	}
    	}).setName("thread4");
		thread4.fork();

		KThread thread1 = new KThread(new Runnable(){
            public void run(){
                lock2.acquire();
                System.out.println("Thread 1 before sleep");
                cv2.sleep();
                System.out.println("Thread 1 after sleep");
				lock2.release();
				}
		}).setName("thread1");
		thread1.fork();

		KThread thread2 = new KThread(new Runnable(){
            public void run(){
                lock2.acquire();
                System.out.println("Thread 2 before sleep");
                cv2.sleep();
                System.out.println("Thread 2 after sleep");
				lock2.release();
				}
    	}).setName("thread2");
		thread2.fork();

		KThread thread5 = new KThread(new Runnable(){
            public void run(){
                lock2.acquire();
                System.out.println("Thread 5 before wake");
                cv2.wake();
				System.out.println("Thread 5 after wake");
				lock2.release();
        	}
    	}).setName("thread5");
		thread5.fork();
		thread4.join();


		KThread thread3 = new KThread(new Runnable(){
            public void run(){
                lock2.acquire();
                System.out.println("Thread 3 before wakeall");
                cv2.wakeAll();
                System.out.println("Thread 3 after wakeall");
				lock2.release();}
    	}).setName("thread3");
		thread3.fork();
		thread1.join();
		thread2.join();
		System.out.println("Expected wakeup order: thread 0, 5, 4, 3, 1, 2");

	}

	 public static void selfTest() {
		System.out.println("Condition2_Test1 ---------------------------------------");
		new InterlockTest();

		System.out.println("Condition2_Test2 ---------------------------------------");
		cvTest5();

		System.out.println("Condition2_Test3 ---------------------------------------");
		Condition2Test3();

		System.out.println("Condition2_Test4 ---------------------------------------");
		Condition2Test4();

		System.out.println("Sleep_For_Test1 ----------------------------------------");
		sleepForTest1();

		System.out.println("Sleep_For_Test2 ----------------------------------------");
		sleepForTest2();

		System.out.println("Sleep_For_Test3 ----------------------------------------");
		sleepForTest3();

		System.out.println("Sleep_For_Test4 ----------------------------------------");
		sleepForTest4();
	}

	// Place sleepFor test code inside of the Condition2 class.
	private static void sleepForTest1 () {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
		         " woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}

	// test sleepfor sanity
	private static void sleepForTest2 () {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		lock.acquire();
		KThread thread1 = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				System.out.println ("Entering thread 1");
				cv.wake();
				lock.release();
			}
		});
        thread1.setName("Thread_1");
		thread1.fork();

		long t0 = Machine.timer().getTime();
		System.out.println (t0);
		System.out.println (KThread.currentThread().getName() + " sleeping");
		cv.sleepFor(2000);

		long t1 = Machine.timer().getTime();
		System.out.println (t1);
		System.out.println (KThread.currentThread().getName() +
		         " woke up, slept for " + (t1 - t0) + " ticks, much smaller than 2000");
		lock.release();
	}

	// Test wake for sleepfor
	private static void sleepForTest3() {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);
		KThread thread1 = new KThread(new Runnable(){
            public void run(){
                lock.acquire();
                System.out.println("Thread 1 before sleepfor");
                long t0 = Machine.timer().getTime();
                cv.sleepFor(20000);
                long t1 = Machine.timer().getTime();
                System.out.println("Thread 1 after sleepfor "+(t1-t0)+ " ticks, instead of 20000+ ticks");
				lock.release();}
    	}).setName("thread1");
    	thread1.fork();
    	KThread thread2 = new KThread(new Runnable(){
            public void run(){
                lock.acquire();
                System.out.println("Thread 2 before wake");
                cv.wake();
                System.out.println("Thread 2 after wake");
				lock.release();}
    	}).setName("thread2");
    	thread2.fork();
		thread1.join();
	}

	// Test wakeall for sleepfor
	private static void sleepForTest4() {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);
		KThread thread1 = new KThread(new Runnable(){
            public void run(){
                lock.acquire();
                System.out.println("Thread 1 before sleepfor");
                long t0 = Machine.timer().getTime();
                cv.sleepFor(20000);
                long t1 = Machine.timer().getTime();
                System.out.println("Thread 1 after sleepfor "+(t1-t0)+ " ticks, due to wakeAll");
				lock.release();}
    	}).setName("thread1");
    	thread1.fork();
    	KThread thread2 = new KThread(new Runnable(){
            public void run(){
                lock.acquire();
                System.out.println("Thread 2 before sleepFor");
                long t0 = Machine.timer().getTime();
                cv.sleepFor(10000);
                long t1 = Machine.timer().getTime();
                System.out.println("Thread 2 after sleepFor "+(t1-t0)+" ticks, due to wakeAll");
				lock.release();}
    	}).setName("thread2");
    	thread2.fork();
    	KThread thread3 = new KThread(new Runnable(){
            public void run(){
                lock.acquire();
                System.out.println("Thread 3 before wake");
                cv.wakeAll();
                System.out.println("Thread 3 after wake");
				lock.release();}
    	}).setName("thread3");
    	thread3.fork();
		thread1.join();
		thread2.join();
	}

}
