package me.brook.selection.tools;

import java.awt.geom.Rectangle2D;

public interface QuadSortable {
	public Vector2 getLocation();

//	public default boolean intersects(Rectangle2D rectangle) {
//		Vector2 loc = getLocation();
//		return rectangle.contains(loc.x, loc.y);
//	}
}