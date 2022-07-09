package me.brook.selection;

import java.util.List;
import java.util.concurrent.Callable;

import me.brook.selection.entity.Agent;
import me.brook.selection.entity.Entity;
import me.brook.selection.tools.Profiler;

public class CalculateTask implements Runnable, Callable<String> {

	private long initTime = System.nanoTime();

	private int id, threads;
	public int processed;

	private World world;

	public CalculateTask(World world, int id) {
		this.world = world;
		this.id = id;
	}

	@Override
	public String call() {
		run();
		return "";
	}

	@Override
	public void run() {

		List<Entity> list = world.getThreadEntities();
		int start = (int) Math.floor(list.size() / World.getThreadCount()) * id;
		int end = (int) (start + Math.floor(list.size() / World.getThreadCount()));
		if(end > list.size())
			end = list.size();

		for(int i = start; i < end; i++) {
			Entity e = list.get(i);
			if(e == null)
				continue;

			if(e.isAgent()) {
				Agent a = (Agent) e;
				long startTime = System.nanoTime();
				a.calculateOutputs();
				Profiler.addRecord("calc inputs", System.nanoTime() - startTime);

				startTime = System.nanoTime();
				a.useBrainOutput();
				Profiler.addRecord("use out", System.nanoTime() - startTime);
			}
			e.tick(world);

			processed++;
		}


	}

}
