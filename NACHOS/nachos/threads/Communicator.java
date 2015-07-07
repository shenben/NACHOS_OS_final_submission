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
		lock = new Lock();
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