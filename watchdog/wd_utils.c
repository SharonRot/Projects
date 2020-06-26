/*****************************************/
/* OL79                                  */
/* wd_utils                              */
/* 20/01/2-                              */
/* Author- Sharon Rottner                */
/* Reviewer- Erez Barr                   */
/*****************************************/
#include <stdio.h> /* printf */
#include <signal.h>

#include "wd_utils.h"
#include "scheduler.h"

int DNRCheck(void *data)
{
	if(dnr_flag == 1)
	{
		SchedStop((scheduler_t *)data);
		printf("dnr killed me\n");
	}
	
	return 1;
}

int SendSignal(void *pid)
{
	kill(*(pid_t *)pid, SIGUSR1); 
	
	return 1;
}

 
