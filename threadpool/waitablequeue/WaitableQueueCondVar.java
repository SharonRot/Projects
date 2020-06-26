package il.co.ilrd.waitablequeue;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WaitableQueueCondVar<T> {
	private PriorityQueue<T> dataQueue;
	private Lock lock = new ReentrantLock();
	private Condition queueSizeCondition = lock.newCondition();
	
	public WaitableQueueCondVar() {
		dataQueue = new PriorityQueue<>();
	}
	
	public WaitableQueueCondVar(Comparator<T> comp) {
		dataQueue = new PriorityQueue<>(comp);
	}
		
	public void enqueue (T elem) {
		 lock.lock();
		 dataQueue.add(elem);
		 queueSizeCondition.signal();
		 lock.unlock();
	}

	public T dequeue() {
		lock.lock();
		if(dataQueue.isEmpty()) {
			queueSizeCondition.awaitUninterruptibly();
		}
		
		T elem = dataQueue.poll();
		lock.unlock();

		return elem;
	}
	
	public T dequeue(int timeout, TimeUnit unit) throws InterruptedException {
		T returnElem = null;
		lock.lock();
		while(dataQueue.isEmpty()) {
			if(!queueSizeCondition.await(timeout, unit)) {
				lock.unlock();
				
				return dataQueue.poll();
			}
		}
		
		returnElem = dataQueue.poll();
		lock.unlock();
		
		return returnElem;
	}
	
	public boolean remove (T elem) {
		boolean isRemoved = false;
		if(lock.tryLock()) {
			isRemoved = dataQueue.remove(elem);
			lock.unlock();
		}
		
		return isRemoved;
	}
}
