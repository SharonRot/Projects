package il.co.ilrd.threadpool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import il.co.ilrd.waitablequeue.WaitableQueueSem;

public class ThreadPool implements Executor {	
	private List<TPThread> threadsList = new ArrayList<>();
	private WaitableQueueSem<ThreadPoolTask<?>> tasksQueue = new WaitableQueueSem<>();;
	private final static int DEAFULT_NUM_THREADS = Runtime.getRuntime().availableProcessors();
	private int numOfThreads;
	private static Semaphore pauseSem = new Semaphore(0);
	private Semaphore awaitSemaphore = new Semaphore(0); 
	private boolean isShutdown = false;
	
	public enum TaskPriority {
		MIN,
		NORM,
		MAX,
		NUM_PRIORITY
	}
	
	public ThreadPool() {
		this(DEAFULT_NUM_THREADS);
	}
	
	public ThreadPool(int numOfThreads) {
		if(numOfThreads < 0) {
			throw new InvalidNumOfThreadException();
		}
		
		this.numOfThreads = numOfThreads;
		createAndStartThread(numOfThreads);
	}
	
	private void createAndStartThread(int numOfThreads) {
		for(int i = 0; i < numOfThreads; ++i) {
			TPThread thread = new TPThread();
			threadsList.add(thread);
			thread.start();
		}
	}
	
	public <T> Future<T> submitTask(Callable<T> callable) {
		return submitTask(callable, TaskPriority.NORM);
	}
	
	public <T> Future<T> submitTask(Callable<T> callable, TaskPriority taskPriority) { 
		return submitTask(callable, taskPriority.ordinal());
	}
	
	private <T> Future<T> submitTask(Callable<T> callable, int taskPriority) {
		if(isShutdown) {
			throw new ShuttingDownException();
		}
		
		ThreadPoolTask<T> poolTask= new ThreadPoolTask<T>(taskPriority, callable);
		tasksQueue.enqueue(poolTask);
		
		return poolTask.getFuture();
	}
	
	public Future<Void> submitTask(Runnable runnable, TaskPriority taskPriority) {
		return submitTask(Executors.callable(runnable, null) , taskPriority);
	}
	
	public <T> Future<T> submitTask(Runnable runnable, TaskPriority taskPriority, T returnVal) {
		return submitTask(Executors.callable(runnable, returnVal) , taskPriority);
	}
	
	public void setNumberOfThread(int newNumOfThreads) {
		if(numOfThreads < newNumOfThreads) {
			createAndStartThread(newNumOfThreads - numOfThreads);
		}
		
		else {
			for(int i = numOfThreads - newNumOfThreads; i > 0; --i) {
				submitTask(new StopTask(), TaskPriority.NUM_PRIORITY.ordinal());
			}
		}
		
		numOfThreads = newNumOfThreads;
	}
	
	@Override
	public void execute(Runnable runnable) {
		submitTask(runnable, TaskPriority.NORM);
	}
	
	public void pause() {
		for(int i = numOfThreads; i > 0; --i) {
			submitTask(new pauseTask(), TaskPriority.NUM_PRIORITY.ordinal());
		}
	}
	
	public void resume() {
		pauseSem.release(numOfThreads);
	}

	public void shutdown() {
		awaitSemaphore.drainPermits();
		for(int i = numOfThreads; i > 0; --i) {
			submitTask(new StopTask(), TaskPriority.MIN.ordinal() - 1);
		}
		isShutdown = true;
	}

	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return awaitSemaphore.tryAcquire(numOfThreads, timeout, unit);
	}
/*************************************Tasks***************************************************/
	private class StopTask implements Callable<Void> {
		@Override
		public Void call() {
			TPThread currentThread = (TPThread)Thread.currentThread();
			currentThread.toRun = false;
			threadsList.remove(currentThread);
			awaitSemaphore.release();
			
			return null;
		}
	}
	
	private class pauseTask implements Callable<Void> {
		@Override
		public Void call() throws Exception {
			pauseSem.acquire();
			return null;
		}
	}
/**************************************TPThread*************************************************/	
	private class TPThread extends Thread {
		private boolean toRun = true;
		
		@Override
		public void run() {
			ThreadPoolTask<?> currTask = null;
			while(toRun) {
				try {
					currTask = tasksQueue.dequeue();
					currTask.runTask();
				} catch (Exception e) {
					currTask.taskFuture.exception = new ExecutionException(e);
					currTask.runTaskSem.release();
				}
			}
		}
	}
	
/**************************************ThreadPoolTask*************************************************/	
	private class ThreadPoolTask<T> implements Comparable<ThreadPoolTask<T>> {	
		private int taskPriority;
		private Callable<T> callable;
		private TaskFuture taskFuture = new TaskFuture();
		private Semaphore runTaskSem = new Semaphore(0);
		private boolean isDone = false;
		private boolean isCancelled = false;
		
		public ThreadPoolTask(int taskPriority, Callable<T> callable) {
			this.taskPriority = taskPriority;
			this.callable = callable;
		}
		
		public TaskFuture getFuture() {
			return taskFuture;
		}
		
		@Override
		public int compareTo(ThreadPoolTask<T> task) {
			return task.taskPriority - this.taskPriority;
		}

		private void runTask() throws Exception {
			if(isCancelled) {
				isDone = true;
				throw new TaskIsCancelledException();
			}
			
			taskFuture.returnObj = callable.call();
			isDone = true;
			runTaskSem.release();
		}

/**************************************TaskFuture*************************************************/			
		private class TaskFuture implements Future<T> {
			private T returnObj = null;
			ExecutionException exception = null;
			
			@Override
			public boolean cancel(boolean arg0) {
				isCancelled = tasksQueue.remove(ThreadPoolTask.this);

				return isCancelled;
			}

			@Override
			public T get() throws InterruptedException, ExecutionException {
				runTaskSem.acquire();
				
				if(null != exception) {
					throw exception;
				}

				return returnObj;
			}

			@Override
			public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				runTaskSem.tryAcquire(timeout, unit);

				if (null != exception) {
					throw exception;
				}

				return returnObj;
			}

			@Override
			public boolean isCancelled() {
				return isCancelled;
			}

			@Override
			public boolean isDone() {
				return isDone;
			}
		}
	}
/**************************************Exceptions*************************************************/			

	private class TaskIsCancelledException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	private class ShuttingDownException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	private class InvalidNumOfThreadException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}