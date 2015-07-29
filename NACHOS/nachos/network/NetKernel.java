
package nachos.network;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import nachos.machine.*;
import nachos.threads.*;
import nachos.vm.*;
import nachos.userprog.*;

/**
 * A kernel with network support.
 */
public class NetKernel extends UserKernel {
	/**
	 * Allocate a new networking kernel.
	 */
	public NetKernel() {
		super();
	}

//	@Override
	protected OpenFile openSwapFile() {
		return fileSystem.open("swapfile" + Machine.networkLink().getLinkAddress(), true);
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		postOffice = new SocketPostOffice();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		postOffice.shutdown();
		super.terminate();
	}

	SocketPostOffice postOffice;

	
	
	/**
	 * A class to encapsulate the management of the NachOS Transport Protocol with threads to do delivery and such.
	 */
	static class SocketPostOffice {
		SocketPostOffice() {
			nothingToSend = new Condition(sendLock);
			terminationCondition = new Condition(terminationLock);

			//Set up the delivery thread interrupt handlers
			Machine.networkLink().setInterruptHandlers(new Runnable() {
				public void run() {
					receiveInterrupt();
				}
			},
			new Runnable() {
				public void run() {
					sendInterrupt();
				}
			});

			//Set up the postal worker threads
			KThread postalDeliveryThread = new KThread(
					new Runnable() {
						public void run() {
							postalDelivery();
						}
					}
			), postalSendThread = new KThread(
					new Runnable() {
						public void run() {
							send();
						}
					}
			), timerInterruptThread = new KThread(
					new Runnable() {
						public void run() {
							timerRoutine();
						}
					}
			);

			postalDeliveryThread.fork();
			postalSendThread.fork();
			timerInterruptThread.fork();
		}

		Socket accept(int port) {
			Socket c = awaitingSocketMap.retrieve(port);

			if (c != null)
				c.accept();

			return c;
		}

		Socket connect(int host, int port) {
			Socket Socket = null;
			boolean found = false;
			int srcPort, tries = 0;
			while (Socket == null) {
				//Find a source port for the Socket
				srcPort = portGenerator.nextInt(MailMessage.portLimit);
				tries = 0;
				
				while (!(found = (SocketMap.get(srcPort, host, port) == null)) && tries++ < MailMessage.portLimit)
					srcPort = (srcPort+1) % MailMessage.portLimit;
				
				if (found) {
					Socket = new Socket(host, port, srcPort);
					SocketMap.put(Socket);
					if (!Socket.connect()) {
						SocketMap.remove(Socket);
						Socket = null;
					}
				}//else port saturation, so randomize and try again
			}

			return Socket;
		}

		private Random portGenerator = new Random();

		/**
		 * Closes the Socket remove it from the SocketMap, if it exists.
		 * <p>
		 * This should only be called from the kernel when closing a "live" Socket 
		 * (i.e. one that successfully returned from connect).
		 * @param Socket (not null)
		 */
		void close(Socket Socket) {
			if (SocketMap.remove(Socket.srcPort, Socket.destAddress, Socket.destPort) != null)
				Socket.close();
		}

		/**
		 * Closes all the <tt>Socket</tt> instances in both the <tt>SocketMap</tt> 
		 * and <tt>awaitingSocketMap</tt>, and remove the instances from the respective maps.
		 */
		void shutdown() {
			SocketMap.shutdown();
			awaitingSocketMap.shutdown();

			terminationLock.acquire();

			while (!SocketMap.isEmpty())
				terminationCondition.sleep();

			terminationLock.release();

		}

		private Lock terminationLock = new Lock();
		private Condition terminationCondition;

		/**
		 * Called by a <tt>Socket</tt> instance when it is fully closed and 
		 * exhausted. This causes NetKernel to remove it from its Socket mappings.
		 * @param Socket
		 */
		void finished(Socket c) {
			if (SocketMap.remove(c.srcPort, c.destAddress, c.destPort) != null) {
				terminationLock.acquire();
				terminationCondition.wake();
				terminationLock.release();
			}
		}

		/**
		 * Enqueue a packet to be sent over the network.
		 * @param p
		 */
		void enqueue(Packet p) {
			sendLock.acquire();
			sendQueue.add(p);
			nothingToSend.wake();
			sendLock.release();
		}

		/**
		 * Enqueue an ordered sequence of packets, using a List.
		 * 
		 * We can switch to an array if that is more convenient.
		 */
		void enqueue(List<Packet> ps) {
			sendLock.acquire();
			sendQueue.addAll(ps);
			nothingToSend.wake();
			sendLock.release();
		}

		/**
		 * The method for delivering the packets to the appropriate Sockets.
		 */
		private void postalDelivery() {
			MailMessage pktMsg = null;
			Socket Socket = null;
			while (true) {
				messageReceived.P();

				try {
					pktMsg = new MailMessage(Machine.networkLink().receive());
				} catch (MalformedPacketException e) {
					continue;//Just drop the packet
				}

				if ((Socket = SocketMap.get(pktMsg.dstPort, pktMsg.packet.srcLink, pktMsg.srcPort)) != null)
					Socket.packet(pktMsg);
				else if (pktMsg.flags == MailMessage.SYN) {
					Socket = new Socket(pktMsg.packet.srcLink, pktMsg.srcPort, pktMsg.dstPort);
					Socket.packet(pktMsg);

					//Put it in the SocketMap
					SocketMap.put(Socket);

					//Put it in the awaiting Socket map
					awaitingSocketMap.addWaiting(Socket);
				} else if (pktMsg.flags == MailMessage.FIN) {
					try {
						enqueue(new MailMessage(pktMsg.packet.srcLink, pktMsg.srcPort, pktMsg.packet.dstLink, pktMsg.dstPort, MailMessage.EMPTY_CONTENT).packet);
					} catch (MalformedPacketException e) {
					}
				}
			}
		}

		/**
		 * Called when a packet has arrived and can be dequeued from the network
		 * link.
		 */
		private void receiveInterrupt() {
			messageReceived.V();
		}

		/**
		 * The method for sending packets over the network link, from a queue.
		 */
		private void send() {
			Packet p = null;
			while (true) {
				sendLock.acquire();

				//MESA Style waiting
				while (sendQueue.isEmpty())
					nothingToSend.sleep();

				//Dequeue the packet
				p = sendQueue.poll();
				sendLock.release();

				//Now work on sending the packet
				Machine.networkLink().send(p);
				messageSent.P();
			}
		}

		/**
		 * Called when a packet has been sent and another can be queued to the
		 * network link. Note that this is called even if the previous packet was
		 * dropped.
		 */
		private void sendInterrupt() {
			messageSent.V();
		}

		/**
		 * The routine for the interrupt handler.
		 * 
		 * Fires off an event to call the retransmit method on all sockets that require a timer 
		 * interrupt.
		 */
		private void timerRoutine() {
			while (true) {
				alarm.waitUntil(20000);

				//Call the retransmit method on all the Sockets
				SocketMap.retransmitAll();
				awaitingSocketMap.retransmitAll();//FIXME: This may not be necessary
			}
		}

		private SocketMap SocketMap = new SocketMap();
		private AwaitingSocketMap awaitingSocketMap = new AwaitingSocketMap();

		private Semaphore messageReceived = new Semaphore(0);
		private Semaphore messageSent = new Semaphore(0);
		private Lock sendLock = new Lock();

		/** A condition variable to wait on in case there is nothing to send. */
		private Condition nothingToSend;

		private LinkedList<Packet> sendQueue = new LinkedList<Packet>();
	}

	/**
	 * A multimap to handle hash collisions
	 */
	private static class SocketMap {
		void retransmitAll() {
			lock.acquire();
			for (Socket c : map.values())
				c.retransmit();
			lock.release();
		}

		Socket remove(Socket conn) {
			return remove(conn.srcPort, conn.destAddress, conn.destPort);
		}

		boolean isEmpty() {
			lock.acquire();
			boolean b = map.isEmpty();
			lock.release();
			return b;
		}

		/**
		 * Closes all Sockets and removes them from this map.
		 */
		void shutdown() {
			lock.acquire();
			for (Socket c : map.values())
				c.close();
			lock.release();
		}

		Socket get(int sourcePort, int destinationAddress, int destinationPort) {
			lock.acquire();
			Socket c = map.get(new SocketKey(sourcePort,destinationAddress,destinationPort));
			lock.release();
			return c;
		}

		void put(Socket c) {
			lock.acquire();
			map.put(new SocketKey(c.srcPort,c.destAddress,c.destPort),c);
			lock.release();
		}

		Socket remove(int sourcePort, int destinationAddress, int destinationPort) {
			lock.acquire();
			Socket c = map.remove(new SocketKey(sourcePort,destinationAddress,destinationPort));
			lock.release();
			return c;
		}

		private HashMap<SocketKey, Socket> map = new HashMap<SocketKey, Socket>();
		
		private Lock lock = new Lock();
	}

	/**
	 * A class that holds <tt>Socket</tt>s waiting to be accepted. 
	 */
	private static class AwaitingSocketMap {
		/**
		 * Add the Socket to the set of waiting Sockets
		 * @param c
		 * @return true if the Socket didn't already exist
		 */
		boolean addWaiting(Socket c) {
			boolean returnBool = false;
			lock.acquire();
			if (!map.containsKey(c.srcPort))
				map.put(c.srcPort, new HashMap<SocketKey,Socket>());

			if (map.get(c.srcPort).containsKey(null))
				returnBool = false;//Socket already exists
			else {
				map.get(c.srcPort).put(new SocketKey(c.srcPort,c.destAddress,c.destPort), c);
				returnBool = true;
			}
			lock.release();
			return returnBool;
		}

		/**
		 * Closes all Sockets and removes them from this map.
		 */
		void shutdown() {
			lock.acquire();
			map.clear();
			lock.release();
		}

		void retransmitAll() {
			lock.acquire();
			for (HashMap<SocketKey,Socket> hm : map.values())
				for (Socket c : hm.values())
					c.retransmit();
			lock.release();
		}

		/**
		 * Retrieve a <tt>Socket</tt> from the given port and remove it from <tt>this</tt>. Return null if one doesn't exist.
		 * @param port
		 * @return a Socket on the port if it exists.
		 */
		Socket retrieve(int port) {
			Socket c = null;
			lock.acquire();
			if (map.containsKey(port)) {
				HashMap<SocketKey,Socket> mp = map.get(port);

				c = mp.remove(mp.keySet().iterator().next());

				//Get rid of set if it is empty
				if (mp.isEmpty())
					map.remove(port);
			}
			lock.release();
			
			return c;
		}

		private HashMap<Integer,HashMap<SocketKey,Socket>> map = new HashMap<Integer,HashMap<SocketKey,Socket>>();
		
		private Lock lock = new Lock();
	}

	private static class SocketKey {
		SocketKey(int srcPrt, int destAddr, int destPrt) {
			sourcePort = srcPrt;
			destAddress = destAddr;
			destPort = destPrt;
			hashcode = Long.valueOf(((long) sourcePort) + ((long) destAddress) + ((long) destPort)).hashCode();
		}

		@Override
		public int hashCode() {
			return hashcode;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			else if (o instanceof SocketKey) {
				SocketKey oC = (SocketKey) o;
				return sourcePort == oC.sourcePort &&
				destAddress == oC.destAddress &&
				destPort == oC.destPort;
			} else
				return false;
		}

		private int sourcePort, destAddress, destPort, hashcode;
	}
	
	
}