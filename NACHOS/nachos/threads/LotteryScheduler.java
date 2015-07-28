package nachos.threads;

import java.util.HashSet;
import java.util.Random;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * A scheduler that chooses threads using a lottery.
 * 
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * 
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * 
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
/*-------------Isaac Flores------------------*/
public class LotteryScheduler extends PriorityScheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}


	/**
	 * Allocate a new lottery thread queue.
	 * 
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer tickets from
	 *            waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	
	/*public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}*/

	 /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 1;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = Integer.MAX_VALUE;
    
    protected static int totalTickets;

    
	@Override
	protected LotteryThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new LotteryThreadState(thread);

		return (LotteryThreadState) thread.schedulingState;
	}
	
	protected static final char dbgProcess = 'a';
	
	
	public static void selfTest() 
	{
		Lib.debug(dbgProcess, "Enter LotteryScheduler.selfTest, this prints random things");
        KThread[] threads = new KThread[20]; 
        for (int i = 1; i < 11; i++)
        {
                threads[i-1] = new KThread(new Runnable() {
                           public void run() {
                                   int j = 0;
                                   while (j < 20)
                                   {
                                           long currentTime = Machine.timer().getTime();
                                           while (Machine.timer().getTime() < currentTime + 500)
                                           {
                                                   KThread.yield();
                                           }
                                           //System.out.println(KThread.currentThread().getName() + " loop # " + j);
                                         Lib.debug(dbgProcess, KThread.currentThread().getName() + " loop # " + j);
                                           j++;
                                   }
                                   }
                           }).setName("Thread #" + i);
        }
        for (int i = 0; i < 10; i++)
        {
                threads[i].fork();
                ((LotteryScheduler.ThreadState)threads[i].schedulingState).setPriority(50*i);
        }
        KThread.yield();
	}

	protected class LotteryQueue extends PriorityScheduler.PriorityQueue {
		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
		}

		@Override
		protected LotteryThreadState pickNextThread() {
			totalTickets = 0;
	    	for(KThread current : waitQueue){
	    		totalTickets += getEffectivePriority(current);//this is really inefficient
	    	}
		    // implement me
			/*-------------Andrew-------------*/
		    /*-------------Isaac--------------*/
				KThread nextThread;
				//int winner = Machine.timer.getTime()%totalTickets;
				int winner = random.nextInt();
				//winning "ticket" must be less than the total tickets in the system
				nextThread = null;
				
					for (KThread thread : waitQueue){//increment by each thread through waitQueue
						if (nextThread == null|| getEffectivePriority(thread) < winner) {
							//Finding the thread with the highest priority to set to nextThread
								nextThread = thread;
							//subtract this thread's tickets from winner. this guarantees a winner
								winner -= getEffectivePriority(thread);
						}
					
						else{
							totalTickets -= getEffectivePriority(thread);
							return getThreadState(thread);
						}
				//return null was taken out, we need to return the next thread we picked
			/*-------------Andrew-------------*/
		    /*-------------Isaac--------------*/
					}
					return null;//we should get here iff waitQueue is empty
		}
	}

	protected class LotteryThreadState extends PriorityScheduler.ThreadState {
		public LotteryThreadState(KThread thread) {
			super(thread);
		}

		@Override
		public int getEffectivePriority() {
			return getEffectivePriority(new HashSet<LotteryThreadState>());
		}
		
		private int getEffectivePriority(HashSet<LotteryThreadState> set) {
			/*-------------Andrew-------------*/
			/*-------------Landon-------------*/
			if (set.contains(this)) {

				return priority;
			}

			effectivePriority = priority;

			for (PriorityQueue queue : donationQueue)
				if (queue.transferPriority)
					for (KThread thread : queue.waitQueue) {
						set.add(this);
						effectivePriority += getThreadState(thread)
								.getEffectivePriority(set);
						set.remove(this);
					}
			
			PriorityQueue queue = (PriorityQueue) thread.threadsJoinedOnMe;
			if (queue.transferPriority)
				for (KThread thread : queue.waitQueue) {
					set.add(this);
					effectivePriority += getThreadState(thread)
							.getEffectivePriority(set);
					set.remove(this);
				}
			//Lib.debug(dbgProcess, KThread.currentThread().getName());
			//Lib.debug(dbgProcess,  "current thread has priority of:" + effectivePriority);
			return effectivePriority;
		}
	}
	
	protected Random random = new Random(25);
}

