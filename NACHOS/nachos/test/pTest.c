#include "syscall.h"


//run test4.c
 
int main()
{

	char* myFile= "test4.coff";
	int argc;
	char * argv[5];

	//int exec(char *file, int argc, char *argv[]);

	exec(myFile, argc, argv);
	//int join(int processID, int *status);
	//void exit(int status);

}
