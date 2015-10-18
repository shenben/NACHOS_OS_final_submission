package nachos.threads;

import java.util.LinkedList;

import nachos.machine.Machine;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		// the associated lock
		lock = new Lock();
		// a list of 
		Speaker = new LinkedList<ResourceWrapper>();
		Listener = new LinkedList<ResourceWrapper>();
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * -----
	 * Speak takes as input a single integer word to be "spoken." After acquiring
	 * the lock, speak then checks to see if listeners exist. If they do, interrupts
	 * are disabled to prevent preemption, and a listener is removed from the store
	 * of listeners. Interrupts are then re-enabled, and the listeners word is set
	 * to the supplied message. As the message is now ready, the condition variable
	 * is called to wake up a waiting thread. 
	 * 
	 * If there are no listeners, the speak creates a new speaker,
	 * sets the appropriate word and adds the speaker to the linke
	 * list. It then puts the speaker to sleep on the associated
	 * condition variable, atomically releasing the lock.
	 * 
	 * @param word
	 *            the integer to transfer.
	 */
	public void speak(int word) 
	{
		lock.acquire();					
		if (!Listener.isEmpty() ) 		//if listeners exist
		{
			Machine.interrupt().disable();							//disable interrupts
			ResourceWrapper listen = Listener.removeFirst();		//remove from CommWaitList
			Machine.interrupt().enable();							//enable ints
			listen.setWord(word);									//set the listener's word
			listen.getCondition().wake();							//call Condition2.wake()
		}
		else 
		{
			ResourceWrapper speaker = new ResourceWrapper();		//create a wrapper
		speaker.setWord(word);										//wrap the int
			Speaker.add(speaker);									//add to LinkedList
			speaker.getCondition().sleep();							//add to Condition2.waitlist
		}
		lock.release();								
	}
	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 * 
	 * Listen first acquires the lock for the condition variable,
	 * sets the word to the default value of 0 and checks to see if there are
	 * any waiting speakers. If there are, it removes the first speaker,
	 * gets the word and wakes up the next speaker.
	 * 
	 * If there are no speakers, a new ResourceWrapper is allocated,
	 * and the listener is encapsulated within it. The listener then
	 * puts itself to sleep on the condition variable and sets the current
	 * word to the initial value.
	 * 
	 */
	public int listen() {
		lock.acquire();
		int word = 0;
		if (!Speaker.isEmpty()) 		//speakers exist
		{
			ResourceWrapper speaker = Speaker.removeFirst();	//get first speaker on list
			word = speaker.getWord();					//unwrap word
			speaker.getCondition().wake();				//remove from Condition2.waitList
		}
		else 
		{
			ResourceWrapper listener = new ResourceWrapper();		//get wrapper
			Listener.add(listener);						//wrap the int
			listener.getCondition().sleep();			//puts self on condition2.Waitlist
			word = listener.getWord();					//
		}
		lock.release();
		return word;
	}

	/**
	 * @author mark A common lock to assure the atomity of <tt>listen</tt> and
	 *         <tt>speak</tt>
	 */
	private static Lock lock;

	/* ResourceWrapper()
	 * 
	 * This class provides a condition variable around a single integer "word,"
	 * used by threads to communicate with each other.
	 * 
	 */
	private class ResourceWrapper 
	{
		int word;
		Condition2 condition;
		public ResourceWrapper() 
		{
			word = 0;
			condition = new Condition2(lock);
		}
		public Condition2 getCondition() {
			return condition;
		}
		public int getWord() {
			return word;
		}
		public void setWord(int w) {
			this.word = w;
		}
	}
	private LinkedList<ResourceWrapper> Speaker;
	private LinkedList<ResourceWrapper> Listener;
}