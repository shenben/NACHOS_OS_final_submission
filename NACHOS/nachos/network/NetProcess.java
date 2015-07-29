package nachos.network;

import nachos.machine.*;
import nachos.vm.*;
import nachos.userprog.*;

/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public NetProcess() {
		super();
	}

	private static final int syscallConnect = 11, syscallAccept = 12;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>11</td>
	 * <td><tt>int  connect(int host, int port);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>12</td>
	 * <td><tt>int  accept(int port);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallAccept:
			return handleAccept(a0);
		case syscallConnect:
			return handleConnect(a0,a1);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		default:
			return super.handleSyscall(syscall, a0, a1, a2, a3);
		}
		
		
	}

	protected int handleRead(int fileDescriptor, int buffer, int count) 
	{
		/*Landon*/
		OpenFile file = descriptorManager.get(fileDescriptor);
	
		if (file == null) {
			Lib.debug(dbgProcess, "Invalid file descriptor");
			return -1;
		}
	
		if (!(buffer >= 0 && count >= 0)) {
			Lib.debug(dbgProcess, "buffer and count must be bigger than zero");
			return -1;
		}
	
		byte buf[] = new byte[count];		//save space of up to the size of the file
	
		int fileLength = file.read(buf, 0, count);	//read file into buffer
	
		if (fileLength == -1) {
			Lib.debug(dbgProcess, "Failure to read file");
			return -1;
		}
	
		Lib.debug(dbgProcess, "Read from file okay");
		return fileLength;
	}

	protected int handleWrite(int fileDescriptor, int buffer, int count) 
	{
		/*Landon*/
		OpenFile file = descriptorManager.get(fileDescriptor);
	
		if (file == null) {
			Lib.debug(dbgProcess, "Invalid file descriptor");
			return -1;
		}
	
		if (!(buffer >= 0 && count >= 0)) {
			Lib.debug(dbgProcess, "buffer and count must be bigger than zero");
			return -1;
		}
	
		byte buf[] = new byte[count];		//allocate space for file
	
		int fileLength = readVirtualMemory(buffer, buf, 0, count);	//reads directly from Virtual Pages to physical memory array
	
		fileLength = file.write(buf, 0, fileLength);	//write to file
		Lib.debug(dbgProcess, "wrote to file okay:" + (char) buffer);
		return fileLength;
	}

	
	
	

	/**
	 * The syscall handler for the connect syscall.
	 * @param host
	 * @param port
	 */
	private int handleConnect(int host, int port) {
		Lib.assertTrue(port >= 0 && port < Packet.linkAddressLimit);
		//int fileDesc = getFileDescriptor();
		
		int i;
		for(i=0; i <descriptorManager.descriptor.length;i++)
		{
			if(descriptorManager.descriptor[i]==null)
			{
				break;
			}
			else
				i=-1;
		}
		
		if (i != -1) {
			try {
				descriptorManager.descriptor[i] = new OpenSocket(((NetKernel) kernel).postOffice.connect(host,port));
				
				//FileRef.referenceFile(fileTable[fileDesc].getName());
			} catch (ClassCastException cce) {
				Lib.assertNotReached("Error - kernel not of type NetKernel");
			}
		}

		return i;
}


	/**
	 * The syscall handler for the accept syscall.
	 * @param port
	 */
	private int handleAccept(int port) {
		Lib.assertTrue(port >= 0 && port < Packet.linkAddressLimit);
		int i;
		for(i=0; i <descriptorManager.descriptor.length;i++)
		{
			if(descriptorManager.descriptor[i]==null)
			{
				break;
			}
			else
				i=-1;
		}
		if (i!= -1) {
			Connection c = null;
			try {
				// Try to get an entry in the file table
	//			c = ((NetKernel) kernel).postOffice.accept(port);
			} catch (ClassCastException cce) {
				Lib.assertNotReached("Error - kernel not of type NetKernel");
			}

			if (c != null) {
	//			fileTable[fileDesc] = new OpenSocket(c);
	//			FileRef.referenceFile(fileTable[fileDesc].getName());
	//			return fileDesc;
			}
		}

		return -1;
	}

	/**
	 * A class to represent sockets that extends <tt>OpenFile</tt> so it can 
	 * reside in the process's page table.
	 */
	private static class OpenSocket extends OpenFile {
		OpenSocket(Connection c) {
			super(null, c.srcPort + "," + c.destAddress + "," + c.destPort);
			connection = c;
		}

		/**
		 * Close this socket and release any associated system resources.
		 */
		@Override
		public void close() {
			connection.close();
			connection = null;
		}

		@Override
		public int read(byte[] buf, int offset, int length) {
			Lib.assertTrue(offset < buf.length && length <= buf.length - offset);
			if (connection == null)
				return -1;
			else {
				byte[] receivedData = connection.receive(length);
				if (receivedData == null)
					return -1;
				else {
					System.arraycopy(receivedData, 0, buf, offset, receivedData.length);
					return receivedData.length;
				}
			}
		}

		@Override
		public int write(byte[] buf, int offset, int length) {
			if (connection == null)
				return -1;
			else
				return connection.send(buf, offset, length);
		}

		/** The underlying connection for this socket. */
		private Connection connection;
	}
	
	
	
	public static UserKernel kernel = null;
}