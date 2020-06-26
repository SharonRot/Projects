/*****************************************/
/*                                       */
/*   Wd_utils                            */
/*                                       */
/*****************************************/
#ifndef ILRD_WD_UTILS_H
#define ILRD_WD_UTILS_H

extern volatile int dnr_flag;

int DNRCheck(void *data);

int SendSignal(void *pid);

#endif
