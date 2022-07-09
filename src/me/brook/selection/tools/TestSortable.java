package me.brook.selection.tools;

import me.brook.selection.entity.body.Hitbox;

public class TestSortable implements QuadSortable {

	private Vector2 location;
	private float size;

	public TestSortable(Vector2 location, float size) {
		this.location = location;
		this.size = size;
	}

	@Override
	public Vector2 getLocation() {
		return location;
	}

	public float getSize() {
		return size;
	}

}