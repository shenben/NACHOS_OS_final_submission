#include "syscall.h"

/*Client.c meant to attempt a connection*/

int main()
{
	int host=0;
	int path=0;
	return connect(host, path);
}
