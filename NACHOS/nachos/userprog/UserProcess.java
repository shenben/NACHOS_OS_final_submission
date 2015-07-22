package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
    	
    	//////andrew
    	childProcesses = new HashSet <Integer>();
    	
    	
    	
    	
	int numPhysPages = Machine.processor().getNumPhysPages();
	pageTable = new TranslationEntry[numPhysPages];
	for (int i=0; i<numPhysPages; i++)
	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	/*---------------Lenny Code---------------*/
	/*---------------Lenny Code---------------*/
	int firstVPN = Processor.pageFromAddress(vaddr);
	int  firstOffset = Processor.offsetFromAddress(vaddr);
	int lastVPN = Processor.pageFromAddress(vaddr + length);

	TranslationEntry entry = getTranslationEntry(firstVPN, false);
	// for now, just assume that virtual addresses equal physical addresses
	if (entry == null)
	    return 0;
	
	//int amount = Math.min(length, memory.length-vaddr); Oldcode
	//System.arraycopy(memory, vaddr, data, offset, amount); Old code
	int amount = Math.min(length, pageSize - firstOffset);
	System.arraycopy(memory, Processor.makeAddress(entry.ppn, firstOffset),
			data, offset, amount);
	offset += amount;

	for (int i = firstVPN + 1; i <= lastVPN; i++) {
		entry = getTranslationEntry(i, false);
		if (entry == null)
			return amount;
		int length2 = Math.min(length - amount, pageSize);
		System.arraycopy(memory, Processor.makeAddress(entry.ppn, 0), data,offset, length2);
		offset += length2;
		amount += length2;
	}
	/*---------------Lenny Code---------------*/
	/*---------------Lenny Code---------------*/
	return amount;
    }


    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	/*---------------Lenny Code---------------*/
	/*---------------Lenny Code---------------*/
	int firstVirtualPageNumber = Processor.pageFromAddress(vaddr);
	int firstOffset = Processor.offsetFromAddress(vaddr);
	int lastVirtualPageNumber = Processor.pageFromAddress(vaddr + length);
	
	TranslationEntry entry = getTranslationEntry(firstVirtualPageNumber, true);
	/*---------------Lenny Code---------------*/
	/*---------------Lenny Code---------------*/
	
	// for now, just assume that virtual addresses equal physical addresses
	/*---------------Lenny Code---------------*/
	/*---------------Lenny Code---------------*/
	if (entry == null) /*old code(vaddr < 0 || vaddr >= memory.length)*/
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(data, offset, memory, vaddr, amount);
	
	offset += amount;
		
		for (int i = firstVirtualPageNumber + 1; i <= lastVirtualPageNumber; i++) {
			entry = getTranslationEntry(i, true);
			if (entry == null)
				return amount;
			int length2 = Math.min(length - amount, pageSize);
			System.arraycopy(data, offset, memory, Processor.makeAddress(entry.ppn, 0), length2);
			offset += length2;
			amount += length2;
	}
	/*---------------Lenny Code---------------*/
	/*---------------Lenny Code---------------*/
	return amount;
    }
    
    protected TranslationEntry getTranslationEntry(int vpn, boolean isWrite) {
		if (vpn < 0 || vpn >= numPages)
			return null;
		TranslationEntry result = pageTable[vpn];
		if (result == null)
			return null;
		if (result.readOnly && isWrite)
			return null;
		result.used = true;
		if (isWrite)
			result.dirty = true;
		return result;
	}



    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
    	
    	
    	if (numPages > Machine.processor().getNumPhysPages()) {
    	    coff.close();
    	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
    	    return false;
    	}
    	
    	/*int[] ppns = UserKernel.allocatePages(numPages);

    		if (ppns == null) {
    			coff.close();
    			Lib.debug(dbgProcess, "\tinsufficient physical memory");
    			return false;
    		}

    		pageTable = new TranslationEntry[numPages];*/

    	// load sections
    	for (int s=0; s<coff.getNumSections(); s++) {
    	    CoffSection section = coff.getSection(s);
    	    
    	    
    	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
    		      + " section (" + section.getLength() + " pages)");

    	    for (int i=0; i<section.getLength(); i++) {
    		int vpn = section.getFirstVPN()+i;

    		// for now, just assume virtual addresses=physical addresses
    		/*int ppn = ppns[vpn];
    				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section
    						.isReadOnly(), false, false);
    				section.loadPage(i, ppn);*/
    		section.loadPage(i, vpn);
    	    }
    	}
    	
    	return true;
        }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
	coff.close();
		
		for (int i = 0; i < numPages; i++)
			UserKernel.releasePage(pageTable[i].ppn);
		pageTable = null;

    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    
    //look at userkernel for testing purposes
    public static void selfTest()
    {
    	
    	UserProcess proc1 = newUserProcess();
    	//handleExec()
    	
    	//create file[0]
    	proc1.handleCreate(0,0);
    	
    	//close file
    	//proc1.processesOpenFiles[0].close();
    	proc1.handleClose(0);
    	
    	//open file[0]
    	proc1.handleOpen(0);
    	
    	//unlink file[0]
    	proc1.handleUnlink(0);
    }
    
    /**
     * Handle the halt() system call. 
     */
	//make it so halt can only be invoked by root
    private int handleHalt() {
    	if(this.processID != 0)
    		return 0;
    	
		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
    	return -1;
    }
    
    
    /**
     * Attempt to open the named disk file, creating it if it does not exist,
     * and return a file descriptor that can be used to access the file.
     *
     * Note that creat() can only be used to create files on disk; creat() will
     * never return a file descriptor referring to a stream.
     *
     * Returns the new file descriptor, or -1 if an error occurred.
     */
    private int handleCreate(int a0, int maxinputArgLength)
    {
    	String name = readVirtualMemoryString(a0, maxinputArgLength );
    	
    	if(name == null)	
    	{
    		Lib.debug(dbgProcess, "invalid name pointer");
    		return -1;
    	}
    	
    	if(deleted.contains(name))
    	{
    		Lib.debug(dbgProcess, "being deleted");
    		return -1;
    	}
    	
    	//filesystem open, create if needed
    	OpenFile file = UserKernel.fileSystem.open(name, true);	
    	/*
    	 * Probably will need some sort of imported OBJECT like hashset
    	 * describe file [#fileID, #fileDescriptor]
    	*/
    	
   	 	//check creation
    	if (file == null) {
			Lib.debug(dbgProcess, "Create file failed");
			return -1;
		}
    	
    	//add to open files
    	for(int i=0; i<=16; i++)
		{
			if(processesOpenFiles[i] == null)
			{
				processesOpenFiles[i] = file;
				if(files.get(file.getName()) !=null)
					files.put(file.getName(),  files.get(file.getName()) +1);
				else
				{
					files.put(file.getName(), 1);
				}
				//fileDescriptor = i;
				//break;
				
				return i;
			}
			
		}
    	return -1;
    	//return file descriptor
    //	return fileDescriptor;	
    }

/**
 * Terminate the current process immediately. Any open file descriptors
 * belonging to the process are closed. Any children of the process no longer
 * have a parent process.
 *
 * status is returned to the parent process as this process's exit status and
 * can be collected using the join syscall. A process exiting normally should
 * (but is not required to) set status to 0.
 *
 * exit() never returns.
 */

	private void handleExit(int status)
	{	
		//close all files
		for(int i=0; i<16; i++)
		{
			handleClose(i);
		}
		
		//check children, set all to orphan 
		for(int child : childProcesses)
		{
			//child.parent = null;
		}
		
		//current process. end
		this.unloadSections();		//remove memory allocation
		if(this == UserKernel.rootProcess)
			Machine.halt();
		else
		{
			UThread.finish();
			Lib.assertNotReached("Thread finished unsuccessfully");
		}
    }

	 private int handleJoin(int processID, int status){
	    	
	    	if (!childProcesses.contains(processID)){
	    		Lib.debug(dbgProcess,"not the right child");
	    		return -1;
	    	}
	    	
	    childProcesses.remove(processID);

		UserProcess child = allProcesses.get(processID);

			if (child == null) 
			{
				Lib.debug(dbgProcess, "join a exited process");
				child = terminatedProcesses.get(processID);
					if (child == null) {
						Lib.debug(dbgProcess, "error in join");
						return -1;
					}
			}

		child.Terminated.P();

		writeVirtualMemory(status, Lib.bytesFromInt(child.status));

			if (child.exitNormally)
				return 1;
 	
 	return 0;
 }
	 
	 
	protected int handleExec(int file, int argc, int argv) 
	{
		String fileName = readVirtualMemoryString(file, maxFileNameLength);

		if (fileName == null || !fileName.endsWith(".coff")) {
			Lib.debug(dbgProcess, "Invalid file name in handleExec()");
			return -1;
		}

		if (argc < 0) {
			Lib.debug(dbgProcess, "argc < 0");
			return -1;
		}

		String[] args = new String[argc];
		byte[] buffer = new byte[4];

			for (int i = 0; i < argc; i++) {
				if (readVirtualMemory(argv + i * 4, buffer) != 4)
					return -1;
				args[i] = readVirtualMemoryString(Lib.bytesToInt(buffer, 0),
						maxFileNameLength);
				if (args[i] == null)
					return -1;
		}

		UserProcess child = newUserProcess();		
	
		//childProcesses.add(child);				//null pointer exception here
		
		//childProcesses.add(newUserProcess());
		childProcesses.add(child.processID);
		
		saveState();

		/*
		if (!child.execute(fileName, args)) 
		{
			Lib.debug(dbgProcess, "fail to execute child process");
			return -1;
		}
		*/
		
		//return child.processID;
		return 0;
	}



/**
 * Attempt to open the named file and return a file descriptor.
 *
 * Note that open() can only be used to open files on disk; open() will never
 * return a file descriptor referring to a stream.
 *
 * Returns the new file descriptor, or -1 if an error occurred.
 */

	private int handleOpen(int name)
	{
		String fileName = readVirtualMemoryString(name, maxinputArgLength);
		
		if (fileName == null) 
		{
			Lib.debug(dbgProcess, "Invalid file name pointer");
			return -1;
		}
		
		
		if(processesOpenFiles[15] !=null)
		{
			Lib.debug(dbgProcess, "max files open already!");
			return -1;
		}

		OpenFile file = UserKernel.fileSystem.open(fileName, false);

		if (file == null) {
			Lib.debug(dbgProcess, "Invalid file name");
			return -1;
		}
		
		if(deleted.contains(fileName))
		{
			Lib.debug(dbgProcess, "being deleted");
			return -1;
		}
		
		//the file is now open, add it to our processesOpenFiles.
		for(int i=0; i<=16; i++)
		{
			if(processesOpenFiles[i] == null)
			{
				processesOpenFiles[i] = file;
				if(files.get(file.getName()) !=null)
					files.put(file.getName(),  files.get(file.getName()) +1);
				else
				{
					files.put(file.getName(), 1);
				}
				//fileDescriptor = i;
				//break;
				
				return i;
			}
			
		}
    	return -1;
	}
	

/**
 * Attempt to read up to count bytes into buffer from the file or stream
 * referred to by fileDescriptor.
 *
 * On success, the number of bytes read is returned. If the file descriptor
 * refers to a file on disk, the file position is advanced by this number.
 *
 * It is not necessarily an error if this number is smaller than the number of
 * bytes requested. If the file descriptor refers to a file on disk, this
 * indicates that the end of the file has been reached. If the file descriptor
 * refers to a stream, this indicates that the fewer bytes are actually
 * available right now than were requested, but more bytes may become available
 * in the future. Note that read() never waits for a stream to have more data;
 * it always returns as much as possible immediately.
 *
 * On error, -1 is returned, and the new file position is undefined. This can
 * happen if fileDescriptor is invalid, if part of the buffer is read-only or
 * invalid, or if a network stream has been terminated by the remote host and
 * no more data is available.
 */

	
	private int handleRead(int fID, int buffer, int count)
	{
		OpenFile file = processesOpenFiles[fID]; //get file
		
		if (file == null) {
			Lib.debug(dbgProcess, "Invalid file descriptor");
			return -1;
		}

		if (!(buffer >= 0 && count >= 0)) {
			Lib.debug(dbgProcess, "buffer and count should bigger then zero");
			return -1;
		}

		byte buf[] = new byte[count];

		//read
		int length = file.read(buf, 0, count);

		if (length == -1) {
			Lib.debug(dbgProcess, "Fail to read from file");
			return -1;
		}
		
		//do we need to write to virtual memory?

		return length;
	}
	
	/**
	 * Attempt to write up to count bytes from buffer to the file or stream
	 * referred to by fileDescriptor. write() can return before the bytes are
	 * actually flushed to the file or stream. A write to a stream can block,
	 * however, if kernel queues are temporarily full.
	 *
	 * On success, the number of bytes written is returned (zero indicates nothing
	 * was written), and the file position is advanced by this number. It IS an
	 * error if this number is smaller than the number of bytes requested. For
	 * disk files, this indicates that the disk is full. For streams, this
	 * indicates the stream was terminated by the remote host before all the data
	 * was transferred.
	 *
	 * On error, -1 is returned, and the new file position is undefined. This can
	 * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
	 * if a network stream has already been terminated by the remote host.
	 */

	private int handleWrite(int fid, int buffer, int count)
	{
		OpenFile file  = processesOpenFiles[fid];
		
		if (file == null) {
			Lib.debug(dbgProcess, "Invalid file descriptor");
			return -1;
		}

		if (!(buffer >= 0 && count >= 0)) 
		{
			Lib.debug(dbgProcess, "buffer and count should bigger then zero");
			return -1;
		}
		
		byte buf[] = new byte[count];		//set #count bytes.
		
		//read virtual memory and write to specified array
		int length = readVirtualMemory(buffer, buf, 0, count);
		length = file.write(buf, 0, length);	//write to file.

		if(length < count)
		{
			Lib.debug(dbgProcess, "did not write all of requested bytes");
			return -1;
		}
		return length;	//return count of what was written
	}
	

/**
 * Close a file descriptor, so that it no longer refers to any file or stream
 * and may be reused.
 *
 * If the file descriptor refers to a file, all data written to it by write()
 * will be flushed to disk before close() returns.
 * If the file descriptor refers to a stream, all data written to it by write()
 * will eventually be flushed (unless the stream is terminated remotely), but
 * not necessarily before close() returns.
 *
 * The resources associated with the file descriptor are released. If the
 * descriptor is the last reference to a disk file which has been removed using
 * unlink, the file is deleted (this detail is handled by the file system
 * implementation).
 *
 * Returns 0 on success, or -1 if an error occurred.
 */

	
	private int handleClose(int fid)
	{
		if(processesOpenFiles[fid] ==null)
		{
			Lib.debug(dbgProcess, "file does not exist");
			return -1;
		}
		OpenFile file = processesOpenFiles[fid];
		processesOpenFiles[fid] = null;
		file.close();

		String fileName = file.getName();

		if (files.get(fileName) > 1)
			files.put(fileName, files.get(fileName) - 1);
		else {
			files.remove(fileName);
			if (deleted.contains(fileName)) {
				deleted.remove(fileName);
				UserKernel.fileSystem.remove(fileName);
			}
		}

		return 0;
	}
	
	/**
	 * Delete a file from the file system. If no processes have the file open, the
	 * file is deleted immediately and the space it was using is made available for
	 * reuse.
	 *
	 * If any processes still have the file open, the file will remain in existence
	 * until the last file descriptor referring to it is closed. However, creat()
	 * and open() will not be able to return new file descriptors for the file
	 * until it is deleted.
	 *
	 * Returns 0 on success, or -1 if an error occurred.
	 */
	private int handleUnlink(int name)
	{
		
		String fileName = readVirtualMemoryString(name, maxinputArgLength);
		
		if (fileName == null) {
			Lib.debug(dbgProcess, "Invalid file name pointer");
			return -1;
		}
		
		
		//check that no process has this file open.
			//delete
		
		return 0;
	}

    private static final int
    syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
	case syscallExit:
		handleExit(a0);
	case syscallExec:
		return handleExec(a0,a1,a2);
	case syscallJoin:
		return handleJoin(a0, a1);
	case syscallCreate:
		return handleCreate(a0,a1);
	case syscallOpen:
		return handleOpen(a0);
	case syscallRead:
		return handleRead(a0,a1,a2);
	case syscallWrite:
		return handleWrite(a0,a1,a2);
	case syscallClose:
		return handleClose(a0);
	case syscallUnlink:
		return handleUnlink(a0);
		
	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    
    
    
    /***************variables added****************/
    /***************variables added****************/
    /***************variables added****************/
    private static int maxinputArgLength = 256;			//filename size
    
    //this processes files
    private OpenFile[] processesOpenFiles = new OpenFile[16];	//array of "file that supports reading, writing, and seeking."
 	
    //protected HashSet<UserProcess> childProcesses;    
    public static int maxFileLimit= 16;
    
    /*Each file that a process has opened should have a 
     * unique file descriptor associated with it*/ 
    
    //The file descriptor should be a nonnegative 
    //integer that is simply used to index into a table of currently-open files by
    //that process.
    //private int fileDescriptor;
    /* When a process is created, two streams are already open. File descriptor 0
     * refers to keyboard input (UNIX stdin), and file descriptor 1 refers to
     * display output (UNIX stdout). File descriptor 0 can be read, and file
     * descriptor 1 can be written, without previous calls to open().
     */
    
    HashMap<Integer, UserProcess> parent = null;
    
    private static UserProcess currentProcess = null;
    /*---------Andrew--------*/
   	///////////////////////////
   	///////////////////////////
   	/*---------Andrew--------*/
       protected int status;
       protected int processID;
       protected int processNumber = 0;
       protected Semaphore Terminated;
       protected HashSet<Integer> childProcesses;
       protected boolean exitNormally = true;
       protected static Hashtable<Integer, UserProcess> allProcesses = new Hashtable<Integer, UserProcess>();
   	   protected static Hashtable<Integer, UserProcess> terminatedProcesses = new Hashtable<Integer, UserProcess>();
   	   protected static final int maxFileNameLength = 256;
    /*---------Andrew--------*/
   	///////////////////////////
   	///////////////////////////
   	/*---------Andrew--------*/
   	   
   	   
   	protected static Hashtable<String, Integer> files = new Hashtable<String, Integer>();
   	protected static HashSet<String> deleted = new HashSet<String>();
	public class DescriptorManager
	{
		public OpenFile descriptor[] = new OpenFile[maxFileLimit];
	
		public int add(int index, OpenFile file) {
			if (index < 0 || index >= maxFileLimit)
				return -1;
	
			if (descriptor[index] == null) {
				descriptor[index] = file;
				if (files.get(file.getName()) != null) {
					files.put(file.getName(), files.get(file.getName()) + 1);
				}
				else {
					files.put(file.getName(), 1);
				}
				return index;
			}
	
			return -1;
		}
	
		public int add(OpenFile file) {
			for (int i = 0; i < maxFileLimit; i++)
				if (descriptor[i] == null)
					return add(i, file);
	
			return -1;
		}
	
		public int close(int fileDescriptor) {
			if (descriptor[fileDescriptor] == null) {
				Lib.debug(dbgProcess, "file descriptor " + fileDescriptor
						+ " doesn't exist");
				return -1;
			}
	
			OpenFile file = descriptor[fileDescriptor];
			descriptor[fileDescriptor] = null;
			file.close();
	
			String fileName = file.getName();
	
			if (files.get(fileName) > 1)
				files.put(fileName, files.get(fileName) - 1);
			else {
				files.remove(fileName);
				if (deleted.contains(fileName)) 
				{
					deleted.remove(fileName);
					UserKernel.fileSystem.remove(fileName);
				}
			}
	
			return 0;
		}
	
		public OpenFile get(int fileDescriptor) {
			if (fileDescriptor < 0 || fileDescriptor >= maxFileLimit)
				return null;
			return descriptor[fileDescriptor];
		}
	}

}
