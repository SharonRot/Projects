package il.co.ilrd.waitablequeue;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WaitableQueueSem<T> {
	private PriorityQueue<T> dataQueue;
	private Semaphore numOfElements = new Semaphore(0);
	private Lock lock = new  ReentrantLock();
	
	public WaitableQueueSem() {
		this(null);
	}
	 
	public WaitableQueueSem(Comparator<T> comp) {
		dataQueue = new PriorityQueue<>(comp);
	}
		
	public void enqueue(T elem) {
		 lock.lock();
		 dataQueue.add(elem);
		 lock.unlock();
		 numOfElements.release();
	}

	public T dequeue() throws InterruptedException {
		numOfElements.acquire();
		
		return safeDequeue();
	}
	
	public T dequeue(int timeout,  TimeUnit unit) throws InterruptedException {
		T valToReturn = null;
		if(numOfElements.tryAcquire(timeout, unit)) {
			valToReturn = safeDequeue();
		}
		
		return valToReturn;
	}
	
	public boolean remove (T elem) {
		boolean isRemoved = false;
		if(numOfElements.tryAcquire()) {
			if(lock.tryLock()) {
				isRemoved = dataQueue.remove(elem);
			}
		
			if(false == isRemoved) {
				numOfElements.release();
			}
			
			lock.unlock();
		}
		
		return isRemoved;
	}
	
	private T safeDequeue() {
		lock.lock();
		T valToReturn = dataQueue.poll();
		lock.unlock();
		
		return valToReturn;
	}
}