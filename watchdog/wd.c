/*****************************************/
/* OL79                                  */
/* WD                                    */
/* 20/01/20                              */
/* Author- Sharon Rottner                */
/* Reviewer-Erez Barr                    */
/*****************************************/
#define _POSIX_C_SOURCE 200112L
#include <pthread.h> /* pthread func */  
#include <stdio.h> /* printf */ 
#include <stdlib.h> /* getenv */
#include <unistd.h> /* fork */
#include <semaphore.h> /* sem_t */
#include <fcntl.h> /* O_CREAT */
#include <signal.h> /* sigaction */

#include "scheduler.h"
#include "wd_utils.h"

#define UNUSED(x) ((void)(x))

typedef struct is_alive_param
{
    int dead_time;
    scheduler_t *scheduler;
}is_alive_t;

static void HandlerFuncWD();
static int IsAlive(void *action_func_param);
static void CreateApp(char **argv);
static void HandlerDNR();

int counter_wd = 0;
volatile int dnr_flag = 0;

int main(int argc, char *argv[])
{
    struct sigaction wd_handler = {0};
    struct sigaction dnr_handler = {0};
    ilrd_uid_t task_uid = {0};
    scheduler_t *scheduler = NULL;
    pid_t pid = 0;
    pid_t pid_to_signal = {0};
    int interval = 0;
	int dead_time = 0;
    sem_t *wd_ready = NULL;
    sem_t *thread_ready = NULL;
    char *app_id = NULL;
    is_alive_t is_alive_param = {0};
    char wd_id_buffer[8] = {0};
    UNUSED(argc);

    counter_wd = 0;
    app_id = getenv("APP_ID");
	pid_to_signal = atoi(app_id);
	interval = atoi(getenv("INTERVAL"));
	dead_time = atoi(getenv("DEAD_TIME"));
	
	wd_ready = sem_open("wd_ready", O_CREAT, 0644, 0);
    if (SEM_FAILED == wd_ready)
    {
        sem_unlink("wd_ready");
        exit(0);
    } 
       
    thread_ready = sem_open("thread_ready", O_CREAT, 0644, 0);
    if (SEM_FAILED == thread_ready)
    {
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        exit(0);
    } 
   
    if (-1 == sprintf(wd_id_buffer, "%d", getpid()))
    {
       sem_unlink("wd_ready");
       sem_unlink("thread_ready"); 
       exit(0);
    }

    setenv("WD_ID", wd_id_buffer, 0);
	dnr_handler.sa_handler = HandlerDNR;	
    wd_handler.sa_sigaction = HandlerFuncWD;
    if (-1 == sigaction(SIGUSR1, &wd_handler, NULL))
    {
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        exit(0);
    }
    
    if(-1 == sigaction(SIGUSR2, &dnr_handler, NULL))
	{
	    sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        exit(0);
	}
    
    scheduler = SchedCreate();
    if (NULL == scheduler)
    {
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        exit(0);        
    }
    
    is_alive_param.dead_time = dead_time;
    is_alive_param.scheduler = scheduler;
    
    task_uid = SchedAdd(scheduler, interval, IsAlive, &is_alive_param);
    if (UIDIsBad(task_uid))
    {
        SchedDestroy(scheduler);
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        exit(0);
    }
    
    task_uid = SchedAdd(scheduler, interval, SendSignal, &pid_to_signal);
    if (UIDIsBad(task_uid))
    {
        SchedDestroy(scheduler);
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        exit(0);
    }
    
    task_uid = SchedAdd(scheduler, interval, DNRCheck, scheduler);
	if (UIDIsBad(task_uid))
	{	
        SchedDestroy(scheduler);
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        exit(0);	
	}

    while (0 == dnr_flag)
    {
        if ((dead_time) == counter_wd)
        {
            counter_wd = 0;
            pid = fork();
            if (-1 == pid)
            {
                SchedDestroy(scheduler);
                exit(0);
            }
        
            if (0 == pid)
            {
               CreateApp(argv);
            }
            
            else
            {
               pid_to_signal = pid;
            }
        }
        
        sem_post(wd_ready);
        printf("wd ready\n");
        sem_wait(thread_ready);
        printf("wd run\n");
        SchedRun(scheduler);
    }
    
    sem_close(wd_ready);
    sem_close(thread_ready);
    SchedDestroy(scheduler);
    
    return 0;
}

static void HandlerFuncWD()
{
    printf("signal1\n");
    sleep(1);
    counter_wd = 0;
}

static void HandlerDNR()
{
    printf("wc dnr changed\n");
	dnr_flag = 1;
}

static int IsAlive(void *action_func_param)
{
	 ++counter_wd;
	
	if (((is_alive_t *)action_func_param)->dead_time == counter_wd)
    { 
        SchedStop(((is_alive_t *)action_func_param)->scheduler);
    }
	return 1;
} 

static void CreateApp(char **argv)
{
    int exec_return_value = execvp(argv[0], argv);
    
    if (-1 == exec_return_value)
    {
        printf("Error\n");
    } 
}
