/* testBlank.c
 *
 *	
 *	Just do a series of syscalls that creates an empty file.
 * 		then writes to it
 * 			then reads(prints)
 *
 */

#include "syscall.h"

 
int main()
{
	//input parameter: char *name
		char *myFile = "name1";
	    //int name= 2;
	     creat(myFile);

	     int a=200;

	     int * buff;

	     int count = 4;

	     //int write(int fileDescriptor, void *buffer, int count);

	     write(2, buff, count);

	     //close(2);

	     //int read(int fileDescriptor, void *buffer, int count);
	     read(2, buff, count);

	     //open(myFile);

	    /* not reached */
}
