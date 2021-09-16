package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.*;

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
	public UserProcess() {

		/*
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		*/
        
		// Allocate the space for stdin and stdout
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();
        
		// Making other space in file table to null. 
		for (OpenFile item : fileTable) {
			item = null;
		}

		// Get the new pid from kernel
		UserKernel.lock2.acquire();
		this.pid = UserKernel.getNewPID();
		UserKernel.lock2.release();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
        
		// Increase the number of processes 
		UserKernel.numProcess++;

		thread = new UThread(this);
		thread.setName(name).fork();

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
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
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
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
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
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int amount = 0;
        // While there are remaining data in memory, we continue to loop.
		while(length > 0) {

			// Get the virtual page number and page offset based on the address.
			int vpn = Processor.pageFromAddress(vaddr);
			int pageoffset = Processor.offsetFromAddress(vaddr);
            
			// Stop reading if vpn is not valid.
			if (vpn < 0 || vpn >= pageTable.length) {
				break;
			}

			// Find the index of pageTable, which contains physical page number.
			int ppn = pageTable[vpn].ppn;
            
			// Stop reading if ppn is -1 (null).
			if (ppn == -1) {
				break;
			}

			// We calculate the pysical address, and get the length of memory.
			int paddr = ppn * pageSize + pageoffset;
			int max = ((pageSize - pageoffset) < length) ? (pageSize - pageoffset) : length;

			// We copy the data in physical memory's page to the data array.
			System.arraycopy(memory, paddr, data, offset, max);
            
			// We update the amount of transfering data, remaining data length.
			amount += max;
			length -= max;

			// We also update the vritual address, and the offset (they are continued).
			vaddr += max;
			offset += max;
		}

		// for now, just assume that virtual addresses equal physical addresses
		//if (vaddr < 0 || vaddr >= memory.length)
		//	return 0;

		//int amount = Math.min(length, memory.length - vaddr);
		//System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
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
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int amount = 0;
        // While there are remaining data in memory, we continue to loop.
		while (length > 0) {
            
			// Get the virtual page number and page offset based on the address.
	    	int vpn = Processor.pageFromAddress(vaddr);
			int pageoffset = Processor.offsetFromAddress(vaddr);
            
			// Stop writing if vpn is invalid.
			if (vpn < 0 || vpn >= pageTable.length) {
				break;
			}

			// Find the index of pageTable, which contains physical page number.
			int ppn = pageTable[vpn].ppn;
            
			// Stop writing if ppn is -1 (null).
			if (ppn == -1) {
				break;
			}

			// We calculate the pysical address, and get the length of memory.
			int paddr = ppn*pageSize + pageoffset;
			int max = ((pageSize - pageoffset) < length) ? (pageSize - pageoffset) : length;

			// We copy the data in physical memory's page to the data array.
			System.arraycopy(data, offset, memory, paddr, max);
            
			// We update the amount of transfering data, remaining data length.
			amount += max;
			length -= max;

			// We also update the vritual address, and the offset (they are continued).
			vaddr += max;
			offset += max;
		}


		// for now, just assume that virtual addresses equal physical addresses
		//if (vaddr < 0 || vaddr >= memory.length)
		//	return 0;

		//int amount = Math.min(length, memory.length - vaddr);
		//System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
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
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
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

		// We use lock to ensure the safety of allocating memory for current process.
		UserKernel.lock.acquire();

		if (numPages > Machine.processor().getNumPhysPages()) {
			UserKernel.lock.release();
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

        // We create a page table for the memory allocation in this process.
		pageTable = new TranslationEntry[numPages];

		// We loop thourgh each virtual pages, and assign the page table (vpn - ppn).
		for (int vpn = 0; vpn < numPages; vpn++) {
            
			// We find the first avaliable physical memory, and make translation in table.
			Integer ppn = UserKernel.avaliablePages.removeFirst(); 
	    	pageTable[vpn] = new TranslationEntry(vpn, ppn.intValue(), true, false, false, false);
		}

		UserKernel.lock.release();

		// load sections, we loop through every sections in this program.
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
                
				// We make that vpn's memory ready, and load the sections to that memory.
                pageTable[vpn].readOnly = section.isReadOnly();
				section.loadPage(i, pageTable[vpn].ppn);

				// for now, just assume virtual addresses=physical addresses
				//section.loadPage(i, vpn);
			}
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
        
		// We use lock to ensure the safety.
		UserKernel.lock.acquire();
        
		// We loop through every entry of page table
		for (int vpn=0; vpn<pageTable.length; vpn++) {
            
			// We make that physical memory to avaliable, and set entry to null. 
	    	UserKernel.avaliablePages.add(new Integer(pageTable[vpn].ppn));
	    	pageTable[vpn] = null;
		}

		// We finally make that table to null, and close the running program.
		pageTable = null;
		//coff.close();

		UserKernel.lock.release();
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
		for (int i = 0; i < processor.numUserRegisters; i++)
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
	private int handleHalt() {

		// Can't halt if its not the root process
		if (this.pid != 0) {
			return -1;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {

	    // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process

		// Close the files
		for (int fd = 0; fd < 16; fd++) {
			handleClose(fd);
		}

		// We free every sections memory, and close the running program.
		unloadSections();
		coff.close();

		UserKernel.lock2.acquire();
        
		// If there is parent process, we try to record the child status to parent. 
		if (parentProcess != null) {
			
			// If there is exception, we make the status as null, else record status.
			if (unknownException) {
				parentProcess.childStatus.put(new Integer(pid), null);

			} else {
				parentProcess.childStatus.put(new Integer(pid), new Integer(status));
			}
            
			// We wake up the parent to wait queue.
			//parentProcess.childProcesses.remove (new Integer(this.pid));
			parentProcess.cv.wake();
		}
        
		// We also iterate every child in current process, and delete their parent reference.
		for (Integer key : childProcesses.keySet()) {
			UserProcess children = childProcesses.get(key);
			children.parentProcess = null;
		}
		
		// We decrease the number of processes in global kernel. 
		UserKernel.numProcess--;
        
		// If we exit the last processes, we just terminate the entire kernel. 
		if (UserKernel.numProcess == 0) {
			Kernel.kernel.terminate();
		}

		UserKernel.lock2.release();
		KThread.finish();

		//Kernel.kernel.terminate();
		return 0;
	}

    /**
	 * Handle the create() system call.
	 * @param address the input virtual address of created file.
	 */
	private int handleCreate (int address) {
        
		// Translate the virtual address to physical address 
        String file_name = readVirtualMemoryString (address, 256);
        
		// Return -1 if the address is invalid.
		if (file_name == null) {
			return -1;
		}
        
		// In kernel, create a new file based on the physical address. 
		OpenFile file = ThreadedKernel.fileSystem.open (file_name, true);
        
		// Return -1 if that address is invalid. 
		if (file == null) {
			return -1;
		}
        
		// We loop through the fileTable, and put created file into free space.
		for (int i = 0; i < 16; i ++) {
			if (fileTable[i] == null) {
				fileTable[i] = file;

				// Return the index of file in table. 
				return i;
			}
		}
        
		// Return -1 if there is no free space in table. 
        return -1; 
	}
    
	/**
	 * Handle the open() system call.
	 * @param address the input virtual address of opened file.
	 */
	private int handleOpen(int address) {

        // Translate the virtual address to physical address 
        String file_name = readVirtualMemoryString (address, 256);

		// Return -1 if the address is invalid.
		if (file_name == null) {
			return -1;
		}

		// In kernel, open a new file based on the physical address. 
		OpenFile file = ThreadedKernel.fileSystem.open (file_name, false);
        
		// Return -1 if that address is invalid. 
		if (file == null) {
			return -1;
		}
        
		// We loop through the fileTable, and put opened file into free space.
		for (int i = 0; i < 16; i ++) {
			if (fileTable[i] == null) {
				fileTable[i] = file;

				// Return the index of file in table. 
				return i;
			}
		}
        
		// Return -1 if there is no free space in table. 
        return -1; 
	}
    
	/**
	 * Handle the read() system call.
	 * @param fd the file descriptor showing the position of reading file in table.
	 * @param address the virtual address we want to read the file
	 * @param size the length we want to read from this file. 
	 */
	private int handleRead (int fd, int address, int size) {

		// If there are any invalid parameter, just return -1 as fail.
		if (fd < 0 || fd >= 16 || address < 0 || size < 0) {
			return -1;
		}

        // Initialize the return value as our final reading bytes.
		int final_read = 0;
        
		// We create a read buffer with 1024 size storing each read bytes.
		byte [] readBuffer = new byte[1024];
        
		// Try to get the file from table by its descriptor, if not existed, return -1.
		OpenFile file = fileTable[fd];

		if (file == null) {
			return -1;
		}
        
		// Continue loop until we read all required bytes. 
		while (size > 0) {

			// We first get the reading length in this turn, should be 1024 or less.
			int readLength = (size > 1024) ? 1024 : size;

			// Then, we try to read the bytes from file to buffer with reading length.
			int success_read = file.read (readBuffer, 0, readLength);

			// If we failed to read, just return -1.
            if (success_read == -1) {
				 return -1;
			}

			// Then, we try to write the buffer's bytes to the virtual memory.
			int success_write = writeVirtualMemory (address, readBuffer, 0, success_read); 
            
			// If we fail to write these buffer's bytes, return -1.
			if (success_write == -1) {
				return -1;
			}
            
			// We update the length of required read, the memory address, and return value.
			size -= success_write;
			address += success_write;
			final_read += success_write;
            
			// If the bytes we read are smaller than expected, we reach the end of file.
			if (success_write < readLength) {
				break;
			}
		}
        
		// We finally return the count of the final reading bytes. 
		return final_read;
	}

	/**
	 * Handle the read() system call.
	 * @param fd the file descriptor showing the position of writing file in table.
	 * @param address the virtual address we want to write from.
	 * @param size the length we want to write to this file. 
	 */
	private int handleWrite (int fd, int address, int size) {

		// If there are any invalid parameter, just return -1 as fail.
		if (fd < 0 || fd >= 16 || address < 0 || size < 0) {
			return -1;
		}

        // Initialize the return value as our final writing bytes.
		int final_write = 0;
		int original_size = size;
        
		// We create a read buffer with 1024 size storing each write bytes.
		byte [] writeBuffer = new byte[1024];
        
		// Try to get the file from table by its descriptor, if not existed, return -1.
		OpenFile file = fileTable[fd];

		if (file == null) {
			return -1;
		}
        
		// Continue loop until we read all required bytes. 
		while (size > 0) {

			// We first get the writing length in this turn, should be 1024 or less.
			int readLength = (size > 1024) ? 1024 : size;

			// Then, we try to read from the virtual memory.
			int success_read = readVirtualMemory (address, writeBuffer, 0, readLength);

			// Then, we try to write these bytes into the file. 
			int success_write = file.write (writeBuffer, 0, success_read);
            
			// If we fail to write these buffer's bytes, break the loop.
			if (success_write == -1) {
				break;
			}
            
			// We update the length of required write, the memory address, and return value.
			size -= success_write;
			address += success_write;
			final_write += success_write;
            
			// If the bytes we write to file is smaller than expected, we reach the end.
			if (success_write < readLength) {
				break;
			}
		}
        
		// We finally return the count of the final writing bytes. 
		if (final_write < original_size) {
			return -1;
		}

		return final_write;
	}
    
	/**
	 * Handle the close() system call.
	 * @param fd the file descriptor that indicating the file location in table.
	 */
	private int handleClose (int fd) {

		// If there are any invalid parameter, just return -1 as fail.
		if (fd < 0 || fd >= 16) {
			return -1;
		}
        
		// We first get the file from the table based on the descriptor.
		OpenFile file = fileTable[fd];
        
		// If we are unable to get that file, just return -1.
		if (file == null) {
			return -1;
		}
        
		// We close that file, and set the containing room in file table to null.
		file.close();
		fileTable[fd] = null;

		return 0; 
	}
    
	/**
	 * Handle the close() system call.
	 * @param address the address of the filename in memory.
	 */
	private int handleUnlink (int address) {
        
		// We get the file name from the memory address.
		String file_name = readVirtualMemoryString (address, 256);
        
		// If we fail to get the name, just return -1.
		if (file_name == null) {
			return -1;
		}
        
		// After we get the file name, we remove that file and return status.
		if (ThreadedKernel.fileSystem.remove (file_name)) {
			return 0;
		}

		return -1;  
	}
    
	/**
	 * Handle the exec() that creates the new process and load it
	 * @param fileNameAddr the address containing the executable file
	 * @param argc number of arguments passed to the child process
	 * @param argv the address of array containing the passed in arguments
	 */
	private int handleExec (int fileNameAddr, int argc, int argv) {

		// We first read the file name from its address
		String file_name = readVirtualMemoryString (fileNameAddr, 256);

		// Return -1 if we fail to get the name or it doesn't contain ".coff"
		if (file_name == null || file_name.contains(".coff") == false || argc < 0 || argc > 16) {
			return -1;
		}

		// Initialize array that stores args
		String[] args = new String[argc];

        // Loop through the address for args
		for (int i = 0; i < argc; i++) {

			// Read bytes into argAdrr
			byte[] argAddr = new byte[4];
			int bytesRead = readVirtualMemory(argv, argAddr);
			
			// Return -1 if reads wrong size, else convert bytes of address to string
			if (bytesRead == 4) {
				args[i] = readVirtualMemoryString(Lib.bytesToInt(argAddr, 0), 256);
				if (args[i] == null) {
					return -1;
				}
			} else {
				return -1;
			}

			// Increase the arg address by 4 at each iteration
			argv += 4;
		}

		// Create new the new child process
		int success = -1;
		UserProcess childProcess = newUserProcess();
		childProcess.parentProcess = this;
		
		UserKernel.lock2.acquire();
		
		// Execute the chilrd process and get the pid of child.
		boolean executed = childProcess.execute(file_name, args);
		int childPID = childProcess.getpid();
        
		// If we successfully excute the child, just store child into parent's list.
		if (executed) {
			childProcesses.put(new Integer(childPID), childProcess);
			success = childPID;
		}

		UserKernel.lock2.release();
		return success;
	}

	/**
	 * Handle the join() that suspending the current process until the child is finished
	 * @param processID the pid of child process
	 * @param statusAddr the address to child process exit status
	 */
	private int handleJoin(int processID, int statusAddr) {

		// Exit if the child process does not belong to parent process
		if (!childProcesses.containsKey(new Integer(processID))){
			return -1;
		}
    
		int success = -1;

		UserKernel.lock2.acquire();
        
		// If we didn't get the exit status from children pid, we sleep current process until waked up.
		while (!childStatus.containsKey(new Integer(processID))) {
			cv.sleep();
		}
        
		// When it is waked up by its joined child, we check its child's exit status
		Integer child_status = childStatus.get(new Integer(processID));
		
		// If status is null, meaning there is exception, return 0.
		if (child_status == null) {
			success = 0;
		
		} else {
			// Otherwise, we write that status to the parameter status address memory.
			byte[] statusByte = Lib.bytesFromInt(child_status.intValue());
			int success_write = writeVirtualMemory(statusAddr, statusByte);

			// If we successfully write to memory, return 1, else return 0.
			if (success_write == -1) {
				success = 0;
			} else {
				success = 1;
			}
		}

		UserKernel.lock2.release();
		return success;
	}


	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

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
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
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
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {

		case syscallHalt:
			return handleHalt();

		case syscallExit:
			return handleExit(a0);

		case syscallCreate:
		    return handleCreate(a0);

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

		case syscallExec:
			return handleExec(a0, a1, a2);

		case syscallJoin:
			return handleJoin(a0, a1);

		default:
			handleExit(1);  
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
		    unknownException = true;
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			handleExit(0);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	public int getpid() {
		return this.pid;
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private OpenFile[] fileTable = new OpenFile[16];

	private int pid;

	private UserProcess parentProcess = null;

	private Hashtable<Integer, UserProcess> childProcesses = new Hashtable<Integer, UserProcess>(); 

	private Condition cv = new Condition(UserKernel.lock2);

	private Hashtable<Integer, Integer> childStatus = new Hashtable<Integer, Integer>();

	private boolean unknownException = false;

}
