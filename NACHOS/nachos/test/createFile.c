/* createFile.c
 *	Simple program to test whether running a user program works.
 *	
 *	Just do a "syscall" that creates an empty file.
 * 	
 */ 

#include "syscall.h"





/**
 * Attempt to open the named disk file, creating it if it does not exist,
 * and return a file descriptor that can be used to access the file.
 *
 * Note that creat() can only be used to create files on disk; creat() will
 * never return a file descriptor referring to a stream.
 *
 * Returns the new file descriptor, or -1 if an error occurred.
 */
 
 
int main()
{

	//input parameter: char *name
	char *myFile = "name1";
    
    return creat(myFile);
    
    /* not reached */
}
