/*****************************************/
/* OL79                                  */
/* Watcher                               */
/* 26/11/19                              */
/* Author- Sharon Rottner                */
/* Reviewer- Erez Barr                   */
/*****************************************/
#define _POSIX_C_SOURCE 200112L
#include <pthread.h> /* pthread func */  
#include <stdio.h> /* printf */ 
#include <stdlib.h> /* getenv */
#include <unistd.h> /* fork */
#include <semaphore.h> /* sem_t */
#include <fcntl.h> /* O_CREAT */
#include <signal.h> /* sigaction */

#include "watcher.h"
#include "wd_utils.h"
#include "scheduler.h"

#define UNUSED(x) ((void)(x))

enum status
{
    SUCCESS = 0,
    FAIL = 1
};

typedef struct wrapper
{
    char **argv;
    int *thread_status;
}wrapper_t;

typedef struct is_alive_param
{
    int dead_time;
    scheduler_t *scheduler;
}is_alive_t;
        
static void *ThreadFunc(void *wrapper);
static int IsAlive(void *action_func_param);
static void CreateWD(wrapper_t *wrapper);
static void HandlerFuncThread();
static void ExitThread(int *thread_status, sem_t *sem);

int counter_thread = 0; 
pid_t pid_to_signal = {0}; 
volatile int dnr_flag = 0;
pthread_t thread = 0;

int MMI(char *argv[], int dead_time, int interval)
{
    int thread_status = SUCCESS;
    wrapper_t wrapper = {0};
    char interval_buffer[10] = {0};
    char dead_buffer[10] = {0};
    char app_id_buffer[10] = {0};
    sem_t *mmi_can_run = sem_open("mmi_can_run", O_CREAT, 0644, 0);
    if (SEM_FAILED == mmi_can_run)
    {
        return FAIL;
    }
    
    if (-1 == sprintf(interval_buffer, "%d", interval))
    {
        return FAIL;
    }
    
    if (-1 == sprintf(dead_buffer, "%d", dead_time))
    {
        return FAIL;
    } 
    
    if(-1 == sprintf(app_id_buffer, "%d", getpid()))
	{
		return FAIL;
	}
           
    setenv("INTERVAL", interval_buffer, 0);    
    setenv("DEAD_TIME", dead_buffer, 0);
    setenv("APP_ID",app_id_buffer, 0);
       
    wrapper.argv = argv;
    wrapper.thread_status = &thread_status;
    
    if (0 != pthread_create(&thread, NULL, ThreadFunc, &wrapper))
    {
        sem_unlink("mmi_can_run");
        return FAIL;
    }
    
    sem_wait(mmi_can_run);
    sem_unlink("mmi_can_run");

    return thread_status; 
}

static void *ThreadFunc(void *wrapper)
{
    struct sigaction thread_handler = {0};
    ilrd_uid_t task_uid = {0};
    int interval = 0;
    int dead_time = 0;
    pid_t pid = 0;
    char *wd_id = NULL;
    is_alive_t is_alive_param = {0};
    scheduler_t *scheduler = NULL;
    sem_t *wd_ready = NULL;
    sem_t *thread_ready = NULL;
    sem_t *mmi_can_run = NULL;
    wrapper_t param = {0};
    
    counter_thread = 0;
    param.argv = ((wrapper_t *)wrapper)->argv;
    param.thread_status = ((wrapper_t *)wrapper)->thread_status;
    interval = atoi(getenv("INTERVAL"));
	dead_time = atoi(getenv("DEAD_TIME"));
    wd_id = getenv("WD_ID");
    
    wd_ready = sem_open("wd_ready", O_CREAT, 0644, 0);
    if (SEM_FAILED == wd_ready)
    {
        sem_unlink("wd_ready");
        ExitThread(param.thread_status, mmi_can_run);
    } 
       
    thread_ready = sem_open("thread_ready", O_CREAT, 0644, 0);
    if (SEM_FAILED == thread_ready)
    {
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        ExitThread(param.thread_status, mmi_can_run);
    } 
       
    mmi_can_run = sem_open("mmi_can_run", O_CREAT, 0644, 0);
    if (SEM_FAILED == mmi_can_run)
    {
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        sem_unlink("mmi_can_run");
        ExitThread(param.thread_status, mmi_can_run);
    }
    
    if (NULL != wd_id)
    {
       pid_to_signal = (pid_t)atoi(wd_id);
    } 

    thread_handler.sa_sigaction = HandlerFuncThread;
    if (-1 == sigaction(SIGUSR1, &thread_handler, NULL))
    {
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        sem_unlink("mmi_can_run");
        ExitThread(param.thread_status, mmi_can_run);
    }

    scheduler = SchedCreate();
    if (NULL == scheduler)
    {
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        sem_unlink("mmi_can_run");
        ExitThread(param.thread_status, mmi_can_run);       
    }
    
    is_alive_param.dead_time = dead_time;
    is_alive_param.scheduler = scheduler;
    
    task_uid = SchedAdd(scheduler, interval, IsAlive, &is_alive_param);
    if (UIDIsBad(task_uid))
    {
        SchedDestroy(scheduler);
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        sem_unlink("mmi_can_run");        
        ExitThread(param.thread_status, mmi_can_run);
    }
    
    task_uid = SchedAdd(scheduler, interval, SendSignal, &pid_to_signal);
    if (UIDIsBad(task_uid))
    {
        SchedDestroy(scheduler); 
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        sem_unlink("mmi_can_run");      
        ExitThread(param.thread_status, mmi_can_run);
    }
    
    task_uid = SchedAdd(scheduler, interval, DNRCheck, scheduler);
	if (UIDIsBad(task_uid))
	{	
        SchedDestroy(scheduler);
        sem_unlink("wd_ready");
        sem_unlink("thread_ready");
        sem_unlink("mmi_can_run");       
        ExitThread(param.thread_status, mmi_can_run);	
	}

    if (NULL == wd_id)
    {
        printf("wd created first time\n");
        counter_thread = 0;
        pid = fork();
        
        if (-1 == pid)
        {
            SchedDestroy(scheduler);
            sem_unlink("wd_ready");
            sem_unlink("thread_ready");
            sem_unlink("mmi_can_run");
            ExitThread(param.thread_status, mmi_can_run);
        }
        
        if (0 == pid)
        {
            CreateWD(&param); 
        }
        
        else
        {
            pid_to_signal = pid;
        }
    }

    while (0 == dnr_flag)
    {
        if (dead_time == counter_thread)
        {
            printf("wd restarted\n");
            counter_thread = 0;
            pid = fork();
            printf("after fork\n");
            if (-1 == pid)
            {
                SchedDestroy(scheduler);
                sem_unlink("wd_ready");
                sem_unlink("thread_ready");
                sem_unlink("mmi_can_run");
                ExitThread(param.thread_status, mmi_can_run);
            }
        
            if (0 == pid)
            {
                printf("inside if\n");
                CreateWD(&param); 
            }
        }
        
        sem_post(thread_ready);
        printf("thread ready\n");
        sem_wait(wd_ready);
        printf("thread run\n");
        sem_post(mmi_can_run);
        SchedRun(scheduler);
     }
     
    sem_unlink("wd_ready");
    sem_unlink("thread_ready");
    SchedDestroy(scheduler); 
    
    return NULL;
}

static int IsAlive(void *action_func_param)
{
    ++counter_thread;
     
    printf("counter %d\n", counter_thread);
	
	if (((is_alive_t *)action_func_param)->dead_time == counter_thread)
    { 
        printf("inside if counter %d\n", counter_thread);
        SchedStop(((is_alive_t *)action_func_param)->scheduler);
    }
    
	return 1;
} 

static void CreateWD(wrapper_t *wrapper)
{
    int exec_return_value = execvp("./wd.out",(wrapper->argv));
    
    if (-1 == exec_return_value)
    {
        printf("Error\n");
    } 
}

static void HandlerFuncThread()
{
    printf("signal2\n");
    sleep(1);
    counter_thread = 0;
}

void DNR()
{
	printf("DNR Called!\n");
	dnr_flag = 1;
	kill(pid_to_signal, SIGUSR2);
	pthread_join(thread, NULL); 
}

static void ExitThread(int *thread_status, sem_t *sem)
{
    *thread_status = FAIL;
    sem_post(sem);
    pthread_exit(NULL);
}
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       
