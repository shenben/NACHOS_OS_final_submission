/* testBlank.c
 *	Simple program to test whether running a user program works.
 *	
 *	Just do a "syscall" that creates an empty file.
 * 	
 */ 

#include "syscall.h"

 
int main()
{
	//input parameter: char *name
		char *myFile = "name1";
	    //int name= 2;
	     creat(myFile);

	     close(2);

	     open(myFile);

	    /* not reached */
}
