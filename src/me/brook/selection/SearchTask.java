package me.brook.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import me.brook.selection.entity.Entity;

public class SearchTask implements Callable<List<Entity>> {

	private World world;
	private int threadIndex;
	private int segments;

	private long initTime = System.nanoTime();
	
	private Entity entity;

	public SearchTask(World world, Entity entity, int threadIndex, int segments) {
		this.world = world;
		this.entity = entity;
		this.threadIndex = threadIndex;
		this.segments = segments;

		// int start = (int) ((100 / (float) segments) * this.threadIndex);
		// int end = (int) ((100 / (float) segments) * (this.threadIndex+1));
		//
		// System.out.println(start + " -> " + end);

	}

	public void task() {

	}

	@Override
	public List<Entity> call() throws Exception {
		List<Entity> list = new ArrayList<>();

		int size = world.getEntities().size();
		int start = (int) ((size / (float) segments) * this.threadIndex);
		int end = (int) ((size / (float) segments) * (this.threadIndex + 1));
		for(int i = start; i < end; i++) {
			Entity e = world.getEntities().get(i);
			
			if(entity.canEat(e)) {
				list.add(e);
			}

		}
		
		return list;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (initTime ^ (initTime >>> 32));
		result = prime * result + segments;
		result = prime * result + threadIndex;
		result = prime * result + ((world == null) ? 0 : world.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		SearchTask other = (SearchTask) obj;
		if(initTime != other.initTime)
			return false;
		if(segments != other.segments)
			return false;
		if(threadIndex != other.threadIndex)
			return false;
		if(world == null) {
			if(other.world != null)
				return false;
		}
		else if(!world.equals(other.world))
			return false;
		return true;
	}

}
