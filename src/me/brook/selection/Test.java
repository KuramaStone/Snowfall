package me.brook.selection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Test {

	public static void main(String[] args) {

		ExecutorService exe = Executors.newFixedThreadPool(1);

		Future<?> future = null;
		while(true) {
			if(future != null && !future.isDone())
				continue;
			future = exe.submit((Runnable) () -> System.out.println(1));
		}

	}

}
