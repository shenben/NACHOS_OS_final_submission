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


	     //int unlink(char *name);
	     unlink(myFile);

	     close(2);

	    /* not reached */
}
