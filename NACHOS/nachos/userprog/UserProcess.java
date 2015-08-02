package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() 
	{	
		//Andrew
		//Landon
		PID = processNumber++;
		status = -1;				
		allProcesses.put(PID, this);
		childProcesses = new HashSet<Integer>();
		terminated = new Semaphore(0);
		
		//Every Process can open 16 (besides i/o) OpenFiles
		descriptorManager = new FileDescriptor();
		descriptorManager.add(0, UserKernel.console.openForReading());
		descriptorManager.add(1, UserKernel.console.openForWriting());
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
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
		//UThread calls "process.saveState();" and then saves the registers
		//and also "super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		/*Andrew*/
		/*Lenny*/
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int firstVPN = Processor.pageFromAddress(vaddr), firstOffset = Processor
				.offsetFromAddress(vaddr), lastVPN = Processor
				.pageFromAddress(vaddr + length);

		TranslationEntry entry = getTranslationEntry(firstVPN, false);

		if (entry == null)
			return 0;

		int amount = Math.min(length, pageSize - firstOffset);
		System.arraycopy(memory, Processor.makeAddress(entry.ppn, firstOffset),
				data, offset, amount);
		offset += amount;

		for (int i = firstVPN + 1; i <= lastVPN; i++) {
			entry = getTranslationEntry(i, false);
			if (entry == null)
				return amount;
			int len = Math.min(length - amount, pageSize);
			System.arraycopy(memory, Processor.makeAddress(entry.ppn, 0), data,
					offset, len);
			offset += len;
			amount += len;
		}

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) 
	{
		/*Andrew*/
		/*Lenny*/
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		//making the byte array equal to the allocated memory pages.
		
		int firstVPN = Processor.pageFromAddress(vaddr); 
		//vaddress is the first page of virtual memory so we use it in our argument
		//and get its page from the above function.
		int firstOffset = Processor.offsetFromAddress(vaddr);
		//the function above returns the offset from vaddr. 
		int lastVPN = Processor.pageFromAddress(vaddr + length);
		//using length and vaddr, we can get the last VPN;
		
		TranslationEntry entry = getTranslationEntry(firstVPN, true);
		//get Page table entry of first Page. make writable. 

		if (entry == null)
			return 0;

		int spaceTaken = Math.min(length, pageSize - firstOffset);
		//the amount of space this write will take up in the page.
		
		System.arraycopy(data, offset, memory, Processor.makeAddress(entry.ppn,firstOffset), spaceTaken);
		//actual write: copy from virtual memory to physical page
		
		offset += spaceTaken;
		//new offset based off previous write
		
		for (int i = firstVPN + 1; i <= lastVPN; i++) 
		{
			//this for loop is to update the VIRTUAL PAGE ENTRIES to the existing Physical addreses.
			//translate every Page Table Entry inside of allocated Virtual Page List
			entry = getTranslationEntry(i, true);		
			
			if (entry == null)
				return spaceTaken;
			
			int availableSpace = Math.min(length - spaceTaken, pageSize);
			//The space taken 
			
			System.arraycopy(data, offset, memory, Processor.makeAddress(entry.ppn, 0), availableSpace);
			//
			
			offset += availableSpace;
			//each iteration of the for loop changes offset by the amount written.
			spaceTaken += availableSpace;
			//same with the amount of space taken.
		}

		return spaceTaken;
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
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	protected boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
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
		for (int i = 0; i < args.length; i++) {
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
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib
					.assertTrue(writeVirtualMemory(entryOffset,
							stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib
					.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib
					.assertTrue(writeVirtualMemory(stringOffset,
							new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		/*Andrew*/
		/*Lenny*/
		
		// physical page numbers allocated
		int[] ppns = UserKernel.allocatePages(numPages);
		
		if (ppns == null) //nothing was allocated
		{
			coff.close();	//close "file system"
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		pageTable = new TranslationEntry[numPages];
		//allocate this processes pageTable

		
		for (int s = 0; s < coff.getNumSections(); s++) 
		{
			// load sections
			// the sections are contiguous and start at page 0
			CoffSection section = coff.getSection(s);
			
			Lib.debug(dbgProcess, "\tinitializing " + section.getName()+ " section (" + section.getLength() + " pages)");
			
			for (int i = 0; i < section.getLength(); i++) 
			{	
				//for each section, the loading processes executable chunks are placed into the caller's virtualPageTable
				int vpn = section.getFirstVPN() + i;				
				int ppn = ppns[vpn];
				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);
				section.loadPage(i, ppn);
			}
		}

		// allocate free pages for stack and argv
		for (int i = numPages - stackPages - 1; i < numPages; i++) 
		{
			pageTable[i] = new TranslationEntry(i, ppns[i], true, false, false,false);
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		/*Andrew*/
		/*Lenny*/
		coff.close();
		//close the program
		
		//release allocated physical pages
		for (int i = 0; i < numPages; i++)
			UserKernel.releasePage(pageTable[i].ppn);
		pageTable = null;
		//set virtual page table to be empty
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < Processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	protected int handleHalt() 
	{
		//Landon
		//Andrew
		if (this != UserKernel.rootProcess)
			return 0;
	
		Kernel.kernel.terminate();
	
		Lib.assertNotReached("Machine.halt() did not halt!");
		return 0;
	}

	protected int handleCreat(int name) 
	{
		/*Landon*/
		String fileName = readVirtualMemoryString(name, maxFileNameLength);
	
		if (fileName == null) 
		{
			Lib.debug(dbgProcess, "Invalid pointer to file name");
			return -1;
		}
	
		if (terminatedFiles.contains(fileName)) 
		{
			Lib.debug(dbgProcess, "File being deleted");
			return -1;
		}
	
		OpenFile file = UserKernel.fileSystem.open(fileName, true);
		//open the named file, creating it if it does not exist

		if (file == null) 
		{
			Lib.debug(dbgProcess, "Create file failed");
			return -1;
		}
	
		Lib.debug(dbgProcess, "Create file:" + name+ " success");
		return descriptorManager.add(file);
	}

	protected int handleOpen(int name) 
	{
		/*Landon*/
		String fileName = readVirtualMemoryString(name, maxFileNameLength);
	
		if (fileName == null) {
			Lib.debug(dbgProcess, "Invalid pointer to file name");
			return -1;
		}
	
		OpenFile file = UserKernel.fileSystem.open(fileName, false);
		Lib.debug(dbgProcess, "File has been opened");
		
		if (file == null) {
			Lib.debug(dbgProcess, "Invalid file name");
			return -1;
		}
	
		if (terminatedFiles.contains(fileName)) {
			Lib.debug(dbgProcess, "File being deleted");
			return -1;
		}
		return descriptorManager.add(file);
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

	protected int handleClose(int fileDescriptor) 
	{
		return descriptorManager.close(fileDescriptor);
	}

	protected int handleUnlink(int name) 
	{
		/*Landon*/
		String fileName = readVirtualMemoryString(name, maxFileNameLength);
	
		if (fileName == null) {
			Lib.debug(dbgProcess, "Invalid pointer to file name");
			return -1;
		}
	
		if (allExistingFiles.containsKey(fileName)) 
		{
			Lib.debug(dbgProcess, "File added to delete list");
			terminatedFiles.add(fileName);
		}
		else 
		{
			if (!UserKernel.fileSystem.remove(fileName))
			{
				Lib.debug(dbgProcess, "file not found");
				return -1;
			}
		}
		
		Lib.debug(dbgProcess, "File unlinked");
		return 0;
	}

	protected int handleExec(int file, int argc, int argv) {
		/*Andrew*/
		String fileName = readVirtualMemoryString(file, maxFileNameLength);
		
		if (fileName == null || !fileName.endsWith(".coff")) {
			Lib.debug(dbgProcess, "Invalid pointer to file name");
			return -1;
		}
	
		if (argc < 0) {
			Lib.debug(dbgProcess, "argc < 0");
			return -1;
		}
	
		String[] args = new String[argc];	//allocate space for the number of arguments
		byte[] buffer = new byte[4];		//allocate 4 bytes
	
		for (int i = 0; i < argc; i++) 
		{
			if (readVirtualMemory(argv + i * 4, buffer) != 4)	//read argument
				return -1;
			args[i] = readVirtualMemoryString(Lib.bytesToInt(buffer, 0),maxFileNameLength);	
			//get filename pointer for each input argument
			if (args[i] == null)
				return -1;
		}
	
		UserProcess child = newUserProcess();
		childProcesses.add(child.PID);
	
		saveState();
	
		if (!child.execute(fileName, args)) {
			Lib.debug(dbgProcess, "failed to execute child");
			return -1;
		}
	
		Lib.debug(dbgProcess, "returning child processID");
		return child.PID;
	}

	protected int handleJoin(int processID, int status) {
		/*Andrew*/
		if (!childProcesses.contains(processID)) 
		{
			Lib.debug(dbgProcess, "processID does not refer to a child process of current process");
			return -1;
		}
	
		childProcesses.remove(processID);				//remove child from parents children list
		//can't join more than once
		
		UserProcess child = allProcesses.get(processID);
	
		if (child == null) {
			Lib.debug(dbgProcess, "joining an exited process");
			child = terminatedProcesses.get(processID);
			//child ran already
			if (child == null) {
				Lib.debug(dbgProcess, "error in join");
				//child never existed
				return -1;
			}
		}
	
		child.terminated.P();
	
		writeVirtualMemory(status, Lib.bytesFromInt(child.status));
	
		if (child.exitNormally)
		{
			Lib.debug(dbgProcess, "in handleJoin:child exited normally");
			return 1;
		}
		else
			return 0;
	}

	protected int handleExit(int status){ 		
		/*Andrew*/
		this.status = status;
		
		for (int i = 2; i < maxOpenFiles; i++)
			descriptorManager.close(i);
	
		unloadSections();		
	
		allProcesses.remove(PID);				//remove from alive
		terminatedProcesses.put(PID, this);		//add to terminated
	
		terminated.V();							//make non 0
	
		if (allProcesses.isEmpty())
			Kernel.kernel.terminate();
	
		UThread.finish();
	
		return 0;
	}

	protected static final int syscallHalt = 0, syscallExit = 1,
			syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
			syscallOpen = 5, syscallRead = 6, syscallWrite = 7,
			syscallClose = 8, syscallUnlink = 9;

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
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
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
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallCreate:
			return handleCreat(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			exitNormally = false;
			handleExit(-1);
			return -1;
		}
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0), processor
							.readRegister(Processor.regA1), processor
							.readRegister(Processor.regA2), processor
							.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			exitNormally = false;
			handleExit(-1);
			return;
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

	protected int initialPC, initialSP;
	protected int argc, argv;

	protected int PID;

	/** The value which this process returns. */
	protected int status;

	protected Semaphore terminated;

	protected HashSet<Integer> childProcesses;

	protected FileDescriptor descriptorManager;
	
	protected boolean exitNormally = true;

	protected static final int maxOpenFiles = 16;

	/** All of the opening files and how many processes refer to them */
	protected static Hashtable<String, Integer> allExistingFiles = new Hashtable<String, Integer>();

	/** The files are going to be deleted */
	protected static HashSet<String> terminatedFiles = new HashSet<String>();

	protected static final int pageSize = Processor.pageSize;
	protected static final char dbgProcess = 'a';
	protected static final int maxFileNameLength = 256;

	protected static int processNumber = 0;

	protected static Hashtable<Integer, UserProcess> allProcesses = new Hashtable<Integer, UserProcess>();
	protected static Hashtable<Integer, UserProcess> terminatedProcesses = new Hashtable<Integer, UserProcess>();
	
	public class FileDescriptor
	{
		int references;
		boolean delete;
		
		public  boolean referenceFile(String fileName) {
			FileDescriptor ref = updateFileReference(fileName);
			boolean canReference = !ref.delete;
			if (canReference)
				ref.references++;
			finishUpdateFileReference();
			return canReference;
		}

		/**
		 * Decrement the number of active references there are to a file
		 * Delete the file if necessary
		 * @return
		 * 		0 on success, -1 on failure
		 */
		public  int unreferenceFile(String fileName) {
			FileDescriptor ref = updateFileReference(fileName);
			ref.references--;
			Lib.assertTrue(ref.references >= 0);
			int ret = removeIfNecessary(fileName, ref);
			finishUpdateFileReference();
			return ret;
		}

		/**
		 * Mark a file as pending deletion, and delete the file if no active references
		 * @return
		 * 		0 on success, -1 on failure
		 */
		public  int deleteFile(String fileName) {
			FileDescriptor ref = updateFileReference(fileName);
			ref.delete = true;
			int ret = removeIfNecessary(fileName, ref);
			finishUpdateFileReference();
			return ret;
		}

		/**
		 * Remove a file if marked for deletion and has no active references
		 * Remove the file from the reference table if no active references
		 * THIS FUNCTION MUST BE CALLED WITHIN AN UPDATEFILEREFERENCE LOCK!
		 * @return
		 * 		0 on success, -1 on failure to remove file
		 */
		private  int removeIfNecessary(String fileName, FileDescriptor ref) {
			if (ref.references <= 0) {
				globalFileReferences.remove(fileName);
				if (ref.delete == true) {
					if (!UserKernel.fileSystem.remove(fileName))
						return -1;
				}
			}
			return 0;
		}

		/**
		 * Lock the global file reference table and return a file reference for modification.
		 * If the reference doesn't already exist, create it.
		 * finishUpdateFileReference() must be called to unlock the table again!
		 *
		 * @param fileName
		 * 		File we with to reference
		 * @return
		 * 		FileRef object
		 */
		private  FileDescriptor updateFileReference(String fileName) {
			globalFileReferencesLock.acquire();
			FileDescriptor ref = globalFileReferences.get(fileName);
			if (ref == null) {
				ref = new FileDescriptor();
				globalFileReferences.put(fileName, ref);
			}

			return ref;
		}

		/**
		 * Release the lock on the global file reference table
		 */
		private  void finishUpdateFileReference() {
			globalFileReferencesLock.release();
		}

		/** Global file reference tracker & lock */
		private HashMap<String, FileDescriptor> globalFileReferences = new HashMap<String, FileDescriptor> ();
		private Lock globalFileReferencesLock = new Lock();
		
		
		public int add(int index, OpenFile file) 
		{
			if (index < 0 || index >= maxOpenFiles)
				return -1;
	
			if (descriptor[index] == null) 
			{
				descriptor[index] = file;
				if (allExistingFiles.get(file.getName()) != null) {
					allExistingFiles.put(file.getName(), allExistingFiles.get(file.getName()) + 1);
				}
				else {
					allExistingFiles.put(file.getName(), 1);
				}
				Lib.debug(dbgProcess, "fileName: " + descriptor[index].getName()
						+ " added with iterator:"+index);
				return index;
			}
	
			return -1;
		}
	
		public int add(OpenFile file) 
		{
			for (int i = 0; i < maxOpenFiles; i++)
				if (descriptor[i] == null)
					return add(i, file);
	
			return -1;
		}
	
		public int close(int fileDescriptor) 
		{
			if (fileDescriptor < 0 || fileDescriptor >= maxOpenFiles)
			{
				Lib.debug(dbgProcess, "file descriptor out of bounds");
				return -1;
			}
			
			if (descriptor[fileDescriptor] == null) {
				Lib.debug(dbgProcess, "file descriptor " + fileDescriptor
						+ " doesn't exist");
				return -1;
			}
			OpenFile file = descriptor[fileDescriptor];
			descriptor[fileDescriptor] = null;
			file.close();
			Lib.debug(dbgProcess, "file descriptor " + fileDescriptor
					+ " closed, starting cleanup");
			String fileName = file.getName();
	
			if (allExistingFiles.get(fileName) > 1)
				allExistingFiles.put(fileName, allExistingFiles.get(fileName) - 1);
			else 
			{
				allExistingFiles.remove(fileName);
				if (terminatedFiles.contains(fileName)) 
				{
					Lib.debug(dbgProcess, "calling deleted list.remove()");
					terminatedFiles.remove(fileName);
					UserKernel.fileSystem.remove(fileName);
				}
			}	
			return 0;
		}
		public OpenFile get(int fileDescriptor) 
		{
			if (fileDescriptor < 0 || fileDescriptor >= maxOpenFiles)
				return null;
			return descriptor[fileDescriptor];
		}
		public int getFileDesc() 
		{
			for (int i = 0; i < descriptor.length; i++) {
				if (descriptor[i] == null)
					return i;
			}
			return -1;
		}
		
		
		
		//variable : array of OpenFiles, up to 16.
		public OpenFile descriptor[] = new OpenFile[maxOpenFiles];
	}
}
