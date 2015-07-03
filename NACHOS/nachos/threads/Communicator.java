package nachos.threads;

import java.util.Queue;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	//i want to send to someone
    	//signal that request
    	//notified that listener is ready
    	//send message
    	//listener returns
    	
    	lock.acquire();
    	lockReady=false;
    	//queue.add(word);
    	dataready.notify(); //signal()
    	KThread.sleep();
    	lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	
    	if(lockReady=true)
    		lock.acquire();	//wait if cant get lock
    	else
    	{
    		
    	}
    	
    	while(queue.isEmpty())
			try {
				dataready.wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	
    	int item = (int) queue.remove();
    	lock.release();
    	return item;
    }
    Lock lock;
    boolean lockReady = true;
    Condition2 dataready;
	Queue<Integer> queue;
	
}
