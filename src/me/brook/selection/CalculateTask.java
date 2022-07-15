package me.brook.selection;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.brook.selection.entity.Agent;
import me.brook.selection.entity.Entity;
import me.brook.selection.tools.Profiler;

public class CalculateTask implements Callable<String> {

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
		try {
			run();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	public void run() {
		ConcurrentLinkedQueue<Entity> list = world.getThreadEntities();
		if(list == null)
			return;

		while(!list.isEmpty()) {
			Entity e = list.poll();
			if(e == null)
				continue;

			Agent a = null;
			if(e.isAgent()) {
				a = (Agent) e;

				a.calculateOutputs();
				a.useBrainOutput();
			}
			e.tick(world);

			if(a != null) {
				a.createShadowRegion(world.getEngine().getDisplay().calcShadowWidth(), world.getEngine().getDisplay().calcShadowHeight());
			}

			processed++;
		}

	}

}
