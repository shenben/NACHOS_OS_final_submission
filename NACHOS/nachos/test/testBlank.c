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

	     	//assuming create works, files[0-2] are existing
	     close(2);
	     //if close is correct:
	    //when Nachos exits, this test will show that files 2-15 don't exist


	    /* not reached */
}
